package `in`.roomcontroller.app

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object SpotifyPkce {
    const val CLIENT_ID = ""
    const val REDIRECT = "in.roomcontroller.app://spotify/callback"
    private const val SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing playlist-read-private playlist-read-collaborative user-read-recently-played user-library-read user-library-modify"
    fun random(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun form(values: Map<String, String>): String = values.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }
    fun authorization(verifier: String, state: String, clientId: String = CLIENT_ID): String {
        require(clientId.isNotBlank()) { "Spotify Client ID is not configured. Enter your Spotify Developer Client ID in Settings before connecting." }
        return "https://accounts.spotify.com/authorize?" + form(mapOf(
            "client_id" to clientId, "response_type" to "code", "redirect_uri" to REDIRECT,
            "state" to state, "code_challenge_method" to "S256", "code_challenge" to challenge(verifier),
            "scope" to SCOPES, "show_dialog" to "true"
        ))
    }
    fun callback(url: String, expectedState: String, expires: Long, now: Long = System.currentTimeMillis()): String {
        val uri = URI(url)
        val target = URI(REDIRECT)
        require(uri.scheme == target.scheme && uri.rawAuthority == target.rawAuthority && uri.path == target.path && uri.fragment == null) { "Invalid Spotify callback" }
        require(expectedState.isNotEmpty() && now < expires) { "Spotify authorization expired. Connect again." }
        val params = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map {
            val pair = it.split('=', limit=2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        require(params.map { it.first }.distinct().size == params.size) { "Invalid Spotify callback parameters" }
        val values = params.toMap()
        require(MessageDigest.isEqual(values["state"].orEmpty().toByteArray(), expectedState.toByteArray())) { "Spotify authorization state mismatch. Connect again." }
        require(!values.containsKey("error")) { "Spotify authorization was declined. You can connect again." }
        return values["code"].orEmpty().also { require(it.isNotEmpty()) { "Spotify did not return an authorization code" } }
    }
}
