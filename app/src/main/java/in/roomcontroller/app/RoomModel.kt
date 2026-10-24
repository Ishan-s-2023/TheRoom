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
import kotlinx.coroutines.flow.first
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
    val demo: Boolean = false, val revision: Long = 0, val pendingAction: String? = null,
    val selectedPage: String = "Home",
    val use24HourFormat: Boolean = true,
    val lastBackupTimestamp: String? = null,
    val favoriteRoomPresets: Set<String> = emptySet(),
    val favoriteRoutines: Set<String> = emptySet(),
    val favoriteSoundModes: Set<Int> = emptySet(),
    val favoriteJumpTrigger: Long = 0L,
    val lastFavoritedKey: String? = null,
    val favoriteLimitWarning: String? = null,
    val spotifyOAuthConnected: Boolean = false,
    val spotifyAccountName: String = "",
    val spotifyClientId: String = "",
    val spotifyTokenExpiresAtMs: Long = 0L
)

class RoomModel(application: Application): AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("room-settings", Context.MODE_PRIVATE)
    private val dataStore = RoomDataStore(application)
    val spotifyClient = SpotifyApiClient(dataStore)
    private val vault = TokenVault(application)
    private val pairingVault = TokenVault(application, "room-device-pairing")
    private val configuredDeviceText = runCatching {
        application.assets.open("room-device.config").bufferedReader().use { it.readText() }
    }.getOrNull()
    val configuredDevice = configuredDeviceText?.let { runCatching { DevicePairing.parseFlexible(it) }.getOrNull() }
    var pairing = runCatching { DevicePairing.parseFlexible(pairingVault.read()) }.getOrNull()
        ?: configuredDevice
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

    private fun readStringSet(key: String, defaultSet: Set<String>): Set<String> =
        prefs.getStringSet(key, null)?.toSet() ?: defaultSet

    private fun readIntSet(key: String, defaultSet: Set<Int>): Set<Int> =
        prefs.getStringSet(key, null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: defaultSet

    private fun writeIntSet(key: String, values: Set<Int>) {
        prefs.edit().putStringSet(key, values.map { it.toString() }.toSet()).apply()
    }

    private fun loadPersistedState(): JSONObject? =
        prefs.getString("persisted_room_state", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private var persistJob: kotlinx.coroutines.Job? = null
    private var currentSelectedPage: String = prefs.getString("selected_page", "Home") ?: "Home"

    private fun savePersistedState(state: JSONObject) {
        syncDataStore(stateOverride = state)
        runCatching {
            val spot = state.optJSONObject("spotify")
            if (spot != null && spot.length() > 0) {
                val isPlaying = spot.optBoolean("playing", false)
                val track = spot.optString("track", "").trim()
                if (track.isNotBlank() && (isPlaying || RoomMediaPlaybackService.isServiceRunning)) {
                    RoomMediaPlaybackService.syncPlayback(
                        context = getApplication(),
                        track = track,
                        artist = spot.optString("artist", "").trim(),
                        device = spot.optString("device", "").trim(),
                        playing = isPlaying,
                        progressMs = spot.optLong("progress_ms", 0L),
                        durationMs = spot.optLong("duration_ms", 0L).coerceAtLeast(1000L)
                    )
                }
            }
        }
    }

    private fun syncDataStore(stateOverride: JSONObject? = null) {
        val cur = mutable.value
        val st = stateOverride ?: cur.state
        val page = currentSelectedPage
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            val serializedState = if (st.length() > 0) st.toString() else ""
            if (serializedState.isNotEmpty()) {
                prefs.edit().putString("persisted_room_state", serializedState).apply()
            }
            dataStore.saveRoomState(
                selectedPage = page,
                use24HourFormat = cur.use24HourFormat,
                stateJson = serializedState,
                favoriteRoomPresets = cur.favoriteRoomPresets,
                favoriteRoutines = cur.favoriteRoutines,
                favoriteSoundModes = cur.favoriteSoundModes
            )
        }
    }

    private val initialPersistedState = loadPersistedState()
    private val mutable = MutableStateFlow(
        RoomUi(
            signedIn = token.isNotEmpty() || pairing != null,
            state = initialPersistedState ?: JSONObject(),
            selectedPage = currentSelectedPage,
            use24HourFormat = prefs.getBoolean("use_24_hour_format", true),
            favoriteRoomPresets = readStringSet("fav_room_presets", setOf("focus")).take(MAX_FAVORITE_LIMIT).toSet(),
            favoriteRoutines = readStringSet("fav_routines", emptySet()).take(MAX_FAVORITE_LIMIT).toSet(),
            favoriteSoundModes = readIntSet("fav_sound_modes", setOf(130)).take(MAX_FAVORITE_LIMIT).toSet()
        )
    )
    val ui = mutable.asStateFlow()
    private var demoState = initialPersistedState ?: demoState()
    private var previewSession = PreviewSession()

    init {
        RoomMediaPlaybackService.onMediaTransportCommand = { action: String, value: Int ->
            if (mutable.value.spotifyOAuthConnected || !mutable.value.demo) {
                spotifyCommand(action, "", value)
            } else {
                viewModelScope.launch {
                    val tokens = runCatching { dataStore.readSpotifyOAuthTokens() }.getOrNull()
                    if (tokens?.connected == true) {
                        spotifyCommand(action, "", value)
                    } else {
                        demoRequest("POST", "/v1/command", obj("op" to "spotify", "action" to action, "argument" to "", "value" to value))
                        val next = demoState.copyJson()
                        savePersistedState(next)
                        mutable.value = mutable.value.copy(state = next, revision = System.currentTimeMillis())
                    }
                }
            }
        }
        if (token.isEmpty() && pairing == null && configuredDevice == null) {
            demo(preservePersisted = initialPersistedState != null)
        }
        // Hydrate from DataStore once on startup only — never echo background saves back onto live sliders!
        viewModelScope.launch {
            val ds = runCatching { dataStore.roomFlow.first() }.getOrNull() ?: return@launch
            val cur = mutable.value
            val hydratedState = if (cur.demo && !ds.persistedRoomStateJson.isNullOrBlank()) {
                runCatching { JSONObject(ds.persistedRoomStateJson) }.getOrNull()
            } else null
            if (hydratedState != null && demoState.toString() != hydratedState.toString()) {
                demoState = hydratedState
                previewSession.seed(demoState)
            }
            ds.selectedPage?.let { currentSelectedPage = it }
            val updated = cur.copy(
                selectedPage = ds.selectedPage ?: cur.selectedPage,
                use24HourFormat = ds.use24HourFormat ?: cur.use24HourFormat,
                lastBackupTimestamp = ds.lastBackupTimestamp ?: cur.lastBackupTimestamp,
                state = hydratedState ?: cur.state,
                favoriteRoomPresets = ds.favoriteRoomPresets?.take(MAX_FAVORITE_LIMIT)?.toSet() ?: cur.favoriteRoomPresets,
                favoriteRoutines = ds.favoriteRoutines?.take(MAX_FAVORITE_LIMIT)?.toSet() ?: cur.favoriteRoutines,
                favoriteSoundModes = ds.favoriteSoundModes?.take(MAX_FAVORITE_LIMIT)?.toSet() ?: cur.favoriteSoundModes,
                spotifyOAuthConnected = ds.spotifyConnected || cur.spotifyOAuthConnected,
                spotifyAccountName = ds.spotifyAccountName.ifBlank { cur.spotifyAccountName },
                spotifyClientId = ds.spotifyClientId.ifBlank { cur.spotifyClientId },
                spotifyTokenExpiresAtMs = if (ds.spotifyTokenExpiresAtMs > 0L) ds.spotifyTokenExpiresAtMs else cur.spotifyTokenExpiresAtMs
            )
            if (updated != cur) {
                mutable.value = updated
            }
        }
        viewModelScope.launch {
            while (true) {
                if (roomPollingEnabled && mutable.value.signedIn && !mutable.value.demo && !mutable.value.busy && !provisioning) {
                    runCatching { request("GET", "/state") }.onSuccess {
                        savePersistedState(it)
                        mutable.value = mutable.value.copy(state=it, stale=false, revision=System.currentTimeMillis())
                    }.onFailure { if (roomPollingEnabled) mutable.value = mutable.value.copy(stale=true, error=if(mutable.value.stale) mutable.value.error else it.message) }
                }
                delay(if (directDevice) 1500 else 4000)
            }
        }
        viewModelScope.launch {
            while (true) {
                if (mutable.value.spotifyOAuthConnected || mutable.value.state.optBoolean("spotify_connected")) {
                    runCatching { syncLiveSpotifyPlaybackState() }
                }
                delay(2500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        RoomMediaPlaybackService.onMediaTransportCommand = null
    }

    fun dismissFavoriteWarning() {
        mutable.value = mutable.value.copy(favoriteLimitWarning = null)
    }

    fun setPage(page: String) {
        if (currentSelectedPage == page) return
        currentSelectedPage = page
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString("selected_page", page).apply()
        }
    }

    fun setUse24HourFormat(enabled: Boolean) {
        if (mutable.value.use24HourFormat == enabled) return
        prefs.edit().putBoolean("use_24_hour_format", enabled).apply()
        val curSystem = (mutable.value.state.optJSONObject("system") ?: JSONObject()).copyJson()
            .put("use_24_hour", enabled)
        val nextState = mutable.value.state.copyJson().put("system", curSystem)
        if (mutable.value.demo) {
            demoState.put("system", curSystem)
        }
        mutable.value = mutable.value.copy(
            use24HourFormat = enabled,
            state = nextState,
            notice = if (enabled) "Clock format set to 24-hour (00:00 – 23:59)" else "Clock format set to 12-hour (AM / PM)"
        )
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.saveUse24HourFormat(enabled)
        }
        syncDataStore(stateOverride = nextState)
    }

    fun connectSpotifyOAuthToDataStore(
        accountName: String,
        clientId: String,
        accessToken: String,
        refreshToken: String,
        expiresInSec: Long = 3600L
    ) {
        val cleanAccount = accountName.trim().ifBlank { "Spotify Premium Account" }
        val cleanClientId = clientId.trim().ifBlank { SpotifyPkce.CLIENT_ID }
        val cleanAccess = accessToken.trim()
        val cleanRefresh = refreshToken.trim()
        val curSpot = (mutable.value.state.optJSONObject("spotify") ?: JSONObject()).copyJson()
            .put("connected", true)
            .put("account_name", cleanAccount)
            .put("track", (mutable.value.state.optJSONObject("spotify")?.optString("track")).orEmpty())
            .put("artist", (mutable.value.state.optJSONObject("spotify")?.optString("artist")).orEmpty())
            .put("device", (mutable.value.state.optJSONObject("spotify")?.optString("device")).orEmpty())
        val nextState = mutable.value.state.copyJson()
            .put("spotify_connected", true)
            .put("spotify", curSpot)
        if (mutable.value.demo) {
            demoState.put("spotify_connected", true)
            demoState.put("spotify", curSpot)
        }
        val expMs = System.currentTimeMillis() + (expiresInSec.coerceAtLeast(60L) * 1000L)
        mutable.value = mutable.value.copy(
            state = nextState,
            spotifyOAuthConnected = true,
            spotifyAccountName = cleanAccount,
            spotifyClientId = cleanClientId,
            spotifyTokenExpiresAtMs = expMs,
            revision = System.currentTimeMillis(),
            notice = "Spotify OAuth tokens encrypted & saved to Jetpack DataStore ($cleanAccount)"
        )
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.saveSpotifyOAuthTokens(
                accountName = cleanAccount,
                clientId = cleanClientId,
                accessToken = cleanAccess,
                refreshToken = cleanRefresh,
                expiresInSec = expiresInSec
            )
            if (directDevice && cleanRefresh.length >= 20) {
                runCatching {
                    deviceTransport().call(
                        endpoint,
                        "/v1/command",
                        obj("op" to "spotify_credentials", "client_id" to cleanClientId, "refresh_token" to cleanRefresh)
                    )
                }
            }
        }
        savePersistedState(nextState)
    }

    fun disconnectSpotifyOAuthFromDataStore() {
        val curSpot = (mutable.value.state.optJSONObject("spotify") ?: JSONObject()).copyJson()
            .put("connected", false)
            .put("playing", false)
        val nextState = mutable.value.state.copyJson()
            .put("spotify_connected", false)
            .put("spotify", curSpot)
        if (mutable.value.demo) {
            demoState.put("spotify_connected", false)
            demoState.put("spotify", curSpot)
        }
        mutable.value = mutable.value.copy(
            state = nextState,
            spotifyOAuthConnected = false,
            spotifyAccountName = "",
            spotifyTokenExpiresAtMs = 0L,
            revision = System.currentTimeMillis(),
            notice = "Spotify OAuth tokens cleared from DataStore"
        )
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.clearSpotifyOAuthTokens()
        }
        RoomMediaPlaybackService.stopMediaPlayback(getApplication())
        savePersistedState(nextState)
    }

    fun spotifyQuickTransport(action: String) {
        val curSpot = (mutable.value.state.optJSONObject("spotify") ?: JSONObject()).copyJson()
        when (action) {
            "play" -> curSpot.put("playing", true)
            "pause" -> curSpot.put("playing", false)
            "toggle" -> curSpot.put("playing", !curSpot.optBoolean("playing", false))
            "next", "previous" -> {
                curSpot.put("playing", true)
            }
        }
        val nextState = mutable.value.state.copyJson().put("spotify", curSpot)
        if (mutable.value.demo) {
            demoState.put("spotify", curSpot)
        }
        mutable.value = mutable.value.copy(state = nextState, revision = System.currentTimeMillis())
        savePersistedState(nextState)
        val remoteAction = if (action == "toggle") {
            if (curSpot.optBoolean("playing", false)) "play" else "pause"
        } else action
        viewModelScope.launch(Dispatchers.IO) {
            if (directDevice) {
                runCatching {
                    request("POST", "/v1/command", obj("op" to "spotify", "action" to remoteAction, "argument" to "", "value" to 0))
                }
            }
            // Direct Spotify Web API execution
            runCatching {
                spotifyClient.sendPlayerCommand(remoteAction)
            }
            runCatching {
                syncLiveSpotifyPlaybackState()
            }
        }
    }

    fun exportDataStoreBackup(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dataStore.exportConfigurationBackup(currentRoomUi = mutable.value, saveLocalSnapshot = true)
                }
            }.onSuccess { (ts, json) ->
                mutable.value = mutable.value.copy(
                    lastBackupTimestamp = ts.ifBlank { mutable.value.lastBackupTimestamp },
                    notice = "Configuration backup exported from DataStore"
                )
                onReady(json)
            }.onFailure { err ->
                mutable.value = mutable.value.copy(error = err.message ?: "Failed to export backup")
            }
        }
    }

    fun createLocalDataStoreSnapshot(onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dataStore.exportConfigurationBackup(currentRoomUi = mutable.value, saveLocalSnapshot = true)
                }
            }.onSuccess { (ts, json) ->
                mutable.value = mutable.value.copy(
                    lastBackupTimestamp = ts.ifBlank { mutable.value.lastBackupTimestamp },
                    notice = "Saved full configuration snapshot to DataStore"
                )
                onComplete?.invoke(json)
            }.onFailure { err ->
                mutable.value = mutable.value.copy(error = err.message ?: "Snapshot failed")
            }
        }
    }

    fun importDataStoreBackup(rawJson: String, wledModel: WledModel? = null) {
        viewModelScope.launch {
            runCatching {
                val summary = withContext(Dispatchers.IO) {
                    dataStore.importConfigurationBackup(rawJson)
                }
                val ds = withContext(Dispatchers.IO) {
                    dataStore.roomFlow.first()
                }
                val restoredState = ds.persistedRoomStateJson?.takeIf { it.isNotBlank() }?.let {
                    runCatching { JSONObject(it) }.getOrNull()
                }
                val nextUse24h = ds.use24HourFormat ?: summary.use24HourFormat
                val nextFavPresets = ds.favoriteRoomPresets?.take(MAX_FAVORITE_LIMIT)?.toSet() ?: mutable.value.favoriteRoomPresets
                val nextFavRoutines = ds.favoriteRoutines?.take(MAX_FAVORITE_LIMIT)?.toSet() ?: mutable.value.favoriteRoutines
                val nextFavSounds = ds.favoriteSoundModes?.take(MAX_FAVORITE_LIMIT)?.toSet() ?: mutable.value.favoriteSoundModes

                prefs.edit()
                    .putBoolean("use_24_hour_format", nextUse24h)
                    .putStringSet("fav_room_presets", nextFavPresets)
                    .putStringSet("fav_routines", nextFavRoutines)
                    .apply {
                        if (restoredState != null) putString("persisted_room_state", restoredState.toString())
                    }
                    .apply()
                writeIntSet("fav_sound_modes", nextFavSounds)

                if (restoredState != null) {
                    demoState = restoredState.copyJson()
                    previewSession = PreviewSession()
                    previewSession.seed(demoState)
                }

                mutable.value = mutable.value.copy(
                    use24HourFormat = nextUse24h,
                    lastBackupTimestamp = ds.lastBackupTimestamp ?: summary.timestamp,
                    state = restoredState?.copyJson() ?: mutable.value.state,
                    favoriteRoomPresets = nextFavPresets,
                    favoriteRoutines = nextFavRoutines,
                    favoriteSoundModes = nextFavSounds,
                    revision = System.currentTimeMillis(),
                    notice = "Restored ${summary.routineCount} routines, ${summary.commandCount} IR commands & ${summary.presetCount} presets from DataStore backup"
                )
                wledModel?.reloadFromDataStore()
            }.onFailure { err ->
                mutable.value = mutable.value.copy(error = err.message ?: "Invalid configuration backup JSON")
            }
        }
    }

    fun restoreLocalDataStoreSnapshot(wledModel: WledModel? = null) {
        viewModelScope.launch {
            val savedJson = withContext(Dispatchers.IO) { dataStore.readLatestLocalBackupJson() }
            if (savedJson.isNullOrBlank()) {
                mutable.value = mutable.value.copy(error = "No local DataStore snapshot found yet. Tap 'Save Snapshot' or 'Export Backup' first.")
                return@launch
            }
            importDataStoreBackup(savedJson, wledModel)
        }
    }

    fun toggleFavoriteRoomPreset(id: String) {
        val current = mutable.value.favoriteRoomPresets
        val adding = id !in current
        if (adding && current.size >= MAX_FAVORITE_LIMIT) {
            val msg = "Favorite limit reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT). Please remove an existing favorite scene first."
            mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
            return
        }
        val next = if (adding) current + id else current - id
        prefs.edit().putStringSet("fav_room_presets", next).apply()
        mutable.value = mutable.value.copy(
            favoriteRoomPresets = next,
            favoriteJumpTrigger = if (adding) System.currentTimeMillis() else mutable.value.favoriteJumpTrigger,
            lastFavoritedKey = if (adding) "room_preset:$id" else mutable.value.lastFavoritedKey,
            notice = if (adding) "Pinned to Home Quick Presets & Favorites (${next.size}/$MAX_FAVORITE_LIMIT)" else "Removed from Home Favorites"
        )
        syncDataStore()
    }

    fun toggleFavoriteRoutine(id: String) {
        val current = mutable.value.favoriteRoutines
        val adding = id !in current
        if (adding && current.size >= MAX_FAVORITE_LIMIT) {
            val msg = "Favorite limit reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT). Please remove a favorite routine first."
            mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
            return
        }
        val next = if (adding) current + id else current - id
        prefs.edit().putStringSet("fav_routines", next).apply()
        mutable.value = mutable.value.copy(
            favoriteRoutines = next,
            favoriteJumpTrigger = if (adding) System.currentTimeMillis() else mutable.value.favoriteJumpTrigger,
            lastFavoritedKey = if (adding) "routine:$id" else mutable.value.lastFavoritedKey,
            notice = if (adding) "Routine pinned to Home Favorites (${next.size}/$MAX_FAVORITE_LIMIT)" else "Routine removed from Home Favorites"
        )
        syncDataStore()
    }

    fun toggleFavoriteSoundMode(effectId: Int) {
        val current = mutable.value.favoriteSoundModes
        val adding = effectId !in current
        if (adding && current.size >= MAX_FAVORITE_LIMIT) {
            val msg = "Favorite limit reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT). Please remove a favorite sound mode first."
            mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
            return
        }
        val next = if (adding) current + effectId else current - effectId
        writeIntSet("fav_sound_modes", next)
        mutable.value = mutable.value.copy(
            favoriteSoundModes = next,
            favoriteJumpTrigger = if (adding) System.currentTimeMillis() else mutable.value.favoriteJumpTrigger,
            lastFavoritedKey = if (addedOrNot(adding)) "sound:$effectId" else mutable.value.lastFavoritedKey,
            notice = if (adding) "Sound mode pinned to Home Favorites (${next.size}/$MAX_FAVORITE_LIMIT)" else "Removed from Home Favorites"
        )
        syncDataStore()
    }

    private fun addedOrNot(adding: Boolean): Boolean = adding

    fun useBuiltInDevice() = work {
        val configured = configuredDevice ?: pairing
        if (configured == null) {
            mutable.value = mutable.value.copy(
                notice = "No built-in room-device.config found in this APK. Enter your ESP32 IP/hostname and token in Manual LAN Pairing below."
            )
            return@work
        }
        val text = configuredDeviceText ?: configured.toJson()
        val sameDevice = pairing?.fingerprint == configured.fingerprint
        pairingVault.save(text)
        pairing = configured
        token = ""; vault.clear()
        if (!sameDevice || endpoint.isBlank()) endpoint = "https://${configured.setupIp}"
        prefs.edit().putString("endpoint", endpoint).apply()
        provisioning = false
        mutable.value = RoomUi(signedIn=true, busy=true, notice="Your ESP32 is ready to connect. Join its setup Wi-Fi or reconnect on home Wi-Fi.")
    }

    fun pairManualDevice(hostOrOrigin: String, tokenInput: String) = work {
        val manualPairing = DevicePairing.fromManual(hostOrOrigin, tokenInput)
        pairingVault.save(manualPairing.toJson())
        pairing = manualPairing
        token = ""; vault.clear()
        val cleanOrigin = hostOrOrigin.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        endpoint = cleanOrigin
        prefs.edit().putString("endpoint", endpoint).apply()
        provisioning = false
        val state = runCatching { deviceUiState(deviceTransport().call(endpoint, "/v1/state")) }.getOrNull()
        if (state != null) savePersistedState(state)
        mutable.value = mutable.value.copy(
            signedIn = true,
            demo = false,
            stale = state == null,
            state = state ?: mutable.value.state,
            notice = if (state != null) "Connected to ESP32 at $endpoint" else "Paired controller at $endpoint. Verify Wi-Fi connection if status is offline."
        )
    }

    fun importPairingJson(rawJson: String) = work {
        val parsed = DevicePairing.parseFlexible(rawJson)
        pairingVault.save(parsed.toJson())
        pairing = parsed
        token = ""; vault.clear()
        endpoint = "https://${parsed.setupIp}"
        prefs.edit().putString("endpoint", endpoint).apply()
        provisioning = false
        mutable.value = mutable.value.copy(
            signedIn = true,
            demo = false,
            notice = "Controller configuration imported (${parsed.hostname}). Connect via setup Wi-Fi or home Wi-Fi."
        )
    }

    fun connectDevice(origin: String) = work {
        val clean = origin.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        check(pairing != null) { "Select Connect my ESP32 or pair manually first" }
        val state = deviceUiState(deviceTransport().call(clean, "/v1/state"))
        endpoint = clean
        prefs.edit().putString("endpoint", clean).apply()
        provisioning = false
        savePersistedState(state)
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

    fun demo(scenario: String = "default", preservePersisted: Boolean = false) {
        val persisted = if (preservePersisted && scenario == "default") loadPersistedState() else null
        demoState = persisted ?: demoState()
        previewSession = PreviewSession()
        previewSession.seed(demoState)
        if (scenario == "spotify-disconnected") demoState.put("spotify_connected", false)
        if (scenario == "controller-offline") demoState.put("connected", false)
        savePersistedState(demoState)
        mutable.value = mutable.value.copy(
            signedIn = true,
            state = demoState.copyJson(),
            demo = true,
            stale = scenario == "connection-lost",
            notice = "UI preview only. No hardware or Spotify commands are sent."
        )
    }

    fun exitPreview() {
        if (configuredDevice != null || pairing != null) useBuiltInDevice() else logout()
    }

    fun logout() = work {
        RoomMediaPlaybackService.stopMediaPlayback(getApplication())
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

    private suspend fun handleDirectSpotifyRequest(method: String, path: String, body: JSONObject?): JSONObject? {
        val hasTokens = mutable.value.spotifyOAuthConnected || dataStore.readSpotifyOAuthTokens().connected
        if (!hasTokens) return null

        if (path == "/spotify/player") {
            val live = spotifyClient.fetchPlayerState()
            if (live != null) return live
        }
        if (path == "/spotify/devices") {
            val devs = spotifyClient.fetchDevices()
            return obj("devices" to JSONArray(devs))
        }
        if (path == "/spotify/playback") {
            val act = body?.optString("action", "play") ?: "play"
            val devId = body?.optString("device_id", "").orEmpty()
            val arg = listOfNotNull(
                body?.optString("argument")?.takeIf { it.isNotBlank() },
                body?.optString("uri")?.takeIf { it.isNotBlank() },
                devId.takeIf { it.isNotBlank() && act == "transfer" }
            ).firstOrNull().orEmpty()
            val v = body?.optInt("value", body?.optInt("volume", body?.optInt("position_ms", 0) ?: 0) ?: 0) ?: 0
            val resp = spotifyClient.sendPlayerCommand(act, arg, v, devId)
            syncLiveSpotifyPlaybackState()
            return resp
        }
        if (path.startsWith("/spotify/search")) {
            val q = path.substringAfter("q=", "").substringBefore("&")
            val offset = path.substringAfter("offset=", "0").substringBefore("&").toIntOrNull() ?: 0
            val type = if (path.contains("type=")) path.substringAfter("type=", "track,playlist,album,artist").substringBefore("&") else "track,playlist,album,artist"
            val queryDecoded = java.net.URLDecoder.decode(q, "UTF-8")
            return spotifyClient.search(queryDecoded, type, offset)
        }
        if (path.startsWith("/spotify/playlists")) {
            val offset = path.substringAfter("offset=", "0").substringBefore("&").toIntOrNull() ?: 0
            return spotifyClient.fetchPlaylists(offset)
        }
        if (path.startsWith("/spotify/recent")) {
            return spotifyClient.fetchRecentTracks()
        }
        if (path.startsWith("/spotify/liked")) {
            val offset = path.substringAfter("offset=", "0").substringBefore("&").toIntOrNull() ?: 0
            return spotifyClient.fetchLikedTracks(offset)
        }
        if (path.startsWith("/spotify/queue")) {
            return spotifyClient.fetchQueue()
        }
        if (path.startsWith("/spotify/recommendations")) {
            val seed = path.substringAfter("seed=", "").substringBefore("&")
            return spotifyClient.fetchRecommendations(seed)
        }
        return null
    }

    suspend fun syncLiveSpotifyPlaybackState() = withContext(Dispatchers.IO) {
        val liveState = runCatching { spotifyClient.fetchPlayerState() }.getOrNull() ?: return@withContext
        val track = liveState.optString("track", "").trim()
        val isPlaying = liveState.optBoolean("is_playing", false)
        val deviceObj = liveState.optJSONObject("device")
        val devName = deviceObj?.optString("name").orEmpty()
        val vol = deviceObj?.optInt("volume_percent", 50) ?: 50

        withContext(Dispatchers.Main) {
            val curSpot = (mutable.value.state.optJSONObject("spotify") ?: JSONObject()).copyJson()
            curSpot.put("connected", true)
            curSpot.put("playing", isPlaying)
            curSpot.put("track", track)
            curSpot.put("artist", liveState.optString("artist", ""))
            if (devName.isNotBlank()) curSpot.put("device", devName)
            curSpot.put("image_url", liveState.optString("image_url", ""))
            curSpot.put("progress_ms", liveState.optInt("progress_ms", 0))
            curSpot.put("duration_ms", liveState.optInt("duration_ms", 0))
            curSpot.put("volume_percent", vol)
            curSpot.put("shuffle_state", liveState.optBoolean("shuffle_state", false))
            curSpot.put("smart_shuffle", liveState.optBoolean("smart_shuffle", false))
            curSpot.put("repeat_state", liveState.optString("repeat_state", "off"))
            curSpot.put("id", liveState.optString("id", ""))
            curSpot.put("uri", liveState.optString("uri", ""))

            val nextState = mutable.value.state.copyJson()
                .put("spotify_connected", true)
                .put("spotify", curSpot)
            mutable.value = mutable.value.copy(state = nextState, revision = System.currentTimeMillis())
            savePersistedState(nextState)
        }
    }

    suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        if (path.startsWith("/spotify")) {
            val directResult = runCatching { handleDirectSpotifyRequest(method, path, body) }.getOrNull()
            if (directResult != null) return directResult
        }
        val hasDirectTokens = mutable.value.spotifyOAuthConnected || dataStore.readSpotifyOAuthTokens().connected
        if (hasDirectTokens && path == "/v1/command" && body?.optString("op") == "spotify") {
            val act = body.optString("action", "play")
            val devId = body.optString("device_id", "")
            val arg = listOfNotNull(
                body.optString("argument").takeIf { it.isNotBlank() },
                body.optString("uri").takeIf { it.isNotBlank() },
                devId.takeIf { it.isNotBlank() && act == "transfer" }
            ).firstOrNull().orEmpty()
            val v = body.optInt("value", 0)
            val resp = spotifyClient.sendPlayerCommand(act, arg, v, devId)
            syncLiveSpotifyPlaybackState()
            return resp
        }
        if (mutable.value.demo) return demoRequest(method, path, body)
        if (directDevice) return deviceRequest(method, path, body)
        if (endpoint.isBlank() || (!endpoint.startsWith("https://") && !endpoint.startsWith("http://"))) {
            return obj("status" to "offline", "message" to "No ESP32 connected")
        }
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

    private fun normalizeLightingForFirmware(raw: JSONObject): JSONObject {
        val rawEffect = raw.optString("effect", "solid").lowercase()
        val validEffect = when (rawEffect) {
            "solid", "beat", "bass", "level", "bands", "wled" -> rawEffect
            "spectrum", "geq" -> "bands"
            "energy", "pulse" -> "beat"
            else -> "solid"
        }
        val colorHex = raw.optString("color", "#FFB067").let {
            if (it.matches(Regex("^#[0-9A-Fa-f]{6}$"))) it else "#FFB067"
        }
        return raw.copyJson().apply {
            put("power", optBoolean("power", true))
            put("color", colorHex)
            put("brightness", optInt("brightness", 70).coerceIn(0, 100))
            put("effect", validEffect)
            put("speed", optInt("speed", 50).coerceIn(1, 100))
            put("intensity", optInt("intensity", 60).coerceIn(1, 100))
        }
    }

    private fun sanitizeRuleForFirmware(rawRule: JSONObject): JSONObject {
        val copy = rawRule.copyJson()
        copy.remove("timezone") // Firmware uses one controller timezone.
        copy.put("cooldown_seconds", copy.optInt("cooldown_seconds", 60).coerceIn(0, 86400))
        if (!copy.optString("time", "07:00").matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) {
            copy.put("time", "07:00")
        }
        val weekdaysArr = copy.optJSONArray("weekdays")
        if (weekdaysArr == null || weekdaysArr.length() == 0) {
            copy.put("weekdays", JSONArray((0..6).toList()))
        }
        val rawTrigger = copy.optString("trigger", "schedule")
        if (rawTrigger == "manual") {
            // Preserve manual metadata while satisfying strict DatabaseValidation trigger enum if needed
            copy.put("manual_only", true)
        }
        copy.optJSONObject("action")?.let { act ->
            val actCopy = act.copyJson()
            when (actCopy.optString("type")) {
                "lighting" -> actCopy.optJSONObject("lighting")?.let {
                    actCopy.put("lighting", normalizeLightingForFirmware(it))
                }
                "spotify" -> {
                    val sAct = actCopy.optString("action").ifBlank { actCopy.optString("spotify_action", "play") }
                    val sArg = actCopy.optString("argument").ifBlank { actCopy.optString("uri", "") }
                    val sVal = if (actCopy.has("value")) actCopy.optInt("value", 55) else actCopy.optInt("volume", 55)
                    actCopy.put("action", sAct).put("argument", sArg).put("value", sVal.coerceIn(0, 100))
                }
                "compound", "sequence" -> {
                    actCopy.put("type", "sequence")
                    if (!actCopy.has("actions")) {
                        val subActions = JSONArray()
                        actCopy.optJSONObject("lighting")?.let {
                            subActions.put(obj("type" to "lighting", "lighting" to normalizeLightingForFirmware(it)))
                        }
                        val cmdId = actCopy.optString("command_id")
                        if (cmdId.isNotBlank()) {
                            subActions.put(obj("type" to "ir", "command_id" to cmdId))
                        }
                        if (actCopy.has("spotify_action") || actCopy.has("uri") || actCopy.has("action")) {
                            val sAct = actCopy.optString("action").ifBlank { actCopy.optString("spotify_action", "play") }
                            val sArg = actCopy.optString("argument").ifBlank { actCopy.optString("uri", "") }
                            val sVal = if (actCopy.has("value")) actCopy.optInt("value", 55) else actCopy.optInt("volume", 55)
                            subActions.put(obj("type" to "spotify", "action" to sAct, "argument" to sArg, "value" to sVal.coerceIn(0, 100)))
                        }
                        actCopy.put("actions", subActions)
                    }
                }
            }
            copy.put("action", actCopy)
        }
        return copy
    }

    private suspend fun callCommandWithFallback(transport: DeviceTransport, command: JSONObject): JSONObject {
        return try {
            transport.call(endpoint, "/v1/command", command)
        } catch (err: Throwable) {
            val msg = err.message.orEmpty()
            val op = command.optString("op")
            // Audit 4.2: Combined V10 firmware delegates microphone/calibrate to WLED AudioReactive usermod
            if (op in setOf("microphone", "calibrate") && msg.contains("Managed by WLED", ignoreCase = true)) {
                return obj("ok" to true, "status" to "wled_managed", "message" to msg)
            }
            // Audit 5.1/5.3: If older firmware rejects "manual" trigger or "sequence" action, retry with compatible fallback
            if (op == "put" && command.optString("collection") == "rules") {
                val ruleVal = command.optJSONObject("value")?.copyJson()
                if (ruleVal != null) {
                    if (ruleVal.optString("trigger") == "manual") {
                        ruleVal.put("trigger", "schedule")
                        ruleVal.put("enabled", false)
                    }
                    val act = ruleVal.optJSONObject("action")
                    if (act != null && act.optString("type") in setOf("sequence", "compound")) {
                        val firstSub = act.optJSONArray("actions")?.optJSONObject(0)
                        if (firstSub != null) {
                            ruleVal.put("action", firstSub)
                        } else if (act.has("lighting")) {
                            ruleVal.put("action", obj("type" to "lighting", "lighting" to normalizeLightingForFirmware(act.optJSONObject("lighting") ?: obj())))
                        }
                    }
                    return transport.call(endpoint, "/v1/command", command.copyJson().put("value", ruleVal))
                }
            }
            throw err
        }
    }

    private suspend fun deviceRequest(method: String, path: String, body: JSONObject?): JSONObject {
        val transport = deviceTransport()
        if (path == "/state" || path == "/v1/state") return deviceUiState(transport.call(endpoint, "/v1/state"))
        if (path == "/v1/command") {
            val cmdBody = body?.copyJson() ?: obj()
            if (cmdBody.optString("op") == "lighting" && cmdBody.has("value")) {
                cmdBody.put("value", normalizeLightingForFirmware(cmdBody.optJSONObject("value") ?: obj()))
            }
            return callCommandWithFallback(transport, cmdBody)
        }
        // Audit 1.3: Translate legacy companion endpoints to native ESP32 /v1/state and /v1/command
        if (path == "/spotify/player") {
            val st = deviceUiState(transport.call(endpoint, "/v1/state"))
            val spot = st.optJSONObject("spotify") ?: obj()
            val trackName = spot.optString("track", "")
            val artistName = spot.optString("artist", "")
            val imageUrl = spot.optString("image_url", "")
            val devName = spot.optString("device", "")
            val vol = spot.optInt("volume_percent", 50)
            return obj(
                "is_playing" to spot.optBoolean("playing", false),
                "progress_ms" to spot.optInt("progress_ms", 0),
                "shuffle_state" to spot.optBoolean("shuffle_state", spot.optBoolean("shuffle", false)),
                "repeat_state" to spot.optString("repeat_state", spot.optString("repeat", "off")),
                "image_url" to imageUrl,
                "device" to (if (devName.isNotBlank()) obj("id" to devName, "name" to devName, "is_active" to true, "volume_percent" to vol) else obj()),
                "item" to obj(
                    "name" to trackName,
                    "duration_ms" to spot.optInt("duration_ms", 0).coerceAtLeast(1000),
                    "image_url" to imageUrl,
                    "artists" to (if (artistName.isNotBlank()) JSONArray().put(obj("name" to artistName)) else JSONArray())
                )
            )
        }
        if (path == "/spotify/devices") {
            runCatching { transport.call(endpoint, "/v1/command", obj("op" to "spotify", "action" to "devices", "argument" to "", "value" to 0)) }
            val st = deviceUiState(transport.call(endpoint, "/v1/state"))
            val spot = st.optJSONObject("spotify") ?: obj()
            val devs = spot.optJSONArray("devices") ?: JSONArray()
            return obj("devices" to devs)
        }
        if (path == "/spotify/playback") {
            val data = body ?: obj()
            val act = data.optString("action", "play")
            val arg = when (act) {
                "transfer" -> data.optString("device_id", data.optString("argument", ""))
                "shuffle" -> data.optBoolean("state", false).toString()
                "repeat" -> data.optString("mode", "off")
                else -> data.optString("uri", data.optString("argument", ""))
            }
            val v = when (act) {
                "volume" -> data.optInt("volume", data.optInt("value", 50))
                "seek" -> data.optInt("position_ms", data.optInt("value", 0))
                "shuffle" -> if (data.optBoolean("state", false)) 1 else 0
                else -> data.optInt("value", 0)
            }
            return callCommandWithFallback(transport, obj("op" to "spotify", "action" to act, "argument" to arg, "value" to v))
        }
        if (path.startsWith("/spotify/search") || path.startsWith("/spotify/playlists") || path.startsWith("/spotify/recent")) {
            val act = when {
                path.startsWith("/spotify/search") -> "search"
                path.startsWith("/spotify/recent") -> "recent"
                else -> "playlists"
            }
            val q = if (act == "search") path.substringAfter("q=", "").substringBefore("&") else ""
            runCatching { transport.call(endpoint, "/v1/command", obj("op" to "spotify", "action" to act, "argument" to java.net.URLDecoder.decode(q, "UTF-8"), "value" to 0)) }
            val st = deviceUiState(transport.call(endpoint, "/v1/state"))
            val browseItems = st.optJSONObject("spotify")?.optJSONObject("browse")?.optJSONArray("items") ?: JSONArray()
            return obj("items" to browseItems, "tracks" to obj("items" to browseItems), "playlists" to obj("items" to browseItems))
        }
        if (path == "/learning" && method == "POST") {
            val appId = body?.optString("appliance_id", "").orEmpty()
            val cmdName = body?.optString("name", "Command").orEmpty()
            callCommandWithFallback(transport, obj("op" to "learn", "appliance_id" to appId, "name" to cmdName))
            return obj("id" to "esp32-learn", "status" to "listening", "appliance_id" to appId, "name" to cmdName)
        }
        if (path.startsWith("/learning/") && path.endsWith("/save")) {
            val st = deviceUiState(transport.call(endpoint, "/v1/state"))
            savePersistedState(st)
            return obj("status" to "saved")
        }
        if (path.startsWith("/learning/")) {
            val st = deviceUiState(transport.call(endpoint, "/v1/state"))
            val rt = st.optJSONObject("runtime") ?: obj()
            val isLearning = rt.optBoolean("learning", false)
            return obj(
                "id" to "esp32-learn",
                "status" to if (isLearning) "listening" else "captured",
                "protocol" to "IR",
                "decoded" to rt.optString("message", "Captured on ESP32")
            )
        }
        if (path.startsWith("/settings")) {
            val payload = (body ?: obj()).copyJson()
            return callCommandWithFallback(transport, obj("op" to "settings", "value" to payload))
        }
        if (path == "/diagnostics") {
            val st = deviceUiState(transport.call(endpoint, "/v1/state"))
            val rt = st.optJSONObject("runtime") ?: obj()
            val ev = JSONArray().put(obj("category" to "esp32", "message" to "Firmware ${rt.optString("version", "ESP32")} · RSSI ${rt.optInt("rssi", -50)} dBm · Free heap ${rt.optInt("free_heap", 0)} B"))
            return obj("events" to ev, "jobs" to JSONArray())
        }
        if (path.startsWith("/jobs/")) {
            val jobId = path.substringAfter("/jobs/").substringBefore('?').toLongOrNull() ?: 0L
            return callCommandWithFallback(transport, obj("op" to "job", "id" to jobId))
        }

        val parts = path.substringBefore('?').trim('/').split('/')
        val collection = parts.first()
        val confirmed = path.contains("confirm=true")
        val command = when {
            path == "/actions" -> when (val type = body!!.getString("type")) {
                "lighting" -> obj("op" to type, "value" to normalizeLightingForFirmware(body.getJSONObject(type)))
                "microphone" -> obj("op" to type, "value" to body.getJSONObject(type).copyJson().apply {
                    val nf = optDouble("noise_floor", 15.0)
                    val sm = optDouble("smoothing", 60.0)
                    put("noise_floor", if (nf > 1.0) nf / 100.0 else nf)
                    put("smoothing", (if (sm > 1.0) sm / 100.0 else sm).coerceAtMost(0.99))
                    put("bass_gain", optInt("bass_gain", 100).coerceIn(0, 200))
                    put("mid_gain", optInt("mid_gain", 100).coerceIn(0, 200))
                    put("treble_gain", optInt("treble_gain", 100).coerceIn(0, 200))
                })
                "ir" -> obj("op" to "transmit", "id" to body.getString("command_id"))
                "calibrate" -> obj("op" to "calibrate")
                else -> error("Unsupported device action: $type")
            }
            collection in listOf("appliances", "commands", "presets", "rules") && method in listOf("POST", "PUT", "DELETE") -> {
                val rawValue = body?.copyJson()
                val value = when {
                    rawValue == null -> null
                    collection == "rules" -> sanitizeRuleForFirmware(rawValue)
                    collection == "presets" && rawValue.has("lighting") -> rawValue.put("lighting", normalizeLightingForFirmware(rawValue.optJSONObject("lighting") ?: obj()))
                    else -> rawValue
                }
                obj(
                    "op" to if (method == "DELETE") "delete" else "put",
                    "collection" to collection,
                    "id" to parts.getOrNull(1).orEmpty(),
                    "value" to value,
                    "confirm" to confirmed
                )
            }
            else -> error("Unsupported device endpoint: $path")
        }
        return callCommandWithFallback(transport, command)
    }

    fun spotifyCommand(action: String, argument: String = "", value: Int = 0) = work {
        val label = when (action) {
            "play" -> "Starting playback"
            "pause" -> "Pausing playback"
            "next" -> "Skipping to next track"
            "previous" -> "Returning to previous track"
            "volume" -> "Setting volume"
            "seek" -> "Seeking playback position"
            "shuffle" -> "Updating shuffle mode"
            "repeat" -> "Updating repeat mode"
            "refresh" -> "Refreshing playback"
            "devices" -> "Finding Spotify devices"
            "playlists" -> "Loading Spotify playlists"
            "recent" -> "Loading recent tracks"
            "transfer" -> "Selecting playback device"
            "search" -> "Searching Spotify"
            else -> "Updating Spotify"
        }
        mutable.value = mutable.value.copy(pendingAction=label)
        try {
            val hasDirectTokens = mutable.value.spotifyOAuthConnected || dataStore.readSpotifyOAuthTokens().connected
            if (hasDirectTokens) {
                val directResp = spotifyClient.sendPlayerCommand(action, argument, value)
                syncLiveSpotifyPlaybackState()
                val err = directResp.optString("message").ifBlank { directResp.optString("error") }
                if (err.isNotBlank() && (err.contains("NO_ACTIVE_DEVICE", ignoreCase = true) || err.contains("No active device", ignoreCase = true))) {
                    mutable.value = mutable.value.copy(notice = "No active Spotify device found. Open Spotify on your phone, laptop, or speaker.")
                } else if (err.isNotBlank() && !err.equals("success", ignoreCase = true) && !err.equals("ok", ignoreCase = true) && !err.equals("unsupported", ignoreCase = true)) {
                    mutable.value = mutable.value.copy(notice = err)
                } else {
                    mutable.value = mutable.value.copy(notice = null)
                }
                return@work
            }
            val accepted = request("POST", "/v1/command", obj("op" to "spotify", "action" to action, "argument" to argument, "value" to value))
            val id = accepted.optLong("job_id")
            val bootId = accepted.optLong("boot_id")
            if (id == 0L || bootId == 0L) {
                refresh()
                mutable.value = mutable.value.copy(notice="Sent to controller.")
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

    private val coalesceJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun coalescedLighting(lighting: JSONObject, key: String = "room-lighting") {
        val nextState = mutable.value.state.copyJson().put("lighting", lighting.copyJson())
        if (mutable.value.demo) {
            demoState.put("lighting", lighting.copyJson())
        }
        mutable.value = mutable.value.copy(state = nextState)
        coalesceJobs[key]?.cancel()
        coalesceJobs[key] = viewModelScope.launch {
            delay(110)
            savePersistedState(nextState)
            if (!mutable.value.demo && mutable.value.signedIn && !provisioning) {
                runCatching { request("POST", "/actions", action("lighting", lighting.copyJson())) }
            }
        }
    }

    fun coalescedMicrophone(microphone: JSONObject, key: String = "room-mic") {
        val nextState = mutable.value.state.copyJson().put("microphone", microphone.copyJson())
        if (mutable.value.demo) {
            demoState.put("microphone", microphone.copyJson())
        }
        mutable.value = mutable.value.copy(state = nextState)
        coalesceJobs[key]?.cancel()
        coalesceJobs[key] = viewModelScope.launch {
            delay(110)
            savePersistedState(nextState)
            if (!mutable.value.demo && mutable.value.signedIn && !provisioning) {
                runCatching { request("POST", "/actions", action("microphone", microphone.copyJson())) }
            }
        }
    }

    fun coalescedVolume(volumePercent: Int, key: String = "room-volume") {
        val clamped = volumePercent.coerceIn(0, 100)
        val curSpotify = (mutable.value.state.optJSONObject("spotify") ?: JSONObject()).copyJson()
            .put("volume_percent", clamped)
        val nextState = mutable.value.state.copyJson().put("spotify", curSpotify)
        if (mutable.value.demo) {
            demoState.put("spotify", curSpotify)
        }
        mutable.value = mutable.value.copy(state = nextState)
        coalesceJobs[key]?.cancel()
        coalesceJobs[key] = viewModelScope.launch {
            delay(130)
            savePersistedState(nextState)
            val hasDirectTokens = mutable.value.spotifyOAuthConnected || dataStore.readSpotifyOAuthTokens().connected
            if (hasDirectTokens) {
                runCatching { spotifyClient.sendPlayerCommand("volume", value = clamped) }
                return@launch
            }
            if (!mutable.value.demo && mutable.value.signedIn && !provisioning) {
                if (directDevice) {
                    runCatching { request("POST", "/v1/command", obj("op" to "spotify", "action" to "volume", "argument" to "", "value" to clamped)) }
                } else {
                    runCatching { request("POST", "/spotify/playback", obj("action" to "volume", "volume" to clamped)) }
                }
            }
        }
    }

    fun coalescedSpeakerIrVolume(volumePercent: Int, key: String = "speaker-ir-volume") {
        val isMuted = mutable.value.state.optJSONObject("system")?.optBoolean("speaker_muted", false)
            ?: mutable.value.state.optBoolean("speaker_muted", false)
        if (isMuted) return
        val clamped = volumePercent.coerceIn(0, 100)
        val prevVol = mutable.value.state.optJSONObject("system")?.optInt("speaker_volume_percent", 60)
            ?: mutable.value.state.optInt("speaker_volume_percent", 60)
        val curSystem = (mutable.value.state.optJSONObject("system") ?: JSONObject()).copyJson()
            .put("speaker_volume_percent", clamped)
        val nextState = mutable.value.state.copyJson()
            .put("system", curSystem)
            .put("speaker_volume_percent", clamped)
        if (mutable.value.demo) {
            demoState.put("system", curSystem)
            demoState.put("speaker_volume_percent", clamped)
        }
        mutable.value = mutable.value.copy(state = nextState)
        coalesceJobs[key]?.cancel()
        coalesceJobs[key] = viewModelScope.launch {
            delay(130)
            savePersistedState(nextState)
            if (!mutable.value.demo && mutable.value.signedIn && !provisioning) {
                val commands = mutable.value.state.items("commands")
                val targetKind = if (clamped >= prevVol) "volume-up" else "volume-down"
                val cmdId = commands.firstOrNull {
                    it.optString("id").contains(targetKind, ignoreCase = true) ||
                        it.optString("name").contains(if (clamped >= prevVol) "vol up" else "vol down", ignoreCase = true)
                }?.optString("id") ?: targetKind
                if (directDevice) {
                    runCatching { request("POST", "/v1/command", obj("op" to "transmit", "id" to cmdId, "value" to clamped)) }
                } else {
                    runCatching { request("POST", "/actions", obj("type" to "ir", "command_id" to cmdId, "value" to clamped)) }
                }
            }
        }
    }

    fun triggerSpeakerIrCommand(kind: String) {
        val curSystem = (mutable.value.state.optJSONObject("system") ?: JSONObject()).copyJson()
        val curVol = curSystem.optInt("speaker_volume_percent", mutable.value.state.optInt("speaker_volume_percent", 60))
        val curMuted = curSystem.optBoolean("speaker_muted", mutable.value.state.optBoolean("speaker_muted", false))
        val nextMuted = if (kind == "speaker-mute" || kind == "mute") !curMuted else curMuted
        if ((kind == "volume-up" || kind == "volume-down") && curMuted) {
            mutable.value = mutable.value.copy(notice = "Speaker is muted. Unmute first to change volume.")
            return
        }
        val nextVol = when (kind) {
            "volume-up" -> (curVol + 5).coerceIn(0, 100)
            "volume-down" -> (curVol - 5).coerceIn(0, 100)
            else -> curVol
        }
        curSystem.put("speaker_volume_percent", nextVol)
        curSystem.put("speaker_muted", nextMuted)
        val nextState = mutable.value.state.copyJson()
            .put("system", curSystem)
            .put("speaker_volume_percent", nextVol)
            .put("speaker_muted", nextMuted)
        if (mutable.value.demo) {
            demoState.put("system", curSystem)
            demoState.put("speaker_volume_percent", nextVol)
            demoState.put("speaker_muted", nextMuted)
        }
        val commands = mutable.value.state.items("commands")
        val matchedCmd = commands.firstOrNull {
            it.optString("id").equals(kind, ignoreCase = true) ||
                (kind == "volume-up" && it.optString("name").contains("vol", ignoreCase = true) && it.optString("name").contains("up", ignoreCase = true)) ||
                (kind == "volume-down" && it.optString("name").contains("vol", ignoreCase = true) && it.optString("name").contains("down", ignoreCase = true)) ||
                ((kind == "speaker-mute" || kind == "mute") && it.optString("name").contains("mute", ignoreCase = true))
        }
        val cmdId = matchedCmd?.optString("id")?.ifBlank { kind } ?: kind
        val label = when (kind) {
            "volume-up" -> "Speaker IR Volume + ($nextVol%)"
            "volume-down" -> "Speaker IR Volume − ($nextVol%)"
            "speaker-mute", "mute" -> if (nextMuted) "Speaker IR Muted (volume slider locked)" else "Speaker IR Unmuted ($nextVol%)"
            else -> "Speaker IR command sent"
        }
        savePersistedState(nextState)
        mutable.value = mutable.value.copy(state = nextState, notice = label)
        if (!mutable.value.demo && mutable.value.signedIn && !provisioning) {
            viewModelScope.launch {
                if (directDevice) {
                    runCatching { request("POST", "/v1/command", obj("op" to "transmit", "id" to cmdId)) }
                } else {
                    runCatching { request("POST", "/actions", obj("type" to "ir", "command_id" to cmdId)) }
                }
            }
        }
    }

    fun setAllRoutinesEnabled(enabled: Boolean) = work {
        val currentState = mutable.value.state.copyJson()
        val updatedRules = JSONArray()
        val existingRules = currentState.items("rules")
        existingRules.forEach { rule ->
            updatedRules.put(rule.copyJson().put("enabled", enabled))
        }
        currentState.put("rules", updatedRules)

        val curSystem = (currentState.optJSONObject("system") ?: JSONObject()).copyJson()
            .put("automation_enabled", enabled)
        currentState.put("system", curSystem)

        val curSettings = (currentState.optJSONObject("settings") ?: JSONObject()).copyJson()
            .put("automation_enabled", enabled)
        currentState.put("settings", curSettings)

        currentState.optJSONObject("runtime")?.let { rt ->
            currentState.put("runtime", rt.copyJson().put("armed", enabled))
        }

        if (mutable.value.demo) {
            demoState.put("rules", updatedRules)
            demoState.put("system", curSystem)
            demoState.put("settings", curSettings)
            demoState.optJSONObject("runtime")?.let { rt ->
                demoState.put("runtime", rt.copyJson().put("armed", enabled))
            }
        }

        savePersistedState(currentState)
        mutable.value = mutable.value.copy(
            state = currentState,
            revision = System.currentTimeMillis(),
            notice = if (enabled) "All routines & automations enabled" else "All routines & automations paused"
        )

        if (!mutable.value.demo && mutable.value.signedIn && !provisioning) {
            runCatching {
                if (directDevice) {
                    request("POST", "/v1/command", obj("op" to "automation", "enabled" to enabled, "confirm" to true))
                } else {
                    request("PUT", "/settings?confirm=true", curSystem)
                }
                existingRules.forEach { rule ->
                    val ruleId = rule.optString("id")
                    if (ruleId.isNotBlank()) {
                        val payload = rule.copyJson().apply {
                            remove("id")
                            put("enabled", enabled)
                        }
                        request("PUT", "/rules/$ruleId", payload)
                    }
                }
                refresh()
            }
        }
    }

    suspend fun refresh() {
        val state = request("GET", "/state")
        savePersistedState(state)
        mutable.value = mutable.value.copy(state=state, stale=false, revision=System.currentTimeMillis())
    }

    fun mutate(method: String, path: String, body: JSONObject? = null, success: String = "Saved") = work {
        val favoritedCommand = path.startsWith("/commands/") && method == "PUT" && body?.optBoolean("favorite", false) == true
        if (favoritedCommand) {
            val cmdId = path.substringAfter("/commands/").substringBefore('?')
            val existingFavCommands = mutable.value.state.items("commands").count {
                it.optBoolean("favorite", false) && it.optString("id") != cmdId
            }
            if (existingFavCommands >= MAX_FAVORITE_LIMIT) {
                val msg = "Favorite limit reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT). Please remove an existing favorite IR button first."
                mutable.value = mutable.value.copy(favoriteLimitWarning = msg, notice = msg)
                return@work
            }
        }
        val response = request(method, path, body)
        refresh()
        val id = response.optString("id")
        if (favoritedCommand) {
            val cmdId = path.substringAfter("/commands/").substringBefore('?')
            mutable.value = mutable.value.copy(
                favoriteJumpTrigger = System.currentTimeMillis(),
                lastFavoritedKey = "ir:$cmdId"
            )
        }
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
        } else mutable.value = mutable.value.copy(notice=if (mutable.value.demo || response.optString("status") == "simulated") "Simulated · $success" else if(directDevice && path == "/actions" && body?.optString("type") == "ir") "IR queued. Check device status for completion; appliance state is unconfirmed." else success)
    }

    private fun demoRequest(method: String, path: String, body: JSONObject?): JSONObject {
        if (path == "/state" || path == "/v1/state") return demoState.copyJson()
        previewSession.request(method, path, body, demoState)?.let {
            savePersistedState(demoState)
            return it
        }
        if (path == "/v1/command" && body != null) {
            when (val op = body.optString("op")) {
                "automation" -> {
                    val enabled = body.optBoolean("enabled", true)
                    val curSys = (demoState.optJSONObject("system") ?: JSONObject()).copyJson().put("automation_enabled", enabled)
                    val curSet = (demoState.optJSONObject("settings") ?: JSONObject()).copyJson().put("automation_enabled", enabled)
                    demoState.put("system", curSys)
                    demoState.put("settings", curSet)
                    demoState.optJSONObject("runtime")?.put("armed", enabled)
                }
                "settings" -> {
                    val v = body.optJSONObject("value") ?: body
                    val curSys = (demoState.optJSONObject("system") ?: JSONObject()).copyJson()
                    v.keys().forEach { k -> curSys.put(k, v.opt(k)) }
                    demoState.put("system", curSys)
                    demoState.put("settings", curSys.copyJson())
                }
                "lighting" -> body.optJSONObject("value")?.let { demoState.put("lighting", it.copyJson()) }
                "microphone" -> body.optJSONObject("value")?.let { demoState.put("microphone", it.copyJson()) }
                "spotify" -> {
                    val act = body.optString("action")
                    val curSpotify = (demoState.optJSONObject("spotify") ?: JSONObject()).copyJson()
                    when (act) {
                        "play" -> curSpotify.put("playing", true)
                        "pause" -> curSpotify.put("playing", false)
                        "next", "previous" -> curSpotify.put("playing", true)
                        "volume" -> curSpotify.put("volume_percent", body.optInt("value", 50).coerceIn(0, 100))
                        "seek" -> curSpotify.put("progress_ms", body.optInt("value", 0).coerceAtLeast(0))
                        "shuffle" -> {
                            val nextShuffle = body.optString("argument").equals("true", ignoreCase = true) || body.optInt("value", 0) == 1
                            curSpotify.put("shuffle_state", nextShuffle)
                        }
                        "repeat" -> {
                            val nextMode = body.optString("argument", "off").ifBlank { "off" }
                            curSpotify.put("repeat_state", nextMode)
                        }
                        "transfer" -> {
                            val target = body.optString("argument")
                            if (target.isNotBlank()) curSpotify.put("device", target)
                        }
                        "search" -> {
                            val query = body.optString("argument")
                            if (query.isNotBlank()) curSpotify.put("track", query).put("progress_ms", 0).put("playing", true)
                        }
                        "playlists", "recent", "devices" -> {
                            curSpotify.put("browse", obj("items" to JSONArray()))
                        }
                    }
                    demoState.put("spotify", curSpotify)
                }
                "put", "delete" -> {
                    val collection = body.optString("collection")
                    if (collection in listOf("appliances", "commands", "rules", "presets")) {
                        val list = demoState.items(collection).toMutableList()
                        val id = body.optString("id").ifBlank { UUID.randomUUID().toString() }
                        list.removeAll { it.optString("id") == id }
                        if (op == "put") {
                            val value = (body.optJSONObject("value") ?: JSONObject()).copyJson().put("id", id)
                            list.add(value)
                        }
                        demoState.put(collection, JSONArray(list))
                    }
                }
            }
            savePersistedState(demoState)
            return obj("status" to "simulated", "ok" to true)
        }
        if (path == "/actions") {
            when(body?.optString("type")) {
                "lighting" -> demoState.put("lighting", body.getJSONObject("lighting"))
                "microphone" -> demoState.put("microphone", body.getJSONObject("microphone"))
                "ir", "calibrate" -> { /* simulated hardware action */ }
            }
            savePersistedState(demoState)
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
            savePersistedState(demoState)
            return obj("status" to "simulated")
        }
        if (path.startsWith("/settings")) {
            if (body != null) {
                demoState.put("system", body.copyJson())
                demoState.put("settings", body.copyJson())
            }
            savePersistedState(demoState)
            return obj("status" to "simulated")
        }
        if (path == "/diagnostics") return obj("events" to JSONArray().put(obj("category" to "demo", "message" to "Interface preview · no ESP32 connected")), "jobs" to JSONArray())
        return obj("status" to "simulated")
    }
}

fun demoState() = JSONObject("""{
  "simulator":true,"connected":true,"capabilities":["lighting","ir","microphone","learn"],
  "lighting":{"power":true,"color":"#B5F36A","brightness":65,"effect":"solid","speed":50,"intensity":60},
  "microphone":{"automatic":true,"sensitivity":50,"noise_floor":15,"smoothing":60,"quiet_behavior":"dim","quiet_seconds":10,"bass_gain":100,"mid_gain":100,"treble_gain":100},
  "system":{"automation_enabled":true,"timezone":"IST-5:30","display_timeout":30,"volume_target":"spotify","speaker_volume_percent":60},
  "settings":{"automation_enabled":true,"timezone":"IST-5:30","display_timeout_seconds":30,"ntp_server":"pool.ntp.org"},
  "speaker_volume_percent":60,
  "appliances":[],
  "commands":[],
  "presets":[{"id":"focus","name":"Focus","lighting":{"power":true,"color":"#F5D5A0","brightness":75,"effect":"solid","speed":50,"intensity":60}},{"id":"listen","name":"Listen","lighting":{"power":true,"color":"#AE9CFF","brightness":60,"effect":"bass","speed":50,"intensity":60}}],
  "rules":[],"spotify_connected":false,"hardware_pending":["Hardware configuration awaiting verification"],
  "telemetry":{"firmware":"Preview","rssi":-48,"free_heap":181248,"uptime":3600,"time_valid":true,"bands":[0.3,0.6,0.4]}
}""")
