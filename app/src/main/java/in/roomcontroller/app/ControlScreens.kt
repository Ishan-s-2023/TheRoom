package `in`.roomcontroller.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

fun parseColor(hex: String): Color = runCatching {
    val clean = hex.trim().removePrefix("#")
    val parsed = clean.toLong(16)
    if (clean.length == 6) Color(0xFF000000L or parsed) else Color(parsed)
}.getOrDefault(Color(0xFFB5F36A))
fun action(type: String, data: JSONObject) = obj("type" to type, type to data)

const val MAX_HOME_FAVORITES = 4
const val MAX_HOME_PRESETS = 4

@Composable
fun HomeScreen(model: RoomModel, ui: RoomUi, wled: WledModel, wledUi: WledUi, navigate: (String) -> Unit) {
    val state = ui.state
    val snap = wledUi.effectiveSnapshot
    val online = (state.optBoolean("connected") && !ui.stale) || wledUi.online

    // Compact Atmospheric Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (online) Lime else Color(0xFFF6BC7B))
            )
            SmallLabel(
                when {
                    wledUi.online && state.optBoolean("connected") && !ui.stale -> "ESP32 + WLED LIVE"
                    wledUi.online -> "WLED LIGHTING LIVE"
                    state.optBoolean("connected") && !ui.stale -> "ROOM CONTROLLER LIVE"
                    else -> "STUDIO READY · LOCAL SYNC"
                }
            )
        }
        val espMusicActive = state.optJSONObject("lighting")?.optString("effect", "solid")?.let { it != "solid" } == true
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Panel)
                .border(1.dp, if (espMusicActive) Lime else Line, RoundedCornerShape(10.dp))
                .livelyClick(pressedScale = 0.92f) {
                    wled.setActiveTab("ESP32 Music")
                    navigate("Lights")
                }
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag("home_esp32_music_badge"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.GraphicEq, null, tint = Lime, modifier = Modifier.size(14.dp))
            Text(
                text = if (espMusicActive) "ESP32 Music: ${state.optJSONObject("lighting")?.optString("effect")?.uppercase()}" else "ESP32 Music DSP",
                fontSize = 11.sp,
                color = if (espMusicActive) Lime else Cream,
                fontWeight = FontWeight.Medium
            )
        }
    }

    PageTitle(
        eyebrow = "Make yourself at home",
        title = "Light & sound, in balance.",
        subtitle = "Instant master brightness, music volume, color correction, and your top 4 pinned favorites."
    )

    // 1. PRIMARY HERO DECK: Master Brightness & Music Volume
    HomeBrightnessAndVolumeHeroCard(
        model = model,
        ui = ui,
        wled = wled,
        wledUi = wledUi,
        navigate = navigate
    )

    // 2. COLOR CORRECTING STUDIO: Hue Spectrum, Kelvin CCT White Balance & Saturation
    HomeColorCorrectingCard(
        model = model,
        ui = ui,
        wled = wled,
        wledUi = wledUi,
        navigate = navigate
    )

    // 3. WITHIN REACH FAVORITES (Strictly capped at MAX_HOME_FAVORITES = 4 to prevent clutter)
    HomeUniversalFavoritesSection(
        model = model,
        ui = ui,
        wled = wled,
        wledUi = wledUi,
        navigate = navigate
    )

    // 4. ROUTINE CARDS DASHBOARD (Unique, minimalistic icons per routine card for instant recognition)
    HomeRoutinesDashboardCard(
        model = model,
        ui = ui,
        navigate = navigate
    )

    // 5. SAVED PRESETS BY CATEGORY (Strictly capped at MAX_HOME_PRESETS = 4 to prevent clutter)
    HomePresetCategorySection(
        model = model,
        ui = ui,
        wled = wled,
        wledUi = wledUi,
        navigate = navigate
    )
}

