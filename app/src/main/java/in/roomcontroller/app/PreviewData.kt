package `in`.roomcontroller.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** In-memory fixtures only. Never opens a network connection or sends a physical command. */
class PreviewSession {
    private val devices = JSONArray("""[
        {"id":"preview-speaker","name":"Desk speaker","is_active":true,"is_restricted":false,"volume_percent":45},
        {"id":"preview-computer","name":"Living room computer","is_active":false,"is_restricted":false,"volume_percent":60},
        {"id":"preview-tv","name":"Bedroom Studio Monitor","is_active":false,"is_restricted":false,"volume_percent":55},
        {"id":"preview-restricted","name":"Restricted device example","is_active":false,"is_restricted":true,"volume_percent":25}
    ]""")

    private val catalog = listOf(
        Triple(
            "Evening Signals",
            "Kiasmos & Room Studio",
            "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=640&auto=format&fit=crop&q=80"
        ),
        Triple(
            "Morning Light",
            "Nils Frahm",
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=640&auto=format&fit=crop&q=80"
        ),
        Triple(
            "Midnight Horizon",
            "Tycho",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=640&auto=format&fit=crop&q=80"
        ),
        Triple(
            "Warm Analog",
            "Bonobo",
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=640&auto=format&fit=crop&q=80"
        )
    )
    private var trackIndex = 0
    private val player = obj(
        "is_playing" to true,
        "item" to track(catalog[0].first),
        "device" to devices.getJSONObject(0).copyJson(),
        "image_url" to catalog[0].third
    )
    private var learning: JSONObject? = null
    private var captureAt = 0L

    fun trackForName(name: String): JSONObject = track(name)

    private fun track(name: String): JSONObject {
        val matched = catalog.firstOrNull { it.first.equals(name, ignoreCase = true) || name.contains(it.first, ignoreCase = true) }
            ?: when {
                name.contains("Morning", ignoreCase = true) -> catalog[1]
                name.contains("Midnight", ignoreCase = true) || name.contains("Slow", ignoreCase = true) -> catalog[2]
                name.contains("Warm", ignoreCase = true) || name.contains("Desk", ignoreCase = true) -> catalog[3]
                else -> Triple(name, "Room Preview", catalog[0].third)
            }
        val imageUrl = matched.third
        val artistName = matched.second
        return obj(
            "name" to matched.first,
            "uri" to "spotify:track:${matched.first.lowercase().replace(' ', '-')}",
            "artists" to JSONArray().put(obj("name" to artistName)),
            "duration_ms" to 214000,
            "image_url" to imageUrl,
            "album" to obj(
                "name" to "${matched.first} LP",
                "images" to JSONArray().put(obj("url" to imageUrl, "width" to 640, "height" to 640))
            )
        )
    }

    fun syncPlayerWithSpotifyState(state: JSONObject) {
        val spot = state.optJSONObject("spotify") ?: return
        val currentTrackName = spot.optString("track").ifBlank { "" }
        if (currentTrackName.isBlank()) {
            player.put("is_playing", spot.optBoolean("playing", false))
            player.remove("item")
            return
        }
        val t = track(currentTrackName)
        val img = t.optString("image_url")
        val art = t.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "Room Preview"
        player.put("is_playing", spot.optBoolean("playing", true))
        player.put("progress_ms", spot.optInt("progress_ms", player.optInt("progress_ms", 41000)))
        player.put("shuffle_state", spot.optBoolean("shuffle_state", player.optBoolean("shuffle_state", false)))
        player.put("repeat_state", spot.optString("repeat_state", player.optString("repeat_state", "off")).ifBlank { "off" })
        player.put("item", t)
        player.put("image_url", img)
        spot.put("image_url", img)
        spot.put("artist", art)
        spot.put("duration_ms", t.optInt("duration_ms", 214000))
        if (!spot.has("progress_ms")) spot.put("progress_ms", player.optInt("progress_ms", 41000))
        val devName = spot.optString("device")
        if (devName.isNotBlank()) {
            for (i in 0 until devices.length()) {
                val d = devices.getJSONObject(i)
                val isMatch = d.optString("name").equals(devName, ignoreCase = true) || d.optString("id").equals(devName, ignoreCase = true)
                d.put("is_active", isMatch)
                if (isMatch) {
                    d.put("volume_percent", spot.optInt("volume_percent", d.optInt("volume_percent", 45)))
                    player.put("device", d.copyJson())
                    spot.put("device", d.optString("name"))
                }
            }
        }
    }

