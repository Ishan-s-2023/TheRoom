package `in`.roomcontroller.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** In-memory fixtures only. Never opens a network connection or sends a physical command. */
class PreviewSession {
    private val devices = JSONArray("""[
        {"id":"preview-speaker","name":"Desk speaker","is_active":true,"is_restricted":false,"volume_percent":45},
        {"id":"preview-computer","name":"Living room computer","is_active":false,"is_restricted":false,"volume_percent":60},
        {"id":"preview-restricted","name":"Restricted device example","is_active":false,"is_restricted":true,"volume_percent":25}
    ]""")
    private val player = obj("is_playing" to true, "item" to track("Evening Signals"), "device" to devices.getJSONObject(0).copyJson())
    private var learning: JSONObject? = null
    private var captureAt = 0L

    private fun track(name: String) = obj("name" to name, "uri" to "spotify:track:preview", "artists" to JSONArray().put(obj("name" to "Room Preview")))

    fun seed(state: JSONObject) {
        state.put("spotify_connected", true)
        for ((id,name) in listOf("volume-up" to "Volume up", "volume-down" to "Volume down")) {
            state.getJSONArray("commands").put(obj("id" to id,"appliance_id" to "speaker","name" to name,"favorite" to false,"toggle" to false,"protocol" to "PREVIEW","carrier_khz" to 38,"raw" to JSONArray(listOf(9000,4500,560,560)),"repeat_raw" to JSONArray()))
        }
        val lights = state.getJSONObject("lighting").copyJson()
        state.getJSONArray("rules").put(obj("id" to "evening","name" to "Evening light","enabled" to true,"trigger" to "schedule","time" to "19:00","timezone" to "Asia/Kolkata","weekdays" to JSONArray((0..6).toList()),"cooldown_seconds" to 60,"action" to action("lighting", lights)))
        state.getJSONArray("rules").put(obj("id" to "sound-rule","name" to "When music starts","enabled" to false,"trigger" to "sound_started","time" to "19:00","timezone" to "Asia/Kolkata","weekdays" to JSONArray((0..6).toList()),"cooldown_seconds" to 60,"action" to action("lighting", lights.copyJson().put("effect","bass"))))
    }

    fun request(method: String, path: String, body: JSONObject?, state: JSONObject): JSONObject? {
        if (path == "/spotify/player") return player.copyJson()
        if (path == "/spotify/devices") return obj("devices" to devices)
        if (path == "/spotify/connect") error("UI preview: Spotify authorization opens in the connected app. Choose Full preview in the browser to inspect playback without an account.")
        if (path == "/spotify/playback") {
            val data = requireNotNull(body)
            when(data.optString("action")) {
                "play", "pause" -> {
                    player.put("is_playing", data.optString("action") == "play")
                    if (data.has("uri")) player.put("item", track("Selected preview track"))
                }
                "next", "previous" -> player.put("item", track(if(data.optString("action")=="next") "Morning Light" else "Evening Signals"))
                "volume" -> player.getJSONObject("device").put("volume_percent", data.optInt("volume"))
                "seek" -> player.put("progress_ms",data.optInt("position_ms"))
                "shuffle" -> player.put("shuffle_state",data.optBoolean("state"))
                "repeat" -> player.put("repeat_state",data.optString("mode"))
                "transfer" -> devices.objects().firstOrNull {it.optString("id")==data.optString("device_id")}?.let {player.put("device",it.copyJson())}
            }
            return obj("status" to "simulated")
        }
        if (path.startsWith("/spotify/search") || path.startsWith("/spotify/playlists") || path.startsWith("/spotify/recent")) {
            val more = path.contains("offset=0") && !path.contains("before=preview-next")
            val tracks = JSONArray().put(track("Evening Signals")).put(track("Morning Light"))
            val playlists = JSONArray().put(obj("name" to "Desk Sessions", "uri" to "spotify:playlist:preview-desk"))
                .put(obj("name" to "Slow Evenings", "uri" to "spotify:playlist:preview-evening"))
            val next = if (more) "preview-next" else JSONObject.NULL
            return when {
                path.startsWith("/spotify/search") -> obj("tracks" to obj("items" to tracks,"next" to next), "playlists" to obj("items" to playlists,"next" to next))
                path.startsWith("/spotify/recent") -> obj("items" to JSONArray(tracks.objects().map {obj("track" to it)}), "next" to next,"cursors" to obj("before" to "preview-next"))
                else -> obj("items" to playlists,"next" to next)
            }
        }
        if (path == "/learning" && method == "POST") {
            val data = requireNotNull(body)
            captureAt = System.currentTimeMillis() + 1800
            learning = obj("id" to "preview-capture", "status" to "listening", "appliance_id" to data.getString("appliance_id"), "name" to data.getString("name"), "toggle" to data.optBoolean("toggle"))
            return learning!!.copyJson()
        }
        if (path.startsWith("/learning/") && path.endsWith("/save")) {
            val capture = learning ?: error("Start a preview capture first")
            check(capture.optString("status")=="captured") { "Wait for the preview capture" }
            state.getJSONArray("commands").put(obj("id" to UUID.randomUUID().toString(),"appliance_id" to capture.getString("appliance_id"),"name" to capture.getString("name"),"toggle" to capture.optBoolean("toggle"),"favorite" to false,"protocol" to "PREVIEW","carrier_khz" to 38,"decoded" to "Simulated capture","raw" to JSONArray(listOf(9000,4500,560,560)),"repeat_raw" to JSONArray()))
            learning = null
            return obj("status" to "simulated")
        }
        if (path.startsWith("/learning/")) {
            val capture = learning ?: error("Start a preview capture first")
            if (System.currentTimeMillis() >= captureAt) capture.put("status","captured").put("protocol","PREVIEW").put("decoded","Simulated signal; no receiver connected")
            return capture.copyJson()
        }
        if (path == "/backup") return obj("archive" to "UI-PREVIEW-ONLY-NOT-A-REAL-BACKUP")
        if (path == "/restore" || path == "/ota") return obj("status" to "simulated")
        if (path == "/diagnostics") return obj("events" to JSONArray().put(obj("category" to "preview", "message" to "UI review session; no ESP32 connected")), "jobs" to JSONArray().put(obj("status" to "simulated","detail" to "Lighting preview applied; no physical output")))
        return null
    }
}