@Composable
private fun HomeRoutinesDashboardCard(
    model: RoomModel,
    ui: RoomUi,
    navigate: (String) -> Unit
) {
    val rules = ui.state.items("rules")
    val uniqueIcons = remember(rules) { assignUniqueRoutineIcons(rules) }
    val runtime = ui.state.optJSONObject("runtime")
    val settings = ui.state.optJSONObject("settings") ?: obj()
    val system = ui.state.optJSONObject("system") ?: obj()
    val masterEnabled = when {
        ui.state.has("runtime") -> runtime?.optBoolean("armed", true) == true
        ui.state.has("settings") -> settings.optBoolean("automation_enabled", true)
        else -> system.optBoolean("automation_enabled", true)
    }

    RoomCard(modifier = Modifier.testTag("home_routines_dashboard_section")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Routines",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Cream,
                    maxLines = 1,
                    softWrap = false
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink)
                        .border(1.dp, Line, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${rules.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Lime,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            val openRoutinesInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = { navigate("Routines") },
                interactionSource = openRoutinesInteraction,
                modifier = Modifier
                    .livelyPressScale(openRoutinesInteraction)
                    .testTag("home_open_routines_button"),
                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp)) },
                label = { Text("Manage", fontSize = 11.sp, maxLines = 1, softWrap = false) }
            )
        }

        if (rules.isEmpty()) {
            Text(
                text = "Create morning, evening, or music-reactive rituals in Routines to trigger them with one tap.",
                color = Muted,
                fontSize = 12.sp
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rules.chunked(2).forEachIndexed { rowIdx, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEachIndexed { colIdx, rule ->
                            val idx = rowIdx * 2 + colIdx
                            val ruleId = rule.optString("id")
                            val ruleKey = ruleId.ifBlank { "${rule.optString("name")}_$idx" }
                            val distinctIcon = uniqueIcons[ruleKey] ?: routineDistinctIcon(rule, idx)
                            val ruleEnabled = masterEnabled && rule.optBoolean("enabled", true)
                            val trigger = rule.optString("trigger", "schedule")
                            val subText = when (trigger) {
                                "schedule" -> formatClockTime(rule.optString("time", "19:00"), ui.use24HourFormat)
                                "sound_started" -> "Sound start"
                                "sound_stopped" -> "Quiet room"
                                "ir_received" -> "IR signal"
                                else -> "One-tap ritual"
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (ruleEnabled) Ink else Ink.copy(alpha = 0.45f))
                                    .border(
                                        width = 1.dp,
                                        color = if (ruleEnabled) Lime.copy(alpha = 0.35f) else Line,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .livelyClick(enabled = !ui.busy, pressedScale = 0.94f) {
                                        executeRoutine(model, ui, rule)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("home_routine_card_${ruleKey.replace(':', '_')}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (ruleEnabled) Lime.copy(alpha = 0.14f) else Line.copy(alpha = 0.4f))
                                        .border(
                                            width = 1.dp,
                                            color = if (ruleEnabled) Lime.copy(alpha = 0.4f) else Line,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = distinctIcon,
                                        contentDescription = rule.optString("name", "Routine"),
                                        tint = if (ruleEnabled) Lime else Muted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.optString("name", "Routine"),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (ruleEnabled) Cream else Muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subText,
                                        fontSize = 10.sp,
                                        color = Muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBrightnessAndVolumeHeroCard(
    model: RoomModel,
    ui: RoomUi,
    wled: WledModel,
    wledUi: WledUi,
    navigate: (String) -> Unit
) {
    val snap = wledUi.effectiveSnapshot
    val roomLighting = ui.state.optJSONObject("lighting") ?: obj()
    val spotify = ui.state.optJSONObject("spotify") ?: obj()
    val primarySeg = snap.primarySegment
    val primaryColor = parseColor(primarySeg.colorHex(0))

    val currentBriPct = remember(snap.bri) {
        (snap.bri * 100f / 255f).coerceIn(0f, 100f)
    }
    var briPct by remember { mutableFloatStateOf(currentBriPct) }
    var lastBriEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentBriPct) {
        if (System.currentTimeMillis() - lastBriEditMs > 750L) {
            briPct = currentBriPct
        }
    }

    val currentVolPct = spotify.optInt("volume_percent", 55).toFloat().coerceIn(0f, 100f)
    var volPct by remember { mutableFloatStateOf(currentVolPct) }
    var lastVolEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentVolPct) {
        if (System.currentTimeMillis() - lastVolEditMs > 750L) {
            volPct = currentVolPct
        }
    }

    val luminousAccent = WledCatalogDefaults.luminousLedColor(primaryColor, (briPct / 100f).coerceIn(0.35f, 1f))
    val isPowerOn = snap.on
    var musicControlsEnabled by remember { mutableStateOf(true) }
    val isMusicInteractive = musicControlsEnabled

    RoomCard(modifier = Modifier.testTag("home_primary_command_card")) {
        // Live hardware-accurate LED Strip Peek right at the top of the command deck
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallLabel("LIVE ${snap.ledCount}-LED STRIP PREVIEW")
                Text(
                    text = if (isPowerOn) primarySeg.colorHex(0) else "STRIP OFF",
                    fontSize = 10.sp,
                    color = if (isPowerOn) luminousAccent else Muted,
                    fontWeight = FontWeight.SemiBold
                )
            }
            WledAnimatedStripCanvas(ui = wledUi, snap = snap, heightDp = 24)
        }

        // A. MASTER BRIGHTNESS CONTROL
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPowerOn) luminousAccent.copy(alpha = 0.16f) else Line)
                            .border(1.dp, if (isPowerOn) luminousAccent.copy(alpha = 0.45f) else Line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.LightMode,
                            null,
                            tint = if (isPowerOn) luminousAccent else Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Light Brightness",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPowerOn) Cream else Muted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (isPowerOn) "${briPct.roundToInt()}%" else "OFF",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPowerOn) luminousAccent else Muted,
                    maxLines = 1,
                    softWrap = false
                )
                LiquidToggle(
                    checked = snap.on,
                    onCheckedChange = { nextOn ->
                        lastBriEditMs = System.currentTimeMillis()
                        wled.command(obj("on" to nextOn))
                        model.coalescedLighting(roomLighting.copyJson().put("power", nextOn))
                    },
                    label = "Lighting power",
                    modifier = Modifier.testTag("wled_home_power_toggle")
                )
            }

            // Disabled when Light Brightness power toggle is OFF
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isPowerOn) 1f else 0.42f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LedSlider(
                    value = (briPct / 100f).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        lastBriEditMs = System.currentTimeMillis()
                        val pct = (frac * 100f).coerceIn(1f, 100f)
                        briPct = pct
                        wled.coalescedCommand("home-bri", obj("bri" to wledByte(pct), "on" to true))
                        if (!wledUi.online) {
                            model.coalescedLighting(
                                roomLighting.copyJson()
                                    .put("brightness", pct.roundToInt())
                                    .put("power", true)
                            )
                        }
                    },
                    onValueChangeFinished = {
                        lastBriEditMs = System.currentTimeMillis()
                        model.coalescedLighting(
                            roomLighting.copyJson()
                                .put("brightness", briPct.roundToInt())
                                .put("power", true)
                        )
                    },
                    trackBrush = Brush.horizontalGradient(
                        listOf(
                            WledCatalogDefaults.luminousLedColor(primaryColor, 0.25f),
                            WledCatalogDefaults.luminousLedColor(primaryColor, 0.68f),
                            WledCatalogDefaults.luminousLedColor(primaryColor, 1f)
                        )
                    ),
                    contentDescription = "Brightness",
                    enabled = isPowerOn,
                    thumbColor = luminousAccent,
                    modifier = Modifier.testTag("home_brightness_slider")
                )

                // Equal-width single-line quick brightness pills on the same horizontal plane
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(15, 40, 70, 100).forEach { presetPct ->
                        val active = isPowerOn && kotlin.math.abs(briPct.roundToInt() - presetPct) <= 3
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) Lime.copy(alpha = 0.18f) else Ink)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) Lime else Line,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .livelyClick(enabled = isPowerOn, pressedScale = 0.93f) {
                                    lastBriEditMs = System.currentTimeMillis()
                                    briPct = presetPct.toFloat()
                                    wled.coalescedCommand("home-bri", obj("bri" to wledByte(presetPct.toFloat()), "on" to true))
                                    model.coalescedLighting(roomLighting.copyJson().put("brightness", presetPct).put("power", true))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$presetPct%",
                                fontSize = 12.sp,
                                color = if (active) Lime else Cream,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Line)

        // B. MUSIC VOLUME & LOCAL NETWORK SPOTIFY CONTROL
        val isPlaying = spotify.optBoolean("playing", false)
        val trackName = spotify.optString("track").ifBlank { "Room Soundtrack" }
        val artistName = spotify.optString("artist").ifBlank { "Spotify & Speaker Volume" }
        val volumeTarget = ui.state.optJSONObject("system")?.optString("volume_target", "spotify") ?: "spotify"

        fun triggerSpotifyAction(act: String) {
            if (model.directDevice || ui.demo) {
                model.spotifyCommand(act)
            } else {
                model.mutate("POST", "/spotify/playback", obj("action" to act), "Spotify $act")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .livelyClick(pressedScale = 0.96f) { navigate("Music") }
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (musicControlsEnabled) Lime.copy(alpha = 0.14f) else Line)
                            .border(1.dp, if (musicControlsEnabled) Lime.copy(alpha = 0.4f) else Line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.VolumeUp,
                            null,
                            tint = if (musicControlsEnabled) Lime else Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Music Volume",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (musicControlsEnabled) Cream else Muted,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "$trackName · $artistName",
                            fontSize = 11.sp,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = if (musicControlsEnabled) "${volPct.roundToInt()}%" else "MUTED",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (musicControlsEnabled) Lime else Muted,
                    maxLines = 1,
                    softWrap = false
                )
                LiquidToggle(
                    checked = musicControlsEnabled,
                    onCheckedChange = { enabledNow ->
                        musicControlsEnabled = enabledNow
                        if (!enabledNow && isPlaying) {
                            triggerSpotifyAction("pause")
                        }
                    },
                    label = "Music control active",
                    modifier = Modifier.testTag("home_music_power_toggle")
                )
            }

            // Disabled when Music Control toggle is OFF
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (musicControlsEnabled) 1f else 0.42f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Local Network Spotify Transport Row (Prev / Play-Pause / Next)
                val view = androidx.compose.ui.platform.LocalView.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            performSubtleHaptic(view)
                            triggerSpotifyAction("previous")
                        },
                        enabled = isMusicInteractive,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("home_music_prev"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Prev", fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                    FilledTonalButton(
                        onClick = {
                            performSubtleHaptic(view, heavy = true)
                            triggerSpotifyAction(if (isPlaying) "pause" else "play")
                        },
                        enabled = isMusicInteractive,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(36.dp)
                            .testTag("home_music_play_pause"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Lime.copy(alpha = 0.2f),
                            contentColor = Lime
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                            contentDescription = if (isPlaying) "Pause music" else "Play music",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) "Pause" else "Play",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            performSubtleHaptic(view)
                            triggerSpotifyAction("next")
                        },
                        enabled = isMusicInteractive,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("home_music_next"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Next", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.SkipNext, contentDescription = "Next track", modifier = Modifier.size(16.dp))
                    }
                }

                // 44-Bar Equalizer Wave Slider (Slider05) for Music Volume
                Slider05(
                    value = volPct,
                    onValueChange = { nextVol ->
                        lastVolEditMs = System.currentTimeMillis()
                        volPct = nextVol
                        model.coalescedVolume(nextVol.roundToInt())
                    },
                    onValueChangeFinished = {
                        lastVolEditMs = System.currentTimeMillis()
                    },
                    enabled = isMusicInteractive,
                    label = "Spotify volume",
                    accentColor = Lime,
                    modifier = Modifier.testTag("home_music_volume_slider")
                )

                // Equal-width single-line quick volume pills on the exact same horizontal plane (fixes Vol 100% wrapping!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(25, 50, 75, 100).forEach { presetVol ->
                        val active = musicControlsEnabled && kotlin.math.abs(volPct.roundToInt() - presetVol) <= 3
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) Lime.copy(alpha = 0.18f) else Ink)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) Lime else Line,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .livelyClick(enabled = isMusicInteractive, pressedScale = 0.93f) {
                                    lastVolEditMs = System.currentTimeMillis()
                                    volPct = presetVol.toFloat()
                                    model.coalescedVolume(presetVol)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Vol $presetVol%",
                                fontSize = 11.sp,
                                color = if (active) Lime else Cream,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                if (volumeTarget == "speaker_ir") {
                    SmallLabel("IR SPEAKER ACTIVE")
                }

                HorizontalDivider(color = Line.copy(alpha = 0.7f))

                // Device / Speaker Hardware Volume Control (via IR) directly on Home screen
                val systemObj = ui.state.optJSONObject("system") ?: obj()
                val speakerVol = systemObj.optInt("speaker_volume_percent", ui.state.optInt("speaker_volume_percent", 60)).toFloat().coerceIn(0f, 100f)
                val speakerMuted = systemObj.optBoolean("speaker_muted", ui.state.optBoolean("speaker_muted", false))
                val activeDeviceName = spotify.optString("device").ifBlank { "Device Speaker" }
                SpeakerIrVolumeControl(
                    value = speakerVol,
                    onChange = { nextSpkVol ->
                        model.coalescedSpeakerIrVolume(nextSpkVol.roundToInt())
                    },
                    onIrStep = { kind ->
                        model.triggerSpeakerIrCommand(kind)
                    },
                    enabled = isMusicInteractive,
                    initialMuted = speakerMuted,
                    deviceName = activeDeviceName
                )
            }
        }
    }
}

