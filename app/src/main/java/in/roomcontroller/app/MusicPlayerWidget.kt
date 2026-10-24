package `in`.roomcontroller.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.sin

import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

fun timeLabel(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

fun defaultCoverForTrackName(trackName: String): String = when {
    trackName.contains("Morning Light", ignoreCase = true) ->
        "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&auto=format&fit=crop&q=80"
    trackName.contains("Midnight Signal", ignoreCase = true) ->
        "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=300&auto=format&fit=crop&q=80"
    trackName.contains("Solaris Echo", ignoreCase = true) ->
        "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=300&auto=format&fit=crop&q=80"
    else ->
        "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=300&auto=format&fit=crop&q=80"
}

fun extractTrackImage(track: JSONObject?, player: JSONObject?): String {
    val direct = track?.optString("image_url")?.takeIf { it.isNotBlank() }
        ?: track?.optString("artwork_url")?.takeIf { it.isNotBlank() }
        ?: player?.optString("image_url")?.takeIf { it.isNotBlank() }
        ?: player?.optString("artwork")?.takeIf { it.isNotBlank() }
    if (!direct.isNullOrBlank()) return direct

    val albumImages = track?.optJSONObject("album")?.optJSONArray("images")
    if (albumImages != null && albumImages.length() > 0) {
        val url = albumImages.optJSONObject(0)?.optString("url")
        if (!url.isNullOrBlank()) return url
    }
    val trackImages = track?.optJSONArray("images")
    if (trackImages != null && trackImages.length() > 0) {
        val url = trackImages.optJSONObject(0)?.optString("url")
        if (!url.isNullOrBlank()) return url
    }
    val playerAlbumImages = player?.optJSONObject("item")?.optJSONObject("album")?.optJSONArray("images")
    if (playerAlbumImages != null && playerAlbumImages.length() > 0) {
        val url = playerAlbumImages.optJSONObject(0)?.optString("url")
        if (!url.isNullOrBlank()) return url
    }
    val trackTitle = track?.optString("name")?.ifBlank { null }
        ?: player?.optString("track")?.ifBlank { null }
        ?: ""
    return if (trackTitle.isNotBlank()) defaultCoverForTrackName(trackTitle) else ""
}

@Composable
fun SpotifyDeviceDropdownSelector(
    activeDeviceName: String,
    activeDeviceId: String,
    devices: List<JSONObject>,
    enabled: Boolean = true,
    onSelectDevice: (deviceId: String, deviceName: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val availableDevices = remember(devices, activeDeviceName, activeDeviceId) {
        if (devices.isNotEmpty()) {
            devices
        } else if (activeDeviceName.isNotBlank()) {
            listOf(obj("id" to activeDeviceId.ifBlank { activeDeviceName }, "name" to activeDeviceName, "is_active" to true))
        } else {
            emptyList()
        }
    }
    val displayDeviceName = activeDeviceName.ifBlank {
        availableDevices.firstOrNull { it.optBoolean("is_active") }?.optString("name")
            ?: availableDevices.firstOrNull()?.optString("name")
            ?: "No device active"
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Ink)
                .border(1.dp, Lime.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Speaker,
                    contentDescription = "Playback output device",
                    tint = Lime,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PLAYING ON SPOTIFY DEVICE",
                        color = Muted,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = displayDeviceName,
                        color = Lime,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Switch",
                    color = Cream,
                    fontSize = 11.sp
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = "Choose Spotify playback device",
                    tint = Lime,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Panel)
                .border(1.dp, Line, RoundedCornerShape(12.dp))
        ) {
            availableDevices.forEach { dev ->
                val id = dev.optString("id").ifBlank { dev.optString("name") }
                val name = dev.optString("name", "Spotify Device")
                val restricted = dev.optBoolean("is_restricted", false)
                val isCurrent = (activeDeviceId.isNotBlank() && id == activeDeviceId) ||
                    name.equals(displayDeviceName, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = name,
                                color = if (isCurrent) Lime else Cream,
                                fontSize = 13.sp
                            )
                            Text(
                                text = when {
                                    restricted -> "Restricted device"
                                    isCurrent -> "Active output source"
                                    else -> "Tap to transfer playback here"
                                },
                                color = if (isCurrent) Lime.copy(alpha = 0.8f) else Muted,
                                fontSize = 10.sp
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (name.contains("TV", ignoreCase = true)) Icons.Outlined.Tv
                            else if (name.contains("Phone", ignoreCase = true)) Icons.Outlined.Smartphone
                            else Icons.Outlined.Speaker,
                            contentDescription = null,
                            tint = if (isCurrent) Lime else Cream,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = Lime,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    enabled = !restricted,
                    onClick = {
                        expanded = false
                        onSelectDevice(id, name)
                    }
                )
            }
        }
    }
}

