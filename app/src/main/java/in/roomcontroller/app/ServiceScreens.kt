package `in`.roomcontroller.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.ZoneId

@Composable fun SpotifyAccountSettingsCard(model: RoomModel, ui: RoomUi) {
    val context = LocalContext.current
    val view = LocalView.current
    val spotifyObj = ui.state.optJSONObject("spotify") ?: obj()
    val connected = ui.spotifyOAuthConnected ||
        ui.state.optBoolean("spotify_connected") ||
        spotifyObj.optBoolean("connected", false)
    val accountLabel = ui.spotifyAccountName.ifBlank {
        spotifyObj.optString("account_name").ifBlank {
            if (connected) "Spotify Premium · DataStore OAuth" else "Not connected"
        }
    }
    var showTokenForm by rememberSaveable { mutableStateOf(false) }
    var accountInput by rememberSaveable { mutableStateOf(ui.spotifyAccountName.ifBlank { "Room Spotify Account" }) }
    var clientIdInput by rememberSaveable { mutableStateOf(ui.spotifyClientId.ifBlank { SpotifyPkce.CLIENT_ID }) }
    var accessTokenInput by rememberSaveable { mutableStateOf("") }
    var refreshTokenInput by rememberSaveable { mutableStateOf("") }

    val trackTitle = spotifyObj.optString("track", "").ifBlank { "" }
    val artistName = spotifyObj.optString("artist", "").ifBlank { "" }
    val deviceName = spotifyObj.optString("device", "").ifBlank { "" }
    val isPlaying = spotifyObj.optBoolean("playing", false)
    val currentVol = spotifyObj.optInt("volume_percent", 55).toFloat().coerceIn(0f, 100f)
    var sliderVol by remember { mutableFloatStateOf(currentVol) }
    var lastVolDragMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentVol) {
        if (System.currentTimeMillis() - lastVolDragMs > 750L) {
            sliderVol = currentVol
        }
    }

    RoomCard {
        SectionLabel("Spotify Account", if (connected) "DATASTORE LINKED" else "NOT LINKED")
        Text(
            text = "OAuth PKCE tokens are encrypted with Android Keystore AES-GCM and persisted in Jetpack DataStore alongside your paired ESP32.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        // OAuth Status Summary Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (connected) Lime.copy(alpha = 0.12f) else Ink.copy(alpha = 0.55f))
                .border(1.dp, if (connected) Lime.copy(alpha = 0.45f) else Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = accountLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (connected) Lime else Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (connected) "OAuth token vault: Encrypted in Jetpack DataStore" else "Tap Connect Spotify or OAuth Token Setup below",
                    fontSize = 11.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (connected) {
                TextButton(
                    onClick = { model.disconnectSpotifyOAuthFromDataStore() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("spotify_disconnect_button")
                ) {
                    Text("Disconnect", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                }
            }
        }

        // Row 1: Browser OAuth PKCE Connect + DataStore Token Form Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (model.directDevice || ui.demo) {
                        val intent = Intent(context, SpotifyConnectActivity::class.java).apply {
                            if (clientIdInput.isNotBlank()) putExtra("client_id", clientIdInput.trim())
                        }
                        context.startActivity(intent)
                    } else {
                        model.work {
                            val url = model.request("GET", "/spotify/connect").getString("url")
                            require(Uri.parse(url).host == "accounts.spotify.com") { "Invalid authorization URL" }
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                },
                enabled = !ui.busy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                modifier = Modifier.weight(1f).testTag("spotify_connect_oauth_button")
            ) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (connected) "Reconnect OAuth" else "Connect Spotify",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            OutlinedButton(
                onClick = { showTokenForm = !showTokenForm },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                modifier = Modifier.weight(1f).testTag("spotify_datastore_token_toggle")
            ) {
                Icon(Icons.Outlined.Key, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (showTokenForm) "Hide Token Setup" else "DataStore Tokens",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (showTokenForm) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.copy(alpha = 0.55f))
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SAVE ENCRYPTED OAUTH TOKENS TO DATASTORE",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    maxLines = 2,
                    softWrap = true
                )
                OutlinedTextField(
                    value = accountInput,
                    onValueChange = { accountInput = it.take(48) },
                    label = { Text("Account label", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("spotify_account_input")
                )
                OutlinedTextField(
                    value = clientIdInput,
                    onValueChange = { clientIdInput = it.trim().take(64) },
                    label = { Text("Spotify Client ID", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("spotify_client_id_input")
                )
                OutlinedTextField(
                    value = accessTokenInput,
                    onValueChange = { accessTokenInput = it.trim() },
                    label = { Text("OAuth Access Token", maxLines = 1) },
                    supportingText = { Text("Encrypted with Keystore AES-GCM in DataStore", fontSize = 11.sp, color = Muted) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("spotify_access_token_input")
                )
                OutlinedTextField(
                    value = refreshTokenInput,
                    onValueChange = { refreshTokenInput = it.trim() },
                    label = { Text("OAuth Refresh Token", maxLines = 1) },
                    supportingText = { Text("Used for token rotation and firmware playback", fontSize = 11.sp, color = Muted) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("spotify_refresh_token_input")
                )
                Button(
                    onClick = {
                        performSubtleHaptic(view)
                        model.connectSpotifyOAuthToDataStore(
                            accountName = accountInput,
                            clientId = clientIdInput,
                            accessToken = accessTokenInput,
                            refreshToken = refreshTokenInput,
                            expiresInSec = 3600L
                        )
                        accessTokenInput = ""
                        refreshTokenInput = ""
                        showTokenForm = false
                    },
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.fillMaxWidth().testTag("spotify_save_datastore_tokens_button")
                ) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save OAuth Tokens to DataStore", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }

        // Embedded Basic Playback Control UI
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.copy(alpha = 0.5f))
                .border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: "PLAYBACK CONTROL" with active device pill if available
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLAYBACK CONTROL",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp
                )
                if (deviceName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Panel,
                        border = BorderStroke(1.dp, Line)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) Lime else Muted)
                            )
                            Text(
                                text = deviceName,
                                fontSize = 11.sp,
                                color = Cream,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Track info: Album Art/Note icon + Full width Track and Artist (Never overlaps controls!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Panel)
                        .border(1.dp, if (isPlaying) Lime.copy(alpha = 0.5f) else Line, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.GraphicEq else Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = if (isPlaying) Lime else Muted,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = trackTitle.ifBlank { if (connected) "No track playing" else "Not playing" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artistName.ifBlank { if (connected) "Ready to stream via Spotify" else "Connect Spotify to start playback" },
                        fontSize = 12.sp,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Centered, well-spaced Transport Controls with haptics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        performSubtleHaptic(view)
                        model.spotifyQuickTransport("previous")
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Panel)
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .testTag("spotify_prev_button")
                ) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous track", tint = Cream, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(20.dp))
                IconButton(
                    onClick = {
                        performSubtleHaptic(view)
                        model.spotifyQuickTransport("toggle")
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isPlaying) Lime else Lime.copy(alpha = 0.2f))
                        .border(1.dp, Lime, RoundedCornerShape(16.dp))
                        .testTag("spotify_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause playback" else "Start playback",
                        tint = if (isPlaying) Ink else Lime,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(20.dp))
                IconButton(
                    onClick = {
                        performSubtleHaptic(view)
                        model.spotifyQuickTransport("next")
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Panel)
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .testTag("spotify_next_button")
                ) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = "Next track", tint = Cream, modifier = Modifier.size(22.dp))
                }
            }

            SettingSlider(
                "Spotify Volume",
                sliderVol,
                { nextVol ->
                    lastVolDragMs = System.currentTimeMillis()
                    sliderVol = nextVol
                    model.coalescedVolume(nextVol.toInt())
                },
                0f..100f,
                "%"
            )
        }
    }
}

