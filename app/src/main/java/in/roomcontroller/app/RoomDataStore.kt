package `in`.roomcontroller.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

val Context.roomDataStore: DataStore<Preferences> by preferencesDataStore(name = "room_user_state")

object PresetCategories {
    const val ALL = "All"
    const val FAVORITES = "Favorites"
    const val AMBIENT = "Ambient"
    const val VIBRANT_FX = "Vibrant & FX"
    const val MUSIC_SOUND = "Music & Sound"
    const val FOCUS_WORK = "Focus & Work"
    const val PLAYLISTS = "Playlists"
    const val ROOM_SCENES = "Room Scenes"

    val FILTER_CHIPS: List<String> = listOf(
        ALL,
        FAVORITES,
        AMBIENT,
        VIBRANT_FX,
        MUSIC_SOUND,
        FOCUS_WORK,
        PLAYLISTS,
        ROOM_SCENES
    )

    val ASSIGNABLE: List<String> = listOf(
        AMBIENT,
        VIBRANT_FX,
        MUSIC_SOUND,
        FOCUS_WORK,
        ROOM_SCENES
    )

    fun inferForWledPreset(entry: WledPresetEntry, customCategories: Map<String, String>): String {
        val custom = customCategories["wled:${entry.id}"]
        if (!custom.isNullOrBlank() && custom in ASSIGNABLE) return custom
        if (entry.isPlaylist) return PLAYLISTS

        val lowerName = "${entry.name} ${entry.quickLabel}".lowercase()
        val rawSeg = entry.raw.optJSONArray("seg")?.optJSONObject(0)
        val fxId = rawSeg?.optInt("fx", -1) ?: -1

        return when {
            lowerName.contains("music") || lowerName.contains("sound") || lowerName.contains("beat") ||
                lowerName.contains("audio") || lowerName.contains("geq") || lowerName.contains("party") ||
                fxId in 128..186 -> MUSIC_SOUND
            lowerName.contains("focus") || lowerName.contains("work") || lowerName.contains("study") ||
                lowerName.contains("desk") || lowerName.contains("read") || lowerName.contains("daylight") -> FOCUS_WORK
            lowerName.contains("warm") || lowerName.contains("cozy") || lowerName.contains("relax") ||
                lowerName.contains("sunset") || lowerName.contains("ambient") || lowerName.contains("night") ||
                lowerName.contains("sleep") || lowerName.contains("candle") || fxId == 0 -> AMBIENT
            else -> VIBRANT_FX
        }
    }

    fun inferForRoomPreset(preset: JSONObject, customCategories: Map<String, String>): String {
        val id = preset.optString("id")
        val custom = customCategories["room:$id"]
        if (!custom.isNullOrBlank() && custom in ASSIGNABLE) return custom

        val lowerName = preset.optString("name", id).lowercase()
        val effect = preset.optJSONObject("lighting")?.optString("effect", "solid")?.lowercase().orEmpty()
        return when {
            lowerName.contains("listen") || lowerName.contains("music") || lowerName.contains("sound") ||
                effect in setOf("bass", "spectrum", "energy", "pulse") -> MUSIC_SOUND
            lowerName.contains("focus") || lowerName.contains("work") || lowerName.contains("study") ||
                lowerName.contains("desk") -> FOCUS_WORK
            lowerName.contains("warm") || lowerName.contains("cozy") || lowerName.contains("relax") ||
                lowerName.contains("sleep") || lowerName.contains("ambient") -> AMBIENT
            else -> ROOM_SCENES
        }
    }

    fun matchesFilter(
        filterCategory: String,
        assignedCategory: String,
        isFavorite: Boolean,
        isPlaylist: Boolean,
        isRoomScene: Boolean
    ): Boolean = when (filterCategory) {
        ALL -> true
        FAVORITES -> isFavorite
        PLAYLISTS -> isPlaylist
        ROOM_SCENES -> isRoomScene || assignedCategory == ROOM_SCENES
        else -> assignedCategory == filterCategory
    }

