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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

const val MAX_FAVORITE_LIMIT = 4

private fun sanitizeSnapshotSegments(snap: WledSnapshot): WledSnapshot {
    if (snap.segments.size <= 1) return snap
    val hasLegacyPinkSplit = snap.segments.any {
        !it.sel || it.name in setOf("Desk Backlight", "Ceiling Cove B")
    }
    if (!hasLegacyPinkSplit) return snap
    val primary = snap.primarySegment
    val primaryCol = JSONArray().apply {
        primary.colors.forEach { slot -> put(JSONArray(slot)) }
    }
    val nextState = snap.state.copyJson()
    val segArr = nextState.optJSONArray("seg") ?: return snap
    for (i in 0 until segArr.length()) {
        val segObj = segArr.optJSONObject(i) ?: continue
        segObj.put("sel", true)
        if (segObj.optString("n") in setOf("Desk Backlight", "Ceiling Cove B")) {
            segObj.put("col", JSONArray(primaryCol.toString()))
            segObj.put("fx", primary.fx)
            segObj.put("pal", primary.pal)
        }
    }
    return WledSnapshot.parse(obj("state" to nextState, "info" to snap.info))
}

data class WledUi(
    val devices: List<WledDevice> = emptyList(),
    val selected: String? = null,
    val snapshot: WledSnapshot? = WledCatalogDefaults.defaultSnapshot(),
    val presets: JSONObject = WledCatalogDefaults.defaultPresets(),
    val presetsRevision: Long = 0L,
    val effects: List<String> = WledCatalogDefaults.EFFECTS,
    val palettes: List<String> = WledCatalogDefaults.PALETTES,
    val fxdata: List<String> = WledCatalogDefaults.FXDATA,
    val config: JSONObject? = WledCatalogDefaults.defaultConfig(),
    val activeTab: String = "Colors",
    val pcMode: Boolean = false,
    val colorSlot: Int = 0,
    val effectFilter: String = "All",
    val configSection: String = "Wi-Fi Setup",
    val homePresetCategory: String = PresetCategories.ALL,
    val presetCategories: Map<String, String> = emptyMap(),
    val favoriteEffects: Set<Int> = setOf(0, 65),
    val favoritePalettes: Set<Int> = setOf(0, 50),
    val favoritePresets: Set<Int> = setOf(1, 2),
    val favoriteColors: Set<String> = setOf("#B5F36A", "#FFD494"),
    val favoriteJumpTrigger: Long = 0L,
    val lastFavoritedKey: String? = null,
    val favoriteLimitWarning: String? = null,
    val livePreviewEnabled: Boolean = false,
    val liveLeds: List<Int> = emptyList(),
    val online: Boolean = false,
    val wsConnected: Boolean = false,
    val busy: Boolean = false,
    val discovering: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
) {
    val device get() = devices.firstOrNull { it.id == selected }
    val effectiveSnapshot: WledSnapshot get() = snapshot ?: WledCatalogDefaults.defaultSnapshot()
    val effectiveConfig: JSONObject get() = config ?: WledCatalogDefaults.defaultConfig()
    val effectiveEffects: List<String> get() = effects.ifEmpty { WledCatalogDefaults.EFFECTS }
    val effectivePalettes: List<String> get() = palettes.ifEmpty { WledCatalogDefaults.PALETTES }
    val effectiveFxdata: List<String> get() = fxdata.ifEmpty { WledCatalogDefaults.FXDATA }
    val presetEntries: List<WledPresetEntry> = parseWledPresets(presets)

    fun categoryForWledPreset(entry: WledPresetEntry): String =
        PresetCategories.inferForWledPreset(entry, presetCategories)

    fun categoryForRoomPreset(preset: JSONObject): String =
        PresetCategories.inferForRoomPreset(preset, presetCategories)

    fun effectMetadata(fxId: Int): WledEffectMetadata {
        val effs = effectiveEffects
        val fxd = effectiveFxdata
        val name = effs.getOrElse(fxId) { if (fxId == 0) "Solid" else "Effect #$fxId" }
        val raw = fxd.getOrNull(fxId)
        return WledEffectMetadata.parse(fxId, name, raw)
    }
}

class WledModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("room-wled", Context.MODE_PRIVATE)
    private val dataStore = RoomDataStore(application)
    private val saved = runCatching { JSONArray(prefs.getString("devices", "[]")) }.getOrDefault(JSONArray())
        .objects().mapNotNull { runCatching { WledDevice.parse(it) }.getOrNull() }
    private val savedFavEffects = runCatching {
        val raw = prefs.getStringSet("favorite_effects", null)?.mapNotNull { it.toIntOrNull() }?.toSet()
        if (raw == null || raw == setOf(0, 2, 9, 45, 65, 66, 80, 101, 139, 155) || raw.size > MAX_FAVORITE_LIMIT) {
            setOf(0, 65)
        } else raw
    }.getOrDefault(setOf(0, 65))
    private val savedFavPalettes = runCatching {
        val raw = prefs.getStringSet("favorite_palettes", null)?.mapNotNull { it.toIntOrNull() }?.toSet()
        if (raw == null || raw == setOf(0, 11, 13, 35, 50) || raw.size > MAX_FAVORITE_LIMIT) {
            setOf(0, 50)
        } else raw
    }.getOrDefault(setOf(0, 50))
    private val savedFavPresets = runCatching {
        val raw = prefs.getStringSet("favorite_presets", null)?.mapNotNull { it.toIntOrNull() }?.toSet()
        if (raw == null || raw == setOf(1, 2, 3, 4) || raw.size > MAX_FAVORITE_LIMIT) {
            setOf(1, 2)
        } else raw
    }.getOrDefault(setOf(1, 2))
    private val savedFavColors = runCatching {
        val raw = prefs.getStringSet("favorite_colors", null)?.toSet()
        if (raw == null || raw == setOf("#B5F36A", "#FFD494", "#00E5FF") || raw.size > MAX_FAVORITE_LIMIT) {
            setOf("#B5F36A", "#FFD494")
        } else raw
    }.getOrDefault(setOf("#B5F36A", "#FFD494"))

    private val savedSnapshot = runCatching {
        val raw = prefs.getString("saved_snapshot", null)
        if (!raw.isNullOrBlank()) sanitizeSnapshotSegments(WledSnapshot.parse(JSONObject(raw))) else null
    }.getOrNull() ?: WledCatalogDefaults.defaultSnapshot()

    private val savedPresets = runCatching {
        val raw = prefs.getString("saved_presets", null)
        if (!raw.isNullOrBlank()) JSONObject(raw) else null
    }.getOrNull() ?: WledCatalogDefaults.defaultPresets()

    private val savedConfig = runCatching {
        val raw = prefs.getString("saved_config", null)
        if (!raw.isNullOrBlank()) JSONObject(raw) else null
    }.getOrNull() ?: WledCatalogDefaults.defaultConfig()

    private val savedPresetCategories = PresetCategories.parseCategoryMap(prefs.getString("preset_categories_json", null))

    private val mutable = MutableStateFlow(
        WledUi(
            devices = saved,
            selected = prefs.getString("selected", null),
            snapshot = savedSnapshot,
            presets = savedPresets,
            config = savedConfig,
            activeTab = prefs.getString("active_tab", "Colors") ?: "Colors",
            pcMode = prefs.getBoolean("pc_mode", false),
            colorSlot = prefs.getInt("color_slot", 0).coerceIn(0, 2),
            effectFilter = prefs.getString("effect_filter", "All") ?: "All",
            configSection = prefs.getString("config_section", "Wi-Fi Setup") ?: "Wi-Fi Setup",
            homePresetCategory = prefs.getString("home_preset_category", PresetCategories.ALL) ?: PresetCategories.ALL,
            presetCategories = savedPresetCategories,
            favoriteEffects = savedFavEffects,
            favoritePalettes = savedFavPalettes,
            favoritePresets = savedFavPresets,
            favoriteColors = savedFavColors
        )
    )
    val ui = mutable.asStateFlow()
    private val commands = Mutex()
    private val coalesceJobs = mutableMapOf<String, Job>()
    private var persistJob: Job? = null
    private val pendingEsp32Payloads = mutableListOf<JSONObject>()
    private val userSavedPresets = mutableMapOf<String, JSONObject>()
    private val userDeletedPresetIds = mutableSetOf<String>()
    private var generation = 0
    private var socket: WebSocket? = null
    private var monitor: Job? = null
    private var discovery: WledDiscovery? = null
    private var cachedApi: WledApi? = null
    private var cachedNetwork: android.net.Network? = null

    private fun mergePresetsPreservingUserSaves(remote: JSONObject, currentLocal: JSONObject): JSONObject {
        val merged = JSONObject()
        merged.put("0", JSONObject())
        // 1. Start with current local slots (atomic per preset ID so playlist step arrays are never partially merged)
        val localKeys = currentLocal.keys()
        while (localKeys.hasNext()) {
            val k = localKeys.next()
            if (k == "0" || k in userDeletedPresetIds) continue
            currentLocal.optJSONObject(k)?.let { merged.put(k, it.copyJson()) }
        }
        // 2. Apply remote slots unless the user explicitly saved or deleted that slot locally in this session
        val remoteKeys = remote.keys()
        while (remoteKeys.hasNext()) {
            val k = remoteKeys.next()
            if (k == "0" || k in userDeletedPresetIds || k in userSavedPresets) continue
            remote.optJSONObject(k)?.takeIf { it.length() > 0 }?.let { merged.put(k, it.copyJson()) }
        }
        // 3. Re-assert user-saved presets/playlists so asynchronous WLED /presets.json writes never revert user edits
        for ((k, v) in userSavedPresets) {
            merged.put(k, v.copyJson())
        }
        return merged
    }

    init {
        // Hydrate from DataStore once on startup only — never echo background saves back onto live sliders!
        viewModelScope.launch {
            hydrateFromDataStoreInternal()
        }
        mutable.value.device?.let { select(it.id) }
    }

    private suspend fun hydrateFromDataStoreInternal(forceOverwrite: Boolean = false) {
        val ds = runCatching { dataStore.wledFlow.first() }.getOrNull() ?: return
        val cur = mutable.value
        val hydratedDevices = ds.devicesJson?.let { raw ->
            runCatching { JSONArray(raw).objects().mapNotNull { runCatching { WledDevice.parse(it) }.getOrNull() } }.getOrNull()
        }
        val hydratedSnap = if ((!cur.online || forceOverwrite) && !ds.savedSnapshotJson.isNullOrBlank()) {
            runCatching { sanitizeSnapshotSegments(WledSnapshot.parse(JSONObject(ds.savedSnapshotJson))) }.getOrNull()
        } else null
        val hydratedPresets = if ((!cur.online || forceOverwrite) && !ds.savedPresetsJson.isNullOrBlank()) {
            runCatching { JSONObject(ds.savedPresetsJson) }.getOrNull()
        } else null
        val hydratedConfig = if ((!cur.online || forceOverwrite) && !ds.savedConfigJson.isNullOrBlank()) {
            runCatching { JSONObject(ds.savedConfigJson) }.getOrNull()
        } else null

        val updated = cur.copy(
            devices = if (!hydratedDevices.isNullOrEmpty() && (cur.devices.isEmpty() || forceOverwrite)) hydratedDevices else cur.devices,
            selected = if (forceOverwrite) (ds.selectedDeviceId ?: cur.selected) else (cur.selected ?: ds.selectedDeviceId),
            snapshot = hydratedSnap ?: cur.snapshot,
            presets = hydratedPresets ?: cur.presets,
            presetsRevision = if (hydratedPresets != null) System.currentTimeMillis() else cur.presetsRevision,
            config = hydratedConfig ?: cur.config,
            activeTab = ds.activeTab ?: cur.activeTab,
            pcMode = ds.pcMode ?: cur.pcMode,
            colorSlot = (ds.colorSlot ?: cur.colorSlot).coerceIn(0, 2),
            effectFilter = ds.effectFilter ?: cur.effectFilter,
            configSection = ds.configSection ?: cur.configSection,
            homePresetCategory = ds.homePresetCategory ?: cur.homePresetCategory,
            presetCategories = ds.presetCategories ?: cur.presetCategories,
            favoriteEffects = ds.favoriteEffects?.takeIf { it.size <= MAX_FAVORITE_LIMIT && it != setOf(0, 2, 9, 45, 65, 66, 80, 101, 139, 155) } ?: cur.favoriteEffects,
            favoritePalettes = ds.favoritePalettes?.takeIf { it.size <= MAX_FAVORITE_LIMIT && it != setOf(0, 11, 13, 35, 50) } ?: cur.favoritePalettes,
            favoritePresets = ds.favoritePresets?.takeIf { it.size <= MAX_FAVORITE_LIMIT && it != setOf(1, 2, 3, 4) } ?: cur.favoritePresets,
            favoriteColors = ds.favoriteColors?.takeIf { it.size <= MAX_FAVORITE_LIMIT && it != setOf("#B5F36A", "#FFD494", "#00E5FF") } ?: cur.favoriteColors
        )
        if (updated != cur) {
            mutable.value = updated
        }
    }

    fun reloadFromDataStore() {
        viewModelScope.launch {
            hydrateFromDataStoreInternal(forceOverwrite = true)
            persist(updateWidget = true)
        }
    }

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

    private fun persist(updateWidget: Boolean = true) {
        val cur = mutable.value
        persistJob?.cancel()
        persistJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            delay(40)
            val snapJson = cur.snapshot?.let { obj("state" to it.state, "info" to it.info).toString() }
            val categoriesJson = PresetCategories.serializeCategoryMap(cur.presetCategories)
            val presetsStr = if (cur.presets.length() > 0) cur.presets.toString() else null
            val configStr = cur.config?.toString()
            prefs.edit()
                .putString("devices", JSONArray().apply { cur.devices.forEach { put(it.json()) } }.toString())
                .putString("selected", cur.selected)
                .putString("active_tab", cur.activeTab)
                .putBoolean("pc_mode", cur.pcMode)
                .putInt("color_slot", cur.colorSlot)
                .putString("effect_filter", cur.effectFilter)
                .putString("config_section", cur.configSection)
                .putString("home_preset_category", cur.homePresetCategory)
                .putString("preset_categories_json", categoriesJson)
                .putStringSet("favorite_effects", cur.favoriteEffects.map { it.toString() }.toSet())
                .putStringSet("favorite_palettes", cur.favoritePalettes.map { it.toString() }.toSet())
                .putStringSet("favorite_presets", cur.favoritePresets.map { it.toString() }.toSet())
                .putStringSet("favorite_colors", cur.favoriteColors)
                .apply {
                    if (snapJson != null) putString("saved_snapshot", snapJson)
                    if (presetsStr != null) putString("saved_presets", presetsStr)
                    if (configStr != null) putString("saved_config", configStr)
                }
                .apply()
            dataStore.saveWledUi(cur)
            if (updateWidget) {
                runCatching { WledWidget.refresh(getApplication()) }
            }
        }
    }

    fun dismiss() {
        mutable.value = mutable.value.copy(error = null, notice = null)
    }

    fun dismissFavoriteWarning() {
        mutable.value = mutable.value.copy(favoriteLimitWarning = null)
    }

    fun setActiveTab(tab: String) {
        if (mutable.value.activeTab == tab && !mutable.value.pcMode) return
        mutable.value = mutable.value.copy(activeTab = tab, pcMode = false)
        persist(updateWidget = false)
    }

    fun setPcMode(enabled: Boolean) {
        if (mutable.value.pcMode == enabled) return
        mutable.value = mutable.value.copy(pcMode = enabled)
        persist(updateWidget = false)
    }

    fun setColorSlot(slot: Int) {
        val coerced = slot.coerceIn(0, 2)
        if (mutable.value.colorSlot == coerced) return
        mutable.value = mutable.value.copy(colorSlot = coerced)
        persist(updateWidget = false)
    }

    fun setEffectFilter(filter: String) {
        if (mutable.value.effectFilter == filter) return
        mutable.value = mutable.value.copy(effectFilter = filter)
        persist(updateWidget = false)
    }

    fun setConfigSection(section: String) {
        if (mutable.value.configSection == section) return
        mutable.value = mutable.value.copy(configSection = section)
        persist(updateWidget = false)
    }

    fun showFavoriteLimitWarning(msg: String) {
        mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
    }

    fun setHomePresetCategory(category: String) {
        mutable.value = mutable.value.copy(homePresetCategory = category)
        persist()
    }

    fun setPresetCategory(presetKey: String, category: String?) {
        val next = mutable.value.presetCategories.toMutableMap()
        if (category.isNullOrBlank() || category == "Auto") {
            next.remove(presetKey)
        } else {
            next[presetKey] = category
        }
        mutable.value = mutable.value.copy(
            presetCategories = next,
            notice = if (category.isNullOrBlank() || category == "Auto") "Preset category reset to Auto" else "Assigned preset to $category"
        )
        persist()
    }

    fun toggleFavoriteEffect(fxId: Int, jumpToHomeOnAdd: Boolean = true) {
        val current = mutable.value.favoriteEffects
        val added = fxId !in current
        val fxName = mutable.value.effectiveEffects.getOrElse(fxId) { "FX #$fxId" }
        if (added && current.size >= MAX_FAVORITE_LIMIT) {
            val msg = "Favorite limit reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT). Please remove a favorite effect first before adding '$fxName'."
            mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
            return
        }
        val next = if (added) current + fxId else current - fxId
        mutable.value = mutable.value.copy(
            favoriteEffects = next,
            favoriteJumpTrigger = if (added && jumpToHomeOnAdd) System.currentTimeMillis() else mutable.value.favoriteJumpTrigger,
            lastFavoritedKey = if (added) "fx:$fxId" else mutable.value.lastFavoritedKey,
            notice = if (added) "Added '$fxName' to Home Quick Presets & Favorites (${next.size}/$MAX_FAVORITE_LIMIT)" else "Removed '$fxName' from favorites"
        )
        persist()
    }

    fun toggleFavoritePalette(palId: Int, jumpToHomeOnAdd: Boolean = true) {
        val current = mutable.value.favoritePalettes
        val added = palId !in current
        val palName = mutable.value.effectivePalettes.getOrElse(palId) { "Palette #$palId" }
        if (added && current.size >= MAX_FAVORITE_LIMIT) {
            val msg = "Favorite limit reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT). Please remove a favorite palette first before adding '$palName'."
            mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
            return
        }
        val next = if (added) current + palId else current - palId
        mutable.value = mutable.value.copy(
            favoritePalettes = next,
            favoriteJumpTrigger = if (added && jumpToHomeOnAdd) System.currentTimeMillis() else mutable.value.favoriteJumpTrigger,
            lastFavoritedKey = if (added) "pal:$palId" else mutable.value.lastFavoritedKey,
            notice = if (added) "Added '$palName' to Home Quick Presets & Favorites (${next.size}/$MAX_FAVORITE_LIMIT)" else "Removed '$palName' from favorites"
        )
        persist()
    }

    fun toggleFavoritePreset(presetId: Int, jumpToHomeOnAdd: Boolean = true) {
        val current = mutable.value.favoritePresets
        val added = presetId !in current
        val presetName = mutable.value.presetEntries.firstOrNull { it.id == presetId }?.name ?: "Preset #$presetId"
        if (added && current.size >= MAX_FAVORITE_LIMIT) {
            val msg = "Favorite limit reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT). Please remove an existing favorite preset first before adding '$presetName'."
            mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
            return
        }
        val next = if (added) current + presetId else current - presetId
        mutable.value = mutable.value.copy(
            favoritePresets = next,
            favoriteJumpTrigger = if (added && jumpToHomeOnAdd) System.currentTimeMillis() else mutable.value.favoriteJumpTrigger,
            lastFavoritedKey = if (added) "ps:$presetId" else mutable.value.lastFavoritedKey,
            notice = if (added) "Added '$presetName' to Home Quick Presets & Favorites (${next.size}/$MAX_FAVORITE_LIMIT)" else "Removed '$presetName' from favorites"
        )
        persist()
    }

    fun toggleFavoriteColor(hex: String, jumpToHomeOnAdd: Boolean = true) {
        val normalized = hex.uppercase()
        val current = mutable.value.favoriteColors
        val added = normalized !in current
        if (added && current.size >= MAX_FAVORITE_LIMIT) {
            val msg = "Favorite limit reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT). Please remove a favorite color first before adding $normalized."
            mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
            return
        }
        val next = if (added) current + normalized else current - normalized
        mutable.value = mutable.value.copy(
            favoriteColors = next,
            favoriteJumpTrigger = if (added && jumpToHomeOnAdd) System.currentTimeMillis() else mutable.value.favoriteJumpTrigger,
            lastFavoritedKey = if (added) "col:$normalized" else mutable.value.lastFavoritedKey,
            notice = if (added) "Added colour $normalized to Home Quick Presets & Favorites (${next.size}/$MAX_FAVORITE_LIMIT)" else "Removed $normalized from favorites"
        )
        persist()
    }

    /** Same-board migration: probe WLED's public HTTP port without sending Room credentials. */
    fun probeRoomPeer(roomOrigin: String) {
        if (mutable.value.busy) return
        viewModelScope.launch {
            runCatching {
                val host = URI(roomOrigin).host ?: return@launch
                val origin = URI("http", null, host, -1, null, null, null).toASCIIString()
                val value = api().state(origin)
                val id = wledDeviceId(value.info)
                val existing = mutable.value.devices.firstOrNull { it.id == id }
                val updated = WledDevice(
                    id = id,
                    origin = origin,
                    name = existing?.name ?: value.info.optString("name", "Room lighting"),
                    hidden = existing?.hidden ?: false,
                    linkedRoomHost = host
                )
                mutable.value = mutable.value.copy(devices = mutable.value.devices.filterNot { it.id == id } + updated)
                persist()
                if (mutable.value.device == null || mutable.value.selected == id) {
                    select(id)
                }
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
                val host = runCatching { URI(origin).host }.getOrNull()
                val device = WledDevice(
                    id = id,
                    origin = origin,
                    name = previous?.name ?: value.info.optString("name", "Room lighting"),
                    hidden = false,
                    linkedRoomHost = previous?.linkedRoomHost ?: host
                )
                mutable.value = mutable.value.copy(
                    devices = mutable.value.devices.filterNot { it.id == id } + device,
                    busy = false,
                    notice = "Connected to ${device.name}"
                )
                select(id)
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(busy = false, error = e.message ?: "Could not connect to WLED")
            }
        }
    }

    fun refreshNow() {
        val device = mutable.value.device
        if (device == null) {
            mutable.value = mutable.value.copy(notice = "Saved state active · Connect a controller IP to sync live")
            return
        }
        select(device.id)
    }

    fun select(id: String) {
        val device = mutable.value.devices.firstOrNull { it.id == id } ?: return
        generation++
        val expected = generation
        coalesceJobs.values.forEach { it.cancel() }
        coalesceJobs.clear()
        runCatching { if (mutable.value.livePreviewEnabled) socket?.send("{\"lv\":false}") }
        socket?.cancel(); socket = null; monitor?.cancel()
        mutable.value = mutable.value.copy(
            selected = id,
            snapshot = mutable.value.snapshot ?: WledCatalogDefaults.defaultSnapshot(),
            presets = if (mutable.value.presets.length() > 0) mutable.value.presets else WledCatalogDefaults.defaultPresets(),
            effects = mutable.value.effects.ifEmpty { WledCatalogDefaults.EFFECTS },
            palettes = mutable.value.palettes.ifEmpty { WledCatalogDefaults.PALETTES },
            fxdata = mutable.value.fxdata.ifEmpty { WledCatalogDefaults.FXDATA },
            config = mutable.value.config ?: WledCatalogDefaults.defaultConfig(),
            livePreviewEnabled = false,
            liveLeds = emptyList(),
            online = false,
            wsConnected = false,
            error = null
        )
        persist()
        monitor = viewModelScope.launch {
            var lastError: String? = null
            var catalogsLoaded = false
            while (generation == expected) {
                try {
                    val client = api()
                    // Flush any queued user commands/presets to the ESP32 first so local saves are never overwritten!
                    if (pendingEsp32Payloads.isNotEmpty()) {
                        val toSend = pendingEsp32Payloads.toList()
                        pendingEsp32Payloads.clear()
                        toSend.forEach { queued ->
                            runCatching { client.command(device.origin, queued) }
                        }
                    }
                    val snapshot = client.state(device.origin)
                    check(wledDeviceId(snapshot.info) == device.id) {
                        "A different WLED device now uses this address. Add it again to verify its identity."
                    }
                    if (generation != expected) break
                    mutable.value = mutable.value.copy(snapshot = snapshot, online = true, error = null)
                    if (socket == null) {
                        runCatching { openSocket(client.client, device, expected) }
                            .onFailure { android.util.Log.w("RoomWLED", "WebSocket unavailable; using HTTP state refresh", it) }
                    }
                    if (!catalogsLoaded) {
                        val effs = runCatching { client.effects(device.origin) }.getOrDefault(emptyList())
                        val pals = runCatching { client.palettes(device.origin) }.getOrDefault(emptyList())
                        val fxd = runCatching { client.fxdata(device.origin) }.getOrDefault(emptyList())
                        val cfg = runCatching { client.config(device.origin) }.getOrNull()
                        if (generation == expected) {
                            mutable.value = mutable.value.copy(
                                effects = effs.ifEmpty { WledCatalogDefaults.EFFECTS },
                                palettes = pals.ifEmpty { WledCatalogDefaults.PALETTES },
                                fxdata = fxd.ifEmpty { WledCatalogDefaults.FXDATA },
                                config = cfg ?: mutable.value.config
                            )
                            catalogsLoaded = effs.isNotEmpty() && pals.isNotEmpty()
                        }
                    }
                    runCatching { client.presets(device.origin) }.onSuccess { remotePresets ->
                        if (generation == expected) {
                            val mergedPresets = mergePresetsPreservingUserSaves(remotePresets, mutable.value.presets)
                            mutable.value = mutable.value.copy(
                                presets = mergedPresets,
                                presetsRevision = System.currentTimeMillis()
                            )
                        }
                    }.onFailure { android.util.Log.w("RoomWLED", "Presets could not be loaded", it) }
                    persist()
                    lastError = null
                } catch (e: Exception) {
                    if (generation != expected) break
                    mutable.value = mutable.value.copy(
                        online = false,
                        wsConnected = false,
                        error = if (lastError != e.message) e.message ?: "WLED is offline" else mutable.value.error
                    )
                    lastError = e.message
                }
                delay(if (socket != null) 15_000 else 4_000)
            }
        }
    }

    private fun openSocket(client: OkHttpClient, device: WledDevice, expected: Int) {
        val url = device.origin.replaceFirst("http", "ws") + "/ws"
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                viewModelScope.launch {
                    if (expected == generation && socket === webSocket) {
                        mutable.value = mutable.value.copy(wsConnected = true)
                        if (mutable.value.livePreviewEnabled) {
                            webSocket.send("{\"lv\":true}")
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > 2_097_152) return
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (json.has("leds")) {
                    val leds = parseLiveLedJson(json)
                    if (leds.isNotEmpty()) {
                        viewModelScope.launch {
                            if (expected == generation && mutable.value.livePreviewEnabled) {
                                mutable.value = mutable.value.copy(liveLeds = leds)
                            }
                        }
                    }
                    return
                }
                val value = runCatching { WledSnapshot.parse(json) }.getOrNull() ?: return
                viewModelScope.launch {
                    if (expected == generation && wledDeviceId(value.info) == device.id) {
                        mutable.value = mutable.value.copy(snapshot = value, online = true, wsConnected = true)
                        persist()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size > 65_536) return
                val leds = parseLiveLedFrame(bytes.toByteArray())
                if (leds.isNotEmpty()) {
                    viewModelScope.launch {
                        if (expected == generation && mutable.value.livePreviewEnabled) {
                            mutable.value = mutable.value.copy(liveLeds = leds)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                viewModelScope.launch {
                    if (expected == generation && socket === webSocket) {
                        socket = null
                        mutable.value = mutable.value.copy(wsConnected = false)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                viewModelScope.launch {
                    if (expected == generation && socket === webSocket) {
                        socket = null
                        mutable.value = mutable.value.copy(wsConnected = false)
                    }
                }
            }
        })
    }

    fun setLivePreview(enabled: Boolean) {
        if (mutable.value.livePreviewEnabled == enabled) return
        mutable.value = mutable.value.copy(
            livePreviewEnabled = enabled,
            liveLeds = if (enabled) mutable.value.liveLeds else emptyList()
        )
        runCatching {
            socket?.send(if (enabled) "{\"lv\":true}" else "{\"lv\":false}")
        }
    }

    fun command(body: JSONObject, successNotice: String? = null, refreshPresetsAfter: Boolean = false) {
        // 1. Always apply and persist immediately to local snapshot & presets so user choices never revert on app close
        val (nextSnap, nextPresets) = WledCatalogDefaults.applyLocalCommand(
            mutable.value.effectiveSnapshot,
            mutable.value.presets,
            body
        )
        val savedId = body.optInt("psave", -1)
        if (savedId in 1..250) {
            val key = savedId.toString()
            userDeletedPresetIds.remove(key)
            nextPresets.optJSONObject(key)?.let { userSavedPresets[key] = it.copyJson() }
        }
        val deletedId = body.optInt("pdel", -1)
        if (deletedId in 1..250) {
            val key = deletedId.toString()
            userSavedPresets.remove(key)
            userDeletedPresetIds.add(key)
        }
        val nowRev = System.currentTimeMillis()
        mutable.value = mutable.value.copy(
            snapshot = nextSnap,
            presets = nextPresets,
            presetsRevision = nowRev,
            notice = successNotice ?: mutable.value.notice
        )
        persist()
        runCatching { WledWidget.refresh(getApplication()) }

        // 2. Sync to ESP32 controller if configured (or queue for immediate delivery when ESP32 connects)
        val device = mutable.value.device
        if (device == null || !mutable.value.online) {
            if (device != null) {
                if (pendingEsp32Payloads.size >= 24) pendingEsp32Payloads.removeAt(0)
                pendingEsp32Payloads.add(body.copyJson())
            }
            return
        }
        if (mutable.value.busy && !refreshPresetsAfter && savedId !in 1..250 && deletedId !in 1..250) return
        val expected = generation
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            commands.withLock {
                try {
                    val client = api()
                    val value = client.command(device.origin, body)
                    check(wledDeviceId(value.info) == device.id) { "WLED identity changed" }
                    if (refreshPresetsAfter) {
                        // Give WLED main loop time to flush /presets.json to LittleFS before reading back
                        delay(300)
                    }
                    val updatedPresets = if (refreshPresetsAfter) {
                        runCatching { client.presets(device.origin) }.getOrNull()?.let { remote ->
                            mergePresetsPreservingUserSaves(remote, mutable.value.presets)
                        }
                    } else null
                    if (expected == generation) {
                        val finalPresets = updatedPresets ?: mergePresetsPreservingUserSaves(nextPresets, mutable.value.presets)
                        mutable.value = mutable.value.copy(
                            snapshot = value,
                            presets = finalPresets,
                            presetsRevision = System.currentTimeMillis(),
                            online = true,
                            notice = successNotice
                        )
                        persist()
                    }
                } catch (e: Exception) {
                    if (expected == generation) {
                        if (pendingEsp32Payloads.size < 24) pendingEsp32Payloads.add(body.copyJson())
                        mutable.value = mutable.value.copy(
                            notice = "${successNotice ?: "Saved locally"} · Queued for ESP32 sync"
                        )
                    }
                } finally {
                    mutable.value = mutable.value.copy(busy = false)
                    WledWidget.refresh(getApplication())
                }
            }
        }
    }

    /**
     * Coalesces rapid slider or color-wheel drags (120 ms window) so continuous interactions
     * never overwhelm the ESP32 HTTP server, while still reading back authoritative `/json/si` state
     * and persisting every adjustment locally.
     */
    fun coalescedCommand(key: String, body: JSONObject) {
        val (nextSnap, nextPresets) = WledCatalogDefaults.applyLocalCommand(
            mutable.value.effectiveSnapshot,
            mutable.value.presets,
            body
        )
        mutable.value = mutable.value.copy(snapshot = nextSnap, presets = nextPresets)

        val expected = generation
        coalesceJobs[key]?.cancel()
        coalesceJobs[key] = viewModelScope.launch {
            delay(95)
            persist()
            val device = mutable.value.device
            if (device == null || !mutable.value.online) {
                return@launch
            }
            commands.withLock {
                if (expected != generation) return@withLock
                try {
                    val value = api().command(device.origin, body)
                    check(wledDeviceId(value.info) == device.id) { "WLED identity changed" }
                    if (expected == generation) {
                        mutable.value = mutable.value.copy(snapshot = value, online = true, error = null)
                        persist()
                    }
                } catch (e: Exception) {
                    if (expected == generation) {
                        mutable.value = mutable.value.copy(error = e.message ?: "Could not update lighting")
                    }
                } finally {
                    WledWidget.refresh(getApplication())
                }
            }
        }
    }

    fun updateSegments(segmentIds: List<Int>, fields: JSONObject, coalesceKey: String? = null) {
        if (segmentIds.isEmpty()) return
        val payload = buildSegmentPatch(segmentIds, fields)
        if (coalesceKey != null) {
            coalescedCommand(coalesceKey, payload)
        } else {
            command(payload)
        }
    }

    fun addSegment(start: Int, stop: Int, name: String) {
        val snap = mutable.value.effectiveSnapshot
        require(start in 0 until snap.ledCount && stop in (start + 1)..snap.ledCount) {
            "Segment range must be within 0..${snap.ledCount}"
        }
        val usedIds = snap.segments.map { it.id }.toSet()
        val nextId = (0 until snap.maxSegments).firstOrNull { it !in usedIds }
            ?: error("Maximum number of segments (${snap.maxSegments}) reached")
        val segObj = obj(
            "id" to nextId,
            "start" to start,
            "stop" to stop,
            "on" to true,
            "bri" to 255,
            "sel" to true
        ).apply {
            if (name.isNotBlank()) put("n", name.trim().take(32))
        }
        command(obj("seg" to JSONArray().put(segObj)), successNotice = "Segment ${nextId + 1} added")
    }

    fun deleteSegment(id: Int) {
        val snap = mutable.value.effectiveSnapshot
        require(snap.segments.size > 1) { "At least one segment must remain active" }
        command(
            obj("seg" to JSONArray().put(obj("id" to id, "stop" to 0))),
            successNotice = "Segment ${id + 1} removed"
        )
    }

    fun resetSegments() {
        val snap = mutable.value.effectiveSnapshot
        val segArr = JSONArray()
        snap.segments.forEachIndexed { idx, seg ->
            if (idx == 0) {
                segArr.put(
                    obj(
                        "id" to seg.id,
                        "start" to 0,
                        "stop" to snap.ledCount,
                        "grp" to 1,
                        "spc" to 0,
                        "of" to 0,
                        "on" to true,
                        "sel" to true,
                        "rev" to false,
                        "mi" to false,
                        "frz" to false
                    )
                )
            } else {
                segArr.put(obj("id" to seg.id, "stop" to 0))
            }
        }
        command(obj("mainseg" to (snap.segments.firstOrNull()?.id ?: 0), "seg" to segArr), successNotice = "Reset to single full-strip segment")
    }

    fun savePreset(
        id: Int,
        name: String,
        quickLabel: String,
        includeBri: Boolean,
        saveBounds: Boolean,
        checkedSegmentsOnly: Boolean = false,
        category: String? = null
    ) {
        if (!category.isNullOrBlank()) {
            val next = mutable.value.presetCategories.toMutableMap()
            if (category == "Auto") next.remove("wled:$id") else next["wled:$id"] = category
            mutable.value = mutable.value.copy(presetCategories = next)
        }
        val payload = buildPresetSavePayload(id, name, quickLabel, includeBri, saveBounds).apply {
            if (checkedSegmentsOnly) put("sc", true)
        }
        command(payload, successNotice = "Saved preset #$id ($name) to app & ESP32", refreshPresetsAfter = true)
    }

    fun savePlaylist(id: Int, name: String, spec: WledPlaylistSpec, playImmediately: Boolean = false) {
        val payload = buildPlaylistSavePayload(id, name, spec).apply {
            if (playImmediately) {
                put("on", true)
            }
        }
        val noticeText = if (playImmediately) {
            "Saved & started playlist #$id ($name)"
        } else {
            "Saved playlist #$id ($name) to app & ESP32"
        }
        command(payload, successNotice = noticeText, refreshPresetsAfter = true)
        if (playImmediately) {
            // Also update activePlaylist locally and dispatch ps=id after save completes on ESP32
            val (nextSnap, nextPresets) = WledCatalogDefaults.applyLocalCommand(
                mutable.value.effectiveSnapshot,
                mutable.value.presets,
                obj("ps" to id, "on" to true)
            )
            mutable.value = mutable.value.copy(
                snapshot = nextSnap,
                presets = nextPresets,
                presetsRevision = System.currentTimeMillis()
            )
            persist()
            val device = mutable.value.device
            if (device != null && mutable.value.online) {
                val expected = generation
                viewModelScope.launch {
                    commands.withLock {
                        runCatching {
                            delay(250)
                            val value = api().command(device.origin, obj("ps" to id, "on" to true))
                            if (expected == generation && wledDeviceId(value.info) == device.id) {
                                mutable.value = mutable.value.copy(snapshot = value, online = true)
                            }
                        }
                    }
                }
            }
        }
    }

    fun deletePreset(id: Int) {
        require(id in 1..250) { "Invalid preset ID" }
        command(obj("pdel" to id), successNotice = "Deleted preset #$id", refreshPresetsAfter = true)
    }

    fun loadConfig() {
        val device = mutable.value.device ?: return
        if (!mutable.value.online) return
        val expected = generation
        viewModelScope.launch {
            runCatching { api().config(device.origin) }.onSuccess { cfg ->
                if (expected == generation) {
                    mutable.value = mutable.value.copy(config = cfg)
                    persist()
                }
            }.onFailure { e ->
                if (expected == generation) mutable.value = mutable.value.copy(error = e.message ?: "Could not load WLED configuration")
            }
        }
    }

    fun saveConfig(patch: JSONObject, successMessage: String = "Configuration saved to controller", reboot: Boolean = false) {
        val mergedLocal = deepMergeJson(mutable.value.effectiveConfig, patch)
        stripUnchangedSecrets(mergedLocal, patch)
        mutable.value = mutable.value.copy(
            config = mergedLocal,
            notice = successMessage
        )
        persist()

        val device = mutable.value.device
        if (device == null || !mutable.value.online) {
            return
        }
        if (mutable.value.busy) return
        val expected = generation
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            commands.withLock {
                try {
                    val client = api()
                    val updatedCfg = client.saveConfig(device.origin, patch)
                    if (reboot) {
                        runCatching { client.request(device.origin, "/json/state", obj("rb" to true)) }
                    }
                    val snap = if (!reboot) runCatching { client.state(device.origin) }.getOrNull() else null
                    if (expected == generation) {
                        mutable.value = mutable.value.copy(
                            config = updatedCfg,
                            snapshot = snap ?: mutable.value.snapshot,
                            notice = if (reboot) "$successMessage · Controller is rebooting…" else successMessage
                        )
                        persist()
                    }
                } catch (e: Exception) {
                    if (expected == generation) {
                        mutable.value = mutable.value.copy(error = e.message ?: "Could not save configuration")
                    }
                } finally {
                    mutable.value = mutable.value.copy(busy = false)
                }
            }
        }
    }

    fun rebootController() {
        val device = mutable.value.device
        if (device == null || !mutable.value.online) {
            mutable.value = mutable.value.copy(notice = "Reboot simulated in preview mode")
            return
        }
        if (mutable.value.busy) return
        val expected = generation
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try {
                runCatching { api().request(device.origin, "/json/state", obj("rb" to true)) }
                if (expected == generation) {
                    mutable.value = mutable.value.copy(
                        online = false,
                        wsConnected = false,
                        notice = "Reboot requested. Reconnecting when the ESP32 restarts…"
                    )
                }
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }

    fun exportBackup(kind: String, onReady: (String, String) -> Unit) {
        val device = mutable.value.device
        if (device == null || !mutable.value.online) {
            val suffix = device?.id?.takeLast(6) ?: "preview"
            val (fileName, json) = when (kind) {
                "presets" -> "wled-presets-$suffix.json" to mutable.value.presets.toString(2)
                "cfg" -> "wled-cfg-$suffix.json" to mutable.value.effectiveConfig.toString(2)
                else -> return
            }
            onReady(fileName, json)
            return
        }
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            try {
                val client = api()
                val (fileName, json) = when (kind) {
                    "presets" -> "wled-presets-${device.id.takeLast(6)}.json" to client.presets(device.origin).toString(2)
                    "cfg" -> "wled-cfg-${device.id.takeLast(6)}.json" to client.config(device.origin).toString(2)
                    else -> error("Unsupported backup export type")
                }
                onReady(fileName, json)
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(error = e.message ?: "Could not export $kind backup")
            } finally {
                mutable.value = mutable.value.copy(busy = false)
            }
        }
    }

    fun importBackup(kind: String, rawText: String) {
        val device = mutable.value.device
        if (device == null || !mutable.value.online) {
            runCatching {
                val validated = validateWledBackupJson(kind, rawText)
                when (kind) {
                    "cfg" -> mutable.value = mutable.value.copy(config = validated, notice = "Configuration restored to preview")
                    "presets" -> mutable.value = mutable.value.copy(presets = validated, notice = "Presets restored to preview")
                }
            }.onFailure { e ->
                mutable.value = mutable.value.copy(error = e.message ?: "Backup restore failed")
            }
            return
        }
        if (mutable.value.busy) return
        val expected = generation
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        viewModelScope.launch {
            commands.withLock {
                try {
                    val validated = validateWledBackupJson(kind, rawText)
                    val client = api()
                    when (kind) {
                        "cfg" -> {
                            val updated = client.saveConfig(device.origin, validated)
                            if (expected == generation) {
                                mutable.value = mutable.value.copy(
                                    config = updated,
                                    notice = "Configuration restored to ${device.name}"
                                )
                            }
                        }
                        "presets" -> {
                            var count = 0
                            validated.keys().forEach { key ->
                                val id = key.toIntOrNull() ?: return@forEach
                                if (id in 1..250) {
                                    val entry = validated.optJSONObject(key) ?: return@forEach
                                    if (entry.length() > 0) {
                                        val payload = entry.copyJson().put("psave", id).put("o", true)
                                        client.request(device.origin, "/json/state", payload)
                                        count++
                                    }
                                }
                            }
                            val refreshed = client.presets(device.origin)
                            if (expected == generation) {
                                mutable.value = mutable.value.copy(
                                    presets = refreshed,
                                    notice = "Restored $count presets to ${device.name}"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (expected == generation) {
                        mutable.value = mutable.value.copy(error = e.message ?: "Backup restore failed")
                    }
                } finally {
                    mutable.value = mutable.value.copy(busy = false)
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
        if (wasSelected) {
            generation++
            socket?.cancel(); socket = null; monitor?.cancel()
        }
        mutable.value = mutable.value.copy(
            devices = mutable.value.devices.filterNot { it.id == id },
            selected = if (wasSelected) null else mutable.value.selected,
            snapshot = if (wasSelected) WledCatalogDefaults.defaultSnapshot() else mutable.value.snapshot,
            online = !wasSelected && mutable.value.online,
            wsConnected = !wasSelected && mutable.value.wsConnected
        )
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
                    val host = runCatching { URI(address).host }.getOrNull()
                    val value = WledDevice(
                        id = id,
                        origin = address,
                        name = previous?.name ?: snapshot.info.optString("name", "WLED"),
                        hidden = previous?.hidden ?: false,
                        linkedRoomHost = previous?.linkedRoomHost ?: host
                    )
                    mutable.value = mutable.value.copy(devices = mutable.value.devices.filterNot { it.id == id } + value)
                    persist()
                    if (mutable.value.selected == null) select(id)
                }
            }
        }, { reason -> mutable.value = mutable.value.copy(error = reason) }).also { it.start() }
        // Probe both WLED's default setup AP (4.3.2.1) and Room's setup AP (192.168.4.1) on HTTP.
        listOf("http://4.3.2.1", "http://192.168.4.1").forEach { setupOrigin ->
            viewModelScope.launch {
                runCatching {
                    val value = api().state(setupOrigin)
                    val id = wledDeviceId(value.info)
                    if (mutable.value.devices.none { it.id == id }) {
                        val host = runCatching { URI(setupOrigin).host }.getOrNull()
                        mutable.value = mutable.value.copy(
                            devices = mutable.value.devices + WledDevice(
                                id = id,
                                origin = setupOrigin,
                                name = value.info.optString("name", "WLED setup"),
                                linkedRoomHost = host
                            )
                        )
                        persist()
                    }
                    if (mutable.value.selected == null) select(id)
                }
            }
        }
        viewModelScope.launch {
            delay(12_000)
            discovery?.stop(); discovery = null
            mutable.value = mutable.value.copy(
                discovering = false,
                notice = if (mutable.value.devices.isEmpty())
                    "No WLED controllers found. Join its Wi-Fi or enter its address."
                else "Discovery finished"
            )
        }
    }

    override fun onCleared() {
        generation++
        coalesceJobs.values.forEach { it.cancel() }
        monitor?.cancel(); socket?.cancel(); discovery?.stop()
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