@Composable fun MusicScreen(model: RoomModel, ui: RoomUi, navigate: (String) -> Unit = {}) {
    var player by remember { mutableStateOf(JSONObject()) }
    var devices by remember { mutableStateOf(emptyList<JSONObject>()) }
    var results by remember { mutableStateOf(emptyList<JSONObject>()) }
    var likedTracks by remember { mutableStateOf(emptyList<JSONObject>()) }
    var playlistsList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var recentList by remember { mutableStateOf(emptyList<JSONObject>()) }
    var queueItems by remember { mutableStateOf(emptyList<JSONObject>()) }
    var queueCurrent by remember { mutableStateOf(JSONObject()) }
    var isLikedScopeMissing by remember { mutableStateOf(false) }

    var selected by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var activeTab by rememberSaveable { mutableStateOf("search") } // "search", "liked", "playlists", "recent", "queue"
    var searchFilter by rememberSaveable { mutableStateOf("track,playlist,album,artist") } // "track,playlist,album,artist", "track", "playlist", "album", "artist"

    val spotifyState = ui.state.optJSONObject("spotify") ?: obj()
    val systemState = ui.state.optJSONObject("system") ?: obj()

    val currentSpotifyVol = (player.optJSONObject("device")?.optInt("volume_percent", -1)?.takeIf { it >= 0 }
        ?: spotifyState.optInt("volume_percent", 55)).toFloat().coerceIn(0f, 100f)
    var volume by remember { mutableFloatStateOf(currentSpotifyVol) }
    var lastVolEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentSpotifyVol) {
        if (System.currentTimeMillis() - lastVolEditMs > 750L) {
            volume = currentSpotifyVol
        }
    }

    val currentSpeakerVol = systemState.optInt("speaker_volume_percent", ui.state.optInt("speaker_volume_percent", 60)).toFloat().coerceIn(0f, 100f)
    var speakerVolume by remember { mutableFloatStateOf(currentSpeakerVol) }
    var lastSpeakerVolEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentSpeakerVol) {
        if (System.currentTimeMillis() - lastSpeakerVolEditMs > 750L) {
            speakerVolume = currentSpeakerVol
        }
    }

    var resultOffset by remember { mutableIntStateOf(0) }
    var moreResults by remember { mutableStateOf(false) }
    val device = player.optJSONObject("device")
    val connected = ui.state.optBoolean("spotify_connected") || spotifyState.optBoolean("connected") || ui.spotifyOAuthConnected || ui.demo

    fun browseSearch(append: Boolean = false) {
        if (query.isBlank()) return
        model.work {
            val offset = if (append) resultOffset else 0
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val data = model.request("GET", "/spotify/search?q=$encodedQuery&type=$searchFilter&offset=$offset")
            val tracks = data.optJSONObject("tracks")?.items("items") ?: emptyList()
            val playlists = data.optJSONObject("playlists")?.items("items") ?: emptyList()
            val albums = data.optJSONObject("albums")?.items("items") ?: emptyList()
            val artists = data.optJSONObject("artists")?.items("items") ?: emptyList()
            val batch = tracks + playlists + albums + artists
            results = if (append) results + batch else batch
            resultOffset = offset + 20
            moreResults = (data.optJSONObject("tracks")?.opt("next") is String ||
                data.optJSONObject("playlists")?.opt("next") is String ||
                data.optJSONObject("albums")?.opt("next") is String)
        }
    }

    fun browseLiked(append: Boolean = false) {
        model.work {
            val offset = if (append) likedTracks.size else 0
            val data = model.request("GET", "/spotify/liked?offset=$offset")
            if (data.optBoolean("scope_required", false)) {
                isLikedScopeMissing = true
            } else {
                isLikedScopeMissing = false
                val items = data.items("items")
                likedTracks = if (append) likedTracks + items else items
            }
        }
    }

    fun browsePlaylists(append: Boolean = false) {
        model.work {
            val offset = if (append) playlistsList.size else 0
            val data = model.request("GET", "/spotify/playlists?offset=$offset")
            val items = data.items("items")
            playlistsList = if (append) playlistsList + items else items
        }
    }

    fun browseRecent() {
        model.work {
            val data = model.request("GET", "/spotify/recent")
            val raw = data.items("items")
            recentList = raw.mapNotNull { it.optJSONObject("track") ?: it }
        }
    }

    fun browseQueue() {
        model.work {
            val data = model.request("GET", "/spotify/queue")
            queueCurrent = data.optJSONObject("currently_playing") ?: obj()
            queueItems = data.items("queue")
        }
    }

    LaunchedEffect(connected) {
        if (connected) {
            runCatching {
                val p = model.request("GET", "/spotify/player")
                val d = model.request("GET", "/spotify/devices").items("devices")
                player = p
                devices = d
            }
            runCatching { browsePlaylists() }
            runCatching { browseLiked() }
        }
    }

    // Merge player with spotifyState so song image, title, artist, and device are always in sync
    val effectivePlayer = remember(player.toString(), spotifyState.toString()) {
        if (player.length() > 0 && player.optJSONObject("item") != null) {
            player.copyJson().apply {
                if (optString("image_url").isBlank() && spotifyState.optString("image_url").isNotBlank()) {
                    put("image_url", spotifyState.optString("image_url"))
                }
            }
        } else {
            val trackName = spotifyState.optString("track").ifBlank { "" }
            val artistName = spotifyState.optString("artist").ifBlank { "" }
            val imgUrl = spotifyState.optString("image_url").ifBlank { if (trackName.isNotBlank()) defaultCoverForTrackName(trackName) else "" }
            val devName = spotifyState.optString("device").ifBlank { "" }
            val trackId = spotifyState.optString("id").ifBlank { "" }
            val trackUri = spotifyState.optString("uri").ifBlank { "" }
            obj(
                "is_playing" to spotifyState.optBoolean("playing", false),
                "progress_ms" to 0,
                "image_url" to imgUrl,
                "track" to trackName,
                "artist" to artistName,
                "id" to trackId,
                "uri" to trackUri,
                "shuffle_state" to spotifyState.optBoolean("shuffle_state", false),
                "smart_shuffle" to spotifyState.optBoolean("smart_shuffle", false),
                "repeat_state" to spotifyState.optString("repeat_state", "off"),
                "device" to (if (devName.isNotBlank()) obj("id" to devName, "name" to devName, "volume_percent" to volume.toInt()) else obj()),
                "item" to obj(
                    "id" to trackId,
                    "uri" to trackUri,
                    "name" to trackName,
                    "duration_ms" to spotifyState.optInt("duration_ms", 0).coerceAtLeast(1000),
                    "image_url" to imgUrl,
                    "artists" to (if (artistName.isNotBlank()) org.json.JSONArray().put(obj("name" to artistName)) else org.json.JSONArray())
                )
            )
        }
    }

    val activeDeviceName = device?.optString("name")?.takeIf { it.isNotBlank() }
        ?: spotifyState.optString("device").takeIf { it.isNotBlank() }
        ?: ""
    val activeDeviceId = selected.ifBlank { device?.optString("id").orEmpty() }
    val allDevices = remember(devices, spotifyState.toString()) {
        if (devices.isNotEmpty()) devices else spotifyState.items("devices")
    }

    PageTitle("Music", "Your personal soundtrack.", "Stream any song, browse liked playlists, magic shuffle, and smart queue.")
    ConnectionHelpButton(if (ui.demo) "spotify-direct" else "spotify", model.endpoint)

    if (!ui.state.optBoolean("spotify_connected") && !spotifyState.optBoolean("connected") && !ui.spotifyOAuthConnected) {
        RoomCard {
            SectionLabel("Spotify Account", "SETTINGS")
            Text(
                "Spotify account linking is managed in Settings. Open Settings to connect or switch your Spotify account.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = { navigate("System") },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Settings to link Spotify", maxLines = 1, softWrap = false)
            }
        }
    }

    RoomCard {
        MusicPlayerWidget(effectivePlayer, ui.demo, ui.busy) { name, data ->
            model.work {
                model.request("POST", "/spotify/playback", data.copyJson().put("action", name).put("device_id", selected))
                player = model.request("GET", "/spotify/player")
                devices = model.request("GET", "/spotify/devices").items("devices")
            }
        }

        // Shuffle & Magic Shuffle Mode Bar
        val curShuffle = effectivePlayer.optBoolean("shuffle_state", false)
        val curSmartShuffle = effectivePlayer.optBoolean("smart_shuffle", false)
        val currentShuffleMode = when {
            curSmartShuffle -> 2
            curShuffle -> 1
            else -> 0
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SHUFFLE MODE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (currentShuffleMode == 2) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Lime, modifier = Modifier.size(12.dp))
                        Text("AI Recommendations Mixed", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(0, "Off", Icons.Outlined.Close),
                    Triple(1, "Standard", Icons.Outlined.Shuffle),
                    Triple(2, "✨ Magic Shuffle", Icons.Filled.AutoAwesome)
                ).forEach { (modeId, label, icon) ->
                    val isModeSelected = currentShuffleMode == modeId
                    FilterChip(
                        selected = isModeSelected,
                        onClick = {
                            val actMode = when (modeId) {
                                1 -> "standard"
                                2 -> "magic"
                                else -> "off"
                            }
                            model.work {
                                model.request("POST", "/spotify/playback", obj("action" to "shuffle", "mode" to actMode, "value" to modeId, "device_id" to selected))
                                player = model.request("GET", "/spotify/player")
                            }
                        },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lime.copy(alpha = 0.16f),
                            selectedLabelColor = Lime,
                            selectedLeadingIconColor = Lime
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isModeSelected,
                            borderColor = if (isModeSelected) Lime.copy(alpha = 0.6f) else Line
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Magic Mix Radio launcher based on current playing track
            val currentTrackTitle = effectivePlayer.optString("track").ifBlank {
                effectivePlayer.optJSONObject("item")?.optString("name").orEmpty()
            }
            if (currentTrackTitle.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        val currentTrackId = effectivePlayer.optString("id").ifBlank {
                            effectivePlayer.optJSONObject("item")?.optString("id").orEmpty()
                        }
                        model.work {
                            model.request("POST", "/spotify/playback", obj("action" to "magic_shuffle", "argument" to currentTrackId, "device_id" to selected))
                            player = model.request("GET", "/spotify/player")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Lime),
                    border = BorderStroke(1.dp, Lime.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mix Smart Radio from this Track", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        HorizontalDivider(color = Line)

        // Clickable Active Device Speaker Dropdown to switch where the song plays
        SpotifyDeviceDropdownSelector(
            activeDeviceName = activeDeviceName,
            activeDeviceId = activeDeviceId,
            devices = allDevices,
            enabled = !ui.busy,
            onSelectDevice = { devId, devName ->
                selected = devId
                model.work {
                    model.request("POST", "/spotify/playback", obj("action" to "transfer", "device_id" to devId, "device_name" to devName))
                    player = model.request("GET", "/spotify/player")
                    devices = model.request("GET", "/spotify/devices").items("devices")
                }
            }
        )

        // 1. Instantaneous Spotify Volume Control
        VolumeControl(
            value = volume,
            onChange = { nextVol ->
                lastVolEditMs = System.currentTimeMillis()
                volume = nextVol
                model.coalescedVolume(nextVol.toInt())
            },
            enabled = true,
            onFinished = {
                lastVolEditMs = System.currentTimeMillis()
            }
        )

        HorizontalDivider(color = Line)

        // 2. Physical Speaker / Device Volume Control (via IR)
        val speakerMuted = systemState.optBoolean("speaker_muted", ui.state.optBoolean("speaker_muted", false))
        SpeakerIrVolumeControl(
            value = speakerVolume,
            onChange = { nextSpkVol ->
                lastSpeakerVolEditMs = System.currentTimeMillis()
                speakerVolume = nextSpkVol
                model.coalescedSpeakerIrVolume(nextSpkVol.toInt())
            },
            onIrStep = { kind ->
                lastSpeakerVolEditMs = System.currentTimeMillis()
                model.triggerSpeakerIrCommand(kind)
            },
            enabled = true,
            initialMuted = speakerMuted,
            deviceName = activeDeviceName,
            onFinished = {
                lastSpeakerVolEditMs = System.currentTimeMillis()
            }
        )
    }

    // Music Library, Search, Playlists & Queue Hub
    RoomCard {
        SectionLabel("Music Library & Discovery")

        // Minimalist category navigation tabs
        val categories = listOf(
            Triple("search", "Search", Icons.Outlined.Search),
            Triple("liked", "Liked Songs", Icons.Filled.Favorite),
            Triple("playlists", "Playlists", Icons.Outlined.QueueMusic),
            Triple("recent", "Recent", Icons.Outlined.History),
            Triple("queue", "Up Next", Icons.Outlined.GraphicEq)
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { (catKey, catLabel, catIcon) ->
                val isSelected = activeTab == catKey
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        activeTab = catKey
                        when (catKey) {
                            "liked" -> if (likedTracks.isEmpty()) browseLiked()
                            "playlists" -> if (playlistsList.isEmpty()) browsePlaylists()
                            "recent" -> if (recentList.isEmpty()) browseRecent()
                            "queue" -> browseQueue()
                        }
                    },
                    label = { Text(catLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                    leadingIcon = {
                        Icon(
                            catIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (catKey == "liked" && isSelected) Color(0xFFFF4B6E) else if (isSelected) Lime else Muted
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Ink.copy(alpha = 0.9f),
                        selectedLabelColor = Cream,
                        selectedLeadingIconColor = Lime
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) Lime else Line
                    )
                )
            }
        }

        when (activeTab) {
            "search" -> {
                // Search Input with Clear Button
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search songs, artists, playlists, albums") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Muted) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = Muted)
                            }
                        }
                    },
                    keyboardActions = KeyboardActions(onSearch = { browseSearch() }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                // Sub-filter chips: All, Songs, Playlists, Albums
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "track,playlist,album,artist" to "All",
                        "track" to "Songs",
                        "playlist" to "Playlists",
                        "album" to "Albums",
                        "artist" to "Artists"
                    ).forEach { (fKey, fLabel) ->
                        val isFSelected = searchFilter == fKey
                        FilterChip(
                            selected = isFSelected,
                            onClick = {
                                searchFilter = fKey
                                if (query.isNotBlank()) browseSearch()
                            },
                            label = { Text(fLabel, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Lime.copy(alpha = 0.15f),
                                selectedLabelColor = Lime
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isFSelected,
                                borderColor = if (isFSelected) Lime else Line
                            )
                        )
                    }
                }

                ActionButton("Search Spotify", Icons.Outlined.Search, query.isNotBlank() && !ui.busy) {
                    browseSearch()
                }

                // Render Search Results
                if (results.isEmpty() && query.isNotBlank() && !ui.busy) {
                    Text("No results found for \"$query\". Try a different search term.", color = Muted, fontSize = 12.sp)
                }

                results.forEach { item ->
                    val itemImage = extractTrackImage(item, null)
                    val title = item.optString("name", "Untitled")
                    val subtitle = item.optJSONObject("artists")?.optString("name")
                        ?: item.items("artists").joinToString { it.optString("name") }.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("owner")?.optString("display_name")
                        ?: item.optString("type").replaceFirstChar { it.uppercase() }
                    val itemUri = item.optString("uri")
                    val durationMs = item.optInt("duration_ms", 0)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !ui.busy) {
                                    model.work {
                                        model.request("POST", "/spotify/playback", obj("action" to "play", "uri" to itemUri, "device_id" to selected))
                                        player = model.request("GET", "/spotify/player")
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (itemImage.isNotBlank()) {
                                AsyncImage(
                                    model = itemImage,
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Ink),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(subtitle, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (durationMs > 0) {
                                        Text("•", fontSize = 10.sp, color = Muted)
                                        Text(timeLabel(durationMs), fontSize = 11.sp, color = Muted)
                                    }
                                }
                            }
                            // Add to Queue button
                            IconButton(
                                onClick = {
                                    model.work {
                                        model.request("POST", "/spotify/playback", obj("action" to "queue", "argument" to itemUri, "device_id" to selected))
                                    }
                                },
                                enabled = !ui.busy,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "Add to Queue", tint = Muted, modifier = Modifier.size(18.dp))
                            }
                            // Play button
                            FilledIconButton(
                                onClick = {
                                    model.work {
                                        model.request("POST", "/spotify/playback", obj("action" to "play", "uri" to itemUri, "device_id" to selected))
                                        player = model.request("GET", "/spotify/player")
                                    }
                                },
                                enabled = !ui.busy,
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime.copy(alpha = 0.18f), contentColor = Lime)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (moreResults) {
                    TextButton(enabled = !ui.busy && resultOffset <= 1000, onClick = { browseSearch(true) }) {
                        Text("Load more results", fontSize = 12.sp)
                    }
                }
            }

            "liked" -> {
                // Liked Songs Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Liked Songs", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
                        Text("${likedTracks.size} saved tracks loaded", fontSize = 11.sp, color = Muted)
                    }
                    if (likedTracks.isNotEmpty()) {
                        Button(
                            onClick = {
                                model.work {
                                    model.request("POST", "/spotify/playback", obj("action" to "play_liked", "device_id" to selected))
                                    player = model.request("GET", "/spotify/player")
                                }
                            },
                            enabled = !ui.busy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle Liked", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isLikedScopeMissing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Liked Songs library access requires updated Spotify permission.", color = Cream, fontSize = 12.sp)
                        Text("Tap below to open Settings and reconnect Spotify to authorize your Liked Songs library.", color = Muted, fontSize = 11.sp)
                        OutlinedButton(onClick = { navigate("System") }) {
                            Text("Open Settings to Re-link", fontSize = 11.sp)
                        }
                    }
                } else if (likedTracks.isEmpty() && !ui.busy) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Muted, modifier = Modifier.size(28.dp))
                        Text("No liked tracks found", color = Muted, fontSize = 13.sp)
                        TextButton(onClick = { browseLiked() }) { Text("Refresh Liked Songs") }
                    }
                }

                likedTracks.forEach { track ->
                    val trackImg = extractTrackImage(track, null)
                    val trackName = track.optString("name", "Untitled")
                    val artistName = track.items("artists").joinToString { it.optString("name") }
                    val trackUri = track.optString("uri")
                    val durationMs = track.optInt("duration_ms", 0)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !ui.busy) {
                                model.work {
                                    model.request("POST", "/spotify/playback", obj("action" to "play", "uri" to trackUri, "device_id" to selected))
                                    player = model.request("GET", "/spotify/player")
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (trackImg.isNotBlank()) {
                            AsyncImage(
                                model = trackImg,
                                contentDescription = trackName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(Ink), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF4B6E), modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(trackName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Cream, maxLines = 1)
                            Text("$artistName • ${timeLabel(durationMs)}", fontSize = 11.sp, color = Muted, maxLines = 1)
                        }
                        IconButton(
                            onClick = {
                                model.work {
                                    model.request("POST", "/spotify/playback", obj("action" to "queue", "argument" to trackUri, "device_id" to selected))
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add to Queue", tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            "playlists" -> {
                // Playlists Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Playlists", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
                    TextButton(onClick = { browsePlaylists() }, enabled = !ui.busy) {
                        Text("Refresh", fontSize = 11.sp)
                    }
                }

                if (playlistsList.isEmpty() && !ui.busy) {
                    Text("No playlists loaded. Tap Refresh above to load your Spotify playlists.", color = Muted, fontSize = 12.sp)
                }

                playlistsList.forEach { pl ->
                    val plImg = extractTrackImage(pl, null)
                    val plName = pl.optString("name", "Playlist")
                    val plTracksCount = pl.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                    val plUri = pl.optString("uri")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !ui.busy) {
                                model.work {
                                    model.request("POST", "/spotify/playback", obj("action" to "play", "uri" to plUri, "device_id" to selected))
                                    player = model.request("GET", "/spotify/player")
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (plImg.isNotBlank()) {
                            AsyncImage(
                                model = plImg,
                                contentDescription = plName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Ink), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.QueueMusic, contentDescription = null, tint = Lime, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Cream, maxLines = 1)
                            Text("$plTracksCount tracks", fontSize = 11.sp, color = Muted)
                        }
                        FilledIconButton(
                            onClick = {
                                model.work {
                                    model.request("POST", "/spotify/playback", obj("action" to "play", "uri" to plUri, "device_id" to selected))
                                    player = model.request("GET", "/spotify/player")
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime.copy(alpha = 0.18f), contentColor = Lime)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play Playlist", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            "recent" -> {
                // Recently Played Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recently Played", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
                    TextButton(onClick = { browseRecent() }, enabled = !ui.busy) {
                        Text("Refresh", fontSize = 11.sp)
                    }
                }

                if (recentList.isEmpty() && !ui.busy) {
                    Text("No recent history recorded. Play any song on Spotify to view it here.", color = Muted, fontSize = 12.sp)
                }

                recentList.forEach { track ->
                    val rImg = extractTrackImage(track, null)
                    val rName = track.optString("name", "Track")
                    val rArtist = track.items("artists").joinToString { it.optString("name") }
                    val rUri = track.optString("uri")
                    val durationMs = track.optInt("duration_ms", 0)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !ui.busy) {
                                model.work {
                                    model.request("POST", "/spotify/playback", obj("action" to "play", "uri" to rUri, "device_id" to selected))
                                    player = model.request("GET", "/spotify/player")
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (rImg.isNotBlank()) {
                            AsyncImage(
                                model = rImg,
                                contentDescription = rName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).background(Ink), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.History, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Cream, maxLines = 1)
                            Text("$rArtist • ${timeLabel(durationMs)}", fontSize = 11.sp, color = Muted, maxLines = 1)
                        }
                        FilledIconButton(
                            onClick = {
                                model.work {
                                    model.request("POST", "/spotify/playback", obj("action" to "play", "uri" to rUri, "device_id" to selected))
                                    player = model.request("GET", "/spotify/player")
                                }
                            },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime.copy(alpha = 0.18f), contentColor = Lime)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            "queue" -> {
                // Queue / Up Next Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Up Next in Queue", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
                    TextButton(onClick = { browseQueue() }, enabled = !ui.busy) {
                        Text("Refresh", fontSize = 11.sp)
                    }
                }

                if (queueCurrent.length() > 0) {
                    Text("NOW PLAYING", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    val nowImg = extractTrackImage(queueCurrent, null)
                    val nowName = queueCurrent.optString("name", "Unknown")
                    val nowArtist = queueCurrent.items("artists").joinToString { it.optString("name") }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (nowImg.isNotBlank()) {
                            AsyncImage(model = nowImg, contentDescription = nowName, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(nowName, color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(nowArtist, color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (queueItems.isEmpty() && !ui.busy) {
                    Text("No upcoming tracks in queue. Add songs from Search or your Liked Songs.", color = Muted, fontSize = 12.sp)
                }

                queueItems.take(20).forEachIndexed { idx, qTrack ->
                    val qImg = extractTrackImage(qTrack, null)
                    val qName = qTrack.optString("name", "Track")
                    val qArtist = qTrack.items("artists").joinToString { it.optString("name") }
                    val durationMs = qTrack.optInt("duration_ms", 0)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${idx + 1}", color = Muted, fontSize = 11.sp, modifier = Modifier.width(20.dp))
                        if (qImg.isNotBlank()) {
                            AsyncImage(model = qImg, contentDescription = qName, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(qName, color = Cream, fontSize = 12.sp, maxLines = 1)
                            Text("$qArtist • ${timeLabel(durationMs)}", color = Muted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        Text("Spotify playback, search, and library content are provided via the Spotify Web API.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
fun ClockFormatSettingSelector(
    use24HourFormat: Boolean,
    onSelect24Hour: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallLabel("ROUTINE & SYSTEM CLOCK FORMAT")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                true to "24-Hour (00:00 – 23:59)",
                false to "12-Hour (AM / PM)"
            ).forEach { (is24, label) ->
                val selected = use24HourFormat == is24
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) Lime.copy(alpha = 0.18f) else Ink)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) Lime else Line,
                            shape = RoundedCornerShape(11.dp)
                        )
                        .livelyClick { onSelect24Hour(is24) }
                        .padding(horizontal = 10.dp)
                        .testTag(if (is24) "clock_format_24h" else "clock_format_12h"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Lime else Cream,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DataStoreBackupRestoreCard(
    model: RoomModel,
    ui: RoomUi,
    wled: WledModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportJson by remember { mutableStateOf("") }
    var showJsonDialog by remember { mutableStateOf(false) }
    var dialogJsonText by remember { mutableStateOf("") }
    var dialogMode by remember { mutableStateOf("export") } // "export" or "import"

    val exportDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExportJson.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(pendingExportJson.toByteArray(Charsets.UTF_8))
                    }
                }
                pendingExportJson = ""
            }
        }
    }

    val importDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBounded(4_000_000) }
                    }.getOrNull()
                }
                if (!raw.isNullOrBlank()) {
                    model.importDataStoreBackup(raw, wled)
                }
            }
        }
    }

    val appliancesCount = ui.state.items("appliances").size
    val commandsCount = ui.state.items("commands").size
    val routinesCount = ui.state.items("rules").size

    RoomCard {
        SectionLabel(
            "Configuration Backup",
            "DATASTORE"
        )
        Text(
            text = "Export all Room Controller settings ($routinesCount routines, $commandsCount IR commands, $appliancesCount appliances, clock format, favorites & WLED presets) from Jetpack DataStore and re-import them on any device.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        if (!ui.lastBackupTimestamp.isNullOrBlank()) {
            Text(
                text = "Latest DataStore snapshot: ${ui.lastBackupTimestamp}",
                color = Lime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Row 1: Export File (.json) & Import File (.json)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    model.exportDataStoreBackup { json ->
                        pendingExportJson = json
                        exportDocLauncher.launch("theroom-datastore-backup.json")
                    }
                },
                enabled = !ui.busy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                modifier = Modifier.weight(1f).testTag("datastore_export_file_button")
            ) {
                Icon(Icons.Outlined.UploadFile, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export Backup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = {
                    importDocLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                enabled = !ui.busy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                modifier = Modifier.weight(1f).testTag("datastore_import_file_button")
            ) {
                Icon(Icons.Outlined.Download, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import Backup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }

        // Row 2: Save Snapshot & Restore Snapshot (2 equal columns so "Restore Snapshot" is 100% displayed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { model.createLocalDataStoreSnapshot() },
                enabled = !ui.busy,
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f).testTag("datastore_save_snapshot_button")
            ) {
                Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Snapshot", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = { model.restoreLocalDataStoreSnapshot(wled) },
                enabled = !ui.busy,
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f).testTag("datastore_restore_snapshot_button")
            ) {
                Icon(Icons.Outlined.Restore, null, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Restore Snapshot", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }

        // Row 3: Full-width Copy / Paste Backup JSON button
        OutlinedButton(
            onClick = {
                model.exportDataStoreBackup { json ->
                    dialogJsonText = json
                    dialogMode = "export"
                    showJsonDialog = true
                }
            },
            enabled = !ui.busy,
            shape = RoundedCornerShape(11.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().testTag("datastore_json_clipboard_button")
        ) {
            Icon(Icons.Outlined.ContentPaste, null, Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy / Paste Backup JSON", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }

    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = {
                Text(
                    text = if (dialogMode == "export") "Export / Copy DataStore JSON" else "Paste Backup JSON to Restore",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = dialogMode == "export",
                            onClick = { dialogMode = "export" },
                            label = { Text("Export JSON", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                        )
                        FilterChip(
                            selected = dialogMode == "import",
                            onClick = {
                                dialogMode = "import"
                            },
                            label = { Text("Paste & Restore", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                        )
                    }
                    OutlinedTextField(
                        value = dialogJsonText,
                        onValueChange = { dialogJsonText = it },
                        label = { Text("DataStore configuration JSON") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 240.dp)
                            .testTag("datastore_backup_json_input"),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                if (dialogMode == "export") {
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("TheRoom Backup JSON", dialogJsonText))
                            showJsonDialog = false
                        }
                    ) {
                        Text("Copy to Clipboard")
                    }
                } else {
                    TextButton(
                        enabled = dialogJsonText.isNotBlank(),
                        onClick = {
                            model.importDataStoreBackup(dialogJsonText, wled)
                            showJsonDialog = false
                        }
                    ) {
                        Text("Restore Now")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable fun SystemScreen(model: RoomModel, ui: RoomUi, wled: WledModel? = null) {
    val context=LocalContext.current
    val current=ui.state.optJSONObject("system") ?: obj()
    var zone by remember(current.toString()) {mutableStateOf(current.optString("timezone","UTC"))}
    var timeout by remember(current.toString()) {mutableStateOf(current.optInt("display_timeout",30).toFloat())}
    var volumeTarget by remember(current.toString()) {mutableStateOf(current.optString("volume_target","spotify"))}
    var diagnostics by remember {mutableStateOf<JSONObject?>(null)}
    var passphrase by remember {mutableStateOf("")}
    var archiveToWrite by remember {mutableStateOf("")}
    var restoreArchive by remember {mutableStateOf<String?>(null)}
    var ota by remember {mutableStateOf<JSONObject?>(null)}
    var signout by remember {mutableStateOf(false)}
    val backupFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri ->
        if(uri!=null) model.work {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.use {it.write(archiveToWrite.toByteArray())} ?: error("Could not create backup file")};archiveToWrite="";passphrase=""}
    }
    val restoreFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) model.work {
            val text=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {it.readBounded(4_000_000)} ?: error("Could not read backup")}
            restoreArchive=JSONObject(text).getString("archive")
        }
    }
    val otaFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) model.work {
            val text=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {it.readBounded(16000)} ?: error("Could not read firmware manifest")}
            ota=JSONObject(text)
        }
    }
    PageTitle("Settings", "Everything in place.", "Preferences, appearance, and controller settings.")
    SpotifyAccountSettingsCard(model = model, ui = ui)
    DataStoreBackupRestoreCard(model = model, ui = ui, wled = wled)
    RoomThreeBandEqSettingsCard(model = model, ui = ui)
    ThemeChooser()
    RoomCard {
        SectionLabel("Connection",if(ui.state.optBoolean("connected")&&!ui.stale) "ONLINE" else "OFFLINE")
        Text(if(ui.demo) "Interface preview" else model.endpoint,color=Muted,fontSize=13.sp)
        if(ui.demo) ActionButton("Connect my ESP32", enabled=!ui.busy) { model.useBuiltInDevice() }
        val telemetry=ui.state.optJSONObject("telemetry")
        Text("Firmware: ${telemetry?.optString("firmware") ?: "Not connected"}\nWi-Fi: ${telemetry?.optInt("rssi") ?: "—"} dBm\nClock: ${if(telemetry?.optBoolean("time_valid")==true) "Synchronized" else "Not synchronized"}",color=Muted,fontSize=13.sp,lineHeight=23.sp)
        Text("Addressable lighting replaces Gesto. Control actions and GPIO assignments are approved for implementation; module specifications and electrical compatibility still need verification.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
    }
    RoomCard {
        SectionLabel("Preferences", if (ui.use24HourFormat) "24-HOUR CLOCK" else "12-HOUR CLOCK")
        ClockFormatSettingSelector(
            use24HourFormat = ui.use24HourFormat,
            onSelect24Hour = { model.setUse24HourFormat(it) }
        )
        OutlinedTextField(value=zone,onValueChange={zone=it},label={Text("Default time zone")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Text("Each routine keeps its own time zone.",color=Muted,fontSize=11.sp)
        SettingSlider("OLED timeout",timeout,{timeout=it},5f..600f," s")
        SmallLabel("VOLUME ACTION TARGET")
        Choices(listOf("spotify","speaker_ir"),volumeTarget) {volumeTarget=it}
        ActionButton("Save preferences",Icons.Outlined.Check,!ui.busy && runCatching {ZoneId.of(zone)}.isSuccess) {
            model.mutate("PUT","/settings",obj("automation_enabled" to current.optBoolean("automation_enabled",true),"timezone" to zone,"display_timeout" to timeout.toInt(),"volume_target" to volumeTarget,"use_24_hour" to ui.use24HourFormat))
        }
        SettingToggleRow(
            title = "Automation enabled",
            subtitle = "Turn all routines and scheduled triggers on or off together",
            checked = current.optBoolean("automation_enabled", true),
            enabled = !ui.busy,
            onCheckedChange = { model.setAllRoutinesEnabled(it) }
        )
    }
    RoomCard {
        SectionLabel("Controller Encrypted Archive")
        Text("Encrypted hardware archive of your ESP32 controller NVS. Keep the passphrase somewhere safe; it cannot be recovered.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        OutlinedTextField(value=passphrase,onValueChange={passphrase=it},label={Text("Backup passphrase · 12+ characters")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=passphrase.length>=12&&!ui.busy,modifier=Modifier.weight(1f),onClick={model.work {
                archiveToWrite=model.request("POST","/backup",obj("passphrase" to passphrase)).toString()
                backupFile.launch("room-backup.json")
            }}) {Text("Export archive", maxLines=1, softWrap=false)}
            OutlinedButton(enabled=passphrase.length>=12&&!ui.busy,modifier=Modifier.weight(1f),onClick={restoreFile.launch(arrayOf("application/json","text/plain"))}) {Text("Restore archive", maxLines=1, softWrap=false)}
        }
    }
    RoomCard {
        SectionLabel("Firmware updates")
        Text("Import a signed manifest after verifying the board and stable power. The server validates its signature. Updates stay disabled until the hardware adapter supports verified OTA.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        OutlinedButton(onClick={otaFile.launch(arrayOf("application/json"))},enabled=!ui.busy) {Text("Import signed manifest")}
    }
    RoomCard {
        SectionLabel("Diagnostics")
        OutlinedButton(onClick={model.work {diagnostics=model.request("GET","/diagnostics")}},enabled=!ui.busy) {Text("Refresh diagnostics")}
        diagnostics?.items("jobs")?.take(8)?.forEach {job ->Text("${job.optString("status")} · ${job.optString("detail")}",color=Muted,fontSize=11.sp)}
        diagnostics?.items("events")?.take(12)?.forEach {event ->Text("${event.optString("category")} · ${event.optString("message")}",color=Muted,fontSize=11.sp,lineHeight=17.sp)}
        Text("Toggle and disruptive IR commands are never automatically retried. Transmitted signals do not confirm appliance state.",color=Muted,fontSize=11.sp,lineHeight=17.sp)
    }
    OutlinedButton(onClick={signout=true},modifier=Modifier.fillMaxWidth()) {Text("Disconnect & sign out")}
    Text("ROOM · v${BuildConfig.VERSION_NAME}",color=Muted,fontSize=10.sp,modifier=Modifier.fillMaxWidth())
    restoreArchive?.let {archive ->ConfirmDialog("Replace saved room data?","This replaces the saved IR library, settings, and routines. Restored routines stay paused. No IR commands will be replayed.",{restoreArchive=null}) {model.mutate("POST","/restore",obj("archive" to archive,"passphrase" to passphrase,"confirm" to true),"Backup restored; routines paused");passphrase=""} }
    ota?.let {manifest ->ConfirmDialog("Install firmware?","Version ${manifest.optString("version")} for ${manifest.optString("board")}. Keep stable power during the update.",{ota=null}) {model.mutate("POST","/ota",manifest.copyJson().put("confirm",true),"Firmware update submitted")} }
    if(signout) ConfirmDialog("Disconnect this phone?","Your saved routines and controller settings will remain available.",{signout=false}) {model.logout()}
}
