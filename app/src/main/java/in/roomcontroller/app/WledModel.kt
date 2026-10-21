package `in`.roomcontroller.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

data class WledUi(
    val devices: List<WledDevice> = emptyList(), val selected: String? = null,
    val snapshot: WledSnapshot? = null, val presets: JSONObject = JSONObject(),
    val online: Boolean = false, val busy: Boolean = false, val discovering: Boolean = false,
    val error: String? = null, val notice: String? = null,
) {
    val device get() = devices.firstOrNull { it.id == selected }
}

class WledModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("room-wled", Context.MODE_PRIVATE)
    private val saved = runCatching { JSONArray(prefs.getString("devices", "[]")) }.getOrDefault(JSONArray())
        .objects().mapNotNull { runCatching { WledDevice.parse(it) }.getOrNull() }
    private val mutable = MutableStateFlow(WledUi(saved, prefs.getString("selected", null)))
    val ui = mutable.asStateFlow()
    private val commands = Mutex()
    private var generation = 0
    private var socket: WebSocket? = null
    private var monitor: Job? = null
    private var discovery: WledDiscovery? = null
    private var cachedApi: WledApi? = null
    private var cachedNetwork: android.net.Network? = null

    init { mutable.value.device?.let { select(it.id) } }

    private fun api(): WledApi {
        val manager = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val wifi = manager.allNetworks.firstOrNull {
            manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (cachedApi != null && cachedNetwork == wifi) return cachedApi!!
        socket?.cancel(); socket = null
        cachedApi?.client?.connectionPool?.evictAll()
        val base = WledApi().client.newBuilder()
        if (wifi != null) base.socketFactory(wifi.socketFactory).dns(object : okhttp3.Dns {
            override fun lookup(hostname: String) = wifi.getAllByName(hostname).toList()
        })
        return WledApi(base.build()).also { cachedApi = it; cachedNetwork = wifi }
    }

    private fun persist() {
        prefs.edit().putString("devices", JSONArray().apply { mutable.value.devices.forEach { put(it.json()) } }.toString())
            .putString("selected", mutable.value.selected).apply()
        WledWidget.refresh(getApplication())
    }

    fun dismiss() { mutable.value = mutable.value.copy(error = null, notice = null) }

    /** Same-board migration: probe WLED's public HTTP port without sending Room credentials. */
    fun probeRoomPeer(roomOrigin: String) {
        if (mutable.value.device != null || mutable.value.busy) return
        viewModelScope.launch {
            runCatching {
                val host = java.net.URI(roomOrigin).host ?: return@launch
                val origin = java.net.URI("http", null, host, -1, null, null, null).toASCIIString()
                val value = api().state(origin)
                if (mutable.value.device != null || mutable.value.busy) return@launch
                val id = wledDeviceId(value.info)
                mutable.value = mutable.value.copy(devices = mutable.value.devices.filterNot { it.id == id } +
                    WledDevice(id, origin, value.info.optString("name", "Room lighting")))
                select(id)
            }
        }
    }

    fun add(address: String) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try {
                val origin = wledOrigin(address)
                val value = api().state(origin)
                val id = wledDeviceId(value.info)
                val previous = mutable.value.devices.firstOrNull { it.id == id }
                val device = WledDevice(id, origin, previous?.name ?: value.info.optString("name", "Room lighting"))
                mutable.value = mutable.value.copy(devices = mutable.value.devices.filterNot { it.id == id } + device, busy = false)
                select(id)
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(busy = false, error = e.message ?: "Could not connect to WLED")
            }
        }
    }

    fun select(id: String) {
        val device = mutable.value.devices.firstOrNull { it.id == id } ?: return
        generation++
        val expected = generation
        socket?.cancel(); socket = null; monitor?.cancel()
        mutable.value = mutable.value.copy(selected = id, snapshot = null, presets = JSONObject(), online = false, error = null)
        persist()
        monitor = viewModelScope.launch {
            var lastError: String? = null
            while (generation == expected) {
                try {
                    val client = api()
                    val snapshot = client.state(device.origin)
                    check(wledDeviceId(snapshot.info) == device.id) { "A different WLED device now uses this address. Add it again to verify its identity." }
                    if (generation != expected) { break }
                    mutable.value = mutable.value.copy(snapshot = snapshot, online = true, error = null)
                    if (socket == null) {
                        runCatching { openSocket(client.client, device, expected) }
                            .onFailure { android.util.Log.w("RoomWLED", "WebSocket unavailable; using HTTP state refresh", it) }
                    }
                    runCatching { JSONObject(client.request(device.origin, "/presets.json")) }.onSuccess {
                        if (generation == expected) { mutable.value = mutable.value.copy(presets = it) }
                    }.onFailure { android.util.Log.w("RoomWLED", "Preset shortcuts could not be loaded", it) }
                    lastError = null
                } catch (e: Exception) {
                    if (generation != expected) break
                    mutable.value = mutable.value.copy(online = false,
                        error = if (lastError != e.message) e.message ?: "WLED is offline" else mutable.value.error)
                    lastError = e.message
                }
                delay(if (socket != null) 15_000 else 4_000)
            }
        }
    }

    private fun openSocket(client: OkHttpClient, device: WledDevice, expected: Int) {
        val url = device.origin.replaceFirst("http", "ws") + "/ws"
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > 2_097_152) return
                val value = runCatching { WledSnapshot.parse(JSONObject(text)) }.getOrNull() ?: return
                viewModelScope.launch {
                    if (expected == generation && wledDeviceId(value.info) == device.id)
                        mutable.value = mutable.value.copy(snapshot = value, online = true)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                viewModelScope.launch { if (expected == generation && socket === webSocket) socket = null }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                viewModelScope.launch { if (expected == generation && socket === webSocket) socket = null }
            }
        })
    }

    fun command(body: JSONObject) {
        val device = mutable.value.device ?: return
        if (mutable.value.busy || !mutable.value.online) return
        val expected = generation
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            commands.withLock {
                try {
                    val value = api().command(device.origin, body)
                    check(wledDeviceId(value.info) == device.id) { "WLED identity changed" }
                    if (expected == generation) mutable.value = mutable.value.copy(snapshot = value, online = true)
                } catch (e: Exception) {
                    if (expected == generation) mutable.value = mutable.value.copy(error = "${e.message}. Refresh to check the result before trying again.")
                } finally {
                    mutable.value = mutable.value.copy(busy = false)
                    WledWidget.refresh(getApplication())
                }
            }
        }
    }

    fun rename(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 64) return
        mutable.value = mutable.value.copy(devices = mutable.value.devices.map { if (it.id == id) it.copy(name = clean) else it })
        persist()
    }

    fun hide(id: String, hidden: Boolean) {
        mutable.value = mutable.value.copy(devices = mutable.value.devices.map { if (it.id == id) it.copy(hidden = hidden) else it })
        persist()
    }

    fun remove(id: String) {
        val wasSelected = mutable.value.selected == id
        if (wasSelected) { generation++; socket?.cancel(); socket = null; monitor?.cancel() }
        mutable.value = mutable.value.copy(devices = mutable.value.devices.filterNot { it.id == id },
            selected = if (wasSelected) null else mutable.value.selected,
            snapshot = if (wasSelected) null else mutable.value.snapshot,
            online = !wasSelected && mutable.value.online)
        persist()
    }

    fun discover() {
        if (mutable.value.discovering) return
        mutable.value = mutable.value.copy(discovering = true, error = null, notice = "Looking for WLED controllers on Wi-Fi…")
        discovery = WledDiscovery(getApplication(), { address ->
            viewModelScope.launch {
                runCatching {
                    val snapshot = api().state(address)
                    val id = wledDeviceId(snapshot.info)
                    val previous = mutable.value.devices.firstOrNull { it.id == id }
                    val value = WledDevice(id, address, previous?.name ?: snapshot.info.optString("name", "WLED"), previous?.hidden ?: false)
                    mutable.value = mutable.value.copy(devices = mutable.value.devices.filterNot { it.id == id } + value)
                    persist()
                    if (mutable.value.selected == null) select(id)
                }
            }
        }, { reason -> mutable.value = mutable.value.copy(error = reason) }).also { it.start() }
        // The default WLED setup AP has no Internet and may have no mDNS responder visible to Android.
        viewModelScope.launch {
            runCatching {
                val origin = "http://4.3.2.1"
                val value = api().state(origin)
                val id = wledDeviceId(value.info)
                if (mutable.value.devices.none { it.id == id }) {
                    mutable.value = mutable.value.copy(devices = mutable.value.devices + WledDevice(id, origin, value.info.optString("name", "WLED setup")))
                    persist()
                }
                if (mutable.value.selected == null) select(id)
            }
        }
        viewModelScope.launch {
            delay(12_000)
            discovery?.stop(); discovery = null
            mutable.value = mutable.value.copy(discovering = false, notice = if (mutable.value.devices.isEmpty())
                "No WLED controllers found. Join its Wi-Fi or enter its address." else "Discovery finished")
        }
    }

    override fun onCleared() {
        generation++; monitor?.cancel(); socket?.cancel(); discovery?.stop()
        cachedApi?.client?.connectionPool?.evictAll()
        cachedApi?.client?.dispatcher?.executorService?.shutdown()
        super.onCleared()
    }
}

