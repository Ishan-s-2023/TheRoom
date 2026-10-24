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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
            val pairing = runCatching { TokenVault(this, "room-device-pairing").read() }.getOrDefault("")
            val origin = getSharedPreferences("room-settings", Context.MODE_PRIVATE).getString("endpoint", "").orEmpty()
            val storedClientId = intent.getStringExtra("client_id")
                ?.takeIf { it.isNotBlank() }
                ?: runBlocking { RoomDataStore(this@SpotifyConnectActivity).settingsFlow.first().spotifyClientId }
                    .takeIf { it.isNotBlank() }
                ?: SpotifyPkce.CLIENT_ID
            require(storedClientId.isNotBlank()) {
                "Spotify Client ID is not configured. Enter your Spotify Developer Client ID in Settings first."
            }
            val verifier = SpotifyPkce.random()
            val state = SpotifyPkce.random()
            pending.save(
                obj(
                    "verifier" to verifier,
                    "state" to state,
                    "client_id" to storedClientId,
                    "expires" to System.currentTimeMillis() + 600_000,
                    "origin" to origin,
                    "pairing" to pairing
                ).toString()
            )
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyPkce.authorization(verifier, state, storedClientId)))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
            )
            message = "Approve access in Spotify to return to Room automatically. Keep this phone connected to the internet."
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
                val clientId = attempt.optString("client_id", "").ifBlank {
                    runBlocking { RoomDataStore(this@SpotifyConnectActivity).settingsFlow.first().spotifyClientId }
                }.ifBlank { SpotifyPkce.CLIENT_ID }
                require(clientId.isNotBlank()) { "Spotify Client ID is not configured." }
                message = "Exchanging Spotify OAuth code and storing encrypted tokens in DataStore…"
                val tokenResponse = exchangeTokens(code, attempt.getString("verifier"), clientId)
                val accessToken = tokenResponse.optString("access_token")
                val refreshToken = tokenResponse.optString("refresh_token")
                val expiresIn = tokenResponse.optLong("expires_in", 3600L)
                RoomDataStore(this@SpotifyConnectActivity).saveSpotifyOAuthTokens(
                    accountName = "Spotify OAuth PKCE",
                    clientId = clientId,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSec = expiresIn
                )
                val pairingText = attempt.optString("pairing")
                val origin = attempt.optString("origin")
                if (pairingText.isNotBlank() && (origin.startsWith("https://") || origin.startsWith("http://"))) {
                    runCatching {
                        require(TokenVault(this@SpotifyConnectActivity, "room-device-pairing").read() == pairingText) {
                            "Room pairing changed. Connect Spotify again."
                        }
                        val parsedPairing = DevicePairing.parseFlexible(pairingText)
                        DeviceTransport(this@SpotifyConnectActivity, parsedPairing).call(
                            origin,
                            "/v1/command",
                            obj("op" to "spotify_credentials", "client_id" to clientId, "refresh_token" to refreshToken)
                        )
                    }
                }
                message = "Spotify connected. Return to Music and refresh playback. Open Spotify on your preferred playback device and play a track to make it available."
            } catch (e: Exception) {
                message = (e.message?.takeIf { it.isNotBlank() } ?: "Spotify connection failed") + " Return to Room and connect again."
            } finally { running = false }
        }
    }

    private suspend fun exchangeTokens(code: String, verifier: String, clientId: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL("https://accounts.spotify.com/api/token").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = SpotifyPkce.form(
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to SpotifyPkce.REDIRECT,
                    "client_id" to clientId,
                    "code_verifier" to verifier
                )
            )
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode == 200) { "Spotify rejected authorization. Check the registered app redirect URI and account access." }
            val token = connection.inputStream.use { JSONObject(it.readBounded(32_768)) }
            val refresh = token.optString("refresh_token")
            require(refresh.length in 20..2048) { "Spotify did not return refresh credentials" }
            token
        } finally { connection.disconnect() }
    }
}
