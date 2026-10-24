package `in`.roomcontroller.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class DevicePairing(
    val ssid: String,
    val apPassword: String,
    val token: String,
    val hostname: String,
    val setupIp: String,
    val fingerprint: String
) {
    val hasPinnedCertificate: Boolean
        get() = fingerprint.length == 64 && fingerprint.any { it != '0' }

    fun toJson(): String = obj(
        "ssid" to ssid,
        "ap_password" to apPassword,
        "token" to token,
        "hostname" to hostname,
        "setup_ip" to setupIp,
        "certificate_sha256" to fingerprint.ifBlank { "0".repeat(64) }
    ).toString()

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
            val ssid = j.getString("ssid")
            val password = j.getString("ap_password")
            require(ssid.toByteArray().size in 1..32 && password.length in 8..63) { "Invalid setup Wi-Fi details" }
            return DevicePairing(ssid, password, token, host, ip, pin)
        }

        fun parseFlexible(text: String): DevicePairing {
            runCatching { return parse(text) }
            val j = JSONObject(text)
            val rawPin = j.optString("certificate_sha256", "").replace(":", "").lowercase()
            val pin = if (rawPin.matches(Regex("[0-9a-f]{64}"))) rawPin else "0".repeat(64)
            val token = j.optString("token", j.optString("api_key", "")).trim()
            require(token.isNotEmpty() && token.all { it.code in 33..126 }) { "Enter a valid device token or API key" }
            val rawHost = j.optString("hostname", j.optString("host", "theroom.local")).trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
            val isIp = rawHost.split('.').let { it.size == 4 && it.all { p -> p.toIntOrNull() in 0..255 } }
            val host = when {
                isIp -> "theroom.local"
                rawHost.endsWith(".local") -> rawHost
                rawHost.isNotBlank() -> "$rawHost.local"
                else -> "theroom.local"
            }
            val ip = j.optString("setup_ip", if (isIp) rawHost else "192.168.4.1").trim().ifBlank { "192.168.4.1" }
            val ssid = j.optString("ssid", "TheRoom-Setup").trim().ifBlank { "TheRoom-Setup" }
            val password = j.optString("ap_password", "roomsetup123").let {
                if (it.length in 8..63) it else "roomsetup123"
            }
            return DevicePairing(ssid, password, token, host, ip, pin)
        }

        fun fromManual(
            hostOrOrigin: String,
            token: String,
            ssid: String = "TheRoom-Setup",
            apPassword: String = "roomsetup123",
            fingerprint: String = ""
        ): DevicePairing {
            val cleanToken = token.trim()
            require(cleanToken.isNotEmpty() && cleanToken.all { it.code in 33..126 }) {
                "Enter a valid device token or API key"
            }
            val cleanHost = hostOrOrigin.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore(':')
                .trim()
            require(cleanHost.isNotEmpty()) { "Enter the ESP32 IP address or .local hostname" }
            val isIpv4 = cleanHost.split('.').let { it.size == 4 && it.all { p -> p.toIntOrNull() in 0..255 } }
            val hostname = when {
                isIpv4 -> "theroom.local"
                cleanHost.endsWith(".local") -> cleanHost
                else -> "$cleanHost.local"
            }
            val setupIp = if (isIpv4) cleanHost else "192.168.4.1"
            val cleanPin = fingerprint.replace(":", "").lowercase().let {
                if (it.matches(Regex("[0-9a-f]{64}"))) it else "0".repeat(64)
            }
            val validSsid = ssid.trim().ifBlank { "TheRoom-Setup" }.take(32)
            val validPass = apPassword.trim().let { if (it.length in 8..63) it else "roomsetup123" }
            return DevicePairing(
                ssid = validSsid,
                apPassword = validPass,
                token = cleanToken,
                hostname = hostname,
                setupIp = setupIp,
                fingerprint = cleanPin
            )
        }
    }
}

internal fun verifyDeviceCertificate(cert: X509Certificate, fingerprint: String) {
    val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
    val expected = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    if (!MessageDigest.isEqual(actual, expected)) {
        throw CertificateException("ESP32 certificate does not match this app's configured controller")
    }
    cert.checkValidity()
}

/**
 * Supports both SHA-256 leaf-pinned HTTPS (`esp_https_server` on port 443) and local LAN HTTP
 * (`ESPAsyncWebServer` on port 80) with `Authorization: Bearer <token>` and `X-API-Key: <token>`.
 *
 * Requests are serialized via [requestMutex] and sent with `Connection: close` so the client
 * never exhausts ESP32 `esp_https_server`'s `max_open_sockets = 2` TLS session limit or reuses
 * half-closed sockets. Responses are read using `Content-Length`-aware bounded framing.
 */
class DeviceTransport(private val context: Context, private val pairing: DevicePairing) {
    private val requestMutex = Mutex()

    private fun matches(cert: X509Certificate): Boolean {
        if (!pairing.hasPinnedCertificate) {
            return runCatching { cert.checkValidity() }.isSuccess
        }
        return runCatching { verifyDeviceCertificate(cert, pairing.fingerprint) }.isSuccess
    }