    fun seed(state: JSONObject) {
        state.put("spotify_connected", true)
        val initialTrack = catalog[trackIndex]
        state.put("spotify", obj(
            "connected" to true,
            "playing" to true,
            "track" to initialTrack.first,
            "artist" to initialTrack.second,
            "device" to "Desk speaker",
            "volume_percent" to 45,
            "speaker_volume_percent" to 60,
            "progress_ms" to 41000,
            "duration_ms" to 214000,
            "shuffle_state" to false,
            "repeat_state" to "off",
            "image_url" to initialTrack.third,
            "devices" to JSONArray(devices.toString())
        ))
        if (!state.has("commands")) {
            val defaultSeedCommands = listOf(
                Triple("power", "fan", "Power"),
                Triple("fan-speed-up", "fan", "Speed +"),
                Triple("fan-speed-down", "fan", "Speed -"),
                Triple("volume-up", "speaker", "Volume +"),
                Triple("volume-down", "speaker", "Volume -"),
                Triple("speaker-mute", "speaker", "Mute")
            )
            val arr = JSONArray()
            for ((id, appId, name) in defaultSeedCommands) {
                arr.put(
                    obj(
                        "id" to id,
                        "appliance_id" to appId,
                        "name" to name,
                        "favorite" to (id == "power"),
                        "toggle" to (id == "power" || id == "speaker-mute"),
                        "protocol" to "NEC",
                        "carrier_khz" to 38,
                        "decoded" to "0x20DF${(id.hashCode() and 0xFFFF).toString(16).uppercase().padStart(4, '0')}",
                        "raw" to JSONArray(listOf(9000, 4500, 560, 560)),
                        "repeat_raw" to JSONArray()
                    )
                )
            }
            state.put("commands", arr)
        }
        val existingRules = state.optJSONArray("rules")
        if (existingRules == null || existingRules.length() == 0) {
            val rulesArr = JSONArray()
            rulesArr.put(obj(
                "id" to "morning",
                "name" to "Morning Wakeup",
                "category" to "morning",
                "enabled" to true,
                "trigger" to "schedule",
                "time" to "07:00",
                "timezone" to "Asia/Kolkata",
                "weekdays" to JSONArray(listOf(0,1,2,3,4)),
                "cooldown_seconds" to 60,
                "action" to action("lighting", obj("power" to true, "color" to "#FFF0D0", "brightness" to 85, "effect" to "solid", "speed" to 50, "intensity" to 60))
            ))
            rulesArr.put(obj(
                "id" to "evening",
                "name" to "Evening Wind-Down",
                "category" to "evening",
                "enabled" to true,
                "trigger" to "schedule",
                "time" to "19:00",
                "timezone" to "Asia/Kolkata",
                "weekdays" to JSONArray((0..6).toList()),
                "cooldown_seconds" to 60,
                "action" to action("lighting", obj("power" to true, "color" to "#FF9E3B", "brightness" to 50, "effect" to "solid", "speed" to 50, "intensity" to 60))
            ))
            rulesArr.put(obj(
                "id" to "sound-rule",
                "name" to "Music Bass Sync",
                "category" to "music",
                "enabled" to true,
                "trigger" to "sound_started",
                "time" to "19:00",
                "timezone" to "Asia/Kolkata",
                "weekdays" to JSONArray((0..6).toList()),
                "cooldown_seconds" to 60,
                "action" to action("lighting", obj("power" to true, "color" to "#B5F36A", "brightness" to 95, "effect" to "bass", "speed" to 75, "intensity" to 80))
            ))
            rulesArr.put(obj(
                "id" to "night",
                "name" to "Goodnight & Sleep",
                "category" to "night",
                "enabled" to false,
                "trigger" to "schedule",
                "time" to "23:30",
                "timezone" to "Asia/Kolkata",
                "weekdays" to JSONArray((0..6).toList()),
                "cooldown_seconds" to 60,
                "action" to action("lighting", obj("power" to false, "color" to "#000000", "brightness" to 0, "effect" to "solid", "speed" to 50, "intensity" to 60))
            ))
            state.put("rules", rulesArr)
        }
    }

