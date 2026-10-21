package `in`.roomcontroller.app

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k,v) -> put(k, v ?: JSONObject.NULL) } }
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.items(key: String) = optJSONArray(key)?.objects() ?: emptyList()
fun JSONObject.copyJson() = JSONObject(toString())

class TokenVault(context: Context, name: String = "room-session") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("room-session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("room-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs.edit().putString("token", Base64.encodeToString(cipher.iv + cipher.doFinal(token.toByteArray()), Base64.NO_WRAP)).apply()
    }
    fun read(): String = runCatching {
        val bytes = Base64.decode(prefs.getString("token", ""), Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            String(doFinal(bytes.copyOfRange(12,bytes.size)))
        }
    }.getOrDefault("")
    fun clear() { prefs.edit().remove("token").apply() }
}

data class RoomUi(
    val signedIn: Boolean = false, val busy: Boolean = false, val state: JSONObject = JSONObject(),
    val error: String? = null, val notice: String? = null, val stale: Boolean = false,
    val demo: Boolean = false, val revision: Long = 0, val pendingAction: String? = null
)

class RoomModel(application: Application): AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("room-settings", Context.MODE_PRIVATE)
    private val vault = TokenVault(application)
    private val pairingVault = TokenVault(application, "room-device-pairing")
    private val configuredDeviceText = application.assets.open("room-device.config").bufferedReader().use { it.readText() }
    private val configuredDevice = DevicePairing.parse(configuredDeviceText)
    var pairing = runCatching { DevicePairing.parse(pairingVault.read()) }.getOrNull()
        ?.takeIf { it == configuredDevice }
        private set
    val directDevice get() = pairing != null && !mutable.value.demo
    private var transportIdentity: DevicePairing? = null
    private var cachedTransport: DeviceTransport? = null
    private fun deviceTransport(): DeviceTransport {
        val identity = pairing ?: error("Connect your ESP32 first")
        if (transportIdentity != identity || cachedTransport == null) {
            cachedTransport = DeviceTransport(getApplication(), identity)
            transportIdentity = identity
        }
        return cachedTransport!!
    }
    private var provisioning = false
    private var roomPollingEnabled = true
    fun pollRoom(enabled: Boolean) { roomPollingEnabled = enabled }
    private var token = vault.read()
    var endpoint: String = prefs.getString("endpoint", "") ?: ""
        private set
    private val mutable = MutableStateFlow(RoomUi(signedIn = token.isNotEmpty() || pairing != null))
    val ui = mutable.asStateFlow()
    private var demoState = demoState()
    private var previewSession = PreviewSession()

    init {
        viewModelScope.launch {
            while (true) {
                if (roomPollingEnabled && mutable.value.signedIn && !mutable.value.demo && !mutable.value.busy && !provisioning) {
                    runCatching { request("GET", "/state") }.onSuccess {
                        mutable.value = mutable.value.copy(state=it, stale=false, revision=System.currentTimeMillis())
                    }.onFailure { if (roomPollingEnabled) mutable.value = mutable.value.copy(stale=true, error=if(mutable.value.stale) mutable.value.error else it.message) }
                }
                delay(if (directDevice) 1500 else 4000)
            }
        }
    }

    fun useBuiltInDevice() = work {
        val text = configuredDeviceText
        val configured = configuredDevice
        val sameDevice = pairing?.fingerprint == configured.fingerprint
        pairingVault.save(text)
        pairing = configured
        token = ""; vault.clear()
        if (!sameDevice || endpoint.isBlank()) endpoint = "https://${configured.setupIp}"
        prefs.edit().putString("endpoint", endpoint).apply()
        provisioning = false
        mutable.value = RoomUi(signedIn=true, busy=true, notice="Your ESP32 is ready to connect. Join its setup Wi-Fi or reconnect on home Wi-Fi.")
    }

    fun connectDevice(origin: String) = work {
        val clean = origin.trim().trimEnd('/')
        check(pairing != null) { "Select Connect my ESP32 first" }
        val state = deviceUiState(deviceTransport().call(clean, "/v1/state"))
        endpoint = clean
        prefs.edit().putString("endpoint", clean).apply()
        provisioning = false
        mutable.value = mutable.value.copy(state=state, stale=false, notice="Connected securely to ESP32")
    }

    fun provisionWifi(ssid: String, password: String) = work {
        require(ssid.toByteArray().size in 1..32 && (password.isEmpty() || password.length in 8..63)) { "Check Wi-Fi name and password length" }
        request("POST", "/v1/command", obj("op" to "wifi", "ssid" to ssid, "password" to password))
        mutable.value = mutable.value.copy(stale=true, notice="Wi-Fi saved. The setup network closes after joining home Wi-Fi. Switch your phone to home Wi-Fi and enter the ESP32 IP from its display or your router's device list.")
    }

    fun login(url: String, username: String, password: String) = work {
        val clean = url.trim().trimEnd('/')
        val uri = URI(clean)
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http")) { "Use an HTTPS server URL" }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty().isEmpty()) { "Enter a server origin, for example https://room.example.com" }
        endpoint = clean
        token = ""
        val result = request("POST", "/auth/login", obj("username" to username, "password" to password))
        token = result.getString("token")
        vault.save(token)
        prefs.edit().putString("endpoint", endpoint).apply()
        val state = request("GET", "/state")
        mutable.value = mutable.value.copy(signedIn=true, state=state, demo=false, stale=false)
    }

    fun demo(scenario: String = "default") {
        demoState = demoState()
        previewSession = PreviewSession()
        previewSession.seed(demoState)
        if (scenario == "spotify-disconnected") demoState.put("spotify_connected", false)
        if (scenario == "controller-offline") demoState.put("connected", false)
        mutable.value = RoomUi(signedIn=true, state=demoState.copyJson(), demo=true,
            stale=scenario == "connection-lost", notice="UI preview only. No hardware or Spotify commands are sent.")
    }

    fun exitPreview() = useBuiltInDevice()

    fun logout() = work {
        if (!mutable.value.demo && !directDevice) runCatching { request("POST", "/auth/logout", obj()) }
        pairingVault.clear(); pairing = null; provisioning = false
        token = ""
        vault.clear()
        mutable.value = RoomUi()
    }

    fun dismiss() { mutable.value = mutable.value.copy(error=null, notice=null) }

    fun work(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy=true, error=null, notice=null)
        viewModelScope.launch {
            try { block() } catch(e: Exception) { mutable.value = mutable.value.copy(error=e.message ?: "Request failed") }
            finally { mutable.value = mutable.value.copy(busy=false) }
        }
    }

    suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        if (mutable.value.demo) return demoRequest(method, path, body)
        if (directDevice) return deviceRequest(method, path, body)
        return withContext(Dispatchers.IO) {
            val connection = URL(endpoint + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 7000
                connection.readTimeout = 12000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
                if (method == "POST") connection.setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (code !in 200..299) {
                    if (code == 401 && !path.startsWith("/spotify")) {
                        vault.clear(); token = ""
                        mutable.value = mutable.value.copy(signedIn=false)
                    }
                    error(json.optString("detail", "Server returned $code"))
                }
                json
            } finally { connection.disconnect() }
        }
    }

    private suspend fun deviceRequest(method: String, path: String, body: JSONObject?): JSONObject {
        val transport = deviceTransport()
        if (path == "/state") return deviceUiState(transport.call(endpoint, "/v1/state"))
        if (path == "/v1/command") return transport.call(endpoint, path, body)
        val parts = path.substringBefore('?').trim('/').split('/')
        val collection = parts.first()
        val confirmed = path.contains("confirm=true")
        val command = when {
            path == "/actions" -> when (val type = body!!.getString("type")) {
                "lighting" -> obj("op" to type, "value" to body.getJSONObject(type))
                "microphone" -> obj("op" to type, "value" to body.getJSONObject(type).copyJson().apply {
                    put("noise_floor", getDouble("noise_floor") / 100)
                    put("smoothing", (getDouble("smoothing") / 100).coerceAtMost(.99))
                    remove("bass_gain"); remove("mid_gain"); remove("treble_gain")
                })
                "ir" -> obj("op" to "transmit", "id" to body.getString("command_id"))
                "calibrate" -> obj("op" to "calibrate")
                else -> error("Unsupported device action: $type")
            }
            collection in listOf("appliances", "commands", "presets", "rules") && method in listOf("POST", "PUT", "DELETE") -> {
                val value = body?.copyJson()
                if (collection == "rules") value?.remove("timezone") // Firmware uses one controller timezone.
                obj("op" to if(method == "DELETE") "delete" else "put", "collection" to collection,
                    "id" to parts.getOrNull(1).orEmpty(), "value" to value, "confirm" to confirmed)
            }
            else -> error("This workflow is unavailable on the direct device connection")
        }
        return transport.call(endpoint, "/v1/command", command)
    }

    fun spotifyCommand(action: String, argument: String = "", value: Int = 0) = work {
        val label = when (action) {
            "play" -> "Starting playback"
            "pause" -> "Pausing playback"
            "next" -> "Skipping to next track"
            "previous" -> "Returning to previous track"
            "volume" -> "Setting volume"
            "refresh" -> "Refreshing playback"
            "devices" -> "Finding Spotify devices"
            "transfer" -> "Selecting playback device"
            "search" -> "Searching Spotify"
            else -> "Updating Spotify"
        }
        mutable.value = mutable.value.copy(pendingAction=label)
        try {
            val accepted = request("POST", "/v1/command", obj("op" to "spotify", "action" to action, "argument" to argument, "value" to value))
            val id = accepted.optLong("job_id")
            val bootId = accepted.optLong("boot_id")
            if (id == 0L || bootId == 0L) {
                refresh()
                mutable.value = mutable.value.copy(notice="Sent to controller. Update its firmware to track completion.")
            } else {
                val job = withTimeoutOrNull(25_000) {
                    awaitDeviceJob(id, bootId, read={ jobId ->
                        request("POST", "/v1/command", obj("op" to "job", "id" to jobId))
                    })
                } ?: error("Spotify hasn't confirmed the result yet. Check playback before sending the action again.")
                val fresh = refreshCompletedDeviceJob(job) { refresh() }
                // Updated state replaces the progress indicator; successful taps need no snackbar.
                mutable.value = mutable.value.copy(stale=!fresh, notice=if(fresh) null else
                    "Spotify accepted the request. Refresh playback to update this screen.")
            }
        } finally {
            mutable.value = mutable.value.copy(pendingAction=null)
        }
    }

    suspend fun refresh() {
        val state = request("GET", "/state")
        mutable.value = mutable.value.copy(state=state, stale=false, revision=System.currentTimeMillis())
    }

    fun mutate(method: String, path: String, body: JSONObject? = null, success: String = "Saved") = work {
        val response = request(method, path, body)
        refresh()
        val id = response.optString("id")
        if (path == "/actions" && response.optString("status") == "pending" && id.isNotEmpty()) {
            var status = "pending"
            var detail = ""
            for (attempt in 0..16) {
                delay(1000)
                val job = request("GET", "/jobs/$id")
                status = job.optString("status")
                detail = job.optString("detail")
                if (status !in listOf("pending", "delivered")) break
            }
            mutable.value = mutable.value.copy(notice=when(status) {
                "transmitted" -> "IR transmitted. Appliance state is unconfirmed."
                "applied" -> success
                else -> "Command $status. $detail No automatic retry."
            })
        } else mutable.value = mutable.value.copy(notice=if (mutable.value.demo || response.optString("status") == "simulated") "Simulated Ãƒâ€šÃ‚Â· $success" else if(directDevice && path == "/actions" && body?.optString("type") == "ir") "IR queued. Check device status for completion; appliance state is unconfirmed." else success)
    }

    private fun demoRequest(method: String, path: String, body: JSONObject?): JSONObject {
        if (path == "/state") return demoState.copyJson()
        previewSession.request(method, path, body, demoState)?.let { return it }
        if (path == "/actions") {
            when(body?.optString("type")) {
                "lighting" -> demoState.put("lighting", body.getJSONObject("lighting"))
                "microphone" -> demoState.put("microphone", body.getJSONObject("microphone"))
            }
            return obj("status" to "simulated")
        }
        val kind = path.substringBefore('?').split('/').getOrNull(1).orEmpty()
        if (kind in listOf("appliances", "commands", "rules", "presets")) {
            val list = demoState.items(kind).toMutableList()
            val id = path.substringBefore('?').split('/').getOrNull(2)
            if (method == "DELETE") list.removeAll { it.optString("id") == id }
            if (method == "PUT") { list.removeAll { it.optString("id") == id }; list.add(body!!.copyJson().put("id", id)) }
            if (method == "POST") list.add(body!!.copyJson().put("id", UUID.randomUUID().toString()))
            demoState.put(kind, JSONArray(list))
            return obj("status" to "simulated")
        }
        if (path.startsWith("/settings")) { demoState.put("system", body); return obj() }
        if (path == "/diagnostics") return obj("events" to JSONArray().put(obj("category" to "demo", "message" to "Interface preview Ãƒâ€šÃ‚Â· no ESP32 connected")), "jobs" to JSONArray())
        error("Connect a real server or the backend simulator to use this workflow.")
    }
}