    fun parseCategoryMap(jsonText: String?): Map<String, String> {
        if (jsonText.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(jsonText)
            val result = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k)
                if (v.isNotBlank()) result[k] = v
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun serializeCategoryMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}

data class PersistedWledDataStoreState(
    val activeTab: String? = null,
    val pcMode: Boolean? = null,
    val colorSlot: Int? = null,
    val effectFilter: String? = null,
    val configSection: String? = null,
    val homePresetCategory: String? = null,
    val presetCategories: Map<String, String>? = null,
    val selectedDeviceId: String? = null,
    val devicesJson: String? = null,
    val savedSnapshotJson: String? = null,
    val savedPresetsJson: String? = null,
    val savedConfigJson: String? = null,
    val favoriteEffects: Set<Int>? = null,
    val favoritePalettes: Set<Int>? = null,
    val favoritePresets: Set<Int>? = null,
    val favoriteColors: Set<String>? = null
)

data class PersistedRoomDataStoreState(
    val selectedPage: String? = null,
    val use24HourFormat: Boolean? = null,
    val lastBackupTimestamp: String? = null,
    val persistedRoomStateJson: String? = null,
    val favoriteRoomPresets: Set<String>? = null,
    val favoriteRoutines: Set<String>? = null,
    val favoriteSoundModes: Set<Int>? = null,
    val spotifyConnected: Boolean = false,
    val spotifyAccountName: String = "",
    val spotifyClientId: String = "",
    val spotifyTokenExpiresAtMs: Long = 0L,
    val hasSpotifyTokens: Boolean = false
)

data class PersistedSpotifyOAuthTokens(
    val connected: Boolean,
    val accountName: String,
    val clientId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long
)

data class DataStoreBackupSummary(
    val timestamp: String,
    val presetCount: Int,
    val routineCount: Int,
    val commandCount: Int,
    val deviceCount: Int,
    val use24HourFormat: Boolean
)

class RoomDataStore(private val context: Context) {
    companion object {
        val KEY_ACTIVE_TAB = stringPreferencesKey("wled_active_tab")
        val KEY_PC_MODE = booleanPreferencesKey("wled_pc_mode")
        val KEY_COLOR_SLOT = intPreferencesKey("wled_color_slot")
        val KEY_EFFECT_FILTER = stringPreferencesKey("wled_effect_filter")
        val KEY_CONFIG_SECTION = stringPreferencesKey("wled_config_section")
        val KEY_HOME_PRESET_CATEGORY = stringPreferencesKey("home_preset_category")
        val KEY_PRESET_CATEGORIES_JSON = stringPreferencesKey("preset_categories_json")
        val KEY_SELECTED_DEVICE = stringPreferencesKey("wled_selected_device")
        val KEY_DEVICES_JSON = stringPreferencesKey("wled_devices_json")
        val KEY_SAVED_SNAPSHOT_JSON = stringPreferencesKey("wled_saved_snapshot_json")
        val KEY_SAVED_PRESETS_JSON = stringPreferencesKey("wled_saved_presets_json")
        val KEY_SAVED_CONFIG_JSON = stringPreferencesKey("wled_saved_config_json")
        val KEY_FAV_EFFECTS = stringSetPreferencesKey("wled_favorite_effects")
        val KEY_FAV_PALETTES = stringSetPreferencesKey("wled_favorite_palettes")
        val KEY_FAV_PRESETS = stringSetPreferencesKey("wled_favorite_presets")
        val KEY_FAV_COLORS = stringSetPreferencesKey("wled_favorite_colors")

        val KEY_SELECTED_PAGE = stringPreferencesKey("room_selected_page")
        val KEY_USE_24_HOUR_FORMAT = booleanPreferencesKey("room_use_24_hour_format")
        val KEY_LAST_BACKUP_JSON = stringPreferencesKey("room_last_backup_json")
        val KEY_LAST_BACKUP_TIMESTAMP = stringPreferencesKey("room_last_backup_timestamp")
        val KEY_ROOM_STATE_JSON = stringPreferencesKey("room_persisted_state_json")
        val KEY_FAV_ROOM_PRESETS = stringSetPreferencesKey("room_fav_presets")
        val KEY_FAV_ROUTINES = stringSetPreferencesKey("room_fav_routines")
        val KEY_FAV_SOUND_MODES = stringSetPreferencesKey("room_fav_sound_modes")

        val KEY_SPOTIFY_CONNECTED = booleanPreferencesKey("spotify_oauth_connected")
        val KEY_SPOTIFY_ACCOUNT_NAME = stringPreferencesKey("spotify_oauth_account_name")
        val KEY_SPOTIFY_CLIENT_ID = stringPreferencesKey("spotify_oauth_client_id")
        val KEY_SPOTIFY_ACCESS_TOKEN_ENC = stringPreferencesKey("spotify_oauth_access_token_enc")
        val KEY_SPOTIFY_REFRESH_TOKEN_ENC = stringPreferencesKey("spotify_oauth_refresh_token_enc")
        val KEY_SPOTIFY_EXPIRES_AT_MS = longPreferencesKey("spotify_oauth_expires_at_ms")

        private const val OAUTH_KEY_ALIAS = "room-datastore-spotify-oauth"

        private fun getOrCreateOAuthSecretKey(): SecretKey? = runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(OAUTH_KEY_ALIAS, null) as? SecretKey)
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(
                        KeyGenParameterSpec.Builder(
                            OAUTH_KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                }.generateKey()
        }.getOrNull()

