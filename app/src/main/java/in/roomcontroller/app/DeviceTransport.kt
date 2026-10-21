package `in`.roomcontroller.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class DevicePairing(val ssid: String, val apPassword: String, val token: String,
    val hostname: String, val setupIp: String, val fingerprint: String) {
    companion object {
        fun parse(text: String): DevicePairing {
            val j = JSONObject(text)
            val pin = j.getString("certificate_sha256").replace(":", "").lowercase()
            require(pin.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256 certificate fingerprint" }
            val token = j.getString("token")
            require(token.length in 32..512 && token.all { it.code in 33..126 }) { "Invalid device token" }
            val host = j.getString("hostname")
            require(host.matches(Regex("[A-Za-z0-9-]+\\.local"))) { "Invalid device hostname" }
            val ip = j.getString("setup_ip")
            require(ip.split('.').let { it.size == 4 && it.all { p -> p.toIntOrNull() in 0..255 } }) { "Invalid setup IP" }
            val ssid = j.getString("ssid"); val password = j.getString("ap_password")
            require(ssid.toByteArray().size in 1..32 && password.length in 8..63) { "Invalid setup Wi-Fi details" }
            return DevicePairing(ssid, password, token, host, ip, pin)
        }
    }
}

internal fun verifyDeviceCertificate(cert: X509Certificate, fingerprint: String) {
    val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
    val expected = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    if (!MessageDigest.isEqual(actual, expected)) throw CertificateException("ESP32 certificate does not match this app's configured controller")
    cert.checkValidity()
}

/** The imported leaf fingerprint is the trust anchor, including at DHCP addresses. */
class DeviceTransport(private val context: Context, private val pairing: DevicePairing) {
    private fun matches(cert: X509Certificate): Boolean {
        return runCatching { verifyDeviceCertificate(cert, pairing.fingerprint) }.isSuccess
    }
    private val tls = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException("Client trust unsupported") }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty() || !matches(chain[0])) throw CertificateException("ESP32 certificate does not match this app's configured controller")
                chain[0].checkValidity()
            }
        }), null)
    }
    suspend fun call(origin: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val uri = URI(origin)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.path.orEmpty().isEmpty()) { "Enter an HTTPS device origin" }
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
            ?: error("Join the ESP32 setup Wi-Fi or your home Wi-Fi first")
        val connection = network.openConnection(URL(origin + path)) as HttpsURLConnection
        try {
            connection.sslSocketFactory = tls.socketFactory
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
                // A pinned device identity remains valid when DHCP changes its IP.
                runCatching { matches(session.peerCertificates[0] as X509Certificate) }.getOrDefault(false)
            }
            connection.connectTimeout = 7000; connection.readTimeout = 12000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.setRequestProperty("Authorization", "Bearer ${pairing.token}")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val payload = body.toString().toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(payload.size)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBounded(150_000) }.orEmpty()
            val result = runCatching { JSONObject(text) }.getOrElse {
                error(when(code) { 401 -> "ESP32 rejected the configured device token. Verify this app matches the flashed controller."; 429 -> "ESP32 authentication is temporarily locked. Wait 30 seconds."; else -> "Invalid ESP32 response (HTTP $code)" })
            }
            require(code in 200..299 && result.opt("ok") != false) { result.optString("error", "ESP32 returned HTTP $code") }
            result
        } finally { connection.disconnect() }
    }
}

fun deviceUiState(raw: JSONObject): JSONObject = raw.copyJson().apply {
    val runtime = raw.optJSONObject("runtime") ?: obj()
    put("connected", true)
    put("system", (raw.optJSONObject("settings") ?: obj()).copyJson().put("display_timeout", raw.optJSONObject("settings")?.optInt("display_timeout_seconds", 60)))
    put("telemetry", runtime.copyJson().put("firmware", runtime.optString("version")).put("uptime", runtime.optLong("uptime_ms") / 1000))
    put("capabilities", JSONArray().apply {
        val drivers = runtime.optJSONObject("drivers") ?: obj()
        if (drivers.optBoolean("led")) put("lighting")
        if (drivers.optBoolean("ir")) { put("ir"); put("learn") }
        if (drivers.optBoolean("microphone")) put("microphone")
    })
    val mic = raw.optJSONObject("microphone")?.copyJson() ?: obj()
    mic.put("noise_floor", mic.optDouble("noise_floor", .01) * 100)
    mic.put("smoothing", mic.optDouble("smoothing", .7) * 100)
    put("microphone", mic)
}