    private val tls = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Client trust unsupported")
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty() || !matches(chain[0])) {
                    throw CertificateException("ESP32 certificate does not match this app's configured controller")
                }
                chain[0].checkValidity()
            }
        }), null)
    }

    suspend fun call(origin: String, path: String, body: JSONObject? = null): JSONObject = requestMutex.withLock {
        withContext(Dispatchers.IO) {
            val normalizedOrigin = origin.trim().trimEnd('/')
            val uri = URI(normalizedOrigin)
            require(
                (uri.scheme == "https" || uri.scheme == "http") &&
                    !uri.host.isNullOrBlank() && uri.userInfo == null &&
                    uri.query == null && uri.fragment == null && uri.path.orEmpty().isEmpty()
            ) { "Enter an HTTP or HTTPS device origin" }

            val candidateOrigins = buildList {
                add(normalizedOrigin)
                if (uri.scheme == "https" && (uri.port == -1 || uri.port == 443)) {
                    add("http://${uri.host}")
                } else if (uri.scheme == "http" && pairing.hasPinnedCertificate && (uri.port == -1 || uri.port == 80)) {
                    add("https://${uri.host}")
                }
            }

            var lastError: Throwable? = null
            for (candidate in candidateOrigins) {
                try {
                    return@withContext executeSingleCall(candidate, path, body)
                } catch (err: Throwable) {
                    lastError = err
                    // Do not fallback if authentication or certificate verification explicitly failed
                    val msg = err.message.orEmpty()
                    if (err is CertificateException ||
                        msg.contains("certificate does not match", ignoreCase = true) ||
                        msg.contains("rejected the configured device token", ignoreCase = true) ||
                        msg.contains("temporarily locked", ignoreCase = true)
                    ) {
                        throw err
                    }
                }
            }
            throw (lastError ?: IllegalStateException("Unable to reach ESP32 at $normalizedOrigin"))
        }
    }

    private fun executeSingleCall(origin: String, path: String, body: JSONObject?): JSONObject {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val wifiNetwork = manager?.allNetworks?.firstOrNull {
            manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: manager?.activeNetwork
        val targetUrl = URL(origin + path)
        val connection = (wifiNetwork?.openConnection(targetUrl) ?: targetUrl.openConnection()) as HttpURLConnection
        try {
            if (connection is HttpsURLConnection) {
                connection.sslSocketFactory = tls.socketFactory
                connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
                    // A pinned device identity remains valid when DHCP changes its IP.
                    runCatching { matches(session.peerCertificates[0] as X509Certificate) }.getOrDefault(false)
                }
            }
            connection.connectTimeout = 6000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.setRequestProperty("Authorization", "Bearer ${pairing.token}")
            connection.setRequestProperty("X-API-Key", pairing.token)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Connection", "close")
            if (body != null) {
                val payload = body.toString().toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(payload.size)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use {
                    it.write(payload)
                    it.flush()
                }
            }
            val code = connection.responseCode
            val expectedLen = connection.contentLength
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = try {
                stream?.use { it.readBounded(150_000, expectedContentLength = expectedLen) }.orEmpty()
            } catch (eof: EOFException) {
                error("Incomplete response from ESP32 ($path). Refresh state before retrying.")
            }
            val result = runCatching { JSONObject(text) }.getOrElse {
                error(
                    when (code) {
                        401, 403 -> "ESP32 rejected the configured device token. Verify this app matches the flashed controller."
                        429 -> "ESP32 authentication is temporarily locked. Wait 30 seconds."
                        else -> "Invalid ESP32 response (HTTP $code)"
                    }
                )
            }
            require(code in 200..299 && result.opt("ok") != false) {
                result.optString("error", "ESP32 returned HTTP $code")
            }
            return result
        } finally {
            connection.disconnect()
        }
    }
}

fun deviceUiState(raw: JSONObject): JSONObject = raw.copyJson().apply {
    val runtime = raw.optJSONObject("runtime") ?: obj()
    val rawSettings = raw.optJSONObject("settings") ?: raw.optJSONObject("system") ?: obj()
    val normalizedSettings = rawSettings.copyJson().apply {
        if (optString("timezone").isBlank()) {
            put("timezone", "IST-5:30")
        }
        if (!has("display_timeout_seconds")) {
            put("display_timeout_seconds", optInt("display_timeout", 60))
        }
    }
    put("connected", true)
    put("settings", normalizedSettings)
    put(
        "system",
        normalizedSettings.copyJson()
            .put("display_timeout", normalizedSettings.optInt("display_timeout_seconds", 60))
    )
    put(
        "telemetry",
        runtime.copyJson()
            .put("firmware", runtime.optString("version"))
            .put("uptime", runtime.optLong("uptime_ms") / 1000)
    )
    put("capabilities", JSONArray().apply {
        val drivers = runtime.optJSONObject("drivers") ?: obj()
        if (drivers.optBoolean("led", true)) put("lighting")
        if (drivers.optBoolean("ir", true)) {
            put("ir")
            put("learn")
        }
        if (drivers.optBoolean("microphone", true)) put("microphone")
    })
    val mic = raw.optJSONObject("microphone")?.copyJson() ?: obj()
    val rawNoiseFloor = mic.optDouble("noise_floor", .01)
    val rawSmoothing = mic.optDouble("smoothing", .7)
    mic.put("noise_floor", if (rawNoiseFloor <= 1.0) rawNoiseFloor * 100 else rawNoiseFloor)
    mic.put("smoothing", if (rawSmoothing <= 1.0) rawSmoothing * 100 else rawSmoothing)
    put("microphone", mic)
}