fun demoState() = JSONObject("""{
  "simulator":true,"connected":true,"capabilities":["lighting","ir","microphone","learn"],
  "lighting":{"power":true,"color":"#B5F36A","brightness":65,"effect":"solid","speed":50,"intensity":60},
  "microphone":{"automatic":true,"sensitivity":50,"noise_floor":15,"smoothing":60,"quiet_behavior":"dim","quiet_seconds":10},
  "system":{"automation_enabled":true,"timezone":"UTC","display_timeout":30,"volume_target":"spotify"},
  "appliances":[{"id":"fan","name":"Ceiling fan","kind":"fan"},{"id":"speaker","name":"Desk speaker","kind":"speaker"}],
  "commands":[{"id":"power","appliance_id":"fan","name":"Power","favorite":true,"toggle":true,"carrier_khz":38,"protocol":"RAW","decoded":"","raw":[9000,4500,560,560],"repeat_raw":[]}],
  "presets":[{"id":"focus","name":"Focus","lighting":{"power":true,"color":"#F5D5A0","brightness":75,"effect":"solid","speed":50,"intensity":60}},{"id":"listen","name":"Listen","lighting":{"power":true,"color":"#AE9CFF","brightness":60,"effect":"bass","speed":50,"intensity":60}}],
  "rules":[],"spotify_connected":false,"hardware_pending":["Hardware configuration awaiting verification"],
  "telemetry":{"firmware":"Preview","rssi":-48,"free_heap":181248,"uptime":3600,"time_valid":true,"bands":[0.3,0.6,0.4]}
}""")