    fun request(method: String, path: String, body: JSONObject?, state: JSONObject): JSONObject? {
        if (path == "/spotify/player") {
            syncPlayerWithSpotifyState(state)
            return player.copyJson()
        }
        if (path == "/spotify/devices") return obj("devices" to devices)
        if (path == "/spotify/connect") error("UI preview: Spotify authorization opens in the connected app. Choose Full preview in the browser to inspect playback without an account.")
        if (path == "/spotify/playback") {
            val data = requireNotNull(body)
            val curSpot = (state.optJSONObject("spotify") ?: JSONObject()).copyJson()
            when(data.optString("action")) {
                "play", "pause" -> {
                    val isPlay = data.optString("action") == "play"
                    player.put("is_playing", isPlay)
                    curSpot.put("playing", isPlay)
                    if (data.has("uri") || data.has("name")) {
                        val uriOrName = data.optString("name").ifBlank {
                            val rawUri = data.optString("uri")
                            when {
                                rawUri.contains("morning", ignoreCase = true) -> "Morning Light"
                                rawUri.contains("midnight", ignoreCase = true) || rawUri.contains("evening", ignoreCase = true) -> "Midnight Horizon"
                                rawUri.contains("desk", ignoreCase = true) || rawUri.contains("warm", ignoreCase = true) -> "Warm Analog"
                                else -> "Evening Signals"
                            }
                        }
                        val t = track(uriOrName)
                        val img = t.optString("image_url")
                        val art = t.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "Room Preview"
                        player.put("item", t)
                        player.put("image_url", img)
                        curSpot.put("track", t.optString("name"))
                        curSpot.put("artist", art)
                        curSpot.put("image_url", img)
                    }
                }
                "next", "previous" -> {
                    trackIndex = if (data.optString("action") == "next") {
                        (trackIndex + 1) % catalog.size
                    } else {
                        (trackIndex - 1 + catalog.size) % catalog.size
                    }
                    val nextEntry = catalog[trackIndex]
                    val t = track(nextEntry.first)
                    player.put("item", t)
                    player.put("image_url", nextEntry.third)
                    player.put("is_playing", true)
                    curSpot
                        .put("track", nextEntry.first)
                        .put("artist", nextEntry.second)
                        .put("image_url", nextEntry.third)
                        .put("playing", true)
                }
                "volume" -> {
                    val vol = data.optInt("volume")
                    player.optJSONObject("device")?.put("volume_percent", vol)
                    curSpot.put("volume_percent", vol)
                }
                "seek" -> {
                    val pos = data.optInt("position_ms", data.optInt("value", 0)).coerceAtLeast(0)
                    player.put("progress_ms", pos)
                    curSpot.put("progress_ms", pos)
                }
                "shuffle" -> {
                    val nextShuffle = if (data.has("state")) data.optBoolean("state") else data.optString("argument").equals("true", ignoreCase = true) || data.optInt("value", 0) == 1
                    player.put("shuffle_state", nextShuffle)
                    curSpot.put("shuffle_state", nextShuffle)
                }
                "repeat" -> {
                    val nextMode = data.optString("mode").ifBlank { data.optString("argument", "off") }
                    player.put("repeat_state", nextMode)
                    curSpot.put("repeat_state", nextMode)
                }
                "transfer" -> {
                    val targetIdOrName = data.optString("device_id")
                    for (i in 0 until devices.length()) {
                        val d = devices.getJSONObject(i)
                        val isMatch = d.optString("id") == targetIdOrName || d.optString("name") == targetIdOrName
                        d.put("is_active", isMatch)
                        if (isMatch) {
                            player.put("device", d.copyJson())
                            curSpot.put("device", d.optString("name"))
                        }
                    }
                    curSpot.put("devices", JSONArray(devices.toString()))
                }
            }
            state.put("spotify", curSpot)
            return obj("status" to "simulated")
        }
        if (path.startsWith("/spotify/search") || path.startsWith("/spotify/playlists") || path.startsWith("/spotify/recent")) {
            val more = path.contains("offset=0") && !path.contains("before=preview-next")
            val tracks = JSONArray().apply {
                catalog.forEach { put(track(it.first)) }
            }
            val playlists = JSONArray()
                .put(obj("name" to "Desk Sessions", "uri" to "spotify:playlist:preview-desk", "image_url" to catalog[3].third))
                .put(obj("name" to "Slow Evenings", "uri" to "spotify:playlist:preview-evening", "image_url" to catalog[2].third))
            val next = if (more) "preview-next" else JSONObject.NULL
            return when {
                path.startsWith("/spotify/search") -> obj("tracks" to obj("items" to tracks,"next" to next), "playlists" to obj("items" to playlists,"next" to next))
                path.startsWith("/spotify/recent") -> obj("items" to JSONArray(tracks.objects().map {obj("track" to it)}), "next" to next,"cursors" to obj("before" to "preview-next"))
                else -> obj("items" to playlists,"next" to next)
            }
        }
        if (path == "/learning" && method == "POST") {
            val data = requireNotNull(body)
            captureAt = System.currentTimeMillis() + 900
            val cmdName = data.getString("name")
            val hexSuffix = ((cmdName.hashCode() and 0xFFFF)).toString(16).uppercase().padStart(4, '0')
            learning = obj(
                "id" to "capture-${UUID.randomUUID().toString().take(8)}",
                "status" to "listening",
                "appliance_id" to data.getString("appliance_id"),
                "name" to cmdName,
                "toggle" to data.optBoolean("toggle"),
                "decoded_hex" to "0x20DF$hexSuffix"
            )
            return learning!!.copyJson()
        }
        if (path.startsWith("/learning/") && path.endsWith("/save")) {
            val capture = learning ?: error("Start an IR capture first")
            check(capture.optString("status") == "captured") { "Wait for IR signal capture to finish" }
            val cmdName = capture.getString("name")
            val slug = cmdName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "ir-cmd" }
            val uniqueId = if (state.items("commands").any { it.optString("id") == slug }) {
                "$slug-${UUID.randomUUID().toString().take(4)}"
            } else slug
            val decodedHex = capture.optString("decoded_hex", "0x20DF10EF")
            state.getJSONArray("commands").put(
                obj(
                    "id" to uniqueId,
                    "appliance_id" to capture.getString("appliance_id"),
                    "name" to cmdName,
                    "toggle" to capture.optBoolean("toggle"),
                    "favorite" to false,
                    "protocol" to "NEC",
                    "carrier_khz" to 38,
                    "decoded" to decodedHex,
                    "raw" to JSONArray(listOf(9000, 4500, 560, 560, 560, 1690, 560, 560)),
                    "repeat_raw" to JSONArray()
                )
            )
            learning = null
            return obj("status" to "saved", "id" to uniqueId)
        }
        if (path.startsWith("/learning/")) {
            val capture = learning ?: error("Start an IR capture first")
            if (System.currentTimeMillis() >= captureAt) {
                val decodedHex = capture.optString("decoded_hex", "0x20DF10EF")
                capture.put("status", "captured").put("protocol", "NEC").put("decoded", "$decodedHex · 38 kHz")
            }
            return capture.copyJson()
        }
        if (path == "/backup") return obj("archive" to "UI-PREVIEW-ONLY-NOT-A-REAL-BACKUP")
        if (path == "/restore" || path == "/ota") return obj("status" to "simulated")
        if (path == "/diagnostics") return obj("events" to JSONArray().put(obj("category" to "preview", "message" to "UI review session; no ESP32 connected")), "jobs" to JSONArray().put(obj("status" to "simulated","detail" to "Lighting preview applied; no physical output")))
        return null
    }
}