@Composable
fun MusicPlayerWidget(
    player: JSONObject,
    preview: Boolean,
    busy: Boolean,
    onAction: (String, JSONObject) -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val track = player.optJSONObject("item")
    val playing = player.optBoolean("is_playing", player.optBoolean("playing", false))
    val total = (track?.optInt("duration_ms", 0)?.takeIf { it > 0 }
        ?: player.optInt("duration_ms", 210000)).coerceAtLeast(1)
    val incomingProgressMs = player.optInt("progress_ms", 41000).coerceIn(0, total)
    var position by remember {
        mutableFloatStateOf(incomingProgressMs.toFloat() / total * 100f)
    }
    var lastSeekEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(incomingProgressMs, total) {
        if (System.currentTimeMillis() - lastSeekEditMs > 1200L) {
            position = (incomingProgressMs.toFloat() / total * 100f).coerceIn(0f, 100f)
        }
    }
    // Smooth real-time playback extrapolation while playing so the scrubber & timer never freeze
    LaunchedEffect(playing, total) {
        if (playing) {
            var lastTick = System.currentTimeMillis()
            while (true) {
                kotlinx.coroutines.delay(250L)
                val now = System.currentTimeMillis()
                val dt = (now - lastTick).coerceIn(0L, 1000L)
                lastTick = now
                if (now - lastSeekEditMs > 1200L) {
                    val currentMs = (position / 100f * total) + dt.toFloat()
                    position = (currentMs / total * 100f).coerceIn(0f, 100f)
                }
            }
        }
    }
    val initialShuffleMode = when {
        player.optBoolean("smart_shuffle", false) -> 2
        player.optBoolean("shuffle_state", player.optBoolean("shuffle", false)) -> 1
        else -> 0
    }
    var shuffleMode by remember(player.optBoolean("shuffle_state", false), player.optBoolean("smart_shuffle", false)) {
        mutableIntStateOf(initialShuffleMode)
    }
    val initialRepeat = when (player.optString("repeat_state", "off").lowercase()) {
        "context" -> 1
        "track" -> 2
        else -> 0
    }
    var repeatMode by remember(initialRepeat) { mutableIntStateOf(initialRepeat) }
    var zoomed by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(24000, easing = LinearEasing))
            }
        }
    }
    val trackTitle = track?.optString("name")?.takeIf { it.isNotBlank() }
        ?: player.optString("track").takeIf { it.isNotBlank() }
        ?: ""
    val artistTitle = track?.items("artists")?.joinToString { it.optString("name") }?.takeIf { it.isNotBlank() }
        ?: player.optString("artist").takeIf { it.isNotBlank() }
        ?: ""
    val imageUrl = remember(track?.toString(), player.toString(), trackTitle) {
        extractTrackImage(track, player).ifBlank { if (trackTitle.isNotBlank()) defaultCoverForTrackName(trackTitle) else "" }
    }
    val accent = Lime
    val text = Cream

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val coverSize = if (zoomed) 224.dp else 184.dp
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(22.dp))
                .background(Panel)
                .border(1.5.dp, if (playing) accent.copy(alpha = 0.65f) else Line, RoundedCornerShape(22.dp))
                .clickable {
                    performSubtleHaptic(view)
                    zoomed = !zoomed
                }
                .semantics { contentDescription = "Track album artwork for $trackTitle; tap to toggle size" },
            contentAlignment = Alignment.Center
        ) {
            // Subtle vinyl backdrop in case network image is loading
            Canvas(Modifier.fillMaxSize().padding(16.dp).rotate(rotation.value)) {
                val radius = size.minDimension / 2
                drawCircle(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFF18252A),
                            accent.copy(alpha = .5f),
                            Color(0xFF393058),
                            Color(0xFF142A31),
                            accent.copy(alpha = .35f),
                            Color(0xFF18252A)
                        )
                    )
                )
                for (i in 0..10) {
                    drawCircle(
                        Color.White.copy(alpha = .06f),
                        radius * (.35f + i * .05f),
                        style = Stroke(1.dp.toPx())
                    )
                }
            }
            // Synced song cover image over the album frame
            AsyncImage(
                model = imageUrl,
                contentDescription = "Album cover for $trackTitle",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
            // Corner status badge on the song image
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.copy(alpha = 0.82f))
                    .border(1.dp, if (playing) accent.copy(alpha = 0.6f) else Line, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (playing) accent else Muted)
                )
                Text(
                    text = if (playing) "NOW PLAYING" else "PAUSED",
                    color = if (playing) accent else text,
                    fontSize = 9.sp
                )
            }
        }

        val trackId = player.optString("id").ifBlank { track?.optString("id").orEmpty() }
        var isLiked by remember(trackId, player.optBoolean("saved", false)) {
            mutableStateOf(player.optBoolean("saved", false))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    artistTitle.ifBlank { if (trackTitle.isNotBlank()) "Unknown Artist" else "Ready to stream" },
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Text(
                    trackTitle.ifBlank { "No track playing" },
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    color = text,
                    maxLines = 1
                )
                if (shuffleMode == 2) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(11.dp))
                        Text("Magic Shuffle Active", color = accent, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
            if (trackTitle.isNotBlank()) {
                IconButton(
                    onClick = {
                        performSubtleHaptic(view)
                        isLiked = !isLiked
                        onAction(if (isLiked) "like" else "unlike", obj("id" to trackId, "argument" to trackId))
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) Color(0xFFFF4B6E) else Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Slider05(
            value = position,
            onValueChange = {
                lastSeekEditMs = System.currentTimeMillis()
                position = it
            },
            enabled = true,
            label = "Playback position",
            onValueChangeFinished = {
                if (System.currentTimeMillis() - lastSeekEditMs <= 2000L) {
                    onAction("seek", obj("position_ms" to (position / 100 * total).toInt()))
                }
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeLabel((position / 100 * total).toInt()), color = Muted, fontSize = 11.sp)
            Text(timeLabel(total), color = Muted, fontSize = 11.sp)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = !busy,
                onClick = {
                    performSubtleHaptic(view)
                    shuffleMode = (shuffleMode + 1) % 3
                    when (shuffleMode) {
                        0 -> onAction("shuffle", obj("state" to false, "mode" to "off", "value" to 0))
                        1 -> onAction("shuffle", obj("state" to true, "mode" to "standard", "value" to 1))
                        2 -> onAction("shuffle", obj("state" to true, "mode" to "magic", "value" to 2))
                    }
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (shuffleMode == 2) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            "Magic Shuffle",
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Shuffle,
                            if (shuffleMode == 1) "Shuffle" else "Shuffle Off",
                            tint = if (shuffleMode == 1) accent else Muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            IconButton(
                enabled = !busy,
                onClick = {
                    performSubtleHaptic(view)
                    onAction("previous", obj())
                }
            ) {
                Icon(Icons.Outlined.SkipPrevious, "previous", modifier = Modifier.size(28.dp))
            }
            FilledIconButton(
                enabled = !busy,
                onClick = {
                    performSubtleHaptic(view, heavy = true)
                    onAction(if (playing) "pause" else "play", obj())
                },
                modifier = Modifier.size(58.dp)
            ) {
                Icon(
                    if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if (playing) "pause" else "play",
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(
                enabled = !busy,
                onClick = {
                    performSubtleHaptic(view)
                    onAction("next", obj())
                }
            ) {
                Icon(Icons.Outlined.SkipNext, "next", modifier = Modifier.size(28.dp))
            }
            IconButton(
                enabled = !busy,
                onClick = {
                    performSubtleHaptic(view)
                    repeatMode = (repeatMode + 1) % 3
                    onAction("repeat", obj("mode" to listOf("off", "context", "track")[repeatMode]))
                }
            ) {
                Icon(
                    if (repeatMode == 2) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                    "Repeat",
                    tint = if (repeatMode > 0) accent else Muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
