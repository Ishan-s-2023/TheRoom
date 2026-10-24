package `in`.roomcontroller.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

private val QUICK_SWATCHES = listOf(
    "Warm" to "#FFD494",
    "Neutral" to "#FFF3E0",
    "Cold" to "#E0F2FE",
    "Amber" to "#FF9800",
    "Coral" to "#FF5252",
    "Red" to "#FF1744",
    "Magenta" to "#F50057",
    "Purple" to "#AA00FF",
    "Indigo" to "#3D5AFE",
    "Blue" to "#00B0FF",
    "Cyan" to "#00E5FF",
    "Mint" to "#1DE9B6",
    "Lime" to "#B5F36A",
    "Gold" to "#FFD600"
)

@Composable
fun WledQuickControls(model: WledModel, ui: WledUi, openLights: () -> Unit) {
    val device = ui.device
    val snap = ui.effectiveSnapshot
    val primarySeg = snap.primarySegment
    val fxName = ui.effectiveEffects.getOrElse(primarySeg.fx) { "FX #${primarySeg.fx}" }
    val palName = ui.effectivePalettes.getOrElse(primarySeg.pal) { "Palette #${primarySeg.pal}" }

    RoomCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                SmallLabel("WLED LIGHTING")
                Text(
                    text = device?.name ?: "Bedroom WLED",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = buildString {
                        append(if (ui.online) "LIVE" else "READY")
                        append(" · $fxName · $palName · ${snap.ledCount} LEDs")
                        if (snap.estimatedPowerMa > 0) append(" · ~${snap.estimatedPowerMa} mA")
                    },
                    color = if (ui.online) Lime else Muted,
                    fontSize = 12.sp
                )
            }
            LiquidToggle(
                checked = snap.on,
                onCheckedChange = { model.command(obj("on" to it)) },
                enabled = !ui.busy,
                label = "Lighting power",
                modifier = Modifier.testTag("wled_home_power_toggle")
            )
        }

        // Compact live LED strip peek inside Home WLED card too
        WledAnimatedStripCanvas(ui = ui, snap = snap, heightDp = 20)

        val actualBriPct = snap.bri * 100f / 255f
        var bri by remember(snap.bri) { mutableFloatStateOf(actualBriPct) }
        BrightnessControl(
            value = bri,
            onChange = {
                bri = it
                model.coalescedCommand("home-bri", obj("bri" to wledByte(it), "on" to (it > 0f)))
            },
            enabled = !ui.busy,
            onFinished = { model.command(obj("bri" to wledByte(bri), "on" to (bri > 0f))) }
        )

        // Quick Action Row: Power / Nightlight / Sync / Random FX / All WLED Controls
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val nlInteraction = remember { MutableInteractionSource() }
            FilterChip(
                selected = snap.nightlight.on,
                onClick = { model.command(obj("nl" to obj("on" to !snap.nightlight.on))) },
                interactionSource = nlInteraction,
                modifier = Modifier.livelyPressScale(nlInteraction),
                leadingIcon = { Icon(Icons.Outlined.Bedtime, null, Modifier.size(15.dp)) },
                label = { Text(if (snap.nightlight.on) "Timer (${snap.nightlight.durationMinutes}m)" else "Timer", fontSize = 11.sp) }
            )
            val syncInteraction = remember { MutableInteractionSource() }
            FilterChip(
                selected = snap.udpSync.send,
                onClick = { model.command(obj("udpn" to obj("send" to !snap.udpSync.send, "recv" to snap.udpSync.recv))) },
                interactionSource = syncInteraction,
                modifier = Modifier.livelyPressScale(syncInteraction),
                leadingIcon = { Icon(Icons.Outlined.Sync, null, Modifier.size(15.dp)) },
                label = { Text(if (snap.udpSync.send) "Sync ON" else "Sync", fontSize = 11.sp) }
            )
            val rndInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = {
                    val rndFx = Random.nextInt(1, ui.effectiveEffects.size)
                    model.updateSegments(snap.selectedSegments.map { it.id }, obj("fx" to rndFx, "on" to true))
                },
                interactionSource = rndInteraction,
                modifier = Modifier.livelyPressScale(rndInteraction),
                leadingIcon = { Icon(Icons.Outlined.Shuffle, null, Modifier.size(15.dp)) },
                label = { Text("Random FX", fontSize = 11.sp) }
            )
            val openInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = openLights,
                interactionSource = openInteraction,
                modifier = Modifier.livelyPressScale(openInteraction),
                leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(15.dp)) },
                label = { Text("All WLED Controls", fontSize = 11.sp) }
            )
        }

        val presets = ui.presetEntries.filter { !it.isPlaylist }.take(6)
        if (presets.isNotEmpty()) {
            SmallLabel("WLED QUICK PRESETS")
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { preset ->
                    val interaction = remember { MutableInteractionSource() }
                    FilterChip(
                        selected = snap.activePreset == preset.id,
                        onClick = { model.command(obj("ps" to preset.id), successNotice = "Applied ${preset.name}") },
                        enabled = !ui.busy,
                        interactionSource = interaction,
                        modifier = Modifier.livelyPressScale(interaction),
                        label = {
                            val badge = if (preset.quickLabel.isNotBlank()) "${preset.quickLabel} · " else ""
                            Text("$badge${preset.name}", fontSize = 12.sp)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WledLightsScreen(
    model: WledModel,
    ui: WledUi,
    roomModel: RoomModel? = null,
    roomUi: RoomUi? = null,
    navigate: (String) -> Unit = {}
) {
    val snap = ui.effectiveSnapshot
    val selectedTab = ui.activeTab
    val pcMode = ui.pcMode
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }
    var showConnectionCard by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            model.setLivePreview(false)
        }
    }

    // 1. WLED Signature Top Action Bar (Power, Timer, Sync, Peek, Info, Config, PC Mode + Master Brightness)
    WledTopActionBar(
        model = model,
        ui = ui,
        snap = snap,
        pcMode = pcMode,
        onTogglePcMode = { model.setPcMode(!pcMode) },
        showInfo = showInfoSheet,
        onToggleInfo = { showInfoSheet = !showInfoSheet },
        isConfigSelected = selectedTab == "Config",
        onSelectConfig = {
            model.setPcMode(false)
            model.setActiveTab(if (selectedTab == "Config") "Colors" else "Config")
        },
        showConnection = showConnectionCard,
        onToggleConnection = { showConnectionCard = !showConnectionCard }
    )

    // 2. Collapsible Controller Connection & Discovery Card
    if (showConnectionCard) {
        WledDevicesCard(model, ui, roomModel, roomUi)
    }

    // 3. Collapsible WLED Info & Diagnostics Panel (same as WLED's "Info" top button)
    if (showInfoSheet) {
        WledInfoDiagnosticsCard(model, ui, snap) { showInfoSheet = false }
    }

    // 4. Live LED Strip Visualizer & Interactive Luminous Peek Bar
    WledStripPreviewCard(model, ui, snap)

    // 4b. Music Response Pipeline Selector (Inbuilt Native ESP32 DSP vs WLED Engine)
    Esp32MusicPipelineQuickBanner(
        selectedTab = selectedTab,
        roomUi = roomUi,
        onSelectEsp32Music = {
            model.setPcMode(false)
            model.setActiveTab("ESP32 Music")
        },
        onSelectWledFx = {
            model.setPcMode(false)
            model.setActiveTab("Effects")
        }
    )

    // 5. WLED Primary Navigation Bar (Colors | FX · Effects | ESP32 Music | Palettes | Segments | Presets | Config)
    // Single-row horizontal scroll so there are no awkward multi-row gaps
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf(
            Triple("Colors", Icons.Outlined.Palette, "Colors"),
            Triple("Effects", Icons.Outlined.AutoAwesome, "FX (${ui.effectiveEffects.size})"),
            Triple("ESP32 Music", Icons.Outlined.GraphicEq, "ESP32 Music"),
            Triple("Palettes", Icons.Outlined.ColorLens, "Palettes (${ui.effectivePalettes.size})"),
            Triple("Segments", Icons.Outlined.ViewColumn, "Segments (${snap.segments.size})"),
            Triple("Presets", Icons.Outlined.Bookmarks, "Presets (${ui.presetEntries.size})"),
            Triple("Config", Icons.Outlined.Settings, "Config")
        )
        tabs.forEach { (id, icon, label) ->
            val isSelected = (!pcMode && selectedTab == id) || (pcMode && id != "Config")
            val interactionSource = remember { MutableInteractionSource() }
            val tagSlug = id.lowercase().replace(" ", "_")
            FilterChip(
                selected = isSelected,
                onClick = {
                    model.setPcMode(false)
                    model.setActiveTab(id)
                },
                interactionSource = interactionSource,
                leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
                label = {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                modifier = Modifier
                    .livelyPressScale(interactionSource, pressedScale = 0.92f)
                    .testTag("wled_tab_$tagSlug")
            )
        }
    }

    // 6. Compact Target Segments Bar (Shown across Colors, FX/Effects, and Palettes with zero unnecessary blank space)
    if (pcMode || selectedTab in setOf("Colors", "Effects", "Palettes")) {
        WledCompactTargetSegmentsBar(model, ui, snap)
    }

    // 7. Active Tab or Unified PC Mode Workspace
    if (pcMode) {
        WledColorAndOutputTab(model, ui, snap)
        Esp32NativeMusicPipelineTab(model, ui, snap, roomModel, roomUi)
        WledEffectsTab(model, ui, snap)
        WledPalettesTab(model, ui, snap)
        WledSegmentsTab(model, ui, snap)
        WledPresetsAndPlaylistsTab(model, ui, snap, roomModel)
    } else {
        when (selectedTab) {
            "Colors" -> WledColorAndOutputTab(model, ui, snap)
            "Effects" -> WledEffectsTab(model, ui, snap)
            "ESP32 Music" -> Esp32NativeMusicPipelineTab(model, ui, snap, roomModel, roomUi)
            "Palettes" -> WledPalettesTab(model, ui, snap)
            "Segments" -> WledSegmentsTab(model, ui, snap)
            "Presets" -> WledPresetsAndPlaylistsTab(model, ui, snap, roomModel)
            "Config" -> WledControllerSettingsSection(model, ui, roomModel, roomUi)
            else -> WledColorAndOutputTab(model, ui, snap)
        }
    }
}

@Composable
private fun WledCompactTargetSegmentsBar(
    model: WledModel,
    ui: WledUi,
    snap: WledSnapshot
) {
    val segments = snap.segments
    val selectedIds = snap.selectedSegments.map { it.id }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                Icon(Icons.Outlined.ViewColumn, null, tint = Lime, modifier = Modifier.size(15.dp))
                Text(
                    text = "Target segments",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Cream,
                    maxLines = 1
                )
                Text(
                    text = "${selectedIds.size}/${segments.size} active",
                    fontSize = 11.sp,
                    color = Muted,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (segments.size > 1) {
                    val allSelected = selectedIds.size == segments.size
                    Text(
                        text = if (allSelected) "Main only" else "All",
                        color = Lime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .livelyClick {
                                if (allSelected) {
                                    val segArr = JSONArray()
                                    segments.forEach { s -> segArr.put(obj("id" to s.id, "sel" to (s.id == snap.mainSegmentId))) }
                                    model.command(obj("seg" to segArr))
                                } else {
                                    model.updateSegments(segments.map { it.id }, obj("sel" to true))
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "Edit zones",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .livelyClick { model.setActiveTab("Segments") }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            segments.forEach { seg ->
                val segColor = WledCatalogDefaults.luminousLedColor(parseColor(seg.colorHex(0)), 0.9f)
                val isSelected = seg.sel
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Lime.copy(alpha = 0.16f) else Ink)
                        .border(1.dp, if (isSelected) Lime else Line, RoundedCornerShape(10.dp))
                        .livelyClick(enabled = !ui.busy, pressedScale = 0.93f) {
                            model.updateSegments(listOf(seg.id), obj("sel" to !seg.sel))
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(segColor)
                    )
                    val title = seg.name.ifBlank { "Seg ${seg.id + 1}" }
                    Text(
                        text = "$title (${seg.start}–${seg.stop})",
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Lime else Cream,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WledTopActionBar(
    model: WledModel,
    ui: WledUi,
    snap: WledSnapshot,
    pcMode: Boolean,
    onTogglePcMode: () -> Unit,
    showInfo: Boolean,
    onToggleInfo: () -> Unit,
    isConfigSelected: Boolean,
    onSelectConfig: () -> Unit,
    showConnection: Boolean,
    onToggleConnection: () -> Unit
) {
    RoomCard {
        // Controller Identity & Connection Toggle Row (Minimalist — no truncated '...' subtitle)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (ui.online) Lime else Color(0xFFF6BC7B))
                )
                Text(
                    text = ui.device?.name ?: "Bedroom WLED",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            val ipInteraction = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = onToggleConnection,
                interactionSource = ipInteraction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .livelyPressScale(ipInteraction)
                    .testTag("wled_toggle_connection_card")
            ) {
                Icon(Icons.Outlined.Router, null, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showConnection) "Hide IP" else "Controller IP", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }

        // Minimalist Single-Line Power Button
        val primaryRawColor = parseColor(snap.primarySegment.colorHex(0))
        val powerGlowColor = WledCatalogDefaults.luminousLedColor(primaryRawColor, 0.95f)
        val reactorBg by animateColorAsState(
            targetValue = if (snap.on) powerGlowColor.copy(alpha = 0.14f) else Ink,
            label = "wledPowerReactorBg"
        )
        val reactorBorder by animateColorAsState(
            targetValue = if (snap.on) powerGlowColor else Line,
            label = "wledPowerReactorBorder"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(reactorBg)
                .border(if (snap.on) 1.5.dp else 1.dp, reactorBorder, RoundedCornerShape(14.dp))
                .livelyClick(enabled = !ui.busy, pressedScale = 0.97f) {
                    model.command(obj("on" to !snap.on))
                }
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("wled_top_power"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (snap.on) powerGlowColor else Panel)
                        .border(1.dp, if (snap.on) Color.White.copy(alpha = 0.7f) else Line, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = if (snap.on) "Turn Strip Off" else "Turn Strip On",
                        tint = if (snap.on) Ink else Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Power",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (snap.on) Cream else Muted,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (snap.on) powerGlowColor else Panel)
                    .border(1.dp, if (snap.on) Color.White.copy(alpha = 0.4f) else Line, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (snap.on) "ON" else "OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (snap.on) Ink else Muted,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Clean 3x2 Equal-Width Utility Action Grid (Timer | Sync | Peek / Info | Config | PC Mode) — zero clipping
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WledActionPill(
                    label = if (snap.nightlight.on) "Timer ${snap.nightlight.durationMinutes}m" else "Timer",
                    icon = Icons.Outlined.Bedtime,
                    active = snap.nightlight.on,
                    tag = "wled_top_nightlight",
                    modifier = Modifier.weight(1f)
                ) {
                    model.command(
                        obj("nl" to obj("on" to !snap.nightlight.on)),
                        successNotice = if (!snap.nightlight.on) "Nightlight timer started (${snap.nightlight.durationMinutes}m)" else "Nightlight stopped"
                    )
                }
                WledActionPill(
                    label = if (snap.udpSync.send) "Sync ON" else "Sync",
                    icon = Icons.Outlined.Sync,
                    active = snap.udpSync.send,
                    tag = "wled_top_sync",
                    modifier = Modifier.weight(1f)
                ) {
                    val nextSend = !snap.udpSync.send
                    model.command(
                        obj("udpn" to obj("send" to nextSend, "recv" to snap.udpSync.recv)),
                        successNotice = if (nextSend) "WLED UDP sync broadcast enabled" else "WLED UDP sync broadcast disabled"
                    )
                }
                WledActionPill(
                    label = if (ui.livePreviewEnabled) "Peek ON" else "Peek",
                    icon = Icons.Outlined.Visibility,
                    active = ui.livePreviewEnabled,
                    tag = "wled_top_peek",
                    modifier = Modifier.weight(1f)
                ) {
                    model.setLivePreview(!ui.livePreviewEnabled)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WledActionPill(
                    label = "Info",
                    icon = Icons.Outlined.Info,
                    active = showInfo,
                    tag = "wled_top_info",
                    modifier = Modifier.weight(1f),
                    onClick = onToggleInfo
                )
                WledActionPill(
                    label = "Config",
                    icon = Icons.Outlined.Settings,
                    active = isConfigSelected,
                    tag = "wled_top_config",
                    modifier = Modifier.weight(1f),
                    onClick = onSelectConfig
                )
                WledActionPill(
                    label = if (pcMode) "PC ON" else "PC Mode",
                    icon = Icons.Outlined.ViewQuilt,
                    active = pcMode,
                    tag = "wled_top_pcmode",
                    modifier = Modifier.weight(1f),
                    onClick = onTogglePcMode
                )
            }
        }

        // Persistent Master Brightness Control in Top Header with Luminous Colored Slider
        var briVal by remember(snap.bri) { mutableFloatStateOf(snap.bri.toFloat()) }
        val luminousBarColor = WledCatalogDefaults.luminousLedColor(primaryRawColor, (briVal / 255f).coerceIn(0.32f, 1f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.LightMode, null, tint = luminousBarColor, modifier = Modifier.size(16.dp))
                    Text("Master Brightness", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Text(
                    "${briVal.roundToInt()} / 255 (${wledPercent(briVal.roundToInt())}%)",
                    color = if (snap.on) Lime else Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Column(
                modifier = Modifier.alpha(if (snap.on) 1f else 0.42f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LedSlider(
                    value = (briVal / 255f).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        if (!snap.on) return@LedSlider
                        val next = (frac * 255f).coerceIn(0f, 255f)
                        briVal = next
                        model.coalescedCommand("top-master-bri", obj("bri" to next.roundToInt(), "on" to (next >= 1f)))
                    },
                    trackBrush = Brush.horizontalGradient(
                        listOf(
                            WledCatalogDefaults.luminousLedColor(primaryRawColor, 0.25f),
                            WledCatalogDefaults.luminousLedColor(primaryRawColor, 0.65f),
                            WledCatalogDefaults.luminousLedColor(primaryRawColor, 1f)
                        )
                    ),
                    contentDescription = "Master brightness",
                    enabled = snap.on && !ui.busy,
                    thumbColor = luminousBarColor,
                    modifier = Modifier.testTag("wled_top_brightness_slider")
                )
                // Equal-width single-line brightness pills with zero clipping on "100%"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("10%" to 26, "25%" to 64, "50%" to 128, "75%" to 191, "100%" to 255).forEach { (lbl, byteVal) ->
                        val isSelectedBri = snap.on && kotlin.math.abs(briVal.roundToInt() - byteVal) <= 6
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelectedBri) Lime.copy(alpha = 0.18f) else Ink)
                                .border(
                                    width = if (isSelectedBri) 1.5.dp else 1.dp,
                                    color = if (isSelectedBri) Lime else Line,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .livelyClick(enabled = snap.on && !ui.busy, pressedScale = 0.93f) {
                                    briVal = byteVal.toFloat()
                                    model.command(obj("bri" to byteVal, "on" to true))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lbl,
                                fontSize = 11.sp,
                                fontWeight = if (isSelectedBri) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelectedBri) Lime else Cream,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WledActionPill(
    label: String,
    icon: ImageVector,
    active: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Lime.copy(alpha = 0.18f) else Ink)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) Lime else Line,
                shape = RoundedCornerShape(10.dp)
            )
            .livelyClick(pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (active) Lime else Muted
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) Lime else Cream,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WledInfoDiagnosticsCard(
    model: WledModel,
    ui: WledUi,
    snap: WledSnapshot,
    onClose: () -> Unit
) {
    var confirmReboot by remember { mutableStateOf(false) }
    val info = snap.info
    val wifi = info.optJSONObject("wifi") ?: obj()
    val fs = info.optJSONObject("fs") ?: obj()
    val leds = info.optJSONObject("leds") ?: obj()
    val uptimeSec = snap.uptimeSeconds
    val hours = uptimeSec / 3600
    val mins = (uptimeSec % 3600) / 60
    val secs = uptimeSec % 60

    RoomCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                SectionLabel("Controller Info", "v${snap.version}")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, "Close Info")
            }
        }

        val rows = listOf(
            "Controller Name" to info.optString("name", ui.device?.name ?: "Bedroom WLED"),
            "Version / Build" to "${snap.version} (build ${info.optInt("vid", 0)})",
            "Architecture" to "${info.optString("arch", "esp32").uppercase()} · SDK ${info.optString("core", "v4.4")}",
            "Power & Bri" to "${if (snap.on) "ON" else "OFF"} · ${snap.bri}/255 (${wledPercent(snap.bri)}%)",
            "LEDs & Segments" to "${snap.ledCount} LEDs · ${snap.segments.size}/${snap.maxSegments} segs",
            "Current Draw" to "${snap.estimatedPowerMa} / ${snap.maxPowerMa} mA",
            "Refresh Rate" to "${leds.optInt("fps", snap.fps)} FPS",
            "Wi-Fi Signal" to "${wifi.optInt("signal", 0)}% (${wifi.optInt("rssi", -55)} dBm) · Ch ${wifi.optInt("channel", 1)}",
            "IP & MAC" to "${snap.ip.ifBlank { "192.168.1.105" }} · ${snap.mac.uppercase()}",
            "BSSID" to wifi.optString("bssid", "AA:BB:CC:DD:EE:FF"),
            "Filesystem" to "${fs.optInt("u", 24)} / ${fs.optInt("t", 983)} kB",
            "Free Heap" to "${info.optInt("freeheap", 0) / 1024} kB",
            "Uptime" to "${hours}h ${mins}m ${secs}s",
            "Sync State" to if (snap.isLiveRealtime) "Live (${snap.liveSource})" else if (ui.wsConnected) "WebSocket live" else "Synced state"
        )

        rows.forEach { (k, v) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = k,
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.42f)
                )
                Text(
                    text = v,
                    color = Cream,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(0.58f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                text = "Refresh Info",
                icon = Icons.Outlined.Refresh,
                enabled = !ui.busy,
                modifier = Modifier.weight(1f)
            ) {
                model.refreshNow()
            }
            OutlinedButton(
                onClick = { confirmReboot = true },
                enabled = !ui.busy,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reboot WLED", maxLines = 1, softWrap = false)
            }
        }
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = "Reboot WLED controller?",
            description = "Sends {\"rb\":true} to restart the ESP32 controller.",
            onDismiss = { confirmReboot = false }
        ) {
            model.rebootController()
        }
    }
}

@Composable
fun WledAnimatedStripCanvas(
    ui: WledUi,
    snap: WledSnapshot,
    heightDp: Int = 34
) {
    var animPhase by remember { mutableFloatStateOf(0f) }
    val hasDynamicFx = snap.on && snap.segments.any { it.on && it.fx != 0 && !it.frz }
    LaunchedEffect(hasDynamicFx, snap.primarySegment.sx) {
        if (!hasDynamicFx) return@LaunchedEffect
        while (true) {
            val speedFactor = 0.008f + (snap.primarySegment.sx / 255f) * 0.036f
            animPhase = (animPhase + speedFactor) % 100f
            delay(33L)
        }
    }

    val livePixels = ui.liveLeds
    val pixelCount = 48
    val computedPixels = remember(snap, animPhase, livePixels, ui.livePreviewEnabled) {
        if (ui.livePreviewEnabled && livePixels.isNotEmpty()) {
            val briFrac = (snap.bri / 255f).coerceIn(0.18f, 1f)
            List(pixelCount) { idx ->
                val srcIdx = ((idx.toFloat() / pixelCount.toFloat()) * livePixels.size).toInt().coerceIn(0, livePixels.lastIndex)
                val rgb = livePixels[srcIdx]
                val r = ((rgb shr 16) and 0xFF) / 255f
                val g = ((rgb shr 8) and 0xFF) / 255f
                val b = (rgb and 0xFF) / 255f
                WledCatalogDefaults.luminousLedColor(Color(r, g, b, 1f), briFrac)
            }
        } else {
            WledCatalogDefaults.computeStripPreviewPixels(snap, animPhase, pixelCount = pixelCount)
        }
    }

    val segBoundaries = remember(snap.segments, snap.ledCount) {
        val total = snap.ledCount.coerceAtLeast(1).toFloat()
        snap.segments.drop(1).map { (it.start.toFloat() / total).coerceIn(0f, 1f) }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A0D0A))
            .border(1.dp, if (snap.on) Color.White.copy(alpha = 0.24f) else Line, RoundedCornerShape(10.dp))
            .semantics { contentDescription = "LED strip preview bar" }
    ) {
        val count = computedPixels.size.coerceAtLeast(1)
        val horizPad = 6.dp.toPx()
        val trackW = (size.width - horizPad * 2f).coerceAtLeast(1f)
        val cellStep = trackW / count.toFloat()
        val cellW = (cellStep * 0.74f).coerceAtLeast(2.5f)
        val cellH = (size.height * 0.56f).coerceAtLeast(8f)
        val topY = (size.height - cellH) / 2f
        val cornerR = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())

        // 1. Recessed PCB channel rails (top & bottom copper/solder bus lines)
        val railColor = Color(0xFF1E261E)
        drawLine(
            color = railColor,
            start = Offset(horizPad, 3.dp.toPx()),
            end = Offset(size.width - horizPad, 3.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = railColor,
            start = Offset(horizPad, size.height - 3.dp.toPx()),
            end = Offset(size.width - horizPad, size.height - 3.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Discrete SMD5050 WS2812B LED cells
        val briMaster = if (snap.on) (snap.bri / 255f).coerceIn(0.15f, 1f) else 0f
        for (i in 0 until count) {
            val c = computedPixels[i]
            val cellX = horizPad + i * cellStep + (cellStep - cellW) / 2f

            // Physical SMD package housing
            drawRoundRect(
                color = if (snap.on) c.copy(alpha = 0.22f * briMaster + 0.08f) else Color(0xFF171C17),
                topLeft = Offset(cellX, topY),
                size = androidx.compose.ui.geometry.Size(cellW, cellH),
                cornerRadius = cornerR
            )

            if (snap.on) {
                // Crisp illuminated diode emitter
                val insetX = cellW * 0.14f
                val insetY = cellH * 0.16f
                drawRoundRect(
                    color = c.copy(alpha = (0.45f + 0.55f * briMaster).coerceIn(0f, 1f)),
                    topLeft = Offset(cellX + insetX, topY + insetY),
                    size = androidx.compose.ui.geometry.Size(
                        (cellW - insetX * 2f).coerceAtLeast(1.5f),
                        (cellH - insetY * 2f).coerceAtLeast(2f)
                    ),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )
                // Specular white-hot center point at high brightness
                if (briMaster > 0.35f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * briMaster),
                        radius = (minOf(cellW, cellH) * 0.18f).coerceAtLeast(1f),
                        center = Offset(cellX + cellW / 2f, size.height / 2f)
                    )
                }
            } else {
                // Unlit phosphor die center when strip is OFF
                drawCircle(
                    color = Color(0xFF252C25),
                    radius = (minOf(cellW, cellH) * 0.18f).coerceAtLeast(1f),
                    center = Offset(cellX + cellW / 2f, size.height / 2f)
                )
            }
        }

        // 3. Segment zone boundary markers
        segBoundaries.forEach { frac ->
            val bx = horizPad + frac * trackW
            drawLine(
                color = Color(0xFFF3FF6B).copy(alpha = 0.8f),
                start = Offset(bx, 2.dp.toPx()),
                end = Offset(bx, size.height - 2.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

@Composable
private fun WledStripPreviewCard(model: WledModel, ui: WledUi, snap: WledSnapshot) {
    val primarySeg = snap.primarySegment
    val fxName = ui.effectiveEffects.getOrElse(primarySeg.fx) { "FX #${primarySeg.fx}" }
    val palName = ui.effectivePalettes.getOrElse(primarySeg.pal) { "Pal #${primarySeg.pal}" }
    val primaryRaw = parseColor(primarySeg.colorHex(0))
    val secondaryRaw = parseColor(primarySeg.colorHex(1))
    val tertiaryRaw = parseColor(primarySeg.colorHex(2))
    val stripPaletteStops = remember(primarySeg.fx, primarySeg.pal, primarySeg.colors, snap.bri) {
        val briFrac = (snap.bri / 255f).coerceIn(0.38f, 1f)
        if (primarySeg.fx == 0) {
            val low = WledCatalogDefaults.luminousLedColor(primaryRaw, 0.35f)
            val mid = WledCatalogDefaults.luminousLedColor(primaryRaw, 0.72f)
            val high = WledCatalogDefaults.luminousLedColor(primaryRaw, 1f)
            listOf(low, mid, high)
        } else {
            WledCatalogDefaults.palettePreviewColors(primarySeg.pal, primaryRaw, secondaryRaw, tertiaryRaw)
                .map { WledCatalogDefaults.luminousLedColor(it, briFrac) }
        }
    }
    val activeThumbColor = remember(primaryRaw, snap.bri) {
        WledCatalogDefaults.luminousLedColor(primaryRaw, (snap.bri / 255f).coerceIn(0.45f, 1f))
    }

    RoomCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                SmallLabel("LIVE STRIP · ${snap.ledCount} LEDS · ${snap.fps} FPS")
                Text(
                    text = if (snap.on) "$fxName · $palName · ${wledPercent(snap.bri)}%" else "Strip Off · Tap Power to light",
                    color = if (snap.on) Cream else Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            val peekInteraction = remember { MutableInteractionSource() }
            FilterChip(
                selected = ui.livePreviewEnabled,
                onClick = { model.setLivePreview(!ui.livePreviewEnabled) },
                enabled = snap.on,
                interactionSource = peekInteraction,
                label = { Text(if (ui.livePreviewEnabled) "Live ON" else "Peek live", fontSize = 11.sp) },
                modifier = Modifier
                    .livelyPressScale(peekInteraction)
                    .testTag("wled_live_preview_toggle")
            )
        }

        // Hardware-accurate discrete WS2812B LED strip representation
        WledAnimatedStripCanvas(ui = ui, snap = snap, heightDp = 34)

        // Interactive Strip Peek Output Slider (disabled when strip power is OFF)
        var stripBriFrac by remember(snap.bri) { mutableFloatStateOf((snap.bri / 255f).coerceIn(0f, 1f)) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (snap.on) 1f else 0.42f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Strip Output",
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1
            )
            Box(Modifier.weight(1f)) {
                LedSlider(
                    value = stripBriFrac,
                    onValueChange = { frac ->
                        if (!snap.on) return@LedSlider
                        stripBriFrac = frac
                        val b = (frac * 255f).roundToInt().coerceIn(0, 255)
                        model.coalescedCommand("peek-bri", obj("bri" to b, "on" to (b > 0)))
                    },
                    trackBrush = Brush.horizontalGradient(
                        if (stripPaletteStops.size >= 2) stripPaletteStops else listOf(activeThumbColor, activeThumbColor)
                    ),
                    contentDescription = "LED strip peek output slider",
                    enabled = snap.on && !ui.busy,
                    thumbColor = activeThumbColor
                )
            }
            Text(
                text = if (snap.on) "${(stripBriFrac * 100f).roundToInt()}%" else "OFF",
                color = if (snap.on) activeThumbColor else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (snap.isLiveRealtime) {
            Text(
                "External realtime stream active (${snap.liveSource} ${snap.liveIp}) · Live override mode: ${snap.liveOverride}",
                color = Lime,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun WledColorAndOutputTab(model: WledModel, ui: WledUi, snap: WledSnapshot) {
    val selectedIds = snap.selectedSegments.map { it.id }
    val primarySeg = snap.primarySegment
    val fxMeta = ui.effectMetadata(primarySeg.fx)

    // Persistent Color Slot (Primary Fx / Secondary Bg / Tertiary Cs) from WledModel
    val colorSlot = ui.colorSlot.coerceIn(0, 2)
    val slotLabels = fxMeta.colorLabels
    val currentSlotColor = primarySeg.colors.getOrElse(colorSlot) { listOf(255, 160, 0, 0) }
    val currentHex = primarySeg.colorHex(colorSlot)
    var hexDraft by remember(currentHex, colorSlot) { mutableStateOf(currentHex) }
    var rVal by remember(currentSlotColor, colorSlot) { mutableFloatStateOf(currentSlotColor.getOrElse(0) { 255 }.toFloat()) }
    var gVal by remember(currentSlotColor, colorSlot) { mutableFloatStateOf(currentSlotColor.getOrElse(1) { 160 }.toFloat()) }
    var bVal by remember(currentSlotColor, colorSlot) { mutableFloatStateOf(currentSlotColor.getOrElse(2) { 0 }.toFloat()) }
    var wVal by remember(currentSlotColor, colorSlot) { mutableFloatStateOf(currentSlotColor.getOrElse(3) { 0 }.toFloat()) }
    var cctVal by remember(primarySeg.cct) { mutableFloatStateOf(primarySeg.cct.toFloat()) }

    fun pushSlotColor(newRgbw: List<Int>, coalesce: Boolean = true) {
        val updatedSlots = (0..2).map { idx ->
            if (idx == colorSlot) newRgbw else primarySeg.colors.getOrElse(idx) { listOf(0, 0, 0, 0) }
        }
        val colJson = JSONArray().apply {
            updatedSlots.forEach { slot -> put(JSONArray(slot)) }
        }
        model.updateSegments(
            segmentIds = selectedIds,
            fields = obj("col" to colJson, "on" to true),
            coalesceKey = if (coalesce) "seg-col-$colorSlot" else null
        )
    }

    RoomCard {
        SectionLabel("WLED Colour Picker & Slots", fxMeta.name)

        // Color Slots 1, 2, 3 (Persisted in WledModel)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..2).forEach { slot ->
                val label = slotLabels.getOrNull(slot) ?: when (slot) {
                    0 -> "Primary (Fx)"
                    1 -> "Secondary (Bg)"
                    else -> "Tertiary (Cs)"
                }
                val slotHex = primarySeg.colorHex(slot)
                val slotLuminous = WledCatalogDefaults.luminousLedColor(parseColor(slotHex), 0.95f)
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = colorSlot == slot,
                    onClick = { model.setColorSlot(slot) },
                    interactionSource = interaction,
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(slotLuminous)
                                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        )
                    },
                    label = { Text("${slot + 1}: $label", fontSize = 12.sp) },
                    modifier = Modifier
                        .livelyPressScale(interaction)
                        .testTag("wled_color_slot_$slot")
                )
            }
        }

        // Color Slot Actions: Swap 1↔2, Random Color, Blackout Slot
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val swapInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = {
                    val c0 = primarySeg.colors.getOrElse(0) { listOf(255, 160, 0, 0) }
                    val c1 = primarySeg.colors.getOrElse(1) { listOf(0, 0, 0, 0) }
                    val c2 = primarySeg.colors.getOrElse(2) { listOf(0, 0, 0, 0) }
                    val colJson = JSONArray().put(JSONArray(c1)).put(JSONArray(c0)).put(JSONArray(c2))
                    model.updateSegments(selectedIds, obj("col" to colJson))
                },
                interactionSource = swapInteraction,
                modifier = Modifier.livelyPressScale(swapInteraction),
                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(16.dp)) },
                label = { Text("Swap 1↔2", fontSize = 11.sp) }
            )
            val rndInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = {
                    val rr = Random.nextInt(32, 256)
                    val gg = Random.nextInt(32, 256)
                    val bb = Random.nextInt(32, 256)
                    rVal = rr.toFloat(); gVal = gg.toFloat(); bVal = bb.toFloat()
                    pushSlotColor(listOf(rr, gg, bb, wVal.roundToInt()), coalesce = false)
                },
                interactionSource = rndInteraction,
                modifier = Modifier.livelyPressScale(rndInteraction),
                leadingIcon = { Icon(Icons.Outlined.Shuffle, null, Modifier.size(16.dp)) },
                label = { Text("Random colour", fontSize = 11.sp) }
            )
            val offInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = {
                    rVal = 0f; gVal = 0f; bVal = 0f; wVal = 0f
                    pushSlotColor(listOf(0, 0, 0, 0), coalesce = false)
                },
                interactionSource = offInteraction,
                modifier = Modifier.livelyPressScale(offInteraction),
                leadingIcon = { Icon(Icons.Outlined.InvertColorsOff, null, Modifier.size(16.dp)) },
                label = { Text("Slot off", fontSize = 11.sp) }
            )
        }

        // Color Wheel with Luminous Brightness & Saturation Sliders + Favorite Button
        LedWheelColorPicker(
            color = currentHex,
            brightness = primarySeg.bri * 100f / 255f,
            isEffectMode = primarySeg.fx != 0,
            onColorChange = { hex ->
                hexDraft = hex
                val clean = hex.removePrefix("#")
                if (clean.length == 6) {
                    val r = clean.substring(0, 2).toInt(16)
                    val g = clean.substring(2, 4).toInt(16)
                    val b = clean.substring(4, 6).toInt(16)
                    rVal = r.toFloat(); gVal = g.toFloat(); bVal = b.toFloat()
                    pushSlotColor(listOf(r, g, b, wVal.roundToInt()), coalesce = true)
                }
            },
            onBrightnessChange = { pct ->
                model.updateSegments(selectedIds, obj("bri" to wledByte(pct), "on" to (pct > 0f)), coalesceKey = "seg-bri")
            },
            onEffectModeChange = { isFx ->
                if (isFx) {
                    val targetFx = if (primarySeg.fx == 0) 65 else primarySeg.fx
                    model.updateSegments(selectedIds, obj("fx" to targetFx, "on" to true))
                    model.setActiveTab("Effects")
                } else {
                    model.updateSegments(selectedIds, obj("fx" to 0, "on" to true))
                }
            },
            onRainbowSelected = {
                val rainbowIdx = ui.effectiveEffects.indexOfFirst { it.contains("Rainbow", ignoreCase = true) }.takeIf { it >= 0 } ?: 9
                model.updateSegments(selectedIds, obj("fx" to rainbowIdx, "on" to true))
            },
            onPowerOff = {
                model.updateSegments(selectedIds, obj("on" to false))
            },
            favoriteColors = ui.favoriteColors,
            onToggleFavoriteColor = { hex -> model.toggleFavoriteColor(hex) },
            enabled = !ui.busy
        )

        // Quick Swatches Bar with Favorite Star support
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallLabel("QUICK COLOUR SWATCHES (TAP ★ TO FAVORITE)")
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_SWATCHES.forEach { (name, hex) ->
                val c = WledCatalogDefaults.luminousLedColor(parseColor(hex), 0.95f)
                val selected = currentHex.equals(hex, ignoreCase = true)
                val isFav = ui.favoriteColors.any { it.equals(hex, ignoreCase = true) }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Lime.copy(alpha = 0.18f) else Ink)
                        .border(1.dp, if (selected) Lime else Line, RoundedCornerShape(12.dp))
                        .livelyClick(enabled = !ui.busy, pressedScale = 0.92f) {
                            hexDraft = hex
                            val clean = hex.removePrefix("#")
                            val r = clean.substring(0, 2).toInt(16)
                            val g = clean.substring(2, 4).toInt(16)
                            val b = clean.substring(4, 6).toInt(16)
                            rVal = r.toFloat(); gVal = g.toFloat(); bVal = b.toFloat()
                            pushSlotColor(listOf(r, g, b, wVal.roundToInt()), coalesce = false)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                    Text(name, fontSize = 11.sp, color = if (selected) Lime else Cream, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Favorite $name colour",
                        tint = if (isFav) Lime else Muted,
                        modifier = Modifier
                            .size(15.dp)
                            .livelyClick(pressedScale = 0.8f) { model.toggleFavoriteColor(hex) }
                    )
                }
            }
        }

        // Always-Visible RGBW, Kelvin/CCT & Hex Controls with Vivid Channel Colored Sliders
        HorizontalDivider(color = Line)
        SectionLabel("RGBW & Color Temp", currentHex)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = hexDraft,
                onValueChange = { hexDraft = it.take(7) },
                label = { Text("Hex colour (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("wled_hex_input")
            )
            ActionButton(
                text = "Set OK",
                enabled = !ui.busy && hexDraft.trim().matches(Regex("#?[0-9a-fA-F]{6}"))
            ) {
                val normalized = if (hexDraft.startsWith("#")) hexDraft.trim().uppercase() else "#${hexDraft.trim().uppercase()}"
                val clean = normalized.removePrefix("#")
                val r = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val b = clean.substring(4, 6).toInt(16)
                rVal = r.toFloat(); gVal = g.toFloat(); bVal = b.toFloat()
                pushSlotColor(listOf(r, g, b, wVal.roundToInt()), coalesce = false)
            }
        }

        ChannelLedSlider("Red (R)", rVal, Color(0xFFFF1744)) {
            rVal = it
            pushSlotColor(listOf(rVal.roundToInt(), gVal.roundToInt(), bVal.roundToInt(), wVal.roundToInt()), coalesce = true)
        }
        ChannelLedSlider("Green (G)", gVal, Color(0xFF00E676)) {
            gVal = it
            pushSlotColor(listOf(rVal.roundToInt(), gVal.roundToInt(), bVal.roundToInt(), wVal.roundToInt()), coalesce = true)
        }
        ChannelLedSlider("Blue (B)", bVal, Color(0xFF2979FF)) {
            bVal = it
            pushSlotColor(listOf(rVal.roundToInt(), gVal.roundToInt(), bVal.roundToInt(), wVal.roundToInt()), coalesce = true)
        }
        ChannelLedSlider("White channel (W / RGBW)", wVal, Color(0xFFFFF3E0)) {
            wVal = it
            pushSlotColor(listOf(rVal.roundToInt(), gVal.roundToInt(), bVal.roundToInt(), wVal.roundToInt()), coalesce = true)
        }

        // Kelvin CCT Slider with Warm-to-Cold gradient
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Color temperature / Kelvin (CCT)", color = Muted, fontSize = 13.sp)
                Text("${cctVal.roundToInt()} / 255", fontSize = 13.sp, color = Cream, fontWeight = FontWeight.Medium)
            }
            val cctFrac = (cctVal / 255f).coerceIn(0f, 1f)
            LedSlider(
                value = cctFrac,
                onValueChange = { f ->
                    val next = (f * 255f).coerceIn(0f, 255f)
                    cctVal = next
                    model.updateSegments(selectedIds, obj("cct" to next.roundToInt()), coalesceKey = "seg-cct")
                },
                trackBrush = Brush.horizontalGradient(
                    listOf(Color(0xFFFFB74D), Color(0xFFFFF3E0), Color(0xFF80D8FF))
                ),
                contentDescription = "Color temperature Kelvin balance",
                thumbColor = if (cctFrac < 0.5f) Color(0xFFFFCA7A) else Color(0xFFB3E5FC)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Warm 2700K" to 0, "Neutral 4500K" to 127, "Cold 6500K" to 255).forEach { (lbl, cct) ->
                val interaction = remember { MutableInteractionSource() }
                AssistChip(
                    onClick = {
                        cctVal = cct.toFloat()
                        model.updateSegments(selectedIds, obj("cct" to cct))
                    },
                    interactionSource = interaction,
                    modifier = Modifier.livelyPressScale(interaction),
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
        }
    }

    // 3. Transitions, Nightlight & Realtime Override Card
    var transitionTenths by remember(snap.transition) { mutableFloatStateOf(snap.transition.coerceIn(0, 200).toFloat()) }
    val nl = snap.nightlight
    var nlDur by remember(nl.durationMinutes) { mutableFloatStateOf(nl.durationMinutes.toFloat()) }
    var nlTbri by remember(nl.targetBrightness) { mutableFloatStateOf(nl.targetBrightness.toFloat()) }
    var nlMode by remember(nl.mode) { mutableIntStateOf(nl.mode) }

    RoomCard {
        SectionLabel("Transitions & Timer")
        SettingSlider(
            title = "Crossfade transition (${String.format("%.1f", transitionTenths / 10f)} s)",
            value = transitionTenths,
            onChange = {
                transitionTenths = it
                model.coalescedCommand("master-transition", obj("transition" to it.roundToInt()))
            },
            range = 0f..150f,
            suffix = " ×100ms"
        )

        SmallLabel("NIGHTLIGHT / SLEEP TIMER")
        SettingSlider("Duration", nlDur, { nlDur = it }, 1f..255f, " min")
        SettingSlider("Target brightness", nlTbri, { nlTbri = it }, 0f..255f, " /255")
        val modes = listOf("Instant (0)" to 0, "Fade (1)" to 1, "Color fade (2)" to 2, "Sunrise (3)" to 3)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            modes.forEach { (label, m) ->
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = nlMode == m,
                    onClick = { nlMode = m },
                    interactionSource = interaction,
                    modifier = Modifier.livelyPressScale(interaction),
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                text = if (nl.on) "Update Timer" else "Start Timer",
                icon = Icons.Outlined.Bedtime,
                enabled = !ui.busy,
                modifier = Modifier.weight(1f)
            ) {
                model.command(
                    obj("nl" to obj("on" to true, "dur" to nlDur.roundToInt(), "tbri" to nlTbri.roundToInt(), "mode" to nlMode)),
                    successNotice = "Nightlight active (${nlDur.roundToInt()} min)"
                )
            }
            if (nl.on) {
                OutlinedButton(
                    onClick = { model.command(obj("nl" to obj("on" to false)), successNotice = "Nightlight stopped") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Stop Timer",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalDivider(color = Line)
        SmallLabel("REALTIME OVERRIDE (LOR)")
        val lorLabels = listOf("0: Off" to 0, "1: Override now" to 1, "2: Until reboot" to 2)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lorLabels.forEach { (label, code) ->
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = snap.liveOverride == code,
                    onClick = { model.command(obj("lor" to code)) },
                    interactionSource = interaction,
                    modifier = Modifier.livelyPressScale(interaction),
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun ChannelLedSlider(
    title: String,
    value: Float,
    channelColor: Color,
    onChange: (Float) -> Unit
) {
    val fraction = (value / 255f).coerceIn(0f, 1f)
    val lowColor = WledCatalogDefaults.luminousLedColor(channelColor, 0.22f)
    val highColor = WledCatalogDefaults.luminousLedColor(channelColor, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Muted, fontSize = 13.sp)
            Text("${value.roundToInt()} / 255", fontSize = 13.sp, color = Cream, fontWeight = FontWeight.Medium)
        }
        LedSlider(
            value = fraction,
            onValueChange = { f -> onChange((f * 255f).coerceIn(0f, 255f)) },
            trackBrush = Brush.horizontalGradient(listOf(lowColor, highColor)),
            contentDescription = title,
            thumbColor = highColor
        )
    }
}

@Composable
private fun WledEffectsTab(model: WledModel, ui: WledUi, snap: WledSnapshot) {
    val selectedIds = snap.selectedSegments.map { it.id }
    val primarySeg = snap.primarySegment
    val fxMeta = ui.effectMetadata(primarySeg.fx)
    var effectQuery by rememberSaveable { mutableStateOf("") }
    val effectFilter = ui.effectFilter
    var showAllSliders by rememberSaveable { mutableStateOf(true) }
    var showAllEffects by rememberSaveable { mutableStateOf(false) }

    // 1. Effect Speed, Intensity, Custom Sliders & Options Card (Always visible at top of Effects tab!)
    RoomCard {
        val currentPalName = ui.effectivePalettes.getOrElse(primarySeg.pal) { "Palette #${primarySeg.pal}" }
        val isActiveFxFav = primarySeg.fx in ui.favoriteEffects
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                SectionLabel("Active FX: ${fxMeta.name}", "FX #${primarySeg.fx} · $currentPalName")
            }
            IconButton(onClick = { model.toggleFavoriteEffect(primarySeg.fx) }) {
                Icon(
                    imageVector = if (isActiveFxFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite active effect",
                    tint = if (isActiveFxFav) Lime else Muted
                )
            }
        }

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val solidInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = { model.updateSegments(selectedIds, obj("fx" to 0, "on" to true)) },
                interactionSource = solidInteraction,
                modifier = Modifier.livelyPressScale(solidInteraction),
                leadingIcon = { Icon(Icons.Outlined.Circle, null, Modifier.size(14.dp)) },
                label = { Text("Solid (#0)", fontSize = 11.sp) }
            )
            val rndInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = {
                    val rnd = Random.nextInt(1, ui.effectiveEffects.size)
                    model.updateSegments(selectedIds, obj("fx" to rnd, "on" to true))
                },
                interactionSource = rndInteraction,
                modifier = Modifier.livelyPressScale(rndInteraction),
                leadingIcon = { Icon(Icons.Outlined.Shuffle, null, Modifier.size(15.dp)) },
                label = { Text("Random FX", fontSize = 11.sp) }
            )
            val resetInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = {
                    val defSx = fxMeta.defaults["sx"] ?: 128
                    val defIx = fxMeta.defaults["ix"] ?: 128
                    val defC1 = fxMeta.defaults["c1"] ?: 128
                    val defC2 = fxMeta.defaults["c2"] ?: 128
                    val defC3 = fxMeta.defaults["c3"] ?: 16
                    model.updateSegments(
                        selectedIds,
                        obj("sx" to defSx, "ix" to defIx, "c1" to defC1, "c2" to defC2, "c3" to defC3, "o1" to false, "o2" to false, "o3" to false)
                    )
                },
                interactionSource = resetInteraction,
                modifier = Modifier.livelyPressScale(resetInteraction),
                leadingIcon = { Icon(Icons.Outlined.RestartAlt, null, Modifier.size(15.dp)) },
                label = { Text("Reset FX defaults", fontSize = 11.sp) }
            )
            val sliderToggleInteraction = remember { MutableInteractionSource() }
            FilterChip(
                selected = showAllSliders,
                onClick = { showAllSliders = !showAllSliders },
                interactionSource = sliderToggleInteraction,
                modifier = Modifier.livelyPressScale(sliderToggleInteraction),
                label = { Text(if (showAllSliders) "All 5 FX sliders" else "Active sliders only", fontSize = 11.sp) }
            )
        }

        // Speed (`sx`), Intensity (`ix`), Custom 1 (`c1`), Custom 2 (`c2`), Custom 3 (`c3`)
        val sliderSpecs = listOf(
            Triple("sx", primarySeg.sx, 0f..255f),
            Triple("ix", primarySeg.ix, 0f..255f),
            Triple("c1", primarySeg.c1, 0f..255f),
            Triple("c2", primarySeg.c2, 0f..255f),
            Triple("c3", primarySeg.c3, 0f..31f)
        )
        val fallbackNames = listOf("Effect speed (sx)", "Effect intensity (ix)", "Custom 1 (c1)", "Custom 2 (c2)", "Custom 3 (c3)")

        sliderSpecs.forEachIndexed { idx, (key, currentVal, range) ->
            val metaLabel = fxMeta.sliderLabels.getOrNull(idx)
            val label = metaLabel ?: if (showAllSliders || idx < 2) fallbackNames[idx] else null
            if (label != null) {
                var localVal by remember(primarySeg.id, primarySeg.fx, currentVal) { mutableFloatStateOf(currentVal.toFloat()) }
                SettingSlider(
                    title = label,
                    value = localVal,
                    onChange = {
                        localVal = it
                        model.updateSegments(selectedIds, obj(key to it.roundToInt()), coalesceKey = "fx-$key")
                    },
                    range = range,
                    suffix = ""
                )
            }
        }

        // Effect Options o1, o2, o3
        SmallLabel("EFFECT OPTIONS & 1D EXPANSION")
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val optSpecs = listOf(
                Triple("o1", primarySeg.o1, fxMeta.optionLabels.getOrNull(0) ?: "Option 1 (o1)"),
                Triple("o2", primarySeg.o2, fxMeta.optionLabels.getOrNull(1) ?: "Option 2 (o2)"),
                Triple("o3", primarySeg.o3, fxMeta.optionLabels.getOrNull(2) ?: "Option 3 (o3)")
            )
            optSpecs.forEach { (key, checked, label) ->
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = checked,
                    onClick = { model.updateSegments(selectedIds, obj(key to !checked)) },
                    interactionSource = interaction,
                    modifier = Modifier.livelyPressScale(interaction),
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        val m12Options = listOf("Pixels (0)" to 0, "Bar (1)" to 1, "Arc (2)" to 2, "Corner (3)" to 3)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            m12Options.forEach { (lbl, code) ->
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = primarySeg.m12 == code,
                    onClick = { model.updateSegments(selectedIds, obj("m12" to code)) },
                    interactionSource = interaction,
                    modifier = Modifier.livelyPressScale(interaction),
                    label = { Text("Expand: $lbl", fontSize = 11.sp) }
                )
            }
        }
    }

    // 2. Effects Browser Card
    RoomCard {
        val effectList = ui.effectiveEffects
        SectionLabel("WLED Effects Library", "${effectList.size} EFFECTS")
        OutlinedTextField(
            value = effectQuery,
            onValueChange = { effectQuery = it },
            label = { Text("Search effects by name or #ID") },
            placeholder = { Text("Rainbow, Pacifica, Fire 2012, Gravcenter, GEQ…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("wled_effect_search")
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("All", "Favorites ★", "1D Strip", "2D Matrix", "Audio Vol ♪", "Audio FFT ♫", "Palette").forEach { filter ->
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = effectFilter == filter,
                    onClick = { model.setEffectFilter(filter) },
                    interactionSource = interaction,
                    modifier = Modifier.livelyPressScale(interaction),
                    label = { Text(filter, fontSize = 11.sp, maxLines = 1, softWrap = false) }
                )
            }
        }

        val filteredEffects = remember(effectList, ui.effectiveFxdata, effectQuery, effectFilter, ui.favoriteEffects) {
            effectList.mapIndexedNotNull { index, name ->
                if (name == "RSVD" || name == "-") return@mapIndexedNotNull null
                val meta = ui.effectMetadata(index)
                val matchesQuery = effectQuery.isBlank() || name.contains(effectQuery, ignoreCase = true) || index.toString() == effectQuery.trim()
                val matchesFilter = when (effectFilter) {
                    "Favorites ★" -> index in ui.favoriteEffects
                    "1D Strip" -> meta.supports1D
                    "2D Matrix" -> meta.supports2D
                    "Audio Vol ♪" -> meta.isVolumeReactive
                    "Audio FFT ♫" -> meta.isFrequencyReactive
                    "Palette" -> meta.usesPalette
                    else -> true
                }
                if (matchesQuery && matchesFilter) meta else null
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Showing ${filteredEffects.size} effects · Tap to apply, ★ to favorite",
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (filteredEffects.size > 50) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink)
                        .border(1.dp, Lime.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .livelyClick { showAllEffects = !showAllEffects }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (showAllEffects) "Top 50" else "Show all ${filteredEffects.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Lime,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        val visibleEffects = if (showAllEffects || effectQuery.isNotBlank() || effectFilter != "All") {
            filteredEffects
        } else {
            filteredEffects.take(60)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleEffects.forEach { meta ->
                val isSelected = primarySeg.fx == meta.id
                val isFav = meta.id in ui.favoriteEffects
                val bg by animateColorAsState(
                    if (isSelected) Lime.copy(alpha = 0.16f) else Ink.copy(alpha = 0.45f),
                    label = "effectRowBg"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Lime else Line, RoundedCornerShape(14.dp))
                        .livelyClick(enabled = !ui.busy, pressedScale = 0.96f) {
                            model.updateSegments(selectedIds, obj("fx" to meta.id, "on" to true))
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isSelected) "● ${meta.name}" else meta.name,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Lime else Cream
                        )
                        val badges = buildList {
                            add("FX #${meta.id}")
                            if (meta.isVolumeReactive) add("♪ Volume")
                            if (meta.isFrequencyReactive) add("♫ FFT")
                            if (meta.supports2D) add("2D")
                            if (meta.usesPalette) add("Palette")
                        }.joinToString(" · ")
                        Text(badges, color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { model.toggleFavoriteEffect(meta.id) }) {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFav) "Remove ${meta.name} from favorites" else "Favorite ${meta.name}",
                            tint = if (isFav) Lime else Muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WledPalettesTab(model: WledModel, ui: WledUi, snap: WledSnapshot) {
    val selectedIds = snap.selectedSegments.map { it.id }
    val primarySeg = snap.primarySegment
    val palettes = ui.effectivePalettes
    var paletteQuery by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }

    val primaryColor = parseColor(primarySeg.colorHex(0))
    val secondaryColor = parseColor(primarySeg.colorHex(1))
    val tertiaryColor = parseColor(primarySeg.colorHex(2))

    RoomCard {
        val currentPalName = palettes.getOrElse(primarySeg.pal) { "Palette #${primarySeg.pal}" }
        SectionLabel("Colour Palettes", "#${primarySeg.pal} · ${currentPalName.take(14)}")

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val favFilterInteraction = remember { MutableInteractionSource() }
            FilterChip(
                selected = onlyFavorites,
                onClick = { onlyFavorites = !onlyFavorites },
                interactionSource = favFilterInteraction,
                modifier = Modifier.livelyPressScale(favFilterInteraction),
                label = { Text("Favorites ★ (${ui.favoritePalettes.size})", fontSize = 11.sp) }
            )
            val defInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = { model.updateSegments(selectedIds, obj("pal" to 0)) },
                interactionSource = defInteraction,
                modifier = Modifier.livelyPressScale(defInteraction),
                leadingIcon = { Icon(Icons.Outlined.Restore, null, Modifier.size(15.dp)) },
                label = { Text("Default (#0)", fontSize = 11.sp) }
            )
            val rndInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = {
                    val rnd = Random.nextInt(0, palettes.size)
                    model.updateSegments(selectedIds, obj("pal" to rnd))
                },
                interactionSource = rndInteraction,
                modifier = Modifier.livelyPressScale(rndInteraction),
                leadingIcon = { Icon(Icons.Outlined.Shuffle, null, Modifier.size(15.dp)) },
                label = { Text("Random Palette", fontSize = 11.sp) }
            )
            val cycleInteraction = remember { MutableInteractionSource() }
            AssistChip(
                onClick = { model.updateSegments(selectedIds, obj("pal" to 1)) },
                interactionSource = cycleInteraction,
                modifier = Modifier.livelyPressScale(cycleInteraction),
                leadingIcon = { Icon(Icons.Outlined.Autorenew, null, Modifier.size(15.dp)) },
                label = { Text("* Random Cycle (#1)", fontSize = 11.sp) }
            )
        }

        OutlinedTextField(
            value = paletteQuery,
            onValueChange = { paletteQuery = it },
            label = { Text("Search ${palettes.size} WLED palettes") },
            placeholder = { Text("Aurora, Sunset, Ocean, Lava, Rainbow, Fire, Sakura…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("wled_palette_search")
        )

        val filteredPalettes = remember(palettes, paletteQuery, onlyFavorites, ui.favoritePalettes) {
            palettes.mapIndexedNotNull { index, name ->
                val matchesFav = !onlyFavorites || index in ui.favoritePalettes
                val matchesQuery = paletteQuery.isBlank() || name.contains(paletteQuery, ignoreCase = true) || index.toString() == paletteQuery.trim()
                if (matchesFav && matchesQuery) index to name else null
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filteredPalettes.forEach { (palId, palName) ->
                val isSelected = primarySeg.pal == palId
                val isFav = palId in ui.favoritePalettes
                val stops = WledCatalogDefaults.palettePreviewColors(palId, primaryColor, secondaryColor, tertiaryColor)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Lime.copy(alpha = 0.16f) else Ink.copy(alpha = 0.45f))
                        .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Lime else Line, RoundedCornerShape(14.dp))
                        .livelyClick(enabled = !ui.busy, pressedScale = 0.96f) {
                            model.updateSegments(selectedIds, obj("pal" to palId))
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (isSelected) "● $palName" else palName,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Lime else Cream
                            )
                            Text("Palette #$palId", color = Muted, fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { model.toggleFavoritePalette(palId) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isFav) "Remove $palName from favorites" else "Favorite $palName",
                                tint = if (isFav) Lime else Muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (stops.size >= 2) stops else listOf(primaryColor, primaryColor)
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(7.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun WledSegmentsTab(model: WledModel, ui: WledUi, snap: WledSnapshot) {
    val segments = snap.segments
    var newStart by remember(segments.size, snap.ledCount) {
        val lastStop = segments.maxOfOrNull { it.stop } ?: 0
        mutableStateOf((if (lastStop < snap.ledCount) lastStop else 0).toString())
    }
    var newStop by remember(segments.size, snap.ledCount) {
        mutableStateOf(snap.ledCount.toString())
    }
    var newName by remember { mutableStateOf("") }
    var deletingSegment by remember { mutableStateOf<WledSegment?>(null) }

    val totalLeds = snap.ledCount.coerceAtLeast(1)
    val oneThird = (totalLeds / 3).coerceAtLeast(1)
    val twoThirds = ((totalLeds * 2) / 3).coerceAtLeast(oneThird + 1)
    val halfLeds = (totalLeds / 2).coerceAtLeast(1)

    data class ZonePreset(val label: String, val start: Int, val stop: Int, val defaultName: String)
    val zonePresets = remember(totalLeds) {
        listOf(
            ZonePreset("Full (0–$totalLeds)", 0, totalLeds, "Full Room Strip"),
            ZonePreset("Zone 1 (0–$oneThird)", 0, oneThird, "Desk & Left Wall"),
            ZonePreset("Zone 2 ($oneThird–$twoThirds)", oneThird, twoThirds, "Ceiling Cove"),
            ZonePreset("Zone 3 ($twoThirds–$totalLeds)", twoThirds, totalLeds, "Right Backdrop"),
            ZonePreset("Left Half (0–$halfLeds)", 0, halfLeds, "Left Half"),
            ZonePreset("Right Half ($halfLeds–$totalLeds)", halfLeds, totalLeds, "Right Half")
        )
    }

    data class StepModePreset(
        val id: String,
        val label: String,
        val description: String,
        val grp: Int,
        val spc: Int,
        val rev: Boolean,
        val mi: Boolean
    )
    val stepModes = remember {
        listOf(
            StepModePreset("linear_1", "Linear 1 px/step", "Every single LED is an independent step (Grp 1 · Spc 0)", 1, 0, false, false),
            StepModePreset("block_2", "2-LED Block Steps", "Pairs of 2 adjacent LEDs act as 1 unified step (Grp 2 · Spc 0)", 2, 0, false, false),
            StepModePreset("block_4", "4-LED Block Steps", "Architectural 4-LED clusters per step (Grp 4 · Spc 0)", 4, 0, false, false),
            StepModePreset("dotted_1_1", "Staggered Dotted", "1 lit LED step followed by 1 dark gap pixel (Grp 1 · Spc 1)", 1, 1, false, false),
            StepModePreset("wide_2_2", "Wide Spaced (2+2)", "2 lit LEDs followed by 2 gap LEDs per step (Grp 2 · Spc 2)", 2, 2, false, false),
            StepModePreset("center_mirror", "Center Mirrored", "Steps radiate symmetrically from center to edges (Mirror ON)", 1, 0, false, true),
            StepModePreset("reverse_dir", "Reversed Stop→Start", "Steps advance from Stop LED backward to Start LED (Reverse ON)", 1, 0, true, false)
        )
    }

    RoomCard {
        SectionLabel("Segments & Zones", "${segments.size}/${snap.maxSegments}")
        // Clean 2x2 Grid of Segment Selection Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { model.updateSegments(segments.map { it.id }, obj("sel" to true)) },
                    enabled = !ui.busy,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Select all", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                OutlinedButton(
                    onClick = {
                        val segArr = JSONArray()
                        segments.forEach { seg -> segArr.put(obj("id" to seg.id, "sel" to !seg.sel)) }
                        model.command(obj("seg" to segArr))
                    },
                    enabled = !ui.busy,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Invert selection", fontSize = 11.sp, maxLines = 1, softWrap = false) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val segArr = JSONArray()
                        segments.forEach { seg ->
                            segArr.put(obj("id" to seg.id, "sel" to (seg.id == snap.mainSegmentId)))
                        }
                        model.command(obj("seg" to segArr))
                    },
                    enabled = !ui.busy && segments.size > 1,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Select main only", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                OutlinedButton(
                    onClick = { model.resetSegments() },
                    enabled = !ui.busy,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Reset (1 full strip)", fontSize = 11.sp, maxLines = 1, softWrap = false) }
            }
        }

        if (segments.size < snap.maxSegments) {
            HorizontalDivider(color = Line)
            SmallLabel("ADD NEW SEGMENT OR PICK ZONE PRESET")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                zonePresets.chunked(2).forEach { rowZones ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowZones.forEach { zp ->
                            val isSelectedZone = newStart == zp.start.toString() && newStop == zp.stop.toString()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelectedZone) Lime.copy(alpha = 0.18f) else Ink)
                                    .border(
                                        width = if (isSelectedZone) 1.5.dp else 1.dp,
                                        color = if (isSelectedZone) Lime else Line,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .livelyClick {
                                        newStart = zp.start.toString()
                                        newStop = zp.stop.toString()
                                        if (newName.isBlank()) newName = zp.defaultName
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = zp.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelectedZone) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelectedZone) Lime else Cream,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newStart,
                    onValueChange = { newStart = it.filter(Char::isDigit).take(4) },
                    label = { Text("Start LED") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = newStop,
                    onValueChange = { newStop = it.filter(Char::isDigit).take(4) },
                    label = { Text("Stop LED") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(32) },
                label = { Text("Segment name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val sInt = newStart.toIntOrNull() ?: -1
            val eInt = newStop.toIntOrNull() ?: -1
            ActionButton(
                text = "Add segment",
                icon = Icons.Outlined.Add,
                enabled = !ui.busy && sInt in 0 until snap.ledCount && eInt in (sInt + 1)..snap.ledCount
            ) {
                model.addSegment(sInt, eInt, newName)
                newName = ""
            }
        }
    }

    segments.forEach { seg ->
        var segName by remember(seg.id, seg.name) { mutableStateOf(seg.name) }
        var segStart by remember(seg.id, seg.start) { mutableStateOf(seg.start.toString()) }
        var segStop by remember(seg.id, seg.stop) { mutableStateOf(seg.stop.toString()) }
        var segGrp by remember(seg.id, seg.grp) { mutableStateOf(seg.grp.toString()) }
        var segSpc by remember(seg.id, seg.spc) { mutableStateOf(seg.spc.toString()) }
        var segOf by remember(seg.id, seg.of) { mutableStateOf(seg.of.toString()) }
        var segBri by remember(seg.id, seg.bri) { mutableFloatStateOf(seg.bri.toFloat()) }
        val segControlsEnabled = snap.on && seg.on && !ui.busy

        val startParsed = segStart.toIntOrNull() ?: seg.start
        val stopParsed = segStop.toIntOrNull() ?: seg.stop
        val grpParsed = (segGrp.toIntOrNull() ?: seg.grp).coerceIn(1, 255)
        val spcParsed = (segSpc.toIntOrNull() ?: seg.spc).coerceIn(0, 255)
        val ofParsed = segOf.toIntOrNull() ?: seg.of
        val spanLeds = (stopParsed - startParsed).coerceAtLeast(1)
        val stridePerStep = (grpParsed + spcParsed).coerceAtLeast(1)
        val effectiveLogicalSteps = ((spanLeds + stridePerStep - 1) / stridePerStep).coerceAtLeast(1)

        RoomCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = seg.sel,
                    onCheckedChange = { model.updateSegments(listOf(seg.id), obj("sel" to it)) },
                    enabled = !ui.busy
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        seg.name.ifBlank { "Segment ${seg.id + 1}" },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (seg.on && snap.on) Cream else Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "ID #${seg.id} · LEDs ${seg.start}–${seg.stop} (${seg.len} px · $effectiveLogicalSteps steps)",
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LiquidToggle(
                    checked = seg.on,
                    onCheckedChange = { model.updateSegments(listOf(seg.id), obj("on" to it)) },
                    enabled = !ui.busy,
                    label = "Segment ${seg.id + 1} power"
                )
            }

            Column(
                modifier = Modifier.alpha(if (seg.on && snap.on) 1f else 0.42f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingSlider(
                    title = "Segment brightness",
                    value = segBri,
                    onChange = {
                        segBri = it
                        model.updateSegments(listOf(seg.id), obj("bri" to it.roundToInt()), coalesceKey = "seg-${seg.id}-bri")
                    },
                    range = 0f..255f,
                    suffix = " /255",
                    enabled = segControlsEnabled
                )

                // Interactive Physical Zone Selector (2-column equal-width grid — zero uneven gaps)
                SmallLabel("STRIP ZONE BOUNDARIES (START → STOP)")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    zonePresets.chunked(2).forEach { rowZones ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowZones.forEach { zp ->
                                val activeZone = startParsed == zp.start && stopParsed == zp.stop
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (activeZone) Lime.copy(alpha = 0.18f) else Ink)
                                        .border(
                                            width = if (activeZone) 1.5.dp else 1.dp,
                                            color = if (activeZone) Lime else Line,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .livelyClick(enabled = segControlsEnabled) {
                                            segStart = zp.start.toString()
                                            segStop = zp.stop.toString()
                                            model.updateSegments(
                                                listOf(seg.id),
                                                obj("start" to zp.start, "stop" to zp.stop)
                                            )
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = zp.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (activeZone) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (activeZone) Lime else Cream,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Step-Determination Mode Selector (2-column equal-width grid, concise title)
                SmallLabel("LOGICAL STEP MODE")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stepModes.chunked(2).forEach { rowModes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowModes.forEach { sm ->
                                val isSelectedMode = grpParsed == sm.grp &&
                                    spcParsed == sm.spc &&
                                    seg.rev == sm.rev &&
                                    seg.mi == sm.mi
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelectedMode) Lime.copy(alpha = 0.18f) else Ink)
                                        .border(
                                            width = if (isSelectedMode) 1.5.dp else 1.dp,
                                            color = if (isSelectedMode) Lime else Line,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .livelyClick(enabled = segControlsEnabled) {
                                            segGrp = sm.grp.toString()
                                            segSpc = sm.spc.toString()
                                            model.updateSegments(
                                                listOf(seg.id),
                                                obj(
                                                    "grp" to sm.grp,
                                                    "spc" to sm.spc,
                                                    "rev" to sm.rev,
                                                    "mi" to sm.mi
                                                )
                                            )
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sm.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelectedMode) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelectedMode) Lime else Cream,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (rowModes.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Live Step-Determination Formula Readout Box (Single-line non-wrapping header)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.copy(alpha = 0.65f))
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Zone: $startParsed → $stopParsed ($spanLeds LEDs)",
                            color = Cream,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$effectiveLogicalSteps steps",
                            color = Lime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    val dirDesc = when {
                        seg.mi && seg.rev -> "Mirrored inward"
                        seg.mi -> "Mirrored outward"
                        seg.rev -> "Reversed ($stopParsed → $startParsed)"
                        else -> "Forward ($startParsed → $stopParsed)"
                    }
                    Text(
                        text = "Grp $grpParsed · Spc $spcParsed · $dirDesc · Offset $ofParsed",
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Clean Equal-Width Grid for Segment Flags (Reverse, Mirror, Freeze, Transpose, Set as main)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("Reverse", seg.rev) { model.updateSegments(listOf(seg.id), obj("rev" to !seg.rev)) },
                            Triple("Mirror", seg.mi) { model.updateSegments(listOf(seg.id), obj("mi" to !seg.mi)) },
                            Triple("Freeze", seg.frz) { model.updateSegments(listOf(seg.id), obj("frz" to !seg.frz)) }
                        ).forEach { (label, active, onClick) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Lime.copy(alpha = 0.18f) else Ink)
                                    .border(
                                        width = if (active) 1.5.dp else 1.dp,
                                        color = if (active) Lime else Line,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .livelyClick(enabled = segControlsEnabled, onClick = onClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) Lime else Cream,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isMain = snap.mainSegmentId == seg.id
                        listOf(
                            Triple("Transpose", seg.tp) { model.updateSegments(listOf(seg.id), obj("tp" to !seg.tp)) },
                            Triple(if (isMain) "★ Main segment" else "Set as main", isMain) { model.command(obj("mainseg" to seg.id)) }
                        ).forEach { (label, active, onClick) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Lime.copy(alpha = 0.18f) else Ink)
                                    .border(
                                        width = if (active) 1.5.dp else 1.dp,
                                        color = if (active) Lime else Line,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .livelyClick(enabled = segControlsEnabled, onClick = onClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) Lime else Cream,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // 2x2 Grid for Start LED, Stop LED, Grouping, and Spacing (labels never cut off)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = segStart,
                        onValueChange = { segStart = it.filter(Char::isDigit).take(4) },
                        label = { Text("Start LED", maxLines = 1, softWrap = false) },
                        singleLine = true,
                        enabled = segControlsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = segStop,
                        onValueChange = { segStop = it.filter(Char::isDigit).take(4) },
                        label = { Text("Stop LED", maxLines = 1, softWrap = false) },
                        singleLine = true,
                        enabled = segControlsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = segGrp,
                        onValueChange = { segGrp = it.filter(Char::isDigit).take(3) },
                        label = { Text("Grouping", maxLines = 1, softWrap = false) },
                        singleLine = true,
                        enabled = segControlsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = segSpc,
                        onValueChange = { segSpc = it.filter(Char::isDigit).take(3) },
                        label = { Text("Spacing", maxLines = 1, softWrap = false) },
                        singleLine = true,
                        enabled = segControlsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = segName,
                        onValueChange = { segName = it.take(32) },
                        label = { Text("Segment name") },
                        singleLine = true,
                        enabled = segControlsEnabled,
                        modifier = Modifier.weight(1.6f)
                    )
                    OutlinedTextField(
                        value = segOf,
                        onValueChange = { segOf = it.filter { c -> c.isDigit() || c == '-' }.take(5) },
                        label = { Text("Offset") },
                        singleLine = true,
                        enabled = segControlsEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }

                SmallLabel("EXPAND 1D FX & SOUND SIMULATION")
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Pixels" to 0, "Bar" to 1, "Arc" to 2, "Corner" to 3).forEach { (lbl, code) ->
                        FilterChip(
                            selected = seg.m12 == code,
                            onClick = { model.updateSegments(listOf(seg.id), obj("m12" to code)) },
                            enabled = segControlsEnabled,
                            label = { Text("1D: $lbl", fontSize = 11.sp) }
                        )
                    }
                    listOf("BeatSin" to 0, "RockYou" to 1, "10_3" to 2, "14_3" to 3).forEach { (lbl, code) ->
                        FilterChip(
                            selected = seg.si == code,
                            onClick = { model.updateSegments(listOf(seg.id), obj("si" to code)) },
                            enabled = segControlsEnabled,
                            label = { Text("SoundSim: $lbl", fontSize = 11.sp) }
                        )
                    }
                }

                // Clean, non-squished action footer row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        text = "Apply segment",
                        icon = Icons.Outlined.Check,
                        enabled = segControlsEnabled && startParsed in 0 until snap.ledCount && stopParsed in (startParsed + 1)..snap.ledCount
                    ) {
                        model.updateSegments(
                            listOf(seg.id),
                            obj(
                                "start" to startParsed,
                                "stop" to stopParsed,
                                "grp" to grpParsed,
                                "spc" to spcParsed,
                                "of" to ofParsed,
                                "n" to segName.trim()
                            )
                        )
                    }

                    if ((seg.len >= 4 && segments.size < snap.maxSegments) || segments.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (seg.len >= 4 && segments.size < snap.maxSegments) {
                                OutlinedButton(
                                    onClick = {
                                        val mid = seg.start + (seg.stop - seg.start) / 2
                                        model.updateSegments(listOf(seg.id), obj("start" to seg.start, "stop" to mid))
                                        model.addSegment(mid, seg.stop, "${seg.name.ifBlank { "Seg ${seg.id + 1}" }} B")
                                    },
                                    enabled = segControlsEnabled,
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Text("Split ½", maxLines = 1, softWrap = false)
                                }
                            }
                            if (segments.size > 1) {
                                OutlinedButton(
                                    onClick = { deletingSegment = seg },
                                    enabled = !ui.busy,
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete segment", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletingSegment?.let { seg ->
        ConfirmDialog(
            title = "Remove segment ${seg.id + 1}?",
            description = "This removes segment '${seg.name.ifBlank { "Segment ${seg.id + 1}" }}' (${seg.start}–${seg.stop}) from active WLED output.",
            onDismiss = { deletingSegment = null }
        ) {
            model.deleteSegment(seg.id)
        }
    }
}

@Composable
private fun WledPresetsAndPlaylistsTab(
    model: WledModel,
    ui: WledUi,
    snap: WledSnapshot,
    roomModel: RoomModel?
) {
    val entries = ui.presetEntries
    val presetsOnly = entries.filter { !it.isPlaylist }
    val playlistsOnly = entries.filter { it.isPlaylist }
    val usedIds = entries.map { it.id }.toSet()
    val nextFreeId = (1..250).firstOrNull { it !in usedIds } ?: 1

    var presetIdText by remember(nextFreeId) { mutableStateOf(nextFreeId.toString()) }
    var presetName by rememberSaveable { mutableStateOf("") }
    var quickLabel by rememberSaveable { mutableStateOf("") }
    var includeBri by rememberSaveable { mutableStateOf(true) }
    var saveBounds by rememberSaveable { mutableStateOf(true) }
    var checkedOnly by rememberSaveable { mutableStateOf(false) }
    var deletingEntry by remember { mutableStateOf<WledPresetEntry?>(null) }

    // 1. Quick Load (QL) Instant Bar (Just like the top of WLED Presets tab!)
    if (presetsOnly.isNotEmpty()) {
        RoomCard {
            SectionLabel("Quick Load Bar", "INSTANT APPLY")
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presetsOnly.forEach { p ->
                    val badge = p.quickLabel.ifBlank { "#${p.id}" }
                    val interaction = remember { MutableInteractionSource() }
                    FilterChip(
                        selected = snap.activePreset == p.id,
                        onClick = { model.command(obj("ps" to p.id), successNotice = "Loaded ${p.name}") },
                        interactionSource = interaction,
                        modifier = Modifier.livelyPressScale(interaction),
                        label = { Text("$badge · ${p.name}", fontSize = 12.sp) }
                    )
                }
                val rndInteraction = remember { MutableInteractionSource() }
                AssistChip(
                    onClick = {
                        val pick = presetsOnly.random()
                        model.command(obj("ps" to pick.id), successNotice = "Random preset: ${pick.name}")
                    },
                    interactionSource = rndInteraction,
                    modifier = Modifier.livelyPressScale(rndInteraction),
                    leadingIcon = { Icon(Icons.Outlined.Shuffle, null, Modifier.size(15.dp)) },
                    label = { Text("Random preset", fontSize = 12.sp) }
                )
            }
        }
    }

    // 2. Save Current State as Preset Card
    var presetCategory by rememberSaveable { mutableStateOf("Auto") }
    RoomCard {
        SectionLabel("Create / Save WLED Preset", "ID 1–250")
        Text(
            "Saves the current colours, effects, palettes and segment configuration to WLED's /presets.json and persists it locally in DataStore and on your ESP32.",
            color = Muted,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = presetIdText,
                onValueChange = { presetIdText = it.filter(Char::isDigit).take(3) },
                label = { Text("Save to ID (1–250)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = quickLabel,
                onValueChange = { quickLabel = it.take(2) },
                label = { Text("Quick load label") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = presetName,
            onValueChange = { presetName = it.take(32) },
            label = { Text("Preset name") },
            placeholder = { Text("Warm evening, Sunset glow, Study focus…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("wled_preset_name_input")
        )
        SmallLabel("PRESET CATEGORY (HOME ORGANIZER)")
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("Auto") + PresetCategories.ASSIGNABLE).forEach { catOption ->
                val chipInteraction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = presetCategory == catOption,
                    onClick = { presetCategory = catOption },
                    interactionSource = chipInteraction,
                    modifier = Modifier.livelyPressScale(chipInteraction),
                    label = { Text(catOption, fontSize = 11.sp) }
                )
            }
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(includeBri, { includeBri = it })
                Text("Include brightness", fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(saveBounds, { saveBounds = it })
                Text("Save segment bounds", fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checkedOnly, { checkedOnly = it })
                Text("Checked segments only", fontSize = 12.sp)
            }
        }
        val parsedId = presetIdText.toIntOrNull() ?: 0
        ActionButton(
            text = if (parsedId in usedIds) "Save & Apply Preset #$parsedId (OK)" else "Save & Apply Preset #$parsedId (OK)",
            icon = Icons.Outlined.BookmarkAdd,
            enabled = !ui.busy && parsedId in 1..250 && presetName.isNotBlank()
        ) {
            model.savePreset(parsedId, presetName, quickLabel, includeBri, saveBounds, checkedOnly, presetCategory)
            presetName = ""
            quickLabel = ""
        }
    }

    // 3. Saved Presets List with ★ Favorite button on every preset
    RoomCard {
        SectionLabel("Saved WLED Presets", "${presetsOnly.size} ON CONTROLLER")
        if (presetsOnly.isEmpty()) {
            Text("No presets saved yet. Use the form above to save your first preset.", color = Muted, fontSize = 13.sp)
        }
        presetsOnly.forEach { entry ->
            val isActive = snap.activePreset == entry.id
            val isFav = entry.id in ui.favoritePresets
            val resolvedCat = ui.categoryForWledPreset(entry)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isActive) Lime.copy(alpha = 0.14f) else Ink.copy(alpha = 0.4f))
                    .border(if (isActive) 1.5.dp else 1.dp, if (isActive) Lime else Line, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val badge = if (entry.quickLabel.isNotBlank()) "[${entry.quickLabel}] " else ""
                        Text(
                            text = if (isActive) "● $badge${entry.name}" else "$badge${entry.name}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) Lime else Cream
                        )
                        Text(
                            "ID #${entry.id} · $resolvedCat · API {\"ps\":${entry.id}}" +
                                (entry.bri?.let { " · Bri ${wledPercent(it)}%" } ?: "") +
                                (if (isActive) " · ACTIVE" else ""),
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { model.toggleFavoritePreset(entry.id) }) {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFav) "Remove ${entry.name} from favorites" else "Favorite ${entry.name}",
                            tint = if (isFav) Lime else Muted
                        )
                    }
                    ActionButton("Apply", Icons.Outlined.PlayArrow, !ui.busy) {
                        model.command(obj("ps" to entry.id), successNotice = "Applied ${entry.name}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            model.savePreset(entry.id, entry.name, entry.quickLabel, entry.includeBrightness, entry.saveSegmentBounds)
                        },
                        enabled = !ui.busy
                    ) { Text("Overwrite with current", fontSize = 11.sp) }
                    if (roomModel != null) {
                        TextButton(
                            onClick = {
                                val roomLighting = obj(
                                    "power" to (entry.on ?: true),
                                    "color" to snap.primarySegment.colorHex(0),
                                    "brightness" to wledPercent(entry.bri ?: snap.bri),
                                    "effect" to "solid",
                                    "speed" to 50,
                                    "intensity" to 60,
                                    "wled_preset_id" to entry.id
                                )
                                roomModel.mutate(
                                    "POST",
                                    "/presets",
                                    obj("name" to entry.name, "lighting" to roomLighting),
                                    "Linked '${entry.name}' (#${entry.id}) to Room routines"
                                )
                            },
                            enabled = !ui.busy
                        ) { Text("Link to Room", fontSize = 11.sp) }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { deletingEntry = entry }, enabled = !ui.busy) {
                        Icon(Icons.Outlined.DeleteOutline, "Delete ${entry.name}", tint = Muted)
                    }
                }
            }
        }
    }

    // 4. Saved Playlists Card
    var plIdText by rememberSaveable { mutableStateOf(nextFreeId.toString()) }
    var plName by rememberSaveable { mutableStateOf("Custom Playlist") }
    var plSelectedPresets by remember(presetsOnly.size) {
        mutableStateOf(
            if (presetsOnly.isNotEmpty()) presetsOnly.take(3).map { it.id }
            else listOf(1, 2, 3)
        )
    }
    var plDurationSec by rememberSaveable { mutableFloatStateOf(10f) }
    var plTransitionSec by rememberSaveable { mutableFloatStateOf(0.7f) }
    var plRepeat by rememberSaveable { mutableStateOf("0") }
    var plEndPreset by rememberSaveable { mutableStateOf("0") }
    var plShuffle by rememberSaveable { mutableStateOf(false) }
    var lastSavedPlaylistBanner by remember { mutableStateOf<String?>(null) }

    RoomCard {
        SectionLabel(
            "Saved Playlists",
            if (snap.activePlaylist > 0) "PLAYING #${snap.activePlaylist}" else "${playlistsOnly.size} SAVED"
        )
        if (snap.activePlaylist > 0) {
            OutlinedButton(
                onClick = { model.command(obj("pl" to -1), successNotice = "Playlist stopped") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Stop active playlist (#${snap.activePlaylist})", maxLines = 1, softWrap = false)
            }
        }
        if (playlistsOnly.isEmpty()) {
            Text(
                text = "No playlists saved yet. Use the Create or Edit Playlist card below to build an automated preset cycle.",
                color = Muted,
                fontSize = 12.sp
            )
        }
        playlistsOnly.forEach { plEntry ->
            val spec = plEntry.playlist ?: return@forEach
            val isRunning = snap.activePlaylist == plEntry.id
            val isFav = plEntry.id in ui.favoritePresets
            val stepDurSec = ((spec.durationsTenths.firstOrNull() ?: 100) / 10f)
            val stepFadeSec = ((spec.transitionsTenths.firstOrNull() ?: 7) / 10f)
            val durLabel = if (stepDurSec % 1f == 0f) "${stepDurSec.roundToInt()}s" else String.format(java.util.Locale.US, "%.1fs", stepDurSec)
            val fadeLabel = if (stepFadeSec % 1f == 0f) "${stepFadeSec.roundToInt()}s" else String.format(java.util.Locale.US, "%.1fs", stepFadeSec)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isRunning) Lime.copy(alpha = 0.14f) else Ink.copy(alpha = 0.45f))
                    .border(if (isRunning) 1.5.dp else 1.dp, if (isRunning) Lime else Line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Row: Full-width title + steps & timing summary
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = plEntry.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) Lime else Cream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isRunning) Lime.copy(alpha = 0.2f) else Panel)
                                .border(1.dp, if (isRunning) Lime else Line, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isRunning) "PLAYING #${plEntry.id}" else "ID #${plEntry.id}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRunning) Lime else Muted,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text(
                        text = "Steps: ${spec.presets.joinToString(" → ") { "#$it" }} · ${durLabel}/step · ${fadeLabel} fade · " +
                            if (spec.repeat == 0) "Loop" else "Repeat ${spec.repeat}×",
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Bottom Row: Aligned Play CTA on left + Favorite, Edit, Delete actions on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            model.command(obj("ps" to plEntry.id, "on" to true), successNotice = "Started playlist ${plEntry.name}")
                        },
                        shape = RoundedCornerShape(11.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Lime.copy(alpha = 0.16f),
                            contentColor = Lime
                        )
                    ) {
                        Icon(Icons.Outlined.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play Playlist", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { model.toggleFavoritePreset(plEntry.id) }) {
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite playlist ${plEntry.name}",
                                tint = if (isFav) Lime else Muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                plIdText = plEntry.id.toString()
                                plName = plEntry.name
                                plSelectedPresets = spec.presets
                                plDurationSec = ((spec.durationsTenths.firstOrNull() ?: 100) / 10f).coerceIn(1f, 120f)
                                plTransitionSec = ((spec.transitionsTenths.firstOrNull() ?: 7) / 10f).coerceIn(0f, 10f)
                                plRepeat = spec.repeat.toString()
                                plEndPreset = spec.endPreset.toString()
                                plShuffle = spec.shuffle
                                lastSavedPlaylistBanner = "Editing playlist #${plEntry.id} (${plEntry.name})"
                            }
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit playlist ${plEntry.name}", tint = Lime, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { deletingEntry = plEntry }) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete playlist ${plEntry.name}", tint = Muted, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }

    // 5. Standalone Create or Edit Playlist Card
    val parsedPlId = (plIdText.toIntOrNull() ?: nextFreeId).coerceIn(1, 250)
    val isEditingExisting = playlistsOnly.any { it.id == parsedPlId }
    val effectivePlName = plName.trim().ifBlank { "Custom Playlist #$parsedPlId" }
    val effectiveSteps = plSelectedPresets.ifEmpty {
        presetsOnly.take(2).map { it.id }.ifEmpty { listOf(1) }
    }

    RoomCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Create or Edit Playlist",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isEditingExisting) {
                        "Updating existing Slot #$parsedPlId · ${effectiveSteps.size} preset steps"
                    } else {
                        "New Slot #$parsedPlId · ${effectiveSteps.size} preset steps"
                    },
                    fontSize = 11.sp,
                    color = if (isEditingExisting) Lime else Muted,
                    maxLines = 1,
                    softWrap = false
                )
            }
            OutlinedButton(
                onClick = {
                    val freshId = ((10..250).firstOrNull { id -> entries.none { it.id == id } } ?: nextFreeId).coerceIn(1, 250)
                    plIdText = freshId.toString()
                    plName = "Custom Playlist #$freshId"
                    plSelectedPresets = presetsOnly.take(3).map { it.id }.ifEmpty { listOf(1, 2, 3) }
                    lastSavedPlaylistBanner = null
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Slot", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = plIdText,
                onValueChange = { plIdText = it.filter(Char::isDigit).take(3) },
                label = { Text("ID (1–250)", maxLines = 1, softWrap = false) },
                singleLine = true,
                modifier = Modifier.weight(0.95f)
            )
            OutlinedTextField(
                value = plName,
                onValueChange = { plName = it.take(32) },
                label = { Text("Playlist name", maxLines = 1, softWrap = false) },
                placeholder = { Text("Evening Cycle", maxLines = 1, softWrap = false) },
                singleLine = true,
                modifier = Modifier.weight(1.65f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.copy(alpha = 0.55f))
                .border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallLabel("SEQUENCE STEPS (${effectiveSteps.size})")
                if (plSelectedPresets.isNotEmpty()) {
                    Text(
                        text = "Clear all",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Lime,
                        modifier = Modifier.clickable { plSelectedPresets = emptyList() }
                    )
                } else {
                    Text(
                        text = "Reset defaults",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Lime,
                        modifier = Modifier.clickable {
                            plSelectedPresets = presetsOnly.take(3).map { it.id }.ifEmpty { listOf(1, 2, 3) }
                        }
                    )
                }
            }

            if (effectiveSteps.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    effectiveSteps.forEachIndexed { stepIdx, pid ->
                        val pName = presetsOnly.firstOrNull { it.id == pid }?.name ?: "Preset #$pid"
                        FilterChip(
                            selected = true,
                            onClick = {
                                val base = if (plSelectedPresets.isNotEmpty()) plSelectedPresets else effectiveSteps
                                plSelectedPresets = base.toMutableList().also {
                                    if (stepIdx in it.indices) it.removeAt(stepIdx)
                                }
                            },
                            label = {
                                Text(
                                    text = "${stepIdx + 1}. #$pid $pName ✕",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }
            }

            SmallLabel("TAP PRESET TO ADD TO SEQUENCE")
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presetsOnly.forEach { p ->
                    AssistChip(
                        onClick = {
                            val base = if (plSelectedPresets.isNotEmpty()) plSelectedPresets else effectiveSteps
                            plSelectedPresets = base + p.id
                        },
                        label = {
                            Text(
                                text = "+ #${p.id} ${p.name}",
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
        }

        SettingSlider("Step duration", plDurationSec, { plDurationSec = it }, 1f..120f, " s")
        SettingSlider("Step transition", plTransitionSec, { plTransitionSec = it }, 0f..10f, " s")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = plRepeat,
                onValueChange = { plRepeat = it.filter(Char::isDigit).take(3) },
                label = { Text("Repeat (0 = loop)", maxLines = 1, softWrap = false) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = plEndPreset,
                onValueChange = { plEndPreset = it.filter(Char::isDigit).take(3) },
                label = { Text("End preset ID", maxLines = 1, softWrap = false) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Ink.copy(alpha = 0.45f))
                .border(1.dp, Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Shuffle preset order",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Cream,
                maxLines = 1,
                softWrap = false
            )
            LiquidToggle(
                checked = plShuffle,
                onCheckedChange = { plShuffle = it },
                label = "Shuffle preset order"
            )
        }

        if (!lastSavedPlaylistBanner.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Lime.copy(alpha = 0.14f))
                    .border(1.dp, Lime.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Lime, modifier = Modifier.size(16.dp))
                Text(
                    text = lastSavedPlaylistBanner!!,
                    color = Lime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val durTenths = (plDurationSec * 10f).roundToInt().coerceAtLeast(1)
                    val trTenths = (plTransitionSec * 10f).roundToInt().coerceAtLeast(0)
                    val spec = WledPlaylistSpec(
                        presets = effectiveSteps,
                        durationsTenths = List(effectiveSteps.size) { durTenths },
                        transitionsTenths = List(effectiveSteps.size) { trTenths },
                        repeat = plRepeat.toIntOrNull() ?: 0,
                        endPreset = plEndPreset.toIntOrNull() ?: 0,
                        shuffle = plShuffle
                    )
                    plIdText = parsedPlId.toString()
                    plName = effectivePlName
                    plSelectedPresets = effectiveSteps
                    model.savePlaylist(parsedPlId, effectivePlName, spec, playImmediately = false)
                    lastSavedPlaylistBanner = "Saved #$parsedPlId \"$effectivePlName\" (${effectiveSteps.joinToString("→") { "#$it" }} · ${plDurationSec.roundToInt()}s/step)"
                },
                enabled = parsedPlId in 1..250,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f).testTag("wled_save_playlist_button")
            ) {
                Icon(Icons.Outlined.PlaylistAddCheck, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save #$parsedPlId", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = {
                    val durTenths = (plDurationSec * 10f).roundToInt().coerceAtLeast(1)
                    val trTenths = (plTransitionSec * 10f).roundToInt().coerceAtLeast(0)
                    val spec = WledPlaylistSpec(
                        presets = effectiveSteps,
                        durationsTenths = List(effectiveSteps.size) { durTenths },
                        transitionsTenths = List(effectiveSteps.size) { trTenths },
                        repeat = plRepeat.toIntOrNull() ?: 0,
                        endPreset = plEndPreset.toIntOrNull() ?: 0,
                        shuffle = plShuffle
                    )
                    plIdText = parsedPlId.toString()
                    plName = effectivePlName
                    plSelectedPresets = effectiveSteps
                    model.savePlaylist(parsedPlId, effectivePlName, spec, playImmediately = true)
                    lastSavedPlaylistBanner = "Saved & playing #$parsedPlId \"$effectivePlName\" (${effectiveSteps.joinToString("→") { "#$it" }})"
                },
                enabled = parsedPlId in 1..250,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f).testTag("wled_save_and_play_playlist_button")
            ) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save & Play", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }

    // Extra bottom clearance so floating notice snackbars never obscure Save #ID / Save & Play buttons
    Spacer(Modifier.height(76.dp))

    deletingEntry?.let { entry ->
        ConfirmDialog(
            title = "Delete ${entry.name} (#${entry.id})?",
            description = "This removes #${entry.id} from WLED's /presets.json.",
            onDismiss = { deletingEntry = null }
        ) {
            model.deletePreset(entry.id)
        }
    }
}

@Composable
fun WledDevicesCard(
    model: WledModel,
    ui: WledUi,
    roomModel: RoomModel? = null,
    roomUi: RoomUi? = null
) {
    var address by rememberSaveable { mutableStateOf("") }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WledDevice?>(null) }
    var deleting by remember { mutableStateOf<WledDevice?>(null) }
    var name by rememberSaveable { mutableStateOf("") }

    RoomCard {
        val bothOnline = ui.online && roomUi?.signedIn == true && roomUi.stale == false && !roomUi.demo
        SectionLabel(
            "Controllers",
            when {
                bothOnline -> "HTTP + HTTPS"
                ui.online -> "WLED ONLINE"
                roomUi?.signedIn == true && !roomUi.stale && !roomUi.demo -> "ROOM ONLINE"
                else -> "DISCOVER"
            }
        )
        Text(
            "Your ESP32 serves WLED lighting & AudioReactive on HTTP/WebSocket port 80 and authenticated Room IR, Spotify & routines on pinned HTTPS port 443. Room never sends your HTTPS bearer token to WLED HTTP.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        if (ui.online && ui.snapshot != null) {
            Text(
                "Active WLED: ${ui.device?.name} · v${ui.snapshot.version} · MAC ${ui.snapshot.mac.uppercase()} · " +
                    "${if (ui.wsConnected) "WebSocket live" else "HTTP polling"} · Uptime ${ui.snapshot.uptimeSeconds / 60}m",
                color = Lime,
                fontSize = 11.sp
            )
        }
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Controller Wi-Fi address") },
            placeholder = { Text("192.168.1.105, wled.local, or 4.3.2.1 (setup AP)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("wled_address_input")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Connect", Icons.Outlined.Link, !ui.busy && address.isNotBlank()) {
                model.add(address)
                if (roomModel != null && roomModel.pairing != null) {
                    val host = runCatching { java.net.URI(wledOrigin(address)).host }.getOrNull()
                    if (!host.isNullOrBlank()) {
                        roomModel.connectDevice("https://$host")
                    }
                }
            }
            OutlinedButton(onClick = model::discover, enabled = !ui.discovering) {
                Text(if (ui.discovering) "Searching…" else "Discover on Wi-Fi")
            }
        }
        if (ui.devices.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showHidden, { showHidden = it })
                Text("Show hidden controllers", color = Muted, fontSize = 12.sp)
            }
        }
        ui.devices.filter { !it.hidden || showHidden }.forEach { device ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { model.select(device.id) },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ui.selected == device.id, onClick = { model.select(device.id) })
                    Column(Modifier.weight(1f)) {
                        Text(device.name, fontSize = 16.sp)
                        val peerNote = device.linkedRoomHost?.let { " · Room HTTPS host: $it" }.orEmpty()
                        Text("${device.origin} · ID ${device.id.takeLast(6)}$peerNote", color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { editing = device; name = device.name }) {
                        Icon(Icons.Outlined.Edit, "Rename ${device.name}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.hide(device.id, !device.hidden) }) {
                        Text(if (device.hidden) "Show" else "Hide")
                    }
                    TextButton(onClick = { deleting = device }) { Text("Remove") }
                    if (ui.selected == device.id) {
                        TextButton(onClick = { model.select(device.id) }) { Text("Reconnect WLED") }
                    }
                    if (roomModel != null && roomModel.pairing != null) {
                        val host = runCatching { java.net.URI(device.origin).host }.getOrNull()
                        if (!host.isNullOrBlank()) {
                            TextButton(onClick = { roomModel.connectDevice("https://$host") }) {
                                Text("Link Room HTTPS")
                            }
                        }
                    }
                }
                HorizontalDivider(color = Line)
            }
        }
    }
    editing?.let { device ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Name your lighting") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name in Room") }) },
            confirmButton = {
                TextButton(
                    onClick = { model.rename(device.id, name); editing = null },
                    enabled = name.trim().length in 1..64
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { device ->
        ConfirmDialog(
            "Remove ${device.name}?",
            "This removes its shortcut from Room. The controller settings stay on the ESP32.",
            { deleting = null }
        ) { model.remove(device.id) }
    }
}

private val Mint = Color(0xFF6EE7C8)
private val Coral = Color(0xFFFF7A59)

private data class Esp32MusicEffectSpec(
    val id: String,
    val title: String,
    val badge: String,
    val subtitle: String,
    val icon: ImageVector
)

private val ESP32_NATIVE_MUSIC_MODES = listOf(
    Esp32MusicEffectSpec(
        id = "beat",
        title = "Beat Pulse",
        badge = "BEAT + FLUX",
        subtitle = "Core 0 beat, bar, drop & spectral flux triggers with tunable pulse decay.",
        icon = Icons.Outlined.Bolt
    ),
    Esp32MusicEffectSpec(
        id = "bass",
        title = "Bass Reactor",
        badge = "SUB-BASS",
        subtitle = "Drives the 300-LED strip from low-frequency sub-bass energy (DSP band #0).",
        icon = Icons.Outlined.Speaker
    ),
    Esp32MusicEffectSpec(
        id = "bands",
        title = "16-Band Spectrum",
        badge = "16-BAND FFT",
        subtitle = "Maps Core 0's 16-band FFT (Bass #0, Mids #6, Treble #13) across the strip.",
        icon = Icons.Outlined.Equalizer
    ),
    Esp32MusicEffectSpec(
        id = "level",
        title = "RMS Level Meter",
        badge = "REAL-TIME VU",
        subtitle = "Continuous acoustic RMS VU bar across 300 pixels with noise-floor gating.",
        icon = Icons.Outlined.GraphicEq
    ),
    Esp32MusicEffectSpec(
        id = "solid",
        title = "Solid Base",
        badge = "STATIC",
        subtitle = "Gamma-corrected solid color rendered on Core 1 without audio modulation.",
        icon = Icons.Outlined.LightMode
    )
)

@Composable
private fun Esp32MusicPipelineQuickBanner(
    selectedTab: String,
    roomUi: RoomUi?,
    onSelectEsp32Music: () -> Unit,
    onSelectWledFx: () -> Unit
) {
    val isEsp32Selected = selectedTab == "ESP32 Music"
    val roomState = roomUi?.state ?: JSONObject()
    val roomLighting = roomState.optJSONObject("lighting")
    val roomMic = roomState.optJSONObject("microphone")
    val roomTelemetry = roomState.optJSONObject("telemetry")
    val activeNativeEffect = roomLighting?.optString("effect", "beat")?.ifBlank { "beat" } ?: "beat"
    val isNativeReactive = activeNativeEffect in setOf("beat", "bass", "bands", "level")
    val soundActive = roomTelemetry?.optBoolean("sound_active", roomMic?.optBoolean("sound_active", false) ?: false) ?: false
    val rmsLevel = roomTelemetry?.optDouble("rms", roomMic?.optDouble("level", 0.24) ?: 0.24) ?: 0.24

    val borderCol by animateColorAsState(
        targetValue = if (isEsp32Selected) Mint else Line,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "esp32BannerBorder"
    )
    val bgCol by animateColorAsState(
        targetValue = if (isEsp32Selected) Mint.copy(alpha = 0.12f) else Panel,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "esp32BannerBg"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgCol)
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("lights_music_pipeline_selector_banner"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = if (isEsp32Selected || isNativeReactive) Mint else Lime,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Music Response",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (roomMic != null || roomTelemetry != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (soundActive) Mint.copy(alpha = 0.18f) else Ink)
                        .border(1.dp, if (soundActive) Mint else Line, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (soundActive) "SOUND ${(rmsLevel * 100.0).roundToInt()}%" else "QUIET",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (soundActive) Mint else Muted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Zero-gap Segmented Pill Switcher for ESP32 Music vs WLED FX
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Ink)
                .border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isEsp32Selected) Mint.copy(alpha = 0.22f) else Color.Transparent)
                    .border(
                        width = if (isEsp32Selected) 1.dp else 0.dp,
                        color = if (isEsp32Selected) Mint else Color.Transparent,
                        shape = RoundedCornerShape(11.dp)
                    )
                    .livelyClick(pressedScale = 0.96f, onClick = onSelectEsp32Music)
                    .padding(horizontal = 10.dp)
                    .testTag("pipeline_select_esp32_native"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = if (isEsp32Selected) Mint else Muted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "ESP32 Music",
                    fontSize = 12.sp,
                    fontWeight = if (isEsp32Selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isEsp32Selected) Mint else Cream,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (!isEsp32Selected) Lime.copy(alpha = 0.20f) else Color.Transparent)
                    .border(
                        width = if (!isEsp32Selected) 1.dp else 0.dp,
                        color = if (!isEsp32Selected) Lime else Color.Transparent,
                        shape = RoundedCornerShape(11.dp)
                    )
                    .livelyClick(pressedScale = 0.96f, onClick = onSelectWledFx)
                    .padding(horizontal = 10.dp)
                    .testTag("pipeline_select_wled_engine"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = if (!isEsp32Selected) Lime else Muted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "WLED FX",
                    fontSize = 12.sp,
                    fontWeight = if (!isEsp32Selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (!isEsp32Selected) Lime else Cream,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun Esp32NativeMusicPipelineTab(
    model: WledModel,
    ui: WledUi,
    snap: WledSnapshot,
    roomModel: RoomModel?,
    roomUi: RoomUi?
) {
    val roomState = roomUi?.state ?: JSONObject()
    val lightingJson = roomState.optJSONObject("lighting") ?: JSONObject()
    val micJson = roomState.optJSONObject("microphone") ?: JSONObject()
    val telemetryJson = roomState.optJSONObject("telemetry") ?: JSONObject()

    val initPower = lightingJson.optBoolean("power", snap.on)
    val initEffect = lightingJson.optString("effect", "beat").lowercase().ifBlank { "beat" }
    val initColor = lightingJson.optString("color", snap.primarySegment.colorHex(0)).ifBlank { snap.primarySegment.colorHex(0) }
    val initBrightness = lightingJson.optInt("brightness", wledPercent(snap.bri)).coerceIn(0, 100)
    val initSpeed = lightingJson.optInt("speed", 58).coerceIn(0, 100)
    val initIntensity = lightingJson.optInt("intensity", 76).coerceIn(0, 100)

    val initMicAuto = micJson.optBoolean("automatic", true)
    val initMicSensitivity = micJson.optInt("sensitivity", 68).coerceIn(1, 100)
    val initMicNoiseFloor = micJson.optInt("noise_floor", 14).coerceIn(0, 100)
    val initMicSmoothing = micJson.optInt("smoothing", 65).coerceIn(0, 99)
    val initQuietBehavior = micJson.optString("quiet_behavior", "dim").ifBlank { "dim" }
    val initQuietSeconds = micJson.optInt("quiet_seconds", 12).coerceIn(1, 300)
    val initBassGain = micJson.optInt("bass_gain", 100).coerceIn(0, 200)
    val initMidGain = micJson.optInt("mid_gain", 100).coerceIn(0, 200)
    val initTrebleGain = micJson.optInt("treble_gain", 100).coerceIn(0, 200)

    val soundActive = telemetryJson.optBoolean("sound_active", micJson.optBoolean("sound_active", true))
    val rmsLevel = telemetryJson.optDouble("rms", micJson.optDouble("level", 0.28)).coerceIn(0.0, 1.0)
    val bandsArr = telemetryJson.optJSONArray("bands") ?: micJson.optJSONArray("bands")
    val band0 = (bandsArr?.optDouble(0, 0.48) ?: 0.48).toFloat().coerceIn(0f, 1f)
    val band1 = (bandsArr?.optDouble(1, 0.34) ?: 0.34).toFloat().coerceIn(0f, 1f)
    val band2 = (bandsArr?.optDouble(2, 0.22) ?: 0.22).toFloat().coerceIn(0f, 1f)

    var power by remember(initPower) { mutableStateOf(initPower) }
    var selectedEffect by remember(initEffect) { mutableStateOf(initEffect) }
    var colorHex by remember(initColor) { mutableStateOf(initColor) }
    var brightness by remember(initBrightness) { mutableFloatStateOf(initBrightness.toFloat()) }
    var speed by remember(initSpeed) { mutableFloatStateOf(initSpeed.toFloat()) }
    var intensity by remember(initIntensity) { mutableFloatStateOf(initIntensity.toFloat()) }

    var micAuto by remember(initMicAuto) { mutableStateOf(initMicAuto) }
    var micSensitivity by remember(initMicSensitivity) { mutableFloatStateOf(initMicSensitivity.toFloat()) }
    var micNoiseFloor by remember(initMicNoiseFloor) { mutableFloatStateOf(initMicNoiseFloor.toFloat()) }
    var micSmoothing by remember(initMicSmoothing) { mutableFloatStateOf(initMicSmoothing.toFloat()) }
    var quietBehavior by remember(initQuietBehavior) { mutableStateOf(initQuietBehavior) }
    var quietSeconds by remember(initQuietSeconds) { mutableIntStateOf(initQuietSeconds) }
    var bassGain by remember(initBassGain) { mutableFloatStateOf(initBassGain.toFloat()) }
    var midGain by remember(initMidGain) { mutableFloatStateOf(initMidGain.toFloat()) }
    var trebleGain by remember(initTrebleGain) { mutableFloatStateOf(initTrebleGain.toFloat()) }

    fun pushNativeLighting(
        nextPower: Boolean = power,
        nextColor: String = colorHex,
        nextBri: Int = brightness.roundToInt().coerceIn(0, 100),
        nextEffect: String = selectedEffect,
        nextSpeed: Int = speed.roundToInt().coerceIn(0, 100),
        nextIntensity: Int = intensity.roundToInt().coerceIn(0, 100),
        commitNotice: String? = null
    ) {
        val payload = obj(
            "power" to nextPower,
            "color" to nextColor,
            "brightness" to nextBri,
            "effect" to nextEffect,
            "speed" to nextSpeed,
            "intensity" to nextIntensity
        )
        if (roomModel != null) {
            if (commitNotice != null) {
                roomModel.mutate("POST", "/actions", action("lighting", payload), commitNotice)
            } else {
                roomModel.coalescedLighting(payload)
            }
        } else {
            model.coalescedCommand(
                "esp32-music-fallback",
                obj("on" to nextPower, "bri" to wledByte(nextBri.toFloat()))
            )
        }
    }

    fun pushNativeMicrophone(
        nextAuto: Boolean = micAuto,
        nextSensitivity: Int = micSensitivity.roundToInt().coerceIn(1, 100),
        nextNoiseFloor: Int = micNoiseFloor.roundToInt().coerceIn(0, 100),
        nextSmoothing: Int = micSmoothing.roundToInt().coerceIn(0, 99),
        nextQuietBehavior: String = quietBehavior,
        nextQuietSeconds: Int = quietSeconds.coerceIn(1, 300),
        nextBassGain: Int = bassGain.roundToInt().coerceIn(0, 200),
        nextMidGain: Int = midGain.roundToInt().coerceIn(0, 200),
        nextTrebleGain: Int = trebleGain.roundToInt().coerceIn(0, 200),
        commitNotice: String? = null
    ) {
        val micPayload = obj(
            "automatic" to nextAuto,
            "sensitivity" to nextSensitivity,
            "noise_floor" to nextNoiseFloor,
            "smoothing" to nextSmoothing,
            "quiet_behavior" to nextQuietBehavior,
            "quiet_seconds" to nextQuietSeconds,
            "bass_gain" to nextBassGain,
            "mid_gain" to nextMidGain,
            "treble_gain" to nextTrebleGain
        )
        if (roomModel != null) {
            if (commitNotice != null) {
                roomModel.mutate("POST", "/actions", action("microphone", micPayload), commitNotice)
            } else {
                roomModel.coalescedMicrophone(micPayload)
            }
        }
    }

    // 1. Real-Time DSP Telemetry & Interactive 3-Band EQ (Bass, Mids, Treble) Card
    RoomCard(modifier = Modifier.testTag("esp32_music_pipeline_card")) {
        SectionLabel(
            "ESP32 Music DSP",
            "1.1.4-MUSIC"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FFT REAL-TIME · RMS ${(rmsLevel * 100.0).roundToInt()}%",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (soundActive) "● SOUND ACTIVE" else "○ QUIET (${quietBehavior.uppercase()})",
                color = if (soundActive) Mint else Coral,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }

        // Interactive 3-Band FFT & Equalizer Sliders (Bass #0, Mids #6, Treble #13) — directly controllable!
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.copy(alpha = 0.68f))
                .border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Bass #0 Interactive Gain & Live FFT Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bass #0 (Sub / Kick)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cream,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "FFT ${((band0 * (bassGain / 100f)).coerceIn(0f, 2f) * 100).roundToInt()}% · Gain ${bassGain.roundToInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Coral,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                LedSlider(
                    value = (bassGain / 200f).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        val next = (frac * 200f).coerceIn(0f, 200f)
                        bassGain = next
                        pushNativeMicrophone(nextBassGain = next.roundToInt())
                    },
                    trackBrush = Brush.horizontalGradient(listOf(Coral.copy(alpha = 0.35f), Coral)),
                    contentDescription = "Bass #0 gain slider",
                    thumbColor = Coral
                )
            }

            // 2. Mids #6 Interactive Gain & Live FFT Meter (Never wraps into 2-3 lines at any setting)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mids #6 (Vocals / Snare)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cream,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "FFT ${((band1 * (midGain / 100f)).coerceIn(0f, 2f) * 100).roundToInt()}% · Gain ${midGain.roundToInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Lime,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                LedSlider(
                    value = (midGain / 200f).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        val next = (frac * 200f).coerceIn(0f, 200f)
                        midGain = next
                        pushNativeMicrophone(nextMidGain = next.roundToInt())
                    },
                    trackBrush = Brush.horizontalGradient(listOf(Lime.copy(alpha = 0.35f), Lime)),
                    contentDescription = "Mids #6 gain slider",
                    thumbColor = Lime
                )
            }

            // 3. Treble #13 Interactive Gain & Live FFT Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Treble #13 (Hi-Hat / Air)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cream,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "FFT ${((band2 * (trebleGain / 100f)).coerceIn(0f, 2f) * 100).roundToInt()}% · Gain ${trebleGain.roundToInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Mint,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                LedSlider(
                    value = (trebleGain / 200f).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        val next = (frac * 200f).coerceIn(0f, 200f)
                        trebleGain = next
                        pushNativeMicrophone(nextTrebleGain = next.roundToInt())
                    },
                    trackBrush = Brush.horizontalGradient(listOf(Mint.copy(alpha = 0.35f), Mint)),
                    contentDescription = "Treble #13 gain slider",
                    thumbColor = Mint
                )
            }
        }
    }

    // 2. Inbuilt ESP32 Music Response Mode Selector Card (Contracted & Single-Line Headers)
    RoomCard(modifier = Modifier.testTag("esp32_music_mode_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SmallLabel("ESP32 MUSIC RESPONSE MODE")
                Text(
                    text = "Native Lighting Engine",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (power) Cream else Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LiquidToggle(
                checked = power,
                onCheckedChange = {
                    power = it
                    pushNativeLighting(nextPower = it, commitNotice = if (it) "ESP32 lighting enabled" else "ESP32 lighting off")
                },
                label = "ESP32 Native Lighting Power"
            )
        }

        Column(
            modifier = Modifier.alpha(if (power) 1f else 0.42f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ESP32_NATIVE_MUSIC_MODES.forEach { mode ->
                val isSelected = selectedEffect == mode.id
                val rowBg by animateColorAsState(
                    targetValue = if (isSelected) Mint.copy(alpha = 0.16f) else Ink.copy(alpha = 0.55f),
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "esp32ModeBg_${mode.id}"
                )
                val rowBorder by animateColorAsState(
                    targetValue = if (isSelected) Mint else Line,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "esp32ModeBorder_${mode.id}"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(rowBg)
                        .border(if (isSelected) 1.5.dp else 1.dp, rowBorder, RoundedCornerShape(14.dp))
                        .livelyClick(enabled = power, pressedScale = 0.97f) {
                            selectedEffect = mode.id
                            power = true
                            pushNativeLighting(
                                nextPower = true,
                                nextEffect = mode.id,
                                commitNotice = "Activated inbuilt ESP32 '${mode.title}' (${mode.id}) pipeline"
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = if (isSelected) 10.dp else 8.dp)
                        .testTag("esp32_music_mode_${mode.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Mint.copy(alpha = 0.22f) else Panel)
                            .border(1.dp, if (isSelected) Mint else Line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mode.icon,
                            contentDescription = null,
                            tint = if (isSelected) Mint else Cream,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isSelected) "● ${mode.title}" else mode.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Mint else Cream,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Mint.copy(alpha = 0.18f) else Line.copy(alpha = 0.7f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = mode.badge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Mint else Lime,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        if (isSelected) {
                            Text(
                                text = mode.subtitle,
                                fontSize = 11.sp,
                                color = Muted,
                                lineHeight = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // 3. Smooth Response Tuning Card (disabled when Native Lighting Power is OFF)
    RoomCard(
        modifier = Modifier
            .alpha(if (power) 1f else 0.42f)
            .testTag("esp32_music_tuning_card")
    ) {
        SectionLabel(
            "Response Tuning",
            if (power) selectedEffect.uppercase() else "OFF"
        )

        BrightnessControl(
            value = brightness,
            enabled = power,
            onChange = { nextBri ->
                brightness = nextBri
                pushNativeLighting(nextBri = nextBri.roundToInt())
            },
            onFinished = {
                pushNativeLighting(nextBri = brightness.roundToInt())
            }
        )

        Box(modifier = Modifier.testTag("esp32_music_speed_slider")) {
            SettingSlider(
                title = "Pulse Decay / Speed",
                value = speed,
                onChange = { nextSpd ->
                    speed = nextSpd
                    pushNativeLighting(nextSpeed = nextSpd.roundToInt())
                },
                range = 0f..100f,
                suffix = "%",
                enabled = power,
                onFinished = {
                    pushNativeLighting(nextSpeed = speed.roundToInt())
                }
            )
        }

        Box(modifier = Modifier.testTag("esp32_music_intensity_slider")) {
            SettingSlider(
                title = "Music Response Intensity",
                value = intensity,
                onChange = { nextInt ->
                    intensity = nextInt
                    pushNativeLighting(nextIntensity = nextInt.roundToInt())
                },
                range = 0f..100f,
                suffix = "%",
                enabled = power,
                onFinished = {
                    pushNativeLighting(nextIntensity = intensity.roundToInt())
                }
            )
        }

        SmallLabel("BASE PALETTE / SOLID COLOR ($colorHex)")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val swatches = listOf("#F3FF6B", "#6EE7C8", "#FF7A59", "#A78BFA", "#38BDF8", "#F43F5E", "#FFB46B", "#FFFFFF")
            swatches.forEach { hex ->
                val isSelected = colorHex.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(parseColor(hex))
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) Cream else Color.White.copy(alpha = 0.24f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .livelyClick(enabled = power, pressedScale = 0.90f) {
                            colorHex = hex
                            pushNativeLighting(nextColor = hex, commitNotice = "Set ESP32 music base color $hex")
                        }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).testTag("esp32_music_apply_button")) {
                ActionButton(
                    text = "Apply ESP32",
                    icon = Icons.Outlined.CheckCircle,
                    enabled = power,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pushNativeLighting(
                        commitNotice = "Applied ESP32 ${selectedEffect.uppercase()} (${brightness.roundToInt()}% · spd ${speed.roundToInt()}% · int ${intensity.roundToInt()}%)"
                    )
                }
            }
            if (roomModel != null) {
                OutlinedButton(
                    onClick = {
                        val routineName = "ESP32 ${selectedEffect.replaceFirstChar { it.uppercase() }}"
                        val newLightingJson = obj(
                            "power" to true,
                            "color" to colorHex,
                            "brightness" to brightness.roundToInt(),
                            "effect" to selectedEffect,
                            "speed" to speed.roundToInt(),
                            "intensity" to intensity.roundToInt()
                        )
                        roomModel.mutate(
                            "POST",
                            "/presets",
                            obj("name" to routineName, "lighting" to newLightingJson),
                            "Saved '$routineName' to Room presets"
                        )
                    },
                    enabled = power,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f).testTag("esp32_music_save_preset_button")
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Preset", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }

    // 4. INMP441 I²S Microphone Gain, 3-Band EQ (Bass/Mid/Treble) & Quiet-Room Policy Card
    RoomCard(modifier = Modifier.testTag("esp32_music_mic_card")) {
        SectionLabel(
            "Mic Gain & 3-Band EQ",
            if (micAuto) "AGC ON" else "MANUAL ${micSensitivity.roundToInt()}%"
        )

        SettingToggleRow(
            title = "Automatic Gain Control (AGC)",
            subtitle = "Adapts INMP441 sensitivity dynamically to room volume",
            checked = micAuto,
            onCheckedChange = {
                micAuto = it
                pushNativeMicrophone(nextAuto = it)
            }
        )

        SettingSlider(
            title = "Manual Mic Sensitivity",
            value = micSensitivity,
            onChange = {
                micSensitivity = it
                pushNativeMicrophone(nextSensitivity = it.roundToInt())
            },
            range = 1f..100f,
            suffix = "%",
            enabled = !micAuto
        )

        SettingSlider(
            title = "Acoustic Noise Floor Gate",
            value = micNoiseFloor,
            onChange = {
                micNoiseFloor = it
                pushNativeMicrophone(nextNoiseFloor = it.roundToInt())
            },
            range = 0f..100f,
            suffix = "%"
        )

        SettingSlider(
            title = "FFT Temporal Smoothing",
            value = micSmoothing,
            onChange = {
                micSmoothing = it
                pushNativeMicrophone(nextSmoothing = it.roundToInt())
            },
            range = 0f..99f,
            suffix = "%"
        )

        HorizontalDivider(color = Line)
        SmallLabel("3-BAND EQUALIZER GAIN (BASS · MID · TREBLE)")
        SettingSlider(
            title = "Bass Gain (Sub / Kick 30–180 Hz)",
            value = bassGain,
            onChange = {
                bassGain = it
                pushNativeMicrophone(nextBassGain = it.roundToInt())
            },
            range = 0f..200f,
            suffix = "%"
        )
        SettingSlider(
            title = "Mid Gain (Vocals / Snare 200–2400 Hz)",
            value = midGain,
            onChange = {
                midGain = it
                pushNativeMicrophone(nextMidGain = it.roundToInt())
            },
            range = 0f..200f,
            suffix = "%"
        )
        SettingSlider(
            title = "Treble Gain (Hi-Hat / Air 2.5–8 kHz)",
            value = trebleGain,
            onChange = {
                trebleGain = it
                pushNativeMicrophone(nextTrebleGain = it.roundToInt())
            },
            range = 0f..200f,
            suffix = "%"
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Triple("Flat (100%)", 100, 100 to 100),
                Triple("Bass Boost", 145, 95 to 105),
                Triple("Vocal / Mid", 90, 140 to 105),
                Triple("Treble Sparkle", 95, 105 to 150),
                Triple("Club V-Curve", 140, 88 to 138)
            ).forEach { (presetLabel, bVal, mtPair) ->
                val (mVal, tVal) = mtPair
                FilterChip(
                    selected = bassGain.roundToInt() == bVal && midGain.roundToInt() == mVal && trebleGain.roundToInt() == tVal,
                    onClick = {
                        bassGain = bVal.toFloat()
                        midGain = mVal.toFloat()
                        trebleGain = tVal.toFloat()
                        pushNativeMicrophone(nextBassGain = bVal, nextMidGain = mVal, nextTrebleGain = tVal)
                    },
                    label = { Text(presetLabel, fontSize = 11.sp) }
                )
            }
        }

        HorizontalDivider(color = Line)
        SmallLabel("QUIET ROOM BEHAVIOR & TIMEOUT (${quietSeconds}S)")
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "dim" to "Dim (6% solid + gamma)",
                "off" to "Off (Blackout)",
                "hold" to "Hold last frame"
            ).forEach { (behId, behLabel) ->
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = quietBehavior == behId,
                    onClick = {
                        quietBehavior = behId
                        pushNativeMicrophone(nextQuietBehavior = behId)
                    },
                    interactionSource = interaction,
                    modifier = Modifier.livelyPressScale(interaction),
                    label = { Text(behLabel, fontSize = 11.sp) }
                )
            }
        }

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(5, 10, 15, 30, 60, 120).forEach { sec ->
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = quietSeconds == sec,
                    onClick = {
                        quietSeconds = sec
                        pushNativeMicrophone(nextQuietSeconds = sec)
                    },
                    interactionSource = interaction,
                    modifier = Modifier.livelyPressScale(interaction),
                    label = { Text("${sec}s quiet timeout", fontSize = 11.sp) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                text = "Save Mic & EQ",
                icon = Icons.Outlined.Save,
                enabled = roomModel != null,
                modifier = Modifier.weight(1f)
            ) {
                pushNativeMicrophone(commitNotice = "Saved ESP32 INMP441 microphone & 3-band EQ settings")
            }
            OutlinedButton(
                onClick = {
                    roomModel?.mutate(
                        "POST",
                        "/actions",
                        action("calibrate", obj()),
                        "Started 5-second quiet-room INMP441 noise floor calibration"
                    )
                },
                enabled = roomModel != null,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("5s Calibrate", fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}


