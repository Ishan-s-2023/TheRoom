package `in`.roomcontroller.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** The browser returns to this phone; no computer or loopback listener needed. */
class SpotifyConnectActivity : ComponentActivity() {
    private var message by mutableStateOf("Opening Spotify sign-in…")
    private var running by mutableStateOf(false)
    private val pending by lazy { TokenVault(this, "room-spotify-pkce") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp).statusBarsPadding(), verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        Text("Connect Spotify", style=MaterialTheme.typography.headlineMedium)
                        Text(message)
                        if (running) CircularProgressIndicator()
                        Button(onClick={ finish() }, enabled=!running) { Text("Return to Room") }
                    }
                }
            }
        }
        if (savedInstanceState != null) {
            message = if (savedInstanceState.getBoolean("running"))
                "Connection interrupted. Return to Room and connect again."
            else savedInstanceState.getString("message").orEmpty()
        } else if (intent.data != null) complete(intent.data.toString())
        else begin()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null && !running) {
            setIntent(intent)
            complete(intent.data.toString())
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("message", message)
        outState.putBoolean("running", running)
        super.onSaveInstanceState(outState)
    }
    private fun begin() {
        runCatching {
            val pairing = TokenVault(this, "room-device-pairing").read()
            DevicePairing.parse(pairing)
            val origin = getSharedPreferences("room-settings", Context.MODE_PRIVATE).getString("endpoint", "").orEmpty()
            require(origin.startsWith("https://")) { "Connect to your paired ESP32 first" }
            val verifier = SpotifyPkce.random()
            val state = SpotifyPkce.random()
            pending.save(obj("verifier" to verifier, "state" to state, "expires" to System.currentTimeMillis()+600_000,
                "origin" to origin, "pairing" to pairing).toString())
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyPkce.authorization(verifier, state))).addCategory(Intent.CATEGORY_BROWSABLE))
            message = "Approve access in Spotify to return to Room automatically. Keep this phone on your room's Wi-Fi with internet access."
        }.onFailure { pending.clear(); message = it.message ?: "Could not open Spotify sign-in" }
    }
    private fun complete(url: String) {
        running = true
        lifecycleScope.launch {
            try {
                val attempt = JSONObject(pending.read())
                val code = SpotifyPkce.callback(url, attempt.getString("state"), attempt.getLong("expires"))
                // Consume once before exchange. A replay cannot reuse the verifier.
                pending.clear()
                val pairingText = attempt.getString("pairing")
                require(TokenVault(this@SpotifyConnectActivity, "room-device-pairing").read() == pairingText) { "Room pairing changed. Connect Spotify again." }
                message = "Connecting Spotify to your paired room…"
                val refresh = exchange(code, attempt.getString("verifier"))
                require(TokenVault(this@SpotifyConnectActivity, "room-device-pairing").read() == pairingText) { "Room pairing changed. Connect Spotify again." }
                DeviceTransport(this@SpotifyConnectActivity, DevicePairing.parse(pairingText)).call(attempt.getString("origin"), "/v1/command",
                    obj("op" to "spotify_credentials", "client_id" to SpotifyPkce.CLIENT_ID, "refresh_token" to refresh))
                message = "Spotify connected. Return to Music and refresh playback. Open Spotify on your preferred playback device and play a track to make it available."
            } catch (e: Exception) {
                message = (e.message?.takeIf { it.isNotBlank() } ?: "Spotify connection failed") + " Return to Room and connect again."
            } finally { running = false }
        }
    }
    private suspend fun exchange(code: String, verifier: String): String = withContext(Dispatchers.IO) {
        val connection = URL("https://accounts.spotify.com/api/token").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = SpotifyPkce.form(mapOf("grant_type" to "authorization_code", "code" to code,
                "redirect_uri" to SpotifyPkce.REDIRECT, "client_id" to SpotifyPkce.CLIENT_ID, "code_verifier" to verifier))
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode == 200) { "Spotify rejected authorization. Check the registered app redirect URI and account access." }
            val token = connection.inputStream.use { JSONObject(it.readBounded(32_768)) }
            token.optString("refresh_token").also { require(it.length in 20..2048) { "Spotify did not return refresh credentials" } }
        } finally { connection.disconnect() }
    }
}