@Composable
private fun HomeColorCorrectingCard(
    model: RoomModel,
    ui: RoomUi,
    wled: WledModel,
    wledUi: WledUi,
    navigate: (String) -> Unit
) {
    val snap = wledUi.effectiveSnapshot
    val isPowerOn = snap.on
    val primarySeg = snap.primarySegment
    // Update all segments from Home Color Studio so the entire strip stays uniform
    val allSegIds = snap.segments.map { it.id }.ifEmpty { listOf(0) }
    val roomLighting = ui.state.optJSONObject("lighting") ?: obj()
    val currentHex = primarySeg.colorHex(0)

    val initialHsv = remember(currentHex) {
        FloatArray(3).also {
            val parsed = runCatching { android.graphics.Color.parseColor(currentHex) }.getOrDefault(android.graphics.Color.GREEN)
            android.graphics.Color.colorToHSV(parsed, it)
        }
    }
    var hue by remember { mutableFloatStateOf(if (initialHsv[1] > 0.02f) initialHsv[0] else 95f) }
    var saturation by remember { mutableFloatStateOf(if (initialHsv[1] > 0.02f) initialHsv[1] else 0.75f) }
    var cctVal by remember { mutableFloatStateOf(primarySeg.cct.toFloat().coerceIn(0f, 255f)) }
    var lastColorEditMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(currentHex) {
        if (System.currentTimeMillis() - lastColorEditMs > 750L) {
            if (initialHsv[1] > 0.02f) hue = initialHsv[0]
            saturation = initialHsv[1]
        }
    }
    LaunchedEffect(primarySeg.cct) {
        if (System.currentTimeMillis() - lastColorEditMs > 750L) {
            cctVal = primarySeg.cct.toFloat().coerceIn(0f, 255f)
        }
    }

    fun applyCalibratedColor(h: Float, s: Float, commitRoomState: Boolean = false) {
        lastColorEditMs = System.currentTimeMillis()
        hue = h.coerceIn(0f, 360f)
        saturation = s.coerceIn(0f, 1f)
        val rgbInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
        val r = (rgbInt shr 16) and 0xFF
        val g = (rgbInt shr 8) and 0xFF
        val b = rgbInt and 0xFF
        val hex = String.format("#%02X%02X%02X", r, g, b)
        val colJson = JSONArray()
            .put(JSONArray(listOf(r, g, b, primarySeg.whiteChannel(0))))
            .put(JSONArray(primarySeg.colors.getOrElse(1) { listOf(0, 0, 0, 0) }))
            .put(JSONArray(primarySeg.colors.getOrElse(2) { listOf(0, 0, 0, 0) }))
        wled.updateSegments(allSegIds, obj("col" to colJson, "on" to true), coalesceKey = "home-col-correct")
        if (commitRoomState || !wledUi.online) {
            model.coalescedLighting(roomLighting.copyJson().put("color", hex).put("power", true))
        }
    }

    val activePureColor = Color.hsv(hue, saturation, 1f)
    val activeHex = remember(hue, saturation) {
        val rgbInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
        String.format("#%06X", 0xFFFFFF and rgbInt)
    }

    RoomCard(modifier = Modifier.testTag("home_color_correction_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPowerOn) WledCatalogDefaults.luminousLedColor(activePureColor, 0.95f)
                                else Line
                            )
                            .border(1.5.dp, if (isPowerOn) Color.White else Muted, CircleShape)
                    )
                    Text(
                        text = "Color Studio",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cream,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Ink)
                            .border(1.dp, Line, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isPowerOn) activeHex else "OFF",
                            color = if (isPowerOn) Lime else Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                val openStudioInteraction = remember { MutableInteractionSource() }
                AssistChip(
                    onClick = {
                        wled.setActiveTab("Colors")
                        navigate("Lights")
                    },
                    interactionSource = openStudioInteraction,
                    modifier = Modifier
                        .livelyPressScale(openStudioInteraction)
                        .testTag("home_open_lights_studio"),
                    leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(14.dp)) },
                    label = { Text("Studio", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isPowerOn) 1f else 0.42f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Chromatic Hue Spectrum Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chromatic Hue", color = Muted, fontSize = 12.sp)
                    Text("${hue.roundToInt()}°", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                LedSlider(
                    value = (hue / 360f).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        applyCalibratedColor(frac * 360f, saturation.coerceAtLeast(0.15f), commitRoomState = false)
                    },
                    onValueChangeFinished = {
                        lastColorEditMs = System.currentTimeMillis()
                        applyCalibratedColor(hue, saturation.coerceAtLeast(0.15f), commitRoomState = true)
                    },
                    trackBrush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF1744),
                            Color(0xFFFF9100),
                            Color(0xFFFFEA00),
                            Color(0xFF00E676),
                            Color(0xFF00E5FF),
                            Color(0xFF2979FF),
                            Color(0xFFD500F9),
                            Color(0xFFFF1744)
                        )
                    ),
                    contentDescription = "Chromatic hue correction",
                    enabled = isPowerOn,
                    thumbColor = Color.hsv(hue, 1f, 1f),
                    modifier = Modifier.testTag("home_color_hue_slider")
                )
            }

            // 2. Warm <-> Cool White Balance (Kelvin CCT Correction)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val kelvinApprox = (2700 + (cctVal / 255f) * 3800).roundToInt()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("White Balance (Warm ↔ Cool CCT)", color = Muted, fontSize = 12.sp)
                    Text("${kelvinApprox}K", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                val cctFrac = (cctVal / 255f).coerceIn(0f, 1f)
                LedSlider(
                    value = cctFrac,
                    onValueChange = { frac ->
                        lastColorEditMs = System.currentTimeMillis()
                        val nextCct = (frac * 255f).coerceIn(0f, 255f)
                        cctVal = nextCct
                        wled.updateSegments(allSegIds, obj("cct" to nextCct.roundToInt(), "on" to true), coalesceKey = "home-cct")
                        if (!wledUi.online) {
                            val cctHue = if (frac < 0.5f) 32f else 200f
                            val cctSat = kotlin.math.abs(frac - 0.5f) * 0.7f
                            val rgbInt = android.graphics.Color.HSVToColor(floatArrayOf(cctHue, cctSat, 1f))
                            val hex = String.format("#%06X", 0xFFFFFF and rgbInt)
                            model.coalescedLighting(roomLighting.copyJson().put("color", hex).put("power", true))
                        }
                    },
                    onValueChangeFinished = {
                        lastColorEditMs = System.currentTimeMillis()
                        val frac = (cctVal / 255f).coerceIn(0f, 1f)
                        val cctHue = if (frac < 0.5f) 32f else 200f
                        val cctSat = kotlin.math.abs(frac - 0.5f) * 0.7f
                        val rgbInt = android.graphics.Color.HSVToColor(floatArrayOf(cctHue, cctSat, 1f))
                        val hex = String.format("#%06X", 0xFFFFFF and rgbInt)
                        model.coalescedLighting(roomLighting.copyJson().put("color", hex).put("power", true))
                    },
                    trackBrush = Brush.horizontalGradient(
                        listOf(Color(0xFFFFB74D), Color(0xFFFFF3E0), Color(0xFF80D8FF))
                    ),
                    contentDescription = "White balance color temperature correction",
                    enabled = isPowerOn,
                    thumbColor = if (cctFrac < 0.5f) Color(0xFFFFCA7A) else Color(0xFFB3E5FC),
                    modifier = Modifier.testTag("home_color_cct_slider")
                )
            }

            // 3. Saturation / Tint Purity Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Color Purity / Saturation", color = Muted, fontSize = 12.sp)
                    Text("${(saturation * 100).roundToInt()}%", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                LedSlider(
                    value = saturation.coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        applyCalibratedColor(hue, frac, commitRoomState = false)
                    },
                    onValueChangeFinished = {
                        lastColorEditMs = System.currentTimeMillis()
                        applyCalibratedColor(hue, saturation, commitRoomState = true)
                    },
                    trackBrush = Brush.horizontalGradient(
                        listOf(Color(0xFFFFF6E5), Color.hsv(hue, 0.5f, 1f), Color.hsv(hue, 1f, 1f))
                    ),
                    contentDescription = "Color saturation correction",
                    enabled = isPowerOn,
                    thumbColor = activePureColor,
                    modifier = Modifier.testTag("home_color_saturation_slider")
                )
            }

            // 4. 6 Calibrated Reference Swatches
            val calibratedSwatches = remember {
                listOf(
                    Triple("2700K Warm", "#FFD494", 0),
                    Triple("4500K Pure", "#FFF3E0", 127),
                    Triple("6500K Cool", "#E0F2FE", 255),
                    Triple("Amber Glow", "#FF8800", 30),
                    Triple("Cinema Teal", "#00E5FF", 200),
                    Triple("Studio Lime", "#B5F36A", 127)
                )
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                calibratedSwatches.forEach { (label, hex, cctTarget) ->
                    val isSelected = isPowerOn && activeHex.equals(hex, ignoreCase = true)
                    val swatchColor = WledCatalogDefaults.luminousLedColor(parseColor(hex), 0.95f)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Lime.copy(alpha = 0.18f) else Ink)
                            .border(1.dp, if (isSelected) Lime else Line, RoundedCornerShape(12.dp))
                            .livelyClick(enabled = isPowerOn, pressedScale = 0.92f) {
                                lastColorEditMs = System.currentTimeMillis()
                                cctVal = cctTarget.toFloat()
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
                                applyCalibratedColor(hsv[0], hsv[1])
                                wled.updateSegments(allSegIds, obj("cct" to cctTarget), coalesceKey = "home-cct")
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                        )
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = if (isSelected) Lime else Cream,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private data class HomeOrganizedPresetItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val isFavorite: Boolean,
    val isActive: Boolean,
    val isPlaylist: Boolean,
    val isRoomScene: Boolean,
    val accentColor: Color,
    val onApply: () -> Unit,
    val onToggleFavorite: () -> Unit
)

