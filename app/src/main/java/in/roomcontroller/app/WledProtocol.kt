package `in`.roomcontroller.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/** WLED origins are independent of Room's pinned HTTPS identity and bearer token. */
internal fun wledOrigin(input: String): String {
    val text = input.trim().trimEnd('/')
    val uri = URI(if (text.contains("://")) text else "http://$text")
    require(uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.query == null && uri.fragment == null &&
        uri.path.orEmpty().isEmpty() && (uri.port == -1 || uri.port in 1..65535)) {
        "Enter a controller address, such as 192.168.1.105 or wled.local"
    }
    return URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, null, null, null).toASCIIString()
}

internal fun sameWledOrigin(origin: String, candidate: String): Boolean = runCatching {
    val base = URI(origin)
    val url = URI(candidate)
    fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80
    url.userInfo == null && base.scheme == url.scheme && base.host.equals(url.host, true) && port(base) == port(url)
}.getOrDefault(false)

data class WledDevice(val id: String, val origin: String, val name: String, val hidden: Boolean = false) {
    fun json() = obj("id" to id, "origin" to origin, "name" to name, "hidden" to hidden)
    companion object {
        fun parse(json: JSONObject) = WledDevice(json.getString("id"), wledOrigin(json.getString("origin")),
            json.getString("name"), json.optBoolean("hidden"))
    }
}

data class WledSnapshot(val state: JSONObject, val info: JSONObject) {
    companion object {
        fun parse(json: JSONObject): WledSnapshot {
            val state = json.getJSONObject("state")
            val info = json.getJSONObject("info")
            require(info.has("ver") && info.optJSONObject("leds") != null && state.has("on") && state.has("bri")) {
                "This address is not serving WLED. Install the WLED-based Room firmware first."
            }
            return WledSnapshot(state, info)
        }
    }
}

/** No automatic POST retries: a transport failure can occur after a command was applied. */
class WledApi(val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
    .callTimeout(9, TimeUnit.SECONDS).retryOnConnectionFailure(false)
    .followRedirects(false).followSslRedirects(false).build()) {
    suspend fun request(origin: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        require(path.startsWith('/') && !path.startsWith("//"))
        val base = wledOrigin(origin)
        val builder = Request.Builder().url(base + path).header("Accept", "application/json")
        if (body != null) builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "WLED returned HTTP ${response.code}. Check the connection and controller access settings." }
            val stream = response.body ?: error("WLED returned an empty response")
            // Bounded read, including responses with no Content-Length header.
            require(stream.contentLength() <= 2_097_152) { "WLED response is too large" }
            val bytes = stream.byteStream().use { it.readBytesBounded(2_097_152) }
            bytes.toString(Charsets.UTF_8)
        }
    }

    suspend fun state(origin: String) = WledSnapshot.parse(JSONObject(request(origin, "/json/si")))

    suspend fun command(origin: String, body: JSONObject): WledSnapshot {
        val result = JSONObject(request(origin, "/json/state", body.copyJson().put("v", true)))
        check(!result.has("error") && (!result.has("success") || result.optBoolean("success"))) {
            "WLED rejected the command: ${result.opt("error") ?: "unsuccessful response"}"
        }
        // Read authoritative state. Never treat an acknowledgement as a confirmed display state.
        return state(origin)
    }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer)
        if (size < 0) return out.toByteArray()
        require(out.size() + size <= limit) { "WLED response is too large" }
        out.write(buffer, 0, size)
    }
}

internal fun wledDeviceId(info: JSONObject) = info.optString("mac").lowercase().filter { it.isLetterOrDigit() }
    .ifBlank { error("WLED did not provide its hardware identity") }

internal fun wledByte(percent: Float) = (percent.coerceIn(0f, 100f) * 255f / 100f).toInt()