/** Serialize legacy NSD resolve calls; overlapping resolutions fail on older supported Android versions. */
@Suppress("DEPRECATION")
private class WledDiscovery(context: Context, private val found: (String) -> Unit, private val error: (String) -> Unit) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var running = false
    private var multicast = context.applicationContext.getSystemService(WifiManager::class.java)
        .createMulticastLock("room-wled-discovery").apply { setReferenceCounted(false) }
    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) {}
        override fun onStartDiscoveryFailed(type: String, code: Int) { handler.post { error("Wi-Fi discovery could not start ($code). Enter the controller address instead."); stop() } }
        override fun onStopDiscoveryFailed(type: String, code: Int) {}
        override fun onServiceFound(info: NsdServiceInfo) { handler.post { if (running) { queue.addLast(info); next() } } }
        override fun onServiceLost(info: NsdServiceInfo) {}
    }
    fun start() {
        running = true
        try { multicast.acquire(); manager.discoverServices("_wled._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (e: Exception) { error(e.message ?: "Discovery unavailable"); stop() }
    }
    private fun next() {
        if (!running || resolving || queue.isEmpty()) return
        resolving = true
        try {
            manager.resolveService(queue.removeFirst(), object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) { handler.post { resolving = false; next() } }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    handler.post {
                        if (running) {
                            info.host?.hostAddress?.let { host ->
                                runCatching { wledOrigin(URIForHost(host, info.port)) }.onSuccess(found)
                            }
                        }
                        resolving = false
                        next()
                    }
                }
            })
        } catch (_: Exception) { resolving = false; next() }
    }
    fun stop() {
        if (running) runCatching { manager.stopServiceDiscovery(listener) }
        running = false; queue.clear()
        if (multicast.isHeld) multicast.release()
    }
}

private fun URIForHost(host: String, port: Int) = java.net.URI("http", null, host, port, null, null, null).toASCIIString()