        fun encryptToken(plainText: String): String {
            if (plainText.isBlank()) return ""
            return runCatching {
                val secretKey = getOrCreateOAuthSecretKey()
                if (secretKey != null) {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.ENCRYPT_MODE, secretKey)
                    }
                    val combined = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                    "gcm:" + Base64.encodeToString(combined, Base64.NO_WRAP)
                } else {
                    "b64:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                }
            }.getOrElse {
                "b64:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
        }

        fun decryptToken(cipherText: String?): String {
            if (cipherText.isNullOrBlank()) return ""
            return runCatching {
                when {
                    cipherText.startsWith("gcm:") -> {
                        val secretKey = getOrCreateOAuthSecretKey() ?: return@runCatching ""
                        val bytes = Base64.decode(cipherText.removePrefix("gcm:"), Base64.NO_WRAP)
                        Cipher.getInstance("AES/GCM/NoPadding").run {
                            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                            String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
                        }
                    }
                    cipherText.startsWith("b64:") -> {
                        String(Base64.decode(cipherText.removePrefix("b64:"), Base64.NO_WRAP), Charsets.UTF_8)
                    }
                    else -> ""
                }
            }.getOrDefault("")
        }

        suspend fun readDevicesJson(context: Context): String {
            return runCatching {
                context.roomDataStore.data
                    .catch { emit(emptyPreferences()) }
                    .map { it[KEY_DEVICES_JSON] ?: "[]" }
                    .firstOrNull() ?: "[]"
            }.getOrDefault("[]")
        }
    }

