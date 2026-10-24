package `in`.roomcontroller.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.roundToInt

/** Native adaptation of the requested IsoFader interaction; no WebView is needed. */
@Composable
fun IsoFader(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val accent = Lime
    val line = Line
    val ink = Ink
    val text = Cream
    val latestChange by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .semantics {
                    contentDescription = "$label response"
                    stateDescription = "${value.roundToInt()} percent, 100 percent is neutral"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..200f)
                    if (!enabled) disabled()
                    if (enabled) setProgress { latestChange(it.coerceIn(0f, 200f)); true }
                }
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures { p ->
                        latestChange(((.86f - p.y / size.height) / .72f * 200f).coerceIn(0f, 200f))
                    }
                }
                .pointerInput(enabled) {
                    if (enabled) detectDragGestures(onDragStart = { p ->
                        latestChange(((.86f - p.y / size.height) / .72f * 200f).coerceIn(0f, 200f))
                    }) { change, _ ->
                        change.consume()
                        latestChange(((.86f - change.position.y / size.height) / .72f * 200f).coerceIn(0f, 200f))
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val opacity = if (enabled) 1f else .4f
            fun polygon(color: Color, points: List<Offset>) {
                drawPath(Path().apply {
                    moveTo(points[0].x, points[0].y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }, color.copy(alpha = color.alpha * opacity))
            }
            // A sloping console lane with a raised, three-faced thumb.
            polygon(ink, listOf(Offset(w * .12f, h * .91f), Offset(w * .43f, h * .05f), Offset(w * .91f, h * .10f), Offset(w * .60f, h * .96f)))
            polygon(line.copy(alpha = .6f), listOf(Offset(w * .12f, h * .91f), Offset(w * .60f, h * .96f), Offset(w * .60f, h * .98f), Offset(w * .12f, h * .93f)))
            val start = Offset(w * .65f, h * .14f)
            val end = Offset(w * .35f, h * .86f)
            val t = 1f - value.coerceIn(0f, 200f) / 200f
            val thumb = start + (end - start) * t
            drawLine(line, start, end, 7.dp.toPx(), StrokeCap.Round)
            drawLine(accent.copy(alpha = .55f * opacity), thumb, end, 3.dp.toPx(), StrokeCap.Round)
            for (tick in 0..4) {
                val point = start + (end - start) * (tick / 4f)
                drawLine(if (tick == 2) accent.copy(alpha = .6f) else line, point + Offset(w * .20f, 0f), point + Offset(w * .28f, h * .006f), 1.dp.toPx())
            }
            val half = minOf(w * .29f, 25.dp.toPx())
            val depth = 9.dp.toPx()
            val tilt = 5.dp.toPx()
            val a = thumb + Offset(-half, -8.dp.toPx() - tilt)
            val b = thumb + Offset(half, -8.dp.toPx() + tilt)
            val c = thumb + Offset(half, 8.dp.toPx() + tilt)
            val d = thumb + Offset(-half, 8.dp.toPx() - tilt)
            polygon(Color.Black.copy(alpha = .22f), listOf(a + Offset(3.dp.toPx(), depth), b + Offset(3.dp.toPx(), depth), c + Offset(3.dp.toPx(), depth * 2), d + Offset(3.dp.toPx(), depth * 2)))
            polygon(accent.copy(alpha = .55f), listOf(d, c, c + Offset(0f, depth), d + Offset(0f, depth)))
            polygon(accent.copy(alpha = .75f), listOf(b, c, c + Offset(0f, depth), b + Offset(0f, depth)))
            polygon(accent, listOf(a, b, c, d))
            drawLine(ink, thumb + Offset(-half * .7f, -tilt * .7f), thumb + Offset(half * .7f, tilt * .7f), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(text.copy(alpha = .15f), 1.5.dp.toPx(), a + Offset(5.dp.toPx(), 4.dp.toPx()))
        }
        Text("${value.roundToInt()}%", color = if (enabled) accent else Muted, fontSize = 18.sp)
    }
}

@Composable
fun SoundTuningScreen(
    model: RoomModel,
    ui: RoomUi,
    wled: WledModel? = null,
    wledUi: WledUi = WledUi()
) {
    LaunchedEffect(wledUi.selected, wledUi.online) {
        if (wled != null && wledUi.online && wledUi.config == null) {
            wled.loadConfig()
        }
    }

    val audioStatus = remember(wledUi.online, wledUi.effectiveSnapshot, wledUi.effectiveConfig, ui.state, ui.stale) {
        evaluateWledAudioStatus(
            wledOnline = wledUi.online,
            snapshot = wledUi.effectiveSnapshot,
            cfg = wledUi.effectiveConfig,
            roomState = ui.state,
            roomStale = ui.stale
        )
    }

    PageTitle(
        "Room microphone",
        "In tune with you.",
        "Autonomous ESP32 INMP441 I2S microphone acquisition, WLED AudioReactive DSP, and Room quiet-response tuning."
    )

    // 1. Live Microphone & AudioReactive Readiness Banner
    RoomCard {
        val badgeColor = when (audioStatus.readiness) {
            AudioReadinessState.HEALTHY_LOCAL, AudioReadinessState.UDP_SYNC_ONLY -> Lime
            AudioReadinessState.SILENT_ROOM -> Color(0xFF9AD1B8)
            AudioReadinessState.STALE_TELEMETRY -> Color(0xFFF6BC7B)
            AudioReadinessState.MIC_INIT_FAILED, AudioReadinessState.MIC_DISABLED, AudioReadinessState.OFFLINE -> Color(0xFFF28D8D)
        }
        SectionLabel("Audio pipeline status", if (ui.demo || !wledUi.online) "PREVIEW" else audioStatus.readiness.badge)
        Text(
            text = if (ui.demo || !wledUi.online)
                "Interactive preview · On your physical ESP32, the INMP441 I2S microphone (BCLK 26, WS 25, SD 35) drives WLED AudioReactive directly on the board."
            else audioStatus.readiness.summary,
            color = if (ui.demo || !wledUi.online) Lime else badgeColor,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Text(
            "Approved board wiring: I2S BCLK = GPIO ${RoomHardwarePins.MIC_BCLK}, WS/LRCLK = GPIO ${RoomHardwarePins.MIC_WS}, SD/DOUT = GPIO ${RoomHardwarePins.MIC_SD}, LED Data = GPIO ${RoomHardwarePins.LED_DATA}.",
            color = Muted,
            fontSize = 11.sp
        )
        audioStatus.audioAgeMs?.let { age ->
            Text("Telemetry sample age: ${age} ms", color = Muted, fontSize = 11.sp)
        }
    }

    // 2. Native WLED AudioReactive Configuration Card (Always visible & interactive)
    if (wled != null) {
        var arEnabled by remember(audioStatus.enabled, wledUi.selected) { mutableStateOf(audioStatus.enabled) }
        var dmicType by remember(audioStatus.dmicType, wledUi.selected) { mutableIntStateOf(audioStatus.dmicType) }
        var pinSd by remember(audioStatus.pinSd, wledUi.selected) { mutableStateOf(audioStatus.pinSd.toString()) }
        var pinWs by remember(audioStatus.pinWs, wledUi.selected) { mutableStateOf(audioStatus.pinWs.toString()) }
        var pinSck by remember(audioStatus.pinSck, wledUi.selected) { mutableStateOf(audioStatus.pinSck.toString()) }
        var squelch by remember(audioStatus.squelch, wledUi.selected) { mutableFloatStateOf(audioStatus.squelch.toFloat()) }
        var gain by remember(audioStatus.gain, wledUi.selected) { mutableFloatStateOf(audioStatus.gain.toFloat()) }
        var agcMode by remember(audioStatus.agcMode, wledUi.selected) { mutableIntStateOf(audioStatus.agcMode) }
        var limiter by remember(audioStatus.limiter, wledUi.selected) { mutableStateOf(audioStatus.limiter) }
        var riseMs by remember(audioStatus.riseMs, wledUi.selected) { mutableFloatStateOf(audioStatus.riseMs.toFloat()) }
        var fallMs by remember(audioStatus.fallMs, wledUi.selected) { mutableFloatStateOf(audioStatus.fallMs.toFloat()) }
        var freqScale by remember(audioStatus.freqScale, wledUi.selected) { mutableIntStateOf(audioStatus.freqScale) }
        var syncMode by remember(audioStatus.syncMode, wledUi.selected) { mutableIntStateOf(audioStatus.syncMode) }
        var syncPort by remember(audioStatus.syncPort, wledUi.selected) { mutableStateOf(audioStatus.syncPort.toString()) }
        var showHardwarePins by rememberSaveable { mutableStateOf(false) }

        val sdInt = pinSd.toIntOrNull() ?: -1
        val wsInt = pinWs.toIntOrNull() ?: -1
        val sckInt = pinSck.toIntOrNull() ?: -1
        val pinError = RoomHardwarePins.validatePinForRole(sdInt, "mic-sd", false)
            ?: RoomHardwarePins.validatePinForRole(wsInt, "mic-ws", true)
            ?: RoomHardwarePins.validatePinForRole(sckInt, "mic-sck", true)
        val requiresReboot = arEnabled != audioStatus.enabled || dmicType != audioStatus.dmicType ||
            sdInt != audioStatus.pinSd || wsInt != audioStatus.pinWs || sckInt != audioStatus.pinSck

        RoomCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("WLED AudioReactive DSP", if (wledUi.online) "LIVE /JSON/CFG" else "SAVED DSP")
                    Text("Controls on-board FFT, squelch gate, sample gain and Automatic Gain Control (AGC).", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                LiquidToggle(
                    checked = arEnabled,
                    onCheckedChange = { arEnabled = it },
                    enabled = !wledUi.busy,
                    label = "Enable WLED AudioReactive",
                    modifier = Modifier.testTag("wled_audioreactive_enable")
                )
            }

            Column(
                modifier = Modifier.alpha(if (arEnabled) 1f else 0.42f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val childEnabled = arEnabled && !wledUi.busy
                SettingSlider("Noise gate / Squelch", squelch, { squelch = it }, 0f..255f, " /255", enabled = childEnabled)
                SettingSlider("Sample input gain", gain, { gain = it }, 0f..255f, " /255", enabled = childEnabled)

                SmallLabel("AUTOMATIC GAIN CONTROL (AGC)")
                val agcOptions = listOf("Off" to 0, "Normal" to 1, "Vivid" to 2, "Lazy" to 3)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    agcOptions.forEach { (label, code) ->
                        FilterChip(
                            selected = agcMode == code,
                            onClick = { agcMode = code },
                            enabled = childEnabled,
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                SmallLabel("FREQUENCY SCALING PROFILE")
                val scaleOptions = listOf("None" to 0, "Logarithmic" to 1, "Linear" to 2, "Square root" to 3)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    scaleOptions.forEach { (label, code) ->
                        FilterChip(
                            selected = freqScale == code,
                            onClick = { freqScale = code },
                            enabled = childEnabled,
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dynamics limiter", fontSize = 14.sp, color = if (childEnabled) Cream else Muted)
                    LiquidToggle(limiter, { limiter = it }, enabled = childEnabled, label = "Dynamics limiter")
                }
                SettingSlider("Attack / Rise time", riseMs, { riseMs = it }, 0f..1000f, " ms", enabled = childEnabled && limiter)
                SettingSlider("Decay / Fall time", fallMs, { fallMs = it }, 0f..5000f, " ms", enabled = childEnabled && limiter)

                SmallLabel("UDP AUDIO SYNC")
                val syncModes = listOf("Off" to 0, "Transmit (Send)" to 1, "Receive" to 2)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    syncModes.forEach { (label, code) ->
                        FilterChip(
                            selected = syncMode == code,
                            onClick = { syncMode = code },
                            enabled = childEnabled,
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                if (syncMode != 0) {
                    OutlinedTextField(
                        value = syncPort,
                        onValueChange = { syncPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("UDP audio sync port") },
                        singleLine = true,
                        enabled = childEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TextButton(onClick = { showHardwarePins = !showHardwarePins }, enabled = childEnabled) {
                    Text(if (showHardwarePins) "Hide I2S microphone pin configuration" else "Configure I2S microphone type & GPIO pins")
                }

                if (showHardwarePins) {
                    HorizontalDivider(color = Line)
                    SmallLabel("DIGITAL MICROPHONE TYPE")
                    val micTypes = listOf(
                        "Generic I2S (INMP441)" to 1,
                        "Analog ADC" to 0,
                        "ES7243" to 2,
                        "SPH0645" to 3,
                        "Generic I2S + MCLK" to 4,
                        "PDM" to 5
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        micTypes.forEach { (label, code) ->
                            FilterChip(
                                selected = dmicType == code,
                                onClick = { dmicType = code },
                                enabled = childEnabled,
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pinSck,
                            onValueChange = { pinSck = it.filter(Char::isDigit).take(2) },
                            label = { Text("BCLK / SCK") },
                            singleLine = true,
                            enabled = childEnabled,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pinWs,
                            onValueChange = { pinWs = it.filter(Char::isDigit).take(2) },
                            label = { Text("WS / LRCLK") },
                            singleLine = true,
                            enabled = childEnabled,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pinSd,
                            onValueChange = { pinSd = it.filter(Char::isDigit).take(2) },
                            label = { Text("SD / DOUT") },
                            singleLine = true,
                            enabled = childEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(
                        onClick = {
                            dmicType = 1
                            pinSck = RoomHardwarePins.MIC_BCLK.toString()
                            pinWs = RoomHardwarePins.MIC_WS.toString()
                            pinSd = RoomHardwarePins.MIC_SD.toString()
                        },
                        enabled = childEnabled
                    ) { Text("Use approved Room INMP441 pins (BCLK 26 / WS 25 / SD 35)") }
                    if (pinError != null) {
                        Text(pinError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            if (requiresReboot) {
                Text(
                    "Changing AudioReactive enable state, microphone type or I2S pins requires a controller reboot to re-initialize the I2S driver.",
                    color = Color(0xFFF6BC7B),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = if (requiresReboot) "Save & reboot controller" else "Save AudioReactive settings",
                    icon = Icons.Outlined.Check,
                    enabled = !wledUi.busy && pinError == null
                ) {
                    val patch = obj(
                        "um" to obj(
                            "AudioReactive" to obj(
                                "enabled" to arEnabled,
                                "squelch" to squelch.roundToInt(),
                                "gain" to gain.roundToInt(),
                                "agc" to agcMode,
                                "dmic" to obj("type" to dmicType),
                                "resources" to obj(
                                    "pin-sd" to sdInt,
                                    "pin-ws" to wsInt,
                                    "pin-sck" to sckInt
                                ),
                                "dynamics" to obj(
                                    "limiter" to limiter,
                                    "rise" to riseMs.roundToInt(),
                                    "fall" to fallMs.roundToInt()
                                ),
                                "frequency" to obj("scale" to freqScale),
                                "sync" to obj(
                                    "port" to (syncPort.toIntOrNull() ?: 11988).coerceIn(1, 65535),
                                    "mode" to syncMode
                                )
                            )
                        )
                    )
                    wled.saveConfig(patch, "AudioReactive configuration saved", reboot = requiresReboot)
                }
            }
        }

        // Quick AudioReactive Effects Launcher
        val audioEffects = remember(wledUi.effectiveEffects, wledUi.effectiveFxdata) {
            wledUi.effectiveEffects.mapIndexedNotNull { idx, _ ->
                val meta = wledUi.effectMetadata(idx)
                if (meta.isAudioReactive) meta else null
            }
        }
        if (audioEffects.isNotEmpty()) {
            RoomCard {
                SectionLabel("Sound-reactive WLED effects", "${audioEffects.size} AVAILABLE")
                Text("Tap an effect to apply microphone-reactive lighting, or tap ★ to pin it to Home Favorites.", color = Muted, fontSize = 12.sp)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activeFx = wledUi.effectiveSnapshot.primarySegment.fx
                    val targetSegs = wledUi.effectiveSnapshot.selectedSegments.map { it.id }.ifEmpty { listOf(0) }
                    audioEffects.take(24).forEach { meta ->
                        val isFav = meta.id in ui.favoriteSoundModes || meta.id in wledUi.favoriteEffects
                        FilterChip(
                            selected = activeFx == meta.id,
                            onClick = { wled.updateSegments(targetSegs, obj("fx" to meta.id, "on" to true)) },
                            enabled = !wledUi.busy,
                            label = {
                                val icon = if (meta.isFrequencyReactive) "♫" else "♪"
                                Text("$icon ${meta.name}", fontSize = 12.sp)
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (isFav) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Favorite ${meta.name}",
                                    tint = if (isFav) Lime else Muted,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .livelyClick(pressedScale = 0.8f) { model.toggleFavoriteSoundMode(meta.id) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // 3. Band Response Console & Room Quiet-Response Settings
    val current = ui.state.optJSONObject("microphone") ?: obj()
    var draft by remember(current.toString(), audioStatus.quietBehavior, audioStatus.quietSeconds) {
        mutableStateOf(
            current.copyJson().apply {
                listOf("bass_gain", "mid_gain", "treble_gain").forEach { put(it, current.optInt(it, 100)) }
                put("quiet_behavior", audioStatus.quietBehavior)
                put("quiet_seconds", audioStatus.quietSeconds)
            }
        )
    }
    val capabilities = ui.state.optJSONArray("capabilities")
    val roomMicReady = ui.demo || (0 until (capabilities?.length() ?: 0)).any { capabilities?.optString(it) == "microphone" }

    RoomCard {
        SectionLabel("3-Band Equalizer", if (ui.demo) "PREVIEW" else audioStatus.readiness.badge)
        Text("Slide up to boost Bass, Mid, or Treble response (0–200%). 100% is neutral flat gain; 0% mutes that band.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Bass" to "bass_gain", "Mids" to "mid_gain", "Treble" to "treble_gain").forEach { (label, key) ->
                IsoFader(
                    label = label,
                    value = draft.optInt(key, 100).toFloat(),
                    onChange = {
                        val updated = draft.copyJson().put(key, it.roundToInt())
                        draft = updated
                        model.coalescedMicrophone(updated)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !ui.busy
                )
            }
        }
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
                    selected = draft.optInt("bass_gain", 100) == bVal &&
                        draft.optInt("mid_gain", 100) == mVal &&
                        draft.optInt("treble_gain", 100) == tVal,
                    onClick = {
                        val updated = draft.copyJson()
                            .put("bass_gain", bVal)
                            .put("mid_gain", mVal)
                            .put("treble_gain", tVal)
                        draft = updated
                        model.coalescedMicrophone(updated)
                    },
                    enabled = !ui.busy,
                    label = { Text(presetLabel, fontSize = 11.sp) }
                )
            }
        }

        SmallLabel(
            if (ui.demo) "SAMPLE INPUT · MIX AUDITION"
            else if (audioStatus.bands.isNotEmpty()) "LIVE ESP32 TELEMETRY · MIX AUDITION"
            else "NO LIVE BAND TELEMETRY (${audioStatus.readiness.badge})"
        )
        listOf("Bass" to "bass_gain", "Mids" to "mid_gain", "Treble" to "treble_gain").forEachIndexed { i, (label, key) ->
            val rawBand = if (ui.demo) {
                ui.state.optJSONObject("telemetry")?.optJSONArray("bands")?.optDouble(i, 0.0)?.toFloat() ?: 0f
            } else {
                audioStatus.bands.getOrElse(i) { 0f }
            }
            val level = (rawBand * draft.optInt(key, 100) / 100f).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$label (${draft.optInt(key, 100)}%)", color = Muted, fontSize = 11.sp)
                    Text("${(level * 100).roundToInt()}%", color = Muted, fontSize = 11.sp)
                }
                LinearProgressIndicator(
                    progress = { level },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = Lime,
                    trackColor = Line
                )
            }
        }
        Text(
            "On combined WLED + Room firmware, FFT band acquisition runs autonomously on the ESP32. Adjusting Bass, Mid, and Treble weights scales each frequency band in real time.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 18.sp
        )
    }

    RoomCard {
        SectionLabel("Quiet-Room Behavior")
        SmallLabel("WHEN THE ROOM GOES QUIET")
        Choices(
            values = listOf("off", "dim", "hold"),
            selected = draft.optString("quiet_behavior", "dim")
        ) { draft = draft.copyJson().put("quiet_behavior", it) }
        SettingSlider(
            title = "Quiet timeout",
            value = draft.optInt("quiet_seconds", 10).toFloat(),
            onChange = { draft = draft.copyJson().put("quiet_seconds", it.toInt()) },
            range = 1f..300f,
            suffix = " s"
        )

        if (!wledUi.online) {
            val isAutoGain = draft.optBoolean("automatic", true)
            SettingToggleRow(
                title = "Automatic sensitivity (AGC)",
                checked = isAutoGain,
                enabled = !ui.busy,
                onCheckedChange = { draft = draft.copyJson().put("automatic", it) }
            )
            SettingSlider(
                title = "Manual sensitivity",
                value = draft.optInt("sensitivity", 50).toFloat(),
                onChange = { draft = draft.copyJson().put("sensitivity", it.toInt()) },
                range = 1f..100f,
                enabled = !isAutoGain && !ui.busy
            )
            SettingSlider(
                title = "Noise floor",
                value = draft.optInt("noise_floor", 15).toFloat(),
                onChange = { draft = draft.copyJson().put("noise_floor", it.toInt()) }
            )
            SettingSlider(
                title = "Smoothing",
                value = draft.optInt("smoothing", 60).toFloat(),
                onChange = { draft = draft.copyJson().put("smoothing", it.toInt()) }
            )
        }

        val canSaveWledQuiet = wled != null && wledUi.online && !wledUi.busy
        val canSaveRoom = (ui.signedIn && !ui.stale && roomMicReady && !ui.busy)
        ActionButton(
            text = "Save quiet-room & EQ settings",
            icon = Icons.Outlined.Check,
            enabled = canSaveWledQuiet || canSaveRoom
        ) {
            if (canSaveWledQuiet && wled != null) {
                wled.saveConfig(
                    obj(
                        "um" to obj(
                            "RoomBridge" to obj(
                                "quiet_behavior" to draft.optString("quiet_behavior", "dim"),
                                "quiet_seconds" to draft.optInt("quiet_seconds", 10),
                                "bass_gain" to draft.optInt("bass_gain", 100),
                                "mid_gain" to draft.optInt("mid_gain", 100),
                                "treble_gain" to draft.optInt("treble_gain", 100)
                            )
                        )
                    ),
                    successMessage = "Saved quiet-room & 3-band EQ settings to controller"
                )
            }
            if (canSaveRoom) {
                val payload = draft.copyJson()
                model.mutate(
                    "POST",
                    "/actions",
                    action("microphone", payload),
                    if (ui.demo) "Preview microphone & EQ settings saved" else "Microphone & 3-band EQ settings submitted"
                )
            }
        }

        if (wledUi.online) {
            Text(
                "Note: Because WLED AudioReactive owns the I2S microphone on this firmware, noise gating is adjusted immediately via the Squelch and AGC controls above rather than legacy 5-second Room calibration.",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        } else {
            OutlinedButton(
                onClick = { model.mutate("POST", "/actions", obj("type" to "calibrate"), "Calibration requested") },
                enabled = !ui.busy && roomMicReady && ui.signedIn && !ui.stale
            ) {
                Text("Calibrate room noise (5s)", maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun RoomThreeBandEqSettingsCard(
    model: RoomModel,
    ui: RoomUi
) {
    val micJson = ui.state.optJSONObject("microphone") ?: obj()
    val initBass = micJson.optInt("bass_gain", 100).coerceIn(0, 200)
    val initMid = micJson.optInt("mid_gain", 100).coerceIn(0, 200)
    val initTreble = micJson.optInt("treble_gain", 100).coerceIn(0, 200)

    var bassGain by remember(initBass) { mutableFloatStateOf(initBass.toFloat()) }
    var midGain by remember(initMid) { mutableFloatStateOf(initMid.toFloat()) }
    var trebleGain by remember(initTreble) { mutableFloatStateOf(initTreble.toFloat()) }

    fun buildMicPayload(
        b: Int = bassGain.roundToInt().coerceIn(0, 200),
        m: Int = midGain.roundToInt().coerceIn(0, 200),
        t: Int = trebleGain.roundToInt().coerceIn(0, 200)
    ): JSONObject {
        return micJson.copyJson().apply {
            if (!has("automatic")) put("automatic", true)
            if (!has("sensitivity")) put("sensitivity", 60)
            if (!has("noise_floor")) put("noise_floor", 15)
            if (!has("smoothing")) put("smoothing", 65)
            if (!has("quiet_behavior")) put("quiet_behavior", "dim")
            if (!has("quiet_seconds")) put("quiet_seconds", 12)
            put("bass_gain", b)
            put("mid_gain", m)
            put("treble_gain", t)
        }
    }

    RoomCard(modifier = Modifier.testTag("settings_three_band_eq_card")) {
        SectionLabel(
            "3-Band Equalizer",
            "${bassGain.roundToInt()} / ${midGain.roundToInt()} / ${trebleGain.roundToInt()}%"
        )
        Text(
            text = "Tune Bass, Mid, and Treble response gains (0–200%) for the controller's audio-reactive DSP and sound profiles.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        SettingSlider(
            title = "Bass Gain (Sub & Kick 30–180 Hz)",
            value = bassGain,
            onChange = {
                bassGain = it
                model.coalescedMicrophone(buildMicPayload(b = it.roundToInt()))
            },
            range = 0f..200f,
            suffix = "%",
            enabled = !ui.busy
        )
        SettingSlider(
            title = "Mid Gain (Vocals & Snare 200–2400 Hz)",
            value = midGain,
            onChange = {
                midGain = it
                model.coalescedMicrophone(buildMicPayload(m = it.roundToInt()))
            },
            range = 0f..200f,
            suffix = "%",
            enabled = !ui.busy
        )
        SettingSlider(
            title = "Treble Gain (Hi-Hat & Air 2.5–8 kHz)",
            value = trebleGain,
            onChange = {
                trebleGain = it
                model.coalescedMicrophone(buildMicPayload(t = it.roundToInt()))
            },
            range = 0f..200f,
            suffix = "%",
            enabled = !ui.busy
        )

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                        model.coalescedMicrophone(buildMicPayload(bVal, mVal, tVal))
                    },
                    enabled = !ui.busy,
                    label = { Text(presetLabel, fontSize = 11.sp) }
                )
            }
        }

        ActionButton(
            text = "Save Bass, Mid & Treble EQ",
            icon = Icons.Outlined.Check,
            enabled = !ui.busy
        ) {
            model.mutate(
                "POST",
                "/actions",
                action("microphone", buildMicPayload()),
                "Saved Bass (${bassGain.roundToInt()}%), Mid (${midGain.roundToInt()}%) & Treble (${trebleGain.roundToInt()}%) EQ"
            )
        }
    }
}