@Composable
private fun HomePresetCategorySection(
    model: RoomModel,
    ui: RoomUi,
    wled: WledModel,
    wledUi: WledUi,
    navigate: (String) -> Unit
) {
    val snap = wledUi.effectiveSnapshot
    val selectedCategory = wledUi.homePresetCategory
    val defaultAccent = Lime
    var categorizingItem by remember { mutableStateOf<HomeOrganizedPresetItem?>(null) }
    val allSegIds = snap.segments.map { it.id }.ifEmpty { listOf(0) }
    val lastFavKey = wledUi.lastFavoritedKey ?: ui.lastFavoritedKey

    val wledItems = remember(wledUi.presetEntries, wledUi.presetCategories, wledUi.favoritePresets, snap.activePreset, snap.activePlaylist, defaultAccent) {
        wledUi.presetEntries.map { entry ->
            val cat = wledUi.categoryForWledPreset(entry)
            val isFav = entry.id in wledUi.favoritePresets
            val isActive = snap.activePreset == entry.id || snap.activePlaylist == entry.id
            val rawSeg = entry.raw.optJSONArray("seg")?.optJSONObject(0)
            val colArr = rawSeg?.optJSONArray("col")?.optJSONArray(0)
            val swatch = if (colArr != null && colArr.length() >= 3) {
                WledCatalogDefaults.luminousLedColor(
                    Color(
                        colArr.optInt(0, 181).coerceIn(0, 255),
                        colArr.optInt(1, 243).coerceIn(0, 255),
                        colArr.optInt(2, 106).coerceIn(0, 255)
                    ),
                    0.95f
                )
            } else defaultAccent
            val badgePrefix = if (entry.quickLabel.isNotBlank()) "${entry.quickLabel} · " else ""
            val sub = buildString {
                append(if (entry.isPlaylist) "Playlist #${entry.id}" else "WLED #${entry.id}")
                entry.bri?.let { append(" · ${wledPercent(it)}%") }
                append(" · $cat")
            }
            HomeOrganizedPresetItem(
                key = "ps:${entry.id}",
                title = "$badgePrefix${entry.name}",
                subtitle = sub,
                category = cat,
                isFavorite = isFav,
                isActive = isActive,
                isPlaylist = entry.isPlaylist,
                isRoomScene = false,
                accentColor = swatch,
                onApply = { wled.command(obj("ps" to entry.id), successNotice = "Applied ${entry.name}") },
                onToggleFavorite = { wled.toggleFavoritePreset(entry.id, jumpToHomeOnAdd = false) }
            )
        }
    }

    val roomPresetsJson = ui.state.optJSONArray("presets")?.toString().orEmpty()
    val roomItems = remember(roomPresetsJson, wledUi.presetCategories, ui.favoriteRoomPresets) {
        ui.state.items("presets").map { preset ->
            val id = preset.optString("id")
            val name = preset.optString("name", "Scene")
            val lighting = preset.optJSONObject("lighting") ?: obj()
            val cat = wledUi.categoryForRoomPreset(preset)
            val isFav = id in ui.favoriteRoomPresets
            val colorHex = lighting.optString("color", "#B5F36A")
            val bri = lighting.optInt("brightness", 75)
            val effect = lighting.optString("effect", "solid")
            HomeOrganizedPresetItem(
                key = "room_preset:$id",
                title = name,
                subtitle = "Room Scene · $bri% · $effect · $cat",
                category = cat,
                isFavorite = isFav,
                isActive = false,
                isPlaylist = false,
                isRoomScene = true,
                accentColor = WledCatalogDefaults.luminousLedColor(parseColor(colorHex), 0.95f),
                onApply = {
                    val linkedWledId = lighting.optInt("wled_preset_id", 0)
                    if (linkedWledId > 0) {
                        wled.command(obj("ps" to linkedWledId), successNotice = "Applied $name (#$linkedWledId)")
                    }
                    model.mutate("POST", "/actions", action("lighting", lighting), "Applied $name")
                },
                onToggleFavorite = { model.toggleFavoriteRoomPreset(id) }
            )
        }
    }

    // Also surface any favorited Effects, Sound Modes, Palettes, and Colors in Quick Presets & Category tabs!
    val limeColor = Lime
    val favEffectAndPaletteItems = remember(
        wledUi.favoriteEffects,
        ui.favoriteSoundModes,
        wledUi.favoritePalettes,
        wledUi.favoriteColors,
        wledUi.presetCategories,
        snap.primarySegment.fx,
        snap.primarySegment.pal,
        snap.primarySegment.colors,
        limeColor
    ) {
        buildList {
            (wledUi.favoriteEffects + ui.favoriteSoundModes).distinct().sorted().forEach { fxId ->
                val fxName = wledUi.effectiveEffects.getOrNull(fxId) ?: return@forEach
                if (fxName == "RSVD" || fxName == "-") return@forEach
                val key = "fx:$fxId"
                val defaultCat = if (fxName.contains("♪") || fxName.contains("♫") || fxId in ui.favoriteSoundModes) {
                    PresetCategories.MUSIC_SOUND
                } else {
                    PresetCategories.VIBRANT_FX
                }
                val cat = wledUi.presetCategories[key] ?: defaultCat
                add(
                    HomeOrganizedPresetItem(
                        key = key,
                        title = fxName,
                        subtitle = "Effect #$fxId · $cat",
                        category = cat,
                        isFavorite = true,
                        isActive = snap.primarySegment.fx == fxId,
                        isPlaylist = false,
                        isRoomScene = false,
                        accentColor = limeColor,
                        onApply = { wled.updateSegments(allSegIds, obj("fx" to fxId, "on" to true)) },
                        onToggleFavorite = {
                            if (fxId in wledUi.favoriteEffects) wled.toggleFavoriteEffect(fxId, jumpToHomeOnAdd = false)
                            if (fxId in ui.favoriteSoundModes) model.toggleFavoriteSoundMode(fxId)
                        }
                    )
                )
            }
            wledUi.favoritePalettes.sorted().forEach { palId ->
                val palName = wledUi.effectivePalettes.getOrNull(palId) ?: return@forEach
                val key = "pal:$palId"
                val cat = wledUi.presetCategories[key] ?: PresetCategories.AMBIENT
                val stops = WledCatalogDefaults.palettePreviewColors(
                    palId,
                    parseColor(snap.primarySegment.colorHex(0)),
                    parseColor(snap.primarySegment.colorHex(1)),
                    parseColor(snap.primarySegment.colorHex(2))
                )
                add(
                    HomeOrganizedPresetItem(
                        key = key,
                        title = palName,
                        subtitle = "Palette #$palId · $cat",
                        category = cat,
                        isFavorite = true,
                        isActive = snap.primarySegment.pal == palId,
                        isPlaylist = false,
                        isRoomScene = false,
                        accentColor = stops.firstOrNull() ?: limeColor,
                        onApply = { wled.updateSegments(allSegIds, obj("pal" to palId, "on" to true)) },
                        onToggleFavorite = { wled.toggleFavoritePalette(palId, jumpToHomeOnAdd = false) }
                    )
                )
            }
            wledUi.favoriteColors.forEach { hex ->
                val norm = hex.uppercase()
                val key = "col:$norm"
                val cat = wledUi.presetCategories[key] ?: PresetCategories.AMBIENT
                add(
                    HomeOrganizedPresetItem(
                        key = key,
                        title = "Colour $norm",
                        subtitle = "Saved Swatch · $cat",
                        category = cat,
                        isFavorite = true,
                        isActive = snap.primarySegment.colorHex(0).equals(norm, ignoreCase = true),
                        isPlaylist = false,
                        isRoomScene = false,
                        accentColor = WledCatalogDefaults.luminousLedColor(parseColor(norm), 0.95f),
                        onApply = {
                            val clean = norm.removePrefix("#")
                            if (clean.length == 6) {
                                val r = clean.substring(0, 2).toInt(16)
                                val g = clean.substring(2, 4).toInt(16)
                                val b = clean.substring(4, 6).toInt(16)
                                val colJson = JSONArray()
                                    .put(JSONArray(listOf(r, g, b, 0)))
                                    .put(JSONArray(snap.primarySegment.colors.getOrElse(1) { listOf(0, 0, 0, 0) }))
                                    .put(JSONArray(snap.primarySegment.colors.getOrElse(2) { listOf(0, 0, 0, 0) }))
                                wled.updateSegments(allSegIds, obj("col" to colJson, "on" to true))
                            }
                        },
                        onToggleFavorite = { wled.toggleFavoriteColor(norm, jumpToHomeOnAdd = false) }
                    )
                )
            }
        }
    }

    val allItems = remember(wledItems, roomItems, favEffectAndPaletteItems) {
        wledItems + roomItems + favEffectAndPaletteItems
    }
    val filteredItems = remember(allItems, selectedCategory, lastFavKey) {
        allItems.filter { item ->
            PresetCategories.matchesFilter(
                filterCategory = selectedCategory,
                assignedCategory = item.category,
                isFavorite = item.isFavorite,
                isPlaylist = item.isPlaylist,
                isRoomScene = item.isRoomScene
            )
        }.sortedWith(
            compareByDescending<HomeOrganizedPresetItem> { it.key == lastFavKey }
                .thenByDescending { it.isFavorite }
                .thenByDescending { it.isActive }
        )
    }

    RoomCard(modifier = Modifier.testTag("home_preset_category_section")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Quick Presets",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Cream,
                    maxLines = 1,
                    softWrap = false
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink)
                        .border(1.dp, Line, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${filteredItems.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Lime,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            val manageInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = {
                    wled.setActiveTab("Presets")
                    navigate("Lights")
                },
                interactionSource = manageInteraction,
                modifier = Modifier
                    .livelyPressScale(manageInteraction)
                    .testTag("home_manage_presets_button"),
                leadingIcon = { Icon(Icons.Outlined.Bookmarks, null, Modifier.size(14.dp)) },
                label = { Text("All (${allItems.size})", fontSize = 11.sp, maxLines = 1, softWrap = false) }
            )
        }

        // Single-row horizontally scrollable Category Selector Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PresetCategories.FILTER_CHIPS.forEach { category ->
                val count = allItems.count { item ->
                    PresetCategories.matchesFilter(
                        filterCategory = category,
                        assignedCategory = item.category,
                        isFavorite = item.isFavorite,
                        isPlaylist = item.isPlaylist,
                        isRoomScene = item.isRoomScene
                    )
                }
                val isSelected = selectedCategory == category
                val chipInteraction = remember { MutableInteractionSource() }
                val tagSlug = category.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        wled.setHomePresetCategory(category)
                    },
                    interactionSource = chipInteraction,
                    modifier = Modifier
                        .livelyPressScale(chipInteraction, pressedScale = 0.92f)
                        .testTag("home_preset_category_$tagSlug"),
                    leadingIcon = {
                        Icon(
                            imageVector = presetCategoryIcon(category),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "$category ($count)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }
        }

        if (filteredItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.copy(alpha = 0.45f))
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "No presets in '$selectedCategory' yet.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Cream
                )
                Text(
                    text = "Tap 'All' or assign a preset's category tag to '$selectedCategory'.",
                    fontSize = 11.sp,
                    color = Muted
                )
            }
        } else {
            // Smoothly scrollable preset list (no page buttons needed!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 310.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredItems.forEach { item ->
                    val rowBg by animateColorAsState(
                        if (item.isActive) Lime.copy(alpha = 0.15f) else Ink.copy(alpha = 0.55f),
                        label = "homePresetRowBg"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(rowBg)
                            .border(
                                width = if (item.isActive) 1.5.dp else 1.dp,
                                color = if (item.isActive) Lime else Line,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .livelyClick(pressedScale = 0.97f, onClick = item.onApply)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("home_preset_card_${item.key.replace(':', '_')}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(item.accentColor)
                                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.isPlaylist) {
                                Icon(Icons.Outlined.PlaylistPlay, null, tint = Ink, modifier = Modifier.size(13.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (item.isActive) "● ${item.title}" else item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.isActive) Lime else Cream,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle,
                                fontSize = 11.sp,
                                color = Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Panel)
                                .border(1.dp, Line, RoundedCornerShape(8.dp))
                                .livelyClick(pressedScale = 0.9f) { categorizingItem = item }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("home_preset_tag_${item.key.replace(':', '_')}")
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 10.sp,
                                color = Lime,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = {
                                val totalFavs = allItems.count { it.isFavorite }
                                if (!item.isFavorite && totalFavs >= MAX_HOME_PRESETS) {
                                    wled.showFavoriteLimitWarning(
                                        "Favorite limit reached ($MAX_HOME_PRESETS/$MAX_HOME_PRESETS). Please remove an existing favorite first before adding '${item.title}'."
                                    )
                                } else {
                                    item.onToggleFavorite()
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("home_preset_fav_${item.key.replace(':', '_')}")
                        ) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (item.isFavorite) "Remove ${item.title} from favorites" else "Favorite ${item.title}",
                                tint = if (item.isFavorite) Lime else Muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    categorizingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { categorizingItem = null },
            title = { Text("Categorize '${item.title}'") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose which Home category this preset belongs to. Your choice is saved locally in DataStore across restarts.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (PresetCategories.ASSIGNABLE + "Auto").forEach { catOption ->
                            val isCurrent = (catOption == "Auto" && item.key !in wledUi.presetCategories) ||
                                wledUi.presetCategories[item.key] == catOption
                            FilterChip(
                                selected = isCurrent,
                                onClick = {
                                    wled.setPresetCategory(item.key, catOption)
                                    categorizingItem = null
                                },
                                leadingIcon = {
                                    Icon(presetCategoryIcon(catOption), null, Modifier.size(15.dp))
                                },
                                label = { Text(catOption, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { categorizingItem = null }) {
                    Text("Done")
                }
            }
        )
    }
}

private fun presetCategoryIcon(category: String): ImageVector = when (category) {
    PresetCategories.ALL -> Icons.Outlined.Apps
    PresetCategories.FAVORITES -> Icons.Filled.Star
    PresetCategories.AMBIENT -> Icons.Outlined.WbTwilight
    PresetCategories.VIBRANT_FX -> Icons.Outlined.AutoAwesome
    PresetCategories.MUSIC_SOUND -> Icons.Outlined.GraphicEq
    PresetCategories.FOCUS_WORK -> Icons.Outlined.LightMode
    PresetCategories.PLAYLISTS -> Icons.Outlined.PlaylistPlay
    PresetCategories.ROOM_SCENES -> Icons.Outlined.Home
    else -> Icons.Outlined.Label
}

private data class HomePinnedFavoriteEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val active: Boolean,
    val swatchColor: Color? = null,
    val isIrCommand: Boolean = false,
    val onClick: () -> Unit,
    val onUnfavorite: () -> Unit
)

@Composable
private fun HomeUniversalFavoritesSection(
    model: RoomModel,
    ui: RoomUi,
    wled: WledModel,
    wledUi: WledUi,
    navigate: (String) -> Unit
) {
    val snap = wledUi.effectiveSnapshot
    val selectedSegIds = snap.selectedSegments.map { it.id }.ifEmpty { listOf(0) }
    val lastFavKey = wledUi.lastFavoritedKey ?: ui.lastFavoritedKey

    val favIrCommands = ui.state.items("commands").filter { it.optBoolean("favorite") }
    val favWledPresets = wledUi.presetEntries.filter { it.id in wledUi.favoritePresets }
    val favRoomPresets = ui.state.items("presets").filter { it.optString("id") in ui.favoriteRoomPresets }
    val favWledEffects = wledUi.favoriteEffects.sorted().mapNotNull { fxId ->
        val name = wledUi.effectiveEffects.getOrNull(fxId) ?: return@mapNotNull null
        if (name == "RSVD" || name == "-") null else fxId to name
    }
    val favSoundModes = ui.favoriteSoundModes.sorted().mapNotNull { fxId ->
        val name = wledUi.effectiveEffects.getOrNull(fxId) ?: return@mapNotNull null
        fxId to name
    }
    val favWledPalettes = wledUi.favoritePalettes.sorted().mapNotNull { palId ->
        val name = wledUi.effectivePalettes.getOrNull(palId) ?: return@mapNotNull null
        palId to name
    }
    val favColors = wledUi.favoriteColors.toList()
    val favRoutines = ui.state.items("rules").filter { it.optString("id") in ui.favoriteRoutines }

    val allEntries = buildList {
        favIrCommands.forEach { command ->
            val cmdId = command.optString("id")
            val name = command.optString("name", "Remote")
            add(
                HomePinnedFavoriteEntry(
                    key = "ir:$cmdId",
                    title = name,
                    subtitle = "IR Remote",
                    icon = Icons.Outlined.SettingsRemote,
                    active = lastFavKey == "ir:$cmdId",
                    isIrCommand = true,
                    onClick = {
                        model.mutate(
                            "POST",
                            "/actions",
                            obj("type" to "ir", "command_id" to cmdId),
                            "IR transmitted; appliance state unconfirmed"
                        )
                    },
                    onUnfavorite = {
                        model.mutate("PUT", "/commands/$cmdId", command.copyJson().put("favorite", false), "Removed from favorites")
                    }
                )
            )
        }
        favRoomPresets.forEach { preset ->
            val id = preset.optString("id")
            val name = preset.optString("name")
            val lighting = preset.optJSONObject("lighting") ?: obj()
            val colorHex = lighting.optString("color", "#B5F36A")
            add(
                HomePinnedFavoriteEntry(
                    key = "room_preset:$id",
                    title = name,
                    subtitle = "Room Scene · ${lighting.optInt("brightness", 75)}%",
                    icon = Icons.Outlined.Lightbulb,
                    active = lastFavKey == "room_preset:$id",
                    swatchColor = WledCatalogDefaults.luminousLedColor(parseColor(colorHex), 0.9f),
                    onClick = { model.mutate("POST", "/actions", action("lighting", lighting), "Applied $name") },
                    onUnfavorite = { model.toggleFavoriteRoomPreset(id) }
                )
            )
        }
        favWledPresets.forEach { p ->
            val isActive = snap.activePreset == p.id || snap.activePlaylist == p.id
            add(
                HomePinnedFavoriteEntry(
                    key = "ps:${p.id}",
                    title = p.name,
                    subtitle = if (p.isPlaylist) "Playlist #${p.id}" else "Preset #${p.id}",
                    icon = if (p.isPlaylist) Icons.Outlined.PlaylistPlay else Icons.Outlined.Bookmarks,
                    active = isActive || lastFavKey == "ps:${p.id}",
                    onClick = { wled.command(obj("ps" to p.id), successNotice = "Applied ${p.name}") },
                    onUnfavorite = { wled.toggleFavoritePreset(p.id) }
                )
            )
        }
        favWledEffects.forEach { (fxId, fxName) ->
            val isActive = snap.primarySegment.fx == fxId
            add(
                HomePinnedFavoriteEntry(
                    key = "fx:$fxId",
                    title = fxName,
                    subtitle = "FX #$fxId",
                    icon = Icons.Outlined.AutoAwesome,
                    active = isActive || lastFavKey == "fx:$fxId",
                    onClick = { wled.updateSegments(selectedSegIds, obj("fx" to fxId, "on" to true)) },
                    onUnfavorite = { wled.toggleFavoriteEffect(fxId) }
                )
            )
        }
        favSoundModes.filter { (id, _) -> id !in wledUi.favoriteEffects }.forEach { (fxId, fxName) ->
            val isActive = snap.primarySegment.fx == fxId
            add(
                HomePinnedFavoriteEntry(
                    key = "sound:$fxId",
                    title = fxName,
                    subtitle = "♫ Sound FX #$fxId",
                    icon = Icons.Outlined.GraphicEq,
                    active = isActive || lastFavKey == "sound:$fxId",
                    onClick = { wled.updateSegments(selectedSegIds, obj("fx" to fxId, "on" to true)) },
                    onUnfavorite = { model.toggleFavoriteSoundMode(fxId) }
                )
            )
        }
        favWledPalettes.forEach { (palId, palName) ->
            val isActive = snap.primarySegment.pal == palId
            val stops = WledCatalogDefaults.palettePreviewColors(
                palId,
                parseColor(snap.primarySegment.colorHex(0)),
                parseColor(snap.primarySegment.colorHex(1)),
                parseColor(snap.primarySegment.colorHex(2))
            )
            add(
                HomePinnedFavoriteEntry(
                    key = "pal:$palId",
                    title = palName,
                    subtitle = "Palette #$palId",
                    icon = Icons.Outlined.ColorLens,
                    active = isActive || lastFavKey == "pal:$palId",
                    swatchColor = stops.firstOrNull(),
                    onClick = { wled.updateSegments(selectedSegIds, obj("pal" to palId, "on" to true)) },
                    onUnfavorite = { wled.toggleFavoritePalette(palId) }
                )
            )
        }
        favColors.forEach { hex ->
            val c = WledCatalogDefaults.luminousLedColor(parseColor(hex), 0.95f)
            val isActive = snap.primarySegment.colorHex(0).equals(hex, ignoreCase = true)
            add(
                HomePinnedFavoriteEntry(
                    key = "col:${hex.uppercase()}",
                    title = hex.uppercase(),
                    subtitle = "LED Colour",
                    icon = Icons.Outlined.Palette,
                    active = isActive || lastFavKey == "col:${hex.uppercase()}",
                    swatchColor = c,
                    onClick = {
                        val clean = hex.removePrefix("#")
                        if (clean.length == 6) {
                            val r = clean.substring(0, 2).toInt(16)
                            val g = clean.substring(2, 4).toInt(16)
                            val b = clean.substring(4, 6).toInt(16)
                            val colJson = JSONArray()
                                .put(JSONArray(listOf(r, g, b, 0)))
                                .put(JSONArray(snap.primarySegment.colors.getOrElse(1) { listOf(0, 0, 0, 0) }))
                                .put(JSONArray(snap.primarySegment.colors.getOrElse(2) { listOf(0, 0, 0, 0) }))
                            wled.updateSegments(selectedSegIds, obj("col" to colJson, "on" to true))
                        }
                    },
                    onUnfavorite = { wled.toggleFavoriteColor(hex) }
                )
            )
        }
        val uniqueFavIcons = assignUniqueRoutineIcons(favRoutines)
        favRoutines.forEachIndexed { idx, rule ->
            val id = rule.optString("id")
            val ruleKey = id.ifBlank { "${rule.optString("name")}_$idx" }
            val name = rule.optString("name", "Routine")
            add(
                HomePinnedFavoriteEntry(
                    key = "routine:$id",
                    title = name,
                    subtitle = "Run routine",
                    icon = uniqueFavIcons[ruleKey] ?: routineDistinctIcon(rule, idx),
                    active = lastFavKey == "routine:$id",
                    onClick = { executeRoutine(model, ui, rule) },
                    onUnfavorite = { model.toggleFavoriteRoutine(id) }
                )
            )
        }
    }

    // Prioritize most recently favorited item first, and strictly cap at MAX_HOME_FAVORITES (4)
    val prioritizedFavorites = remember(allEntries, lastFavKey) {
        val sorted = if (lastFavKey != null) {
            val match = allEntries.filter { it.key == lastFavKey }
            val rest = allEntries.filterNot { it.key == lastFavKey }
            match + rest
        } else {
            allEntries
        }
        sorted.take(MAX_HOME_FAVORITES)
    }

    RoomCard(modifier = Modifier.testTag("home_within_reach_section")) {
        SectionLabel(
            title = "Within reach",
            trailing = "${prioritizedFavorites.size}/$MAX_HOME_FAVORITES PINNED"
        )
        if (prioritizedFavorites.isEmpty()) {
            Text(
                "Tap ★ on up to $MAX_HOME_FAVORITES favorite presets, effects, colours or IR buttons to keep them right here.",
                color = Muted,
                fontSize = 12.sp
            )
        } else {
            // Render IR remote favorites as OutlinedButtons for one-tap remote access
            val irVisible = prioritizedFavorites.filter { it.isIrCommand }
            val pillVisible = prioritizedFavorites.filterNot { it.isIrCommand }

            irVisible.forEach { irFav ->
                val interaction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = irFav.onClick,
                    enabled = !ui.busy,
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .livelyPressScale(interaction)
                ) {
                    Icon(Icons.Outlined.SettingsRemote, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(irFav.title)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp))
                }
            }

            if (pillVisible.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pillVisible.forEach { fav ->
                        HomeFavoritePill(
                            title = fav.title,
                            subtitle = fav.subtitle,
                            icon = fav.icon,
                            active = fav.active,
                            swatchColor = fav.swatchColor,
                            onClick = fav.onClick,
                            onUnfavorite = fav.onUnfavorite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFavoritePill(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    swatchColor: Color? = null,
    onClick: () -> Unit,
    onUnfavorite: () -> Unit
) {
    val bg by animateColorAsState(
        if (active) Lime.copy(alpha = 0.18f) else Ink,
        label = "favPillBg"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (active) 1.5.dp else 1.dp, if (active) Lime else Line, RoundedCornerShape(14.dp))
            .livelyClick(pressedScale = 0.93f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (swatchColor != null) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            )
        } else {
            Icon(icon, contentDescription = null, tint = if (active) Lime else Cream, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) Lime else Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Muted,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Remove $title from favorites",
            tint = Lime,
            modifier = Modifier
                .size(15.dp)
                .livelyClick(pressedScale = 0.8f, onClick = onUnfavorite)
        )
    }
}

@Composable fun EmptyCard(title: String, detail: String, icon: ImageVector) {
    RoomCard { Icon(icon,null,tint=Lime);Text(title,fontSize=18.sp);Text(detail,color=Muted,fontSize=13.sp,lineHeight=20.sp) }
}

@Composable fun LightsScreen(model: RoomModel, ui: RoomUi) {
    val current=ui.state.optJSONObject("lighting") ?: obj()
    var draft by remember(current.toString()) {mutableStateOf(current.copyJson())}
    var deleting by remember { mutableStateOf<String?>(null) }
    var creating by remember {mutableStateOf(false)}
    var editing by remember {mutableStateOf<JSONObject?>(null)}
    PageTitle("Lighting", "Find your glow.", "Fine-tune the color, energy, and feel of your room.")
    RoomCard {
        LightingEditor(draft,{draft=it})
        ActionButton("Apply lighting",Icons.Outlined.Check,!ui.busy && draft.optString("color").matches(Regex("#[0-9a-fA-F]{6}"))) {model.mutate("POST","/actions",action("lighting",draft),"Lighting applied")}
    }
    RoomCard {
        SectionLabel("Your presets")
        Text("Define exactly how the room should look, then save it for later.",color=Muted,fontSize=12.sp)
        ActionButton("Create preset",Icons.Outlined.Add,!ui.busy) {creating=true}
        ui.state.items("presets").forEach {preset ->
            val id = preset.optString("id")
            val isFav = id in ui.favoriteRoomPresets
            val value=preset.getJSONObject("lighting")
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(parseColor(value.optString("color"))))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable {draft=value.copyJson()}) {
                    Text(preset.optString("name"),fontSize=15.sp)
                    Text("${if(value.optBoolean("power")) "On" else "Off"} · ${value.optInt("brightness")}% · ${value.optString("effect")}",color=Muted,fontSize=11.sp)
                }
                IconButton(onClick={model.toggleFavoriteRoomPreset(id)}) {
                    Icon(if(isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,"Favorite ${preset.optString("name")}",tint=if(isFav) Lime else Muted)
                }
                IconButton(onClick={editing=preset}) {Icon(Icons.Outlined.Edit,"Edit ${preset.optString("name")}",Modifier.size(18.dp))}
                IconButton(onClick={deleting=id}) {Icon(Icons.Outlined.DeleteOutline,"Delete preset")}
            }
        }
    }
    if(creating || editing!=null) PresetEditor(editing,draft,{creating=false;editing=null}) {name,value->
        val id=editing?.optString("id")
        model.mutate(if(id==null)"POST" else "PUT",if(id==null)"/presets" else "/presets/$id",obj("name" to name,"lighting" to value),"Preset saved")
    }
    deleting?.let {id -> ConfirmDialog("Delete preset?","This removes this saved favorite.",{deleting=null}) {model.mutate("DELETE","/presets/$id?confirm=true")} }
}

@Composable fun SectionLabelInline(text: String) {Text(text,fontSize=18.sp,fontWeight=FontWeight.Medium)}

@Composable fun SoundScreen(model: RoomModel, ui: RoomUi) {
    val current=ui.state.optJSONObject("microphone") ?: obj()
    var draft by remember(current.toString()) {mutableStateOf(current.copyJson())}
    PageTitle("Room microphone", "In tune with you.", "Shape how the lights respond to the sound in your room.")
    RoomCard {
        SectionLabel("Live frequency bands",if(ui.demo) "PREVIEW" else "TELEMETRY")
        val bands=ui.state.optJSONObject("telemetry")?.optJSONArray("bands")
        Row(Modifier.fillMaxWidth().height(95.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Bottom) {
            listOf("Bass","Mids","Treble").forEachIndexed {i,name ->
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height((bands?.optDouble(i,0.0)?.coerceIn(0.0,1.0)?.times(65)?.toFloat() ?: 0f).dp+4.dp).clip(RoundedCornerShape(8.dp)).background(if(i==0)Lime else Lime.copy(alpha=.6f)))
                    Text(name,color=Muted,fontSize=11.sp)
                }
            }
        }
    }
    RoomCard {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("Automatic sensitivity",fontSize=15.sp);LiquidToggle(draft.optBoolean("automatic"),{draft=draft.copyJson().put("automatic",it)},label="Automatic sensitivity")}
        if(!draft.optBoolean("automatic")) SettingSlider("Sensitivity",draft.optInt("sensitivity").toFloat(),{draft=draft.copyJson().put("sensitivity",it.toInt())},1f..100f)
        SettingSlider("Noise floor",draft.optInt("noise_floor").toFloat(),{draft=draft.copyJson().put("noise_floor",it.toInt())})
        SettingSlider("Smoothing",draft.optInt("smoothing").toFloat(),{draft=draft.copyJson().put("smoothing",it.toInt())})
        SmallLabel("WHEN THE ROOM GOES QUIET")
        Choices(listOf("off","dim","hold"),draft.optString("quiet_behavior")) {draft=draft.copyJson().put("quiet_behavior",it)}
        SettingSlider("Quiet timeout",draft.optInt("quiet_seconds",10).toFloat(),{draft=draft.copyJson().put("quiet_seconds",it.toInt())},1f..300f," s")
        ActionButton("Save microphone settings",Icons.Outlined.Check,!ui.busy) {model.mutate("POST","/actions",action("microphone",draft),"Microphone settings saved")}
        OutlinedButton(onClick={model.mutate("POST","/actions",obj("type" to "calibrate"),"Calibration started")},enabled=!ui.busy) {Text("Calibrate room noise")}
    }
}

@Composable fun MoreScreen(navigate: (String)->Unit) {
    PageTitle("Make it yours", "A room that listens.", "Music, microphone tuning, and the details behind your room.")
    listOf(Triple("Music","Spotify, playlists & playback devices",Icons.Outlined.MusicNote),Triple("Sound","Room microphone & reactive tuning",Icons.Outlined.GraphicEq),Triple("System","Connections, diagnostics, backup & firmware",Icons.Outlined.Tune)).forEach {(name,detail,icon) ->
        RoomCard(Modifier.livelyClick {navigate(name)}) {
            Icon(icon,null,tint=Lime)
            Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){Text(name,fontSize=21.sp);Text(detail,color=Muted,fontSize=12.sp,lineHeight=19.sp)};Icon(Icons.Outlined.ArrowOutward,null)}
        }
    }
}