    val wledFlow: Flow<PersistedWledDataStoreState> = context.roomDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            PersistedWledDataStoreState(
                activeTab = prefs[KEY_ACTIVE_TAB],
                pcMode = prefs[KEY_PC_MODE],
                colorSlot = prefs[KEY_COLOR_SLOT],
                effectFilter = prefs[KEY_EFFECT_FILTER],
                configSection = prefs[KEY_CONFIG_SECTION],
                homePresetCategory = prefs[KEY_HOME_PRESET_CATEGORY],
                presetCategories = prefs[KEY_PRESET_CATEGORIES_JSON]?.let { PresetCategories.parseCategoryMap(it) },
                selectedDeviceId = prefs[KEY_SELECTED_DEVICE],
                devicesJson = prefs[KEY_DEVICES_JSON],
                savedSnapshotJson = prefs[KEY_SAVED_SNAPSHOT_JSON],
                savedPresetsJson = prefs[KEY_SAVED_PRESETS_JSON],
                savedConfigJson = prefs[KEY_SAVED_CONFIG_JSON],
                favoriteEffects = prefs[KEY_FAV_EFFECTS]?.mapNotNull { it.toIntOrNull() }?.toSet(),
                favoritePalettes = prefs[KEY_FAV_PALETTES]?.mapNotNull { it.toIntOrNull() }?.toSet(),
                favoritePresets = prefs[KEY_FAV_PRESETS]?.mapNotNull { it.toIntOrNull() }?.toSet(),
                favoriteColors = prefs[KEY_FAV_COLORS]
            )
        }

    val roomFlow: Flow<PersistedRoomDataStoreState> = context.roomDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val hasAccess = !prefs[KEY_SPOTIFY_ACCESS_TOKEN_ENC].isNullOrBlank()
            val hasRefresh = !prefs[KEY_SPOTIFY_REFRESH_TOKEN_ENC].isNullOrBlank()
            PersistedRoomDataStoreState(
                selectedPage = prefs[KEY_SELECTED_PAGE],
                use24HourFormat = prefs[KEY_USE_24_HOUR_FORMAT],
                lastBackupTimestamp = prefs[KEY_LAST_BACKUP_TIMESTAMP],
                persistedRoomStateJson = prefs[KEY_ROOM_STATE_JSON],
                favoriteRoomPresets = prefs[KEY_FAV_ROOM_PRESETS],
                favoriteRoutines = prefs[KEY_FAV_ROUTINES],
                favoriteSoundModes = prefs[KEY_FAV_SOUND_MODES]?.mapNotNull { it.toIntOrNull() }?.toSet(),
                spotifyConnected = prefs[KEY_SPOTIFY_CONNECTED] == true || hasAccess || hasRefresh,
                spotifyAccountName = prefs[KEY_SPOTIFY_ACCOUNT_NAME].orEmpty(),
                spotifyClientId = prefs[KEY_SPOTIFY_CLIENT_ID].orEmpty(),
                spotifyTokenExpiresAtMs = prefs[KEY_SPOTIFY_EXPIRES_AT_MS] ?: 0L,
                hasSpotifyTokens = hasAccess || hasRefresh
            )
        }

    suspend fun saveSpotifyOAuthTokens(
        accountName: String,
        clientId: String,
        accessToken: String,
        refreshToken: String,
        expiresInSec: Long = 3600L
    ): Long {
        val expiresAtMs = System.currentTimeMillis() + (expiresInSec.coerceAtLeast(60L) * 1000L)
        val encAccess = encryptToken(accessToken.trim())
        val encRefresh = encryptToken(refreshToken.trim())
        context.roomDataStore.edit { prefs ->
            prefs[KEY_SPOTIFY_CONNECTED] = true
            prefs[KEY_SPOTIFY_ACCOUNT_NAME] = accountName.trim().ifBlank { "Spotify Premium Account" }
            prefs[KEY_SPOTIFY_CLIENT_ID] = clientId.trim().ifBlank { SpotifyPkce.CLIENT_ID }
            if (encAccess.isNotBlank()) prefs[KEY_SPOTIFY_ACCESS_TOKEN_ENC] = encAccess
            if (encRefresh.isNotBlank()) prefs[KEY_SPOTIFY_REFRESH_TOKEN_ENC] = encRefresh
            prefs[KEY_SPOTIFY_EXPIRES_AT_MS] = expiresAtMs
        }
        return expiresAtMs
    }

    suspend fun clearSpotifyOAuthTokens() {
        context.roomDataStore.edit { prefs ->
            prefs[KEY_SPOTIFY_CONNECTED] = false
            prefs.remove(KEY_SPOTIFY_ACCOUNT_NAME)
            prefs.remove(KEY_SPOTIFY_ACCESS_TOKEN_ENC)
            prefs.remove(KEY_SPOTIFY_REFRESH_TOKEN_ENC)
            prefs.remove(KEY_SPOTIFY_EXPIRES_AT_MS)
        }
    }

    suspend fun readSpotifyOAuthTokens(): PersistedSpotifyOAuthTokens {
        var result = PersistedSpotifyOAuthTokens(
            connected = false,
            accountName = "",
            clientId = SpotifyPkce.CLIENT_ID,
            accessToken = "",
            refreshToken = "",
            expiresAtMs = 0L
        )
        context.roomDataStore.edit { prefs ->
            val acc = decryptToken(prefs[KEY_SPOTIFY_ACCESS_TOKEN_ENC])
            val ref = decryptToken(prefs[KEY_SPOTIFY_REFRESH_TOKEN_ENC])
            val connected = prefs[KEY_SPOTIFY_CONNECTED] == true || acc.isNotBlank() || ref.isNotBlank()
            result = PersistedSpotifyOAuthTokens(
                connected = connected,
                accountName = prefs[KEY_SPOTIFY_ACCOUNT_NAME].orEmpty(),
                clientId = prefs[KEY_SPOTIFY_CLIENT_ID].orEmpty().ifBlank { SpotifyPkce.CLIENT_ID },
                accessToken = acc,
                refreshToken = ref,
                expiresAtMs = prefs[KEY_SPOTIFY_EXPIRES_AT_MS] ?: 0L
            )
        }
        return result
    }

    suspend fun saveWledUi(ui: WledUi) {
        val snapJson = ui.snapshot?.let { obj("state" to it.state, "info" to it.info).toString() }
        val devicesJson = JSONArray().apply { ui.devices.forEach { put(it.json()) } }.toString()
        val categoriesJson = PresetCategories.serializeCategoryMap(ui.presetCategories)
        context.roomDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_TAB] = ui.activeTab
            prefs[KEY_PC_MODE] = ui.pcMode
            prefs[KEY_COLOR_SLOT] = ui.colorSlot
            prefs[KEY_EFFECT_FILTER] = ui.effectFilter
            prefs[KEY_CONFIG_SECTION] = ui.configSection
            prefs[KEY_HOME_PRESET_CATEGORY] = ui.homePresetCategory
            prefs[KEY_PRESET_CATEGORIES_JSON] = categoriesJson
            if (ui.selected != null) prefs[KEY_SELECTED_DEVICE] = ui.selected else prefs.remove(KEY_SELECTED_DEVICE)
            prefs[KEY_DEVICES_JSON] = devicesJson
            if (snapJson != null) prefs[KEY_SAVED_SNAPSHOT_JSON] = snapJson
            if (ui.presets.length() > 0) prefs[KEY_SAVED_PRESETS_JSON] = ui.presets.toString()
            if (ui.config != null) prefs[KEY_SAVED_CONFIG_JSON] = ui.config.toString()
            prefs[KEY_FAV_EFFECTS] = ui.favoriteEffects.map { it.toString() }.toSet()
            prefs[KEY_FAV_PALETTES] = ui.favoritePalettes.map { it.toString() }.toSet()
            prefs[KEY_FAV_PRESETS] = ui.favoritePresets.map { it.toString() }.toSet()
            prefs[KEY_FAV_COLORS] = ui.favoriteColors
        }
    }

    suspend fun saveRoomState(
        selectedPage: String,
        use24HourFormat: Boolean,
        stateJson: String,
        favoriteRoomPresets: Set<String>,
        favoriteRoutines: Set<String>,
        favoriteSoundModes: Set<Int>
    ) {
        context.roomDataStore.edit { prefs ->
            prefs[KEY_SELECTED_PAGE] = selectedPage
            prefs[KEY_USE_24_HOUR_FORMAT] = use24HourFormat
            if (stateJson.length > 2) {
                prefs[KEY_ROOM_STATE_JSON] = stateJson
            }
            prefs[KEY_FAV_ROOM_PRESETS] = favoriteRoomPresets
            prefs[KEY_FAV_ROUTINES] = favoriteRoutines
            prefs[KEY_FAV_SOUND_MODES] = favoriteSoundModes.map { it.toString() }.toSet()
        }
    }

    suspend fun saveSelectedPage(page: String) {
        context.roomDataStore.edit { prefs ->
            prefs[KEY_SELECTED_PAGE] = page
        }
    }

    suspend fun saveUse24HourFormat(use24Hour: Boolean) {
        context.roomDataStore.edit { prefs ->
            prefs[KEY_USE_24_HOUR_FORMAT] = use24Hour
        }
    }

    suspend fun exportConfigurationBackup(
        currentRoomUi: RoomUi,
        saveLocalSnapshot: Boolean = true
    ): Pair<String, String> {
        val timestamp = java.time.Instant.now().toString()
        val roomPrefs = context.getSharedPreferences("room-settings", Context.MODE_PRIVATE)
        val wledPrefs = context.getSharedPreferences("room-wled", Context.MODE_PRIVATE)
        val appearancePrefs = context.getSharedPreferences("room-appearance", Context.MODE_PRIVATE)

        var backupText = ""
        context.roomDataStore.edit { prefs ->
            val roomStateStr = prefs[KEY_ROOM_STATE_JSON]
                ?: currentRoomUi.state.takeIf { it.length() > 0 }?.toString()
                ?: roomPrefs.getString("persisted_room_state", null)
                ?: "{}"
            val roomStateObj = runCatching { JSONObject(roomStateStr) }.getOrDefault(JSONObject())
            val use24h = prefs[KEY_USE_24_HOUR_FORMAT] ?: currentRoomUi.use24HourFormat
            val selectedPage = prefs[KEY_SELECTED_PAGE] ?: currentRoomUi.selectedPage
            val favRoomPresets = prefs[KEY_FAV_ROOM_PRESETS] ?: currentRoomUi.favoriteRoomPresets
            val favRoutines = prefs[KEY_FAV_ROUTINES] ?: currentRoomUi.favoriteRoutines
            val favSoundModes = prefs[KEY_FAV_SOUND_MODES] ?: currentRoomUi.favoriteSoundModes.map { it.toString() }.toSet()

            val wledDevicesObj = runCatching {
                JSONArray(prefs[KEY_DEVICES_JSON] ?: wledPrefs.getString("devices", "[]") ?: "[]")
            }.getOrDefault(JSONArray())
            val wledSnapshotObj = runCatching {
                JSONObject(prefs[KEY_SAVED_SNAPSHOT_JSON] ?: wledPrefs.getString("saved_snapshot", "{}") ?: "{}")
            }.getOrDefault(JSONObject())
            val wledPresetsObj = runCatching {
                JSONObject(prefs[KEY_SAVED_PRESETS_JSON] ?: wledPrefs.getString("saved_presets", "{}") ?: "{}")
            }.getOrDefault(JSONObject())
            val wledConfigObj = runCatching {
                JSONObject(prefs[KEY_SAVED_CONFIG_JSON] ?: wledPrefs.getString("saved_config", "{}") ?: "{}")
            }.getOrDefault(JSONObject())
            val presetCategoriesObj = runCatching {
                JSONObject(prefs[KEY_PRESET_CATEGORIES_JSON] ?: wledPrefs.getString("preset_categories_json", "{}") ?: "{}")
            }.getOrDefault(JSONObject())

            val root = obj(
                "schema" to "room-controller-datastore-backup-v1",
                "exported_at" to timestamp,
                "appearance_theme" to (appearancePrefs.getString("theme", "oled") ?: "oled"),
                "room_datastore" to obj(
                    "selected_page" to selectedPage,
                    "use_24_hour_format" to use24h,
                    "endpoint" to (roomPrefs.getString("endpoint", "") ?: ""),
                    "favorite_room_presets" to JSONArray(favRoomPresets.toList()),
                    "favorite_routines" to JSONArray(favRoutines.toList()),
                    "favorite_sound_modes" to JSONArray(favSoundModes.mapNotNull { it.toIntOrNull() }),
                    "room_state" to roomStateObj
                ),
                "wled_datastore" to obj(
                    "active_tab" to (prefs[KEY_ACTIVE_TAB] ?: wledPrefs.getString("active_tab", "Colors") ?: "Colors"),
                    "pc_mode" to (prefs[KEY_PC_MODE] ?: wledPrefs.getBoolean("pc_mode", false)),
                    "color_slot" to (prefs[KEY_COLOR_SLOT] ?: wledPrefs.getInt("color_slot", 0)),
                    "effect_filter" to (prefs[KEY_EFFECT_FILTER] ?: wledPrefs.getString("effect_filter", "All") ?: "All"),
                    "config_section" to (prefs[KEY_CONFIG_SECTION] ?: wledPrefs.getString("config_section", "Wi-Fi Setup") ?: "Wi-Fi Setup"),
                    "home_preset_category" to (prefs[KEY_HOME_PRESET_CATEGORY] ?: wledPrefs.getString("home_preset_category", PresetCategories.ALL) ?: PresetCategories.ALL),
                    "selected_device" to (prefs[KEY_SELECTED_DEVICE] ?: wledPrefs.getString("selected", null)),
                    "preset_categories" to presetCategoriesObj,
                    "favorite_effects" to JSONArray((prefs[KEY_FAV_EFFECTS] ?: emptySet()).mapNotNull { it.toIntOrNull() }),
                    "favorite_palettes" to JSONArray((prefs[KEY_FAV_PALETTES] ?: emptySet()).mapNotNull { it.toIntOrNull() }),
                    "favorite_presets" to JSONArray((prefs[KEY_FAV_PRESETS] ?: emptySet()).mapNotNull { it.toIntOrNull() }),
                    "favorite_colors" to JSONArray((prefs[KEY_FAV_COLORS] ?: emptySet()).toList()),
                    "devices" to wledDevicesObj,
                    "saved_snapshot" to wledSnapshotObj,
                    "saved_presets" to wledPresetsObj,
                    "saved_config" to wledConfigObj
                )
            )
            backupText = root.toString(2)
            if (saveLocalSnapshot) {
                prefs[KEY_LAST_BACKUP_JSON] = backupText
                prefs[KEY_LAST_BACKUP_TIMESTAMP] = timestamp
            }
        }
        return timestamp to backupText
    }

    suspend fun importConfigurationBackup(rawJson: String): DataStoreBackupSummary {
        val parsed = JSONObject(rawJson.trim())
        val timestamp = parsed.optString("exported_at").ifBlank { java.time.Instant.now().toString() }
        val roomDs = parsed.optJSONObject("room_datastore") ?: parsed
        val wledDs = parsed.optJSONObject("wled_datastore")

        val roomStateObj = roomDs.optJSONObject("room_state")
            ?: parsed.optJSONObject("state")
            ?: if (parsed.has("commands") || parsed.has("rules") || parsed.has("lighting")) parsed else null

        val use24h = when {
            roomDs.has("use_24_hour_format") -> roomDs.optBoolean("use_24_hour_format", true)
            roomStateObj?.optJSONObject("system")?.has("use_24_hour_format") == true ->
                roomStateObj.optJSONObject("system")!!.optBoolean("use_24_hour_format", true)
            else -> true
        }
        val selectedPage = roomDs.optString("selected_page", "Home").ifBlank { "Home" }

        fun JSONArray?.toStringSet(): Set<String> {
            if (this == null) return emptySet()
            return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
        }

        val favRoomPresets = roomDs.optJSONArray("favorite_room_presets")?.toStringSet()
        val favRoutines = roomDs.optJSONArray("favorite_routines")?.toStringSet()
        val favSoundModes = roomDs.optJSONArray("favorite_sound_modes")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx -> arr.optInt(idx, -1).takeIf { it >= 0 }?.toString() }.toSet()
        }

        val roomPrefs = context.getSharedPreferences("room-settings", Context.MODE_PRIVATE)
        val wledPrefs = context.getSharedPreferences("room-wled", Context.MODE_PRIVATE)

        context.roomDataStore.edit { prefs ->
            prefs[KEY_SELECTED_PAGE] = selectedPage
            prefs[KEY_USE_24_HOUR_FORMAT] = use24h
            prefs[KEY_LAST_BACKUP_TIMESTAMP] = timestamp
            prefs[KEY_LAST_BACKUP_JSON] = rawJson.trim()

            if (roomStateObj != null && roomStateObj.length() > 0) {
                val sys = (roomStateObj.optJSONObject("system") ?: JSONObject()).put("use_24_hour_format", use24h)
                roomStateObj.put("system", sys)
                val stateStr = roomStateObj.toString()
                prefs[KEY_ROOM_STATE_JSON] = stateStr
                roomPrefs.edit().putString("persisted_room_state", stateStr).apply()
            }
            roomPrefs.edit().putBoolean("use_24_hour_format", use24h).putString("selected_page", selectedPage).apply()
            if (roomDs.optString("endpoint").isNotBlank()) {
                roomPrefs.edit().putString("endpoint", roomDs.optString("endpoint")).apply()
            }
            if (favRoomPresets != null) {
                prefs[KEY_FAV_ROOM_PRESETS] = favRoomPresets
                roomPrefs.edit().putStringSet("fav_room_presets", favRoomPresets).apply()
            }
            if (favRoutines != null) {
                prefs[KEY_FAV_ROUTINES] = favRoutines
                roomPrefs.edit().putStringSet("fav_routines", favRoutines).apply()
            }
            if (favSoundModes != null) {
                prefs[KEY_FAV_SOUND_MODES] = favSoundModes
                roomPrefs.edit().putStringSet("fav_sound_modes", favSoundModes).apply()
            }

            if (wledDs != null) {
                val activeTab = wledDs.optString("active_tab", "Colors")
                val pcMode = wledDs.optBoolean("pc_mode", false)
                val colorSlot = wledDs.optInt("color_slot", 0).coerceIn(0, 2)
                val effectFilter = wledDs.optString("effect_filter", "All")
                val configSection = wledDs.optString("config_section", "Wi-Fi Setup")
                val homeCat = wledDs.optString("home_preset_category", PresetCategories.ALL)
                val selectedDev = wledDs.optString("selected_device").takeIf { it.isNotBlank() && it != "null" }

                prefs[KEY_ACTIVE_TAB] = activeTab
                prefs[KEY_PC_MODE] = pcMode
                prefs[KEY_COLOR_SLOT] = colorSlot
                prefs[KEY_EFFECT_FILTER] = effectFilter
                prefs[KEY_CONFIG_SECTION] = configSection
                prefs[KEY_HOME_PRESET_CATEGORY] = homeCat
                if (selectedDev != null) prefs[KEY_SELECTED_DEVICE] = selectedDev

                val wledEdit = wledPrefs.edit()
                    .putString("active_tab", activeTab)
                    .putBoolean("pc_mode", pcMode)
                    .putInt("color_slot", colorSlot)
                    .putString("effect_filter", effectFilter)
                    .putString("config_section", configSection)
                    .putString("home_preset_category", homeCat)
                if (selectedDev != null) wledEdit.putString("selected", selectedDev)

                wledDs.optJSONObject("preset_categories")?.let { catObj ->
                    val s = catObj.toString()
                    prefs[KEY_PRESET_CATEGORIES_JSON] = s
                    wledEdit.putString("preset_categories_json", s)
                }
                wledDs.optJSONArray("devices")?.let { devArr ->
                    val s = devArr.toString()
                    prefs[KEY_DEVICES_JSON] = s
                    wledEdit.putString("devices", s)
                }
                wledDs.optJSONObject("saved_snapshot")?.takeIf { it.length() > 0 }?.let { snapObj ->
                    val s = snapObj.toString()
                    prefs[KEY_SAVED_SNAPSHOT_JSON] = s
                    wledEdit.putString("saved_snapshot", s)
                }
                wledDs.optJSONObject("saved_presets")?.takeIf { it.length() > 0 }?.let { presObj ->
                    val s = presObj.toString()
                    prefs[KEY_SAVED_PRESETS_JSON] = s
                    wledEdit.putString("saved_presets", s)
                }
                wledDs.optJSONObject("saved_config")?.takeIf { it.length() > 0 }?.let { cfgObj ->
                    val s = cfgObj.toString()
                    prefs[KEY_SAVED_CONFIG_JSON] = s
                    wledEdit.putString("saved_config", s)
                }
                wledDs.optJSONArray("favorite_effects")?.let { arr ->
                    val set = (0 until arr.length()).mapNotNull { arr.optInt(it, -1).takeIf { v -> v >= 0 }?.toString() }.toSet()
                    prefs[KEY_FAV_EFFECTS] = set
                    wledEdit.putStringSet("favorite_effects", set)
                }
                wledDs.optJSONArray("favorite_palettes")?.let { arr ->
                    val set = (0 until arr.length()).mapNotNull { arr.optInt(it, -1).takeIf { v -> v >= 0 }?.toString() }.toSet()
                    prefs[KEY_FAV_PALETTES] = set
                    wledEdit.putStringSet("favorite_palettes", set)
                }
                wledDs.optJSONArray("favorite_presets")?.let { arr ->
                    val set = (0 until arr.length()).mapNotNull { arr.optInt(it, -1).takeIf { v -> v >= 0 }?.toString() }.toSet()
                    prefs[KEY_FAV_PRESETS] = set
                    wledEdit.putStringSet("favorite_presets", set)
                }
                wledDs.optJSONArray("favorite_colors")?.toStringSet()?.let { set ->
                    prefs[KEY_FAV_COLORS] = set
                    wledEdit.putStringSet("favorite_colors", set)
                }
                wledEdit.apply()
            }
        }

        val wledPresetCount = wledDs?.optJSONObject("saved_presets")?.keys()?.asSequence()?.count { it != "0" } ?: 0
        val roomPresetCount = roomStateObj?.optJSONArray("presets")?.length() ?: 0
        val routineCount = roomStateObj?.optJSONArray("rules")?.length() ?: 0
        val commandCount = roomStateObj?.optJSONArray("commands")?.length() ?: 0
        val deviceCount = wledDs?.optJSONArray("devices")?.length() ?: 0

        return DataStoreBackupSummary(
            timestamp = timestamp,
            presetCount = wledPresetCount + roomPresetCount,
            routineCount = routineCount,
            commandCount = commandCount,
            deviceCount = deviceCount,
            use24HourFormat = use24h
        )
    }

    suspend fun readLatestLocalBackupJson(): String? {
        var json: String? = null
        context.roomDataStore.edit { prefs ->
            json = prefs[KEY_LAST_BACKUP_JSON]
        }
        return json
    }
}
