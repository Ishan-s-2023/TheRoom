package `in`.roomcontroller.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Direct Spotify Web API client that enables music playback controls, active device discovery,
 * and live track metadata retrieval directly from this Android device using encrypted OAuth tokens
 * stored in Jetpack DataStore, working whether the ESP32 controller is connected or offline.
 */
class SpotifyApiClient(private val dataStore: RoomDataStore) {

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val tokens = dataStore.readSpotifyOAuthTokens()
        if (tokens.accessToken.isBlank() && tokens.refreshToken.isBlank()) {
            return@withContext null
        }
        val now = System.currentTimeMillis()
        // If access token is still fresh (expires in > 60 seconds), use it directly
        if (tokens.accessToken.isNotBlank() && now < tokens.expiresAtMs - 60_000L) {
            return@withContext tokens.accessToken
        }
        // If access token is missing or expired, refresh it using the refresh token
        if (tokens.refreshToken.isNotBlank()) {
            val refreshed = refreshAccessToken(tokens.refreshToken, tokens.clientId)
            if (refreshed != null) {
                return@withContext refreshed
            }
        }
        return@withContext tokens.accessToken.takeIf { it.isNotBlank() }
    }

    private suspend fun refreshAccessToken(refreshToken: String, clientId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("https://accounts.spotify.com/api/token").openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val cleanClientId = clientId.ifBlank { SpotifyPkce.CLIENT_ID }
                if (cleanClientId.isBlank()) return@runCatching null
                val body = SpotifyPkce.form(
                    mapOf(
                        "grant_type" to "refresh_token",
                        "refresh_token" to refreshToken,
                        "client_id" to cleanClientId
                    )
                )
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode in 200..299) {
                    val resp = JSONObject(connection.inputStream.use { it.readBounded(32_768) })
                    val newAccess = resp.optString("access_token")
                    val newRefresh = resp.optString("refresh_token").ifBlank { refreshToken }
                    val expiresIn = resp.optLong("expires_in", 3600L)
                    if (newAccess.isNotBlank()) {
                        dataStore.saveSpotifyOAuthTokens(
                            accountName = "Spotify Account",
                            clientId = cleanClientId,
                            accessToken = newAccess,
                            refreshToken = newRefresh,
                            expiresInSec = expiresIn
                        )
                        return@runCatching newAccess
                    }
                }
                null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    suspend fun executeSpotifyRequest(
        method: String,
        endpointPath: String,
        body: JSONObject? = null,
        isRetry: Boolean = false
    ): SpotifyResponse = withContext(Dispatchers.IO) {
        val token = getValidAccessToken()
            ?: return@withContext SpotifyResponse(401, null, "Spotify not connected. Tap Connect Spotify in Settings.")

        val urlString = if (endpointPath.startsWith("http")) endpointPath else "https://api.spotify.com/v1$endpointPath"
        val connection = URL(urlString).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            // HTTP 204 No Content (normal response for playback commands like play, pause, next, volume)
            if (code == 204) {
                return@withContext SpotifyResponse(204, obj("ok" to true, "status" to "success"), null)
            }
            val stream: InputStream? = if (code in 200..299) connection.inputStream else connection.errorStream
            val rawText = stream?.use { it.readBounded(65_536) }.orEmpty()

            if (code == 401 && !isRetry) {
                // Token may have been invalidated remotely; force refresh once and retry
                val tokens = dataStore.readSpotifyOAuthTokens()
                if (tokens.refreshToken.isNotBlank()) {
                    val freshToken = refreshAccessToken(tokens.refreshToken, tokens.clientId)
                    if (freshToken != null) {
                        return@withContext executeSpotifyRequest(method, endpointPath, body, isRetry = true)
                    }
                }
            }

            val json = runCatching {
                if (rawText.trim().startsWith("[")) {
                    obj("items" to JSONArray(rawText))
                } else if (rawText.trim().startsWith("{")) {
                    JSONObject(rawText)
                } else {
                    obj("raw" to rawText)
                }
            }.getOrDefault(obj())

            if (code !in 200..299) {
                val errorMsg = json.optJSONObject("error")?.optString("message")
                    ?: json.optString("error_description")
                    ?: "Spotify API error ($code)"
                return@withContext SpotifyResponse(code, json, errorMsg)
            }
            SpotifyResponse(code, json, null)
        } catch (e: Exception) {
            SpotifyResponse(500, null, e.message ?: "Network error connecting to Spotify")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchPlayerState(): JSONObject? = withContext(Dispatchers.IO) {
        val resp = executeSpotifyRequest("GET", "/me/player")
        if (resp.code == 204 || resp.json == null) {
            return@withContext obj("is_playing" to false, "track" to "", "artist" to "", "device" to obj())
        }
        val raw = resp.json
        val item = raw.optJSONObject("item")
        val trackTitle = item?.optString("name").orEmpty()
        val artistsArr = item?.optJSONArray("artists")
        val artistTitle = if (artistsArr != null && artistsArr.length() > 0) {
            (0 until artistsArr.length()).joinToString(", ") { artistsArr.optJSONObject(it)?.optString("name").orEmpty() }
        } else ""
        val albumImages = item?.optJSONObject("album")?.optJSONArray("images")
        val albumArtUrl = albumImages?.optJSONObject(0)?.optString("url").orEmpty()
        val deviceObj = raw.optJSONObject("device") ?: obj()
        val isPlaying = raw.optBoolean("is_playing", false)
        val progressMs = raw.optInt("progress_ms", 0)
        val durationMs = item?.optInt("duration_ms", 0) ?: 0
        val shuffleState = raw.optBoolean("shuffle_state", false)
        val repeatState = raw.optString("repeat_state", "off")
        val volumePercent = deviceObj.optInt("volume_percent", 50)

        val trackId = item?.optString("id").orEmpty()
        val trackUri = item?.optString("uri").orEmpty()
        val smartShuffle = raw.optBoolean("smart_shuffle", false) || raw.optString("shuffle_state") == "smart"

        obj(
            "is_playing" to isPlaying,
            "progress_ms" to progressMs,
            "duration_ms" to durationMs,
            "shuffle_state" to shuffleState,
            "smart_shuffle" to smartShuffle,
            "repeat_state" to repeatState,
            "volume_percent" to volumePercent,
            "image_url" to albumArtUrl,
            "track" to trackTitle,
            "artist" to artistTitle,
            "id" to trackId,
            "uri" to trackUri,
            "device" to deviceObj,
            "item" to (item ?: obj(
                "id" to trackId,
                "uri" to trackUri,
                "name" to trackTitle,
                "duration_ms" to durationMs,
                "image_url" to albumArtUrl,
                "artists" to (artistsArr ?: JSONArray())
            ))
        )
    }

    suspend fun fetchDevices(): List<JSONObject> = withContext(Dispatchers.IO) {
        val resp = executeSpotifyRequest("GET", "/me/player/devices")
        if (resp.json != null) {
            val devs = resp.json.optJSONArray("devices")
            if (devs != null) {
                return@withContext (0 until devs.length()).mapNotNull { devs.optJSONObject(it) }
            }
        }
        emptyList()
    }

    suspend fun search(
        query: String,
        type: String = "track,playlist,album,artist",
        offset: Int = 0
    ): JSONObject = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val cleanType = java.net.URLEncoder.encode(type, "UTF-8")
        val resp = executeSpotifyRequest("GET", "/search?q=$encoded&type=$cleanType&limit=20&offset=$offset")
        resp.json ?: obj("tracks" to obj("items" to JSONArray()), "playlists" to obj("items" to JSONArray()))
    }

    suspend fun fetchPlaylists(offset: Int = 0): JSONObject = withContext(Dispatchers.IO) {
        val resp = executeSpotifyRequest("GET", "/me/playlists?limit=25&offset=$offset")
        resp.json ?: obj("items" to JSONArray())
    }

    suspend fun fetchRecentTracks(): JSONObject = withContext(Dispatchers.IO) {
        val resp = executeSpotifyRequest("GET", "/me/player/recently-played?limit=25")
        resp.json ?: obj("items" to JSONArray())
    }

    suspend fun fetchLikedTracks(offset: Int = 0): JSONObject = withContext(Dispatchers.IO) {
        val resp = executeSpotifyRequest("GET", "/me/tracks?limit=25&offset=$offset")
        if (resp.code == 403) {
            return@withContext obj("items" to JSONArray(), "scope_required" to true)
        }
        val items = resp.json?.optJSONArray("items")
        if (items != null) {
            val unwrapped = JSONArray()
            for (i in 0 until items.length()) {
                val itemObj = items.optJSONObject(i)
                val track = itemObj?.optJSONObject("track")
                if (track != null) unwrapped.put(track)
            }
            return@withContext obj("items" to unwrapped, "total" to resp.json.optInt("total", unwrapped.length()))
        }
        resp.json ?: obj("items" to JSONArray())
    }

    suspend fun fetchQueue(): JSONObject = withContext(Dispatchers.IO) {
        val resp = executeSpotifyRequest("GET", "/me/player/queue")
        resp.json ?: obj("currently_playing" to obj(), "queue" to JSONArray())
    }

    suspend fun addToQueue(uri: String): JSONObject = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(uri, "UTF-8")
        val resp = executeSpotifyRequest("POST", "/me/player/queue?uri=$encoded")
        resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
    }

    suspend fun fetchRecommendations(seedTrackId: String, limit: Int = 12): JSONObject = withContext(Dispatchers.IO) {
        if (seedTrackId.isBlank()) return@withContext obj("tracks" to JSONArray())
        val cleanId = seedTrackId.removePrefix("spotify:track:").trim()
        val resp = executeSpotifyRequest("GET", "/recommendations?seed_tracks=$cleanId&limit=$limit")
        resp.json ?: obj("tracks" to JSONArray())
    }

    suspend fun checkTrackSaved(trackId: String): Boolean = withContext(Dispatchers.IO) {
        if (trackId.isBlank()) return@withContext false
        val cleanId = trackId.removePrefix("spotify:track:").trim()
        val resp = executeSpotifyRequest("GET", "/me/tracks/contains?ids=$cleanId")
        val arr = resp.json?.optJSONArray("items") ?: (runCatching {
            val raw = resp.json?.optString("raw", "").orEmpty()
            if (raw.startsWith("[")) JSONArray(raw) else null
        }.getOrNull())
        arr?.optBoolean(0, false) ?: false
    }

    suspend fun saveTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        if (trackId.isBlank()) return@withContext false
        val cleanId = trackId.removePrefix("spotify:track:").trim()
        val resp = executeSpotifyRequest("PUT", "/me/tracks?ids=$cleanId")
        resp.isSuccess
    }

    suspend fun removeTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        if (trackId.isBlank()) return@withContext false
        val cleanId = trackId.removePrefix("spotify:track:").trim()
        val resp = executeSpotifyRequest("DELETE", "/me/tracks?ids=$cleanId")
        resp.isSuccess
    }

    suspend fun sendPlayerCommand(
        action: String,
        argument: String = "",
        value: Int = 0,
        deviceId: String = ""
    ): JSONObject = withContext(Dispatchers.IO) {
        val devParam = if (deviceId.isNotBlank()) "?device_id=${java.net.URLEncoder.encode(deviceId, "UTF-8")}" else ""
        when (action) {
            "play" -> {
                val body = if (argument.isNotBlank()) {
                    if (argument.contains("playlist") || argument.contains("album") || argument.contains("artist")) {
                        obj("context_uri" to argument)
                    } else if (argument.contains("track")) {
                        obj("uris" to JSONArray().put(argument))
                    } else null
                } else null
                var resp = executeSpotifyRequest("PUT", "/me/player/play$devParam", body)
                // If no active device and no deviceId specified, try to find an available device and wake it up
                if (!resp.isSuccess && deviceId.isBlank() && resp.error?.contains("device", ignoreCase = true) == true) {
                    val availableDevices = fetchDevices()
                    val targetDev = availableDevices.firstOrNull()?.optString("id")
                    if (!targetDev.isNullOrBlank()) {
                        resp = executeSpotifyRequest("PUT", "/me/player/play?device_id=${java.net.URLEncoder.encode(targetDev, "UTF-8")}", body)
                    }
                }
                resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
            }
            "pause" -> {
                val resp = executeSpotifyRequest("PUT", "/me/player/pause$devParam")
                resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
            }
            "toggle" -> {
                val current = fetchPlayerState()
                val currentlyPlaying = current?.optBoolean("is_playing", false) ?: false
                if (currentlyPlaying) {
                    sendPlayerCommand("pause", deviceId = deviceId)
                } else {
                    sendPlayerCommand("play", deviceId = deviceId)
                }
            }
            "next" -> {
                val resp = executeSpotifyRequest("POST", "/me/player/next$devParam")
                resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
            }
            "previous" -> {
                val resp = executeSpotifyRequest("POST", "/me/player/previous$devParam")
                resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
            }
            "volume" -> {
                val vol = value.coerceIn(0, 100)
                val sep = if (devParam.isNotEmpty()) "&" else ""
                val devPart = if (devParam.isNotEmpty()) devParam.removePrefix("?") else ""
                val query = if (devPart.isNotEmpty()) "volume_percent=$vol&$devPart" else "volume_percent=$vol"
                val resp = executeSpotifyRequest("PUT", "/me/player/volume?$query")
                resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
            }
            "seek" -> {
                val pos = value.coerceAtLeast(0)
                val sep = if (devParam.isNotEmpty()) "&" else ""
                val devPart = if (devParam.isNotEmpty()) devParam.removePrefix("?") else ""
                val query = if (devPart.isNotEmpty()) "position_ms=$pos&$devPart" else "position_ms=$pos"
                val resp = executeSpotifyRequest("PUT", "/me/player/seek?$query")
                resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
            }
            "shuffle" -> {
                val isMagic = argument.equals("magic", ignoreCase = true) || argument.equals("smart", ignoreCase = true) || value == 2
                val shuffleState = isMagic || argument.equals("true", ignoreCase = true) || value == 1
                val sep = if (devParam.isNotEmpty()) "&" else ""
                val devPart = if (devParam.isNotEmpty()) devParam.removePrefix("?") else ""
                val query = if (devPart.isNotEmpty()) "state=$shuffleState&$devPart" else "state=$shuffleState"
                val resp = executeSpotifyRequest("PUT", "/me/player/shuffle?$query")
                if (isMagic) {
                    val current = fetchPlayerState()
                    val trackId = current?.optJSONObject("item")?.optString("id").orEmpty()
                    if (trackId.isNotBlank()) {
                        val recs = fetchRecommendations(trackId, limit = 5)
                        val tracks = recs.optJSONArray("tracks")
                        if (tracks != null) {
                            for (i in 0 until tracks.length()) {
                                val u = tracks.optJSONObject(i)?.optString("uri").orEmpty()
                                if (u.isNotBlank()) addToQueue(u)
                            }
                        }
                    }
                }
                resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error, "magic_shuffle" to isMagic)
            }
            "magic_shuffle", "smart_shuffle" -> {
                sendPlayerCommand("shuffle", argument = "magic", value = 2, deviceId = deviceId)
            }
            "queue" -> {
                val uri = if (argument.startsWith("spotify:")) argument else "spotify:track:$argument"
                addToQueue(uri)
            }
            "like", "save" -> {
                val trackId = argument.removePrefix("spotify:track:").trim()
                val ok = saveTrack(trackId)
                obj("status" to if (ok) "ok" else "error", "saved" to ok)
            }
            "unlike", "unsave" -> {
                val trackId = argument.removePrefix("spotify:track:").trim()
                val ok = removeTrack(trackId)
                obj("status" to if (ok) "ok" else "error", "saved" to !ok)
            }
            "play_liked" -> {
                val liked = fetchLikedTracks(0)
                val items = liked.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val uris = JSONArray()
                    for (i in 0 until items.length().coerceAtMost(50)) {
                        val u = items.optJSONObject(i)?.optString("uri").orEmpty()
                        if (u.isNotBlank()) uris.put(u)
                    }
                    val body = obj("uris" to uris)
                    val resp = executeSpotifyRequest("PUT", "/me/player/play$devParam", body)
                    sendPlayerCommand("shuffle", argument = "true", value = 1, deviceId = deviceId)
                    resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
                } else {
                    obj("status" to "error", "message" to "No liked songs found to play")
                }
            }
            "repeat" -> {
                val mode = when (argument.lowercase()) {
                    "track" -> "track"
                    "context", "all" -> "context"
                    else -> "off"
                }
                val sep = if (devParam.isNotEmpty()) "&" else ""
                val devPart = if (devParam.isNotEmpty()) devParam.removePrefix("?") else ""
                val query = if (devPart.isNotEmpty()) "state=$mode&$devPart" else "state=$mode"
                val resp = executeSpotifyRequest("PUT", "/me/player/repeat?$query")
                resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
            }
            "transfer" -> {
                val targetDevId = if (deviceId.isNotBlank()) deviceId.trim() else argument.trim()
                if (targetDevId.isNotBlank()) {
                    val body = obj("device_ids" to JSONArray().put(targetDevId), "play" to true)
                    val resp = executeSpotifyRequest("PUT", "/me/player", body)
                    resp.json ?: obj("status" to if (resp.isSuccess) "ok" else "error", "message" to resp.error)
                } else {
                    obj("status" to "error", "message" to "No device ID specified")
                }
            }
            else -> obj("status" to "unsupported")
        }
    }
}

data class SpotifyResponse(
    val code: Int,
    val json: JSONObject?,
    val error: String?
) {
    val isSuccess get() = code in 200..299
}
