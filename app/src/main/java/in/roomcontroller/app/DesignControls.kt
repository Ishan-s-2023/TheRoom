package `in`.roomcontroller.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.platform.LocalView
import kotlin.math.*

/**
 * Triggers a crisp, subtle tactile haptic pulse (vibration) for button, toggle, and control interactions.
 */
fun performSubtleHaptic(view: View, heavy: Boolean = false) {
    runCatching {
        val constant = if (heavy) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) HapticFeedbackConstants.CONTEXT_CLICK
            else HapticFeedbackConstants.VIRTUAL_KEY
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        val performed = view.performHapticFeedback(
            constant,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        if (!performed) {
            val context = view.context
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(
                        VibrationEffect.createPredefined(
                            if (heavy) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
                        )
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(if (heavy) 12L else 7L, 70))
                }
            }
        }
    }
}

/**
 * Lively spring press animation modifier with subtle haptic feedback on buttons, chips, swatches, and cards.
 */
fun Modifier.livelyClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.93f,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            performSubtleHaptic(view)
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
        label = "livelyClickScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = {
                performSubtleHaptic(view)
                onClick()
            }
        )
}

@Composable
fun Modifier.livelyPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.94f
): Modifier {
    val view = LocalView.current
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            performSubtleHaptic(view)
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 550f),
        label = "livelyPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: String = "default",
    label: String = "Toggle"
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth transition for circles and track
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "liquidToggleProgress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 550f),
        label = "liquidTogglePress"
    )

    val activeBgColor = when (variant.lowercase()) {
        "success" -> Color(0xFF10B981)
        "warning" -> Color(0xFFF59E0B)
        "danger" -> Color(0xFFEF4444)
        else -> Lime // Use active theme accent color instead of hardcoded blue
    }
    val defaultBgColor = Line
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeBgColor else defaultBgColor,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
        label = "liquidToggleTrackColor"
    )
    val thumbColor = Color.White
    val inkColor = Ink

    Box(
        modifier = modifier
            .requiredSize(width = 52.dp, height = 32.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .semantics { contentDescription = label }
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { next ->
                    performSubtleHaptic(view, heavy = true)
                    onCheckedChange(next)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleXUnit = size.width / 52f
            val scaleYUnit = size.height / 32f
            val unit = min(scaleXUnit, scaleYUnit)

            // 1. Pill Track
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
            drawRoundRect(
                color = if (checked) inkColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                style = Stroke(width = 1.dp.toPx())
            )

            val t = progress.coerceIn(0f, 1f)
            // Circle 1: cx=16, cy=16, r=10, translateX(t * 12px), scale(1 - t)
            val c1x = (16f + t * 12f) * scaleXUnit
            val c1y = 16f * scaleYUnit
            val r1 = (10f * (1f - t)).coerceAtLeast(0f) * unit

            // Circle 2: cx=36, cy=16, r=10, translateX((1 - t) * -12px), scale(t)
            val c2x = (36f - (1f - t) * 12f) * scaleXUnit
            val c2y = 16f * scaleYUnit
            val r2 = (10f * t).coerceAtLeast(0f) * unit

            // Gooey Metaball Bridge during transition
            if (t in 0.04f..0.96f && r1 > 0.5f && r2 > 0.5f) {
                val bridgeAlpha = (sin(t * PI).toFloat() * 0.95f).coerceIn(0f, 1f)
                val midX = (c1x + c2x) * 0.5f
                val midY = (c1y + c2y) * 0.5f
                val bridgeRadius = ((r1 + r2) * 0.62f).coerceAtLeast(2f * unit)
                drawCircle(
                    color = thumbColor.copy(alpha = bridgeAlpha),
                    radius = bridgeRadius,
                    center = Offset(midX, midY)
                )
            }

            // Draw Circle 1 (Left -> Center)
            if (r1 > 0.2f) {
                drawCircle(
                    color = inkColor.copy(alpha = 0.16f),
                    radius = r1 + 1.dp.toPx(),
                    center = Offset(c1x, c1y)
                )
                drawCircle(
                    color = thumbColor,
                    radius = r1,
                    center = Offset(c1x, c1y)
                )
            }

            // Draw Circle 2 (Center -> Right)
            if (r2 > 0.2f) {
                drawCircle(
                    color = inkColor.copy(alpha = 0.16f),
                    radius = r2 + 1.dp.toPx(),
                    center = Offset(c2x, c2y)
                )
                drawCircle(
                    color = thumbColor,
                    radius = r2,
                    center = Offset(c2x, c2y)
                )
            }
        }
    }
}

/**
 * 44-Bar Equalizer Wave Slider with Floating NumberFlow Tooltip (`slider-05.tsx` port).
 * Expands smoothly on touch/drag with a cosine wave dip around the finger and sine arc on active bars.
 */
@Composable
fun Slider05(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 100f,
    enabled: Boolean = true,
    label: String = "Volume",
    accentColor: Color = Lime,
    onValueChangeFinished: () -> Unit = {}
) {
    val span = (max - min).coerceAtLeast(1f)
    var isDragging by remember { mutableStateOf(false) }
    var localValue by remember { mutableFloatStateOf(value.coerceIn(min, max)) }
    var pointerXFrac by remember { mutableFloatStateOf(((value - min) / span).coerceIn(0f, 1f)) }
    var lastReleaseMs by remember { mutableLongStateOf(0L) }
    var lastDispatchMs by remember { mutableLongStateOf(0L) }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(value, isDragging) {
        if (!isDragging) {
            val elapsed = System.currentTimeMillis() - lastReleaseMs
            if (elapsed < 750L && abs(value - localValue) > 0.5f) {
                kotlinx.coroutines.delay(750L - elapsed)
            }
            if (!isDragging && System.currentTimeMillis() - lastReleaseMs >= 700L) {
                localValue = value.coerceIn(min, max)
                pointerXFrac = ((localValue - min) / span).coerceIn(0f, 1f)
            }
        }
    }

    val displayedValue = localValue.coerceIn(min, max)
    val progress = ((displayedValue - min) / span).coerceIn(0f, 1f)

    // Spring-smoothed pointer X fraction (matching useSpring(mouseX, { damping: 20, stiffness: 300 }))
    val smoothPointerFrac by animateFloatAsState(
        targetValue = if (isDragging) pointerXFrac else progress,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
        label = "slider05SmoothMouseX"
    )
    val activeAnim by animateFloatAsState(
        targetValue = if (isDragging && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 280f),
        label = "slider05ActiveExpand"
    )

    val containerHeightDp = (40f + activeAnim * 38f).dp
    val numBars = 44

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeightDp)
            .alpha(if (enabled) 1f else 0.42f)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(displayedValue, min..max)
                if (!enabled) disabled()
                if (enabled) {
                    setProgress { target ->
                        val clamped = target.coerceIn(min, max)
                        localValue = clamped
                        pointerXFrac = ((clamped - min) / span).coerceIn(0f, 1f)
                        lastReleaseMs = System.currentTimeMillis()
                        latestOnValueChange(clamped)
                        latestOnFinished()
                        true
                    }
                }
            }
            .pointerInput(min, max) {
                val padPx = 12.dp.toPx()
                val grabRadiusPx = 28.dp.toPx()
                fun fracAt(x: Float): Float {
                    val innerW = (size.width - padPx * 2f).coerceAtLeast(1f)
                    return ((x - padPx) / innerW).coerceIn(0f, 1f)
                }
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        val first = down.changes.firstOrNull { it.pressed } ?: continue
                        if (!latestEnabled) continue
                        isDragging = true
                        val innerW = (size.width - padPx * 2f).coerceAtLeast(1f)
                        val currentThumbX = padPx + ((localValue - min) / span).coerceIn(0f, 1f) * innerW
                        // If the user touches near the current slider position, lock onto the current value
                        // so attaching a finger never causes an unintended jump!
                        val startFrac = ((localValue - min) / span).coerceIn(0f, 1f)
                        val touchFrac = fracAt(first.position.x)
                        val attachedToThumb = abs(first.position.x - currentThumbX) <= grabRadiusPx
                        val f0 = if (attachedToThumb) startFrac else touchFrac
                        pointerXFrac = f0
                        if (!attachedToThumb) {
                            val v0 = (min + f0 * span).roundToInt().toFloat().coerceIn(min, max)
                            localValue = v0
                            lastDispatchMs = System.currentTimeMillis()
                            latestOnValueChange(v0)
                        }
                        first.consume()

                        val pointerId = first.id
                        val downX = first.position.x
                        var hasMoved = !attachedToThumb
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (!hasMoved && abs(change.position.x - downX) > 3.dp.toPx()) {
                                hasMoved = true
                            }
                            if (hasMoved) {
                                val fNext = if (attachedToThumb) {
                                    (startFrac + (change.position.x - downX) / innerW).coerceIn(0f, 1f)
                                } else {
                                    fracAt(change.position.x)
                                }
                                pointerXFrac = fNext
                                val vNext = (min + fNext * span).roundToInt().toFloat().coerceIn(min, max)
                                if (abs(vNext - localValue) >= 0.5f) {
                                    localValue = vNext
                                    val now = System.currentTimeMillis()
                                    if (now - lastDispatchMs >= 45L) {
                                        lastDispatchMs = now
                                        latestOnValueChange(vNext)
                                    }
                                }
                            }
                            change.consume()
                        }
                        isDragging = false
                        lastReleaseMs = System.currentTimeMillis()
                        if (hasMoved) {
                            latestOnValueChange(localValue)
                        }
                        latestOnFinished()
                    }
                }
            },
        contentAlignment = Alignment.BottomStart
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val padPx = with(density) { 12.dp.toPx() }
        val innerWidthPx = (widthPx - padPx * 2f).coerceAtLeast(1f)
        val pointerXPx = padPx + smoothPointerFrac * innerWidthPx

        val isLightTheme = LocalRoomPalette.current.light
        val tipBarColor = if (isLightTheme) Cream else Color.White
        val inactiveBarColor = if (isLightTheme) Line else Color(0xFF343B35)

        // 44 Equalizer Bars Canvas (Zero Object Allocation per Frame)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val stepW = canvasW / numBars.toFloat()
            val barWidthPx = (stepW * 0.48f).coerceIn(2.dp.toPx(), 4.5.dp.toPx())
            val cornerR = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            val influenceRadiusPx = 90.dp.toPx()
            val localPointerX = smoothPointerFrac * canvasW

            for (i in 0 until numBars) {
                val barProgress = i.toFloat() / (numBars - 1).toFloat()
                val isFilled = barProgress <= progress
                val barCenterX = (i + 0.5f) * stepW

                // Idle height = 16.dp; Active height = wave dip + active sine arc (exact slider-05.tsx math)
                val idleHeightDp = 16f
                val distPx = abs(barCenterX - localPointerX)
                val normalizedDist = (distPx / influenceRadiusPx).coerceIn(0f, 1f)
                val wave = cos(normalizedDist * (PI.toFloat() / 2f))
                val dipDp = wave * 24f
                val activeArcDp = if (isFilled) sin(barProgress * PI.toFloat()) * 10f else 0f
                val expandedHeightDp = max(8f, 44f - dipDp + activeArcDp)

                val currentBarHeightPx = ((idleHeightDp + (expandedHeightDp - idleHeightDp) * activeAnim).dp.toPx())
                    .coerceIn(6.dp.toPx(), canvasH)

                val barColor = if (isFilled) {
                    if (barProgress > progress - 0.08f) tipBarColor else accentColor
                } else {
                    inactiveBarColor
                }

                val topY = canvasH - currentBarHeightPx
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(barCenterX - barWidthPx / 2f, topY),
                    size = androidx.compose.ui.geometry.Size(barWidthPx, currentBarHeightPx),
                    cornerRadius = cornerR
                )
            }
        }

        // Floating NumberFlow Tooltip when dragging / active
        if (activeAnim > 0.05f) {
            val pillWidthPx = with(density) { 46.dp.toPx() }
            val clampedOffsetX = (pointerXPx - pillWidthPx / 2f)
                .coerceIn(0f, (widthPx - pillWidthPx).coerceAtLeast(0f))
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { androidx.compose.ui.unit.IntOffset(clampedOffsetX.roundToInt(), 0) }
                    .graphicsLayer {
                        scaleX = 0.85f + 0.15f * activeAnim
                        scaleY = 0.85f + 0.15f * activeAnim
                        alpha = activeAnim
                    }
                    .clip(CircleShape)
                    .background(Cream)
                    .border(1.dp, accentColor, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${displayedValue.roundToInt()}%",
                    color = Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun BrightnessControl(
    value: Float,
    onChange: (Float) -> Unit,
    enabled: Boolean = true,
    onFinished: () -> Unit = {}
) {
    var displayVal by remember { mutableFloatStateOf(value.coerceIn(0f, 100f)) }
    var lastUserEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value) {
        if (System.currentTimeMillis() - lastUserEditMs >= 750L) {
            displayVal = value.coerceIn(0f, 100f)
        }
    }
    Column(
        modifier = Modifier.alpha(if (enabled) 1f else 0.42f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LightMode, null, tint = Lime, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Brightness", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("${displayVal.roundToInt()}%", fontFamily = FontFamily.Serif, fontSize = 32.sp)
        }
        LedSlider(
            value = (displayVal / 100f).coerceIn(0f, 1f),
            onValueChange = { frac ->
                val pct = (frac * 100f).coerceIn(0f, 100f)
                displayVal = pct
                lastUserEditMs = System.currentTimeMillis()
                onChange(pct)
            },
            trackBrush = Brush.horizontalGradient(
                listOf(
                    WledCatalogDefaults.luminousLedColor(Lime, 0.25f),
                    WledCatalogDefaults.luminousLedColor(Lime, 0.65f),
                    WledCatalogDefaults.luminousLedColor(Lime, 1f)
                )
            ),
            contentDescription = "Brightness",
            enabled = enabled,
            thumbColor = Lime,
            onValueChangeFinished = {
                lastUserEditMs = System.currentTimeMillis()
                onFinished()
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallLabel("Soft")
            SmallLabel("Bright")
        }
    }
}

@Composable
fun VolumeControl(
    value: Float,
    onChange: (Float) -> Unit,
    enabled: Boolean = true,
    label: String = "Spotify volume",
    onFinished: () -> Unit = {}
) {
    var displayVal by remember { mutableFloatStateOf(value.coerceIn(0f, 100f)) }
    var lastUserEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value) {
        if (System.currentTimeMillis() - lastUserEditMs >= 750L) {
            displayVal = value.coerceIn(0f, 100f)
        }
    }
    Column(
        modifier = Modifier.alpha(if (enabled) 1f else 0.42f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.VolumeUp, null, tint = Lime, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            Text("${displayVal.roundToInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Lime)
        }
        Slider05(
            value = displayVal,
            onValueChange = {
                displayVal = it
                lastUserEditMs = System.currentTimeMillis()
                onChange(it)
            },
            enabled = enabled,
            label = label,
            onValueChangeFinished = {
                lastUserEditMs = System.currentTimeMillis()
                onFinished()
            }
        )
    }
}

@Composable
fun SpeakerIrVolumeControl(
    value: Float,
    onChange: (Float) -> Unit,
    onIrStep: (String) -> Unit = {},
    enabled: Boolean = true,
    initialMuted: Boolean = false,
    deviceName: String = "Device Speaker",
    onFinished: () -> Unit = {}
) {
    val isLightTheme = LocalRoomPalette.current.light
    val irAccent = if (isLightTheme) Color(0xFF0E8A68) else Color(0xFF42E6B5)
    val mutedAlertColor = Color(0xFFFF6B6B)
    var displayVal by remember { mutableFloatStateOf(value.coerceIn(0f, 100f)) }
    var isMuted by remember { mutableStateOf(initialMuted) }
    var lastUserEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value) {
        if (System.currentTimeMillis() - lastUserEditMs >= 750L) {
            displayVal = value.coerceIn(0f, 100f)
        }
    }
    LaunchedEffect(initialMuted) {
        if (System.currentTimeMillis() - lastUserEditMs >= 450L) {
            isMuted = initialMuted
        }
    }
    val sliderInteractive = enabled && !isMuted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.42f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Outlined.Speaker,
                    contentDescription = null,
                    tint = if (isMuted) mutedAlertColor else irAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$deviceName volume (IR)",
                    fontSize = 12.sp,
                    color = if (isMuted) mutedAlertColor else Muted,
                    maxLines = 1
                )
            }
            Text(
                text = if (isMuted) "MUTED" else "${displayVal.roundToInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMuted) mutedAlertColor else irAccent
            )
        }
        Slider05(
            value = displayVal,
            onValueChange = {
                if (!isMuted) {
                    displayVal = it
                    lastUserEditMs = System.currentTimeMillis()
                    onChange(it)
                }
            },
            enabled = sliderInteractive,
            label = "Speaker IR volume",
            accentColor = if (isMuted) Muted else irAccent,
            onValueChangeFinished = {
                if (!isMuted) {
                    lastUserEditMs = System.currentTimeMillis()
                    onFinished()
                }
            }
        )
        val view = LocalView.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    performSubtleHaptic(view)
                    if (!isMuted) {
                        val next = (displayVal - 5f).coerceIn(0f, 100f)
                        displayVal = next
                        lastUserEditMs = System.currentTimeMillis()
                        onChange(next)
                        onIrStep("volume-down")
                    }
                },
                enabled = sliderInteractive,
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Outlined.VolumeDown, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("IR Vol −", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = {
                    performSubtleHaptic(view, heavy = true)
                    lastUserEditMs = System.currentTimeMillis()
                    isMuted = !isMuted
                    onIrStep("speaker-mute")
                },
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isMuted) mutedAlertColor.copy(alpha = 0.18f) else Color.Transparent,
                    contentColor = if (isMuted) mutedAlertColor else Cream
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isMuted) 1.5.dp else 1.dp,
                    color = if (isMuted) mutedAlertColor else Line
                ),
                modifier = Modifier.weight(0.95f).height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isMuted) "Unmute" else "Mute", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
            OutlinedButton(
                onClick = {
                    performSubtleHaptic(view)
                    if (!isMuted) {
                        val next = (displayVal + 5f).coerceIn(0f, 100f)
                        displayVal = next
                        lastUserEditMs = System.currentTimeMillis()
                        onChange(next)
                        onIrStep("volume-up")
                    }
                },
                enabled = sliderInteractive,
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Outlined.VolumeUp, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("IR Vol +", fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Reusable Compose Music Control Component interfacing with Spotify's Local Network API
 * (`/v1/command` `op: "spotify"` on the ESP32 controller and `/spotify/playback` on the local companion)
 * to play, pause, skip tracks (previous / next), and smoothly adjust volume via `Slider05`.
 */
@Composable
fun SpotifyLocalNetworkControlCard(
    model: RoomModel,
    ui: RoomUi,
    modifier: Modifier = Modifier,
    onOpenMusicScreen: (() -> Unit)? = null
) {
    val spotify = ui.state.optJSONObject("spotify") ?: obj()
    val isPlaying = spotify.optBoolean("playing", false)
    val trackName = spotify.optString("track").ifBlank { "Room Soundtrack" }
    val artistName = spotify.optString("artist").ifBlank { "Local Network Spotify API" }
    val deviceName = spotify.optString("device").ifBlank { "ESP32 Local Controller" }
    val currentVol = spotify.optInt("volume_percent", 55).toFloat().coerceIn(0f, 100f)

    var volPct by remember { mutableFloatStateOf(currentVol) }
    LaunchedEffect(currentVol) { volPct = currentVol }

    var musicControlsEnabled by remember { mutableStateOf(true) }
    val controlsInteractive = musicControlsEnabled && !ui.busy

    fun sendLocalSpotifyCommand(action: String, value: Int = 0) {
        if (model.directDevice || ui.demo) {
            model.spotifyCommand(action, value = value)
        } else {
            when (action) {
                "play", "pause", "next", "previous" ->
                    model.mutate("POST", "/spotify/playback", obj("action" to action), "Spotify $action")
                "volume" ->
                    model.coalescedVolume(value)
                "refresh" ->
                    model.work { model.refresh() }
            }
        }
    }

    RoomCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .let { mod ->
                        if (onOpenMusicScreen != null) mod.livelyClick(pressedScale = 0.97f, onClick = onOpenMusicScreen)
                        else mod
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (musicControlsEnabled) Lime.copy(alpha = 0.16f) else Line)
                        .border(1.dp, if (musicControlsEnabled) Lime.copy(alpha = 0.45f) else Line, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = if (musicControlsEnabled) Lime else Muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    SmallLabel("SPOTIFY LOCAL NETWORK API · $deviceName")
                    Text(
                        text = trackName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (musicControlsEnabled) Cream else Muted,
                        maxLines = 1
                    )
                    Text(
                        text = artistName,
                        fontSize = 12.sp,
                        color = Muted,
                        maxLines = 1
                    )
                }
            }
            LiquidToggle(
                checked = musicControlsEnabled,
                onCheckedChange = { nextEnabled ->
                    musicControlsEnabled = nextEnabled
                    if (!nextEnabled && isPlaying) {
                        sendLocalSpotifyCommand("pause")
                    }
                },
                label = "Enable Spotify local network control"
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (musicControlsEnabled) 1f else 0.42f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Transport Controls: Previous | Play/Pause | Next | Refresh
            val view = LocalView.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        performSubtleHaptic(view)
                        sendLocalSpotifyCommand("previous")
                    },
                    enabled = controlsInteractive,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Prev", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = {
                        performSubtleHaptic(view, heavy = true)
                        sendLocalSpotifyCommand(if (isPlaying) "pause" else "play")
                    },
                    enabled = controlsInteractive,
                    modifier = Modifier.weight(1.35f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lime,
                        contentColor = Ink
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause track" else "Play track",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isPlaying) "Pause" else "Play",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                OutlinedButton(
                    onClick = {
                        performSubtleHaptic(view)
                        sendLocalSpotifyCommand("next")
                    },
                    enabled = controlsInteractive,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text("Next", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.SkipNext, contentDescription = "Next track", modifier = Modifier.size(18.dp))
                }
            }

            // 44-Bar Equalizer Wave Slider (Slider05) for Music Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Music Volume", fontSize = 12.sp, color = Muted)
                Text(
                    text = "${volPct.roundToInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (musicControlsEnabled) Lime else Muted
                )
            }
            Slider05(
                value = volPct,
                onValueChange = { nextVol ->
                    volPct = nextVol
                    model.coalescedVolume(nextVol.roundToInt())
                },
                enabled = controlsInteractive,
                label = "Spotify local network volume"
            )

            // Equal-width single-line quick volume pills (never wraps!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(25, 50, 75, 100).forEach { presetVol ->
                    val isSelected = abs(volPct.roundToInt() - presetVol) <= 3
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected && musicControlsEnabled) Lime.copy(alpha = 0.18f) else Ink)
                            .border(
                                width = if (isSelected && musicControlsEnabled) 1.5.dp else 1.dp,
                                color = if (isSelected && musicControlsEnabled) Lime else Line,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .livelyClick(enabled = controlsInteractive, pressedScale = 0.93f) {
                                volPct = presetVol.toFloat()
                                model.coalescedVolume(presetVol)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Vol $presetVol%",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected && musicControlsEnabled) Lime else Cream,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable fun LedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    trackBrush: Brush,
    modifier: Modifier = Modifier,
    contentDescription: String = "Slider",
    enabled: Boolean = true,
    thumbColor: Color = Lime,
    onValueChangeFinished: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    var lastReleaseMs by remember { mutableLongStateOf(0L) }
    var lastDispatchMs by remember { mutableLongStateOf(0L) }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(value, isDragging) {
        if (!isDragging) {
            val elapsed = System.currentTimeMillis() - lastReleaseMs
            if (elapsed < 750L && abs(value - dragFraction) > 0.005f) {
                kotlinx.coroutines.delay(750L - elapsed)
            }
            if (!isDragging && System.currentTimeMillis() - lastReleaseMs >= 700L) {
                dragFraction = value.coerceIn(0f, 1f)
            }
        }
    }

    val activeFraction = dragFraction.coerceIn(0f, 1f)
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging && enabled) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 600f),
        label = "ledSliderThumbScale"
    )
    val luminousThumb = remember(thumbColor, activeFraction) {
        WledCatalogDefaults.luminousLedColor(thumbColor, activeFraction.coerceIn(0.35f, 1f))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(activeFraction, 0f..1f)
                if (!enabled) disabled()
                if (enabled) {
                    setProgress { target ->
                        val clamped = target.coerceIn(0f, 1f)
                        dragFraction = clamped
                        lastReleaseMs = System.currentTimeMillis()
                        latestOnValueChange(clamped)
                        latestOnFinished()
                        true
                    }
                }
            }
            .pointerInput(Unit) {
                val thumbPadPx = 12.dp.toPx()
                val grabRadiusPx = 28.dp.toPx()
                fun fractionForX(x: Float): Float {
                    val travel = (size.width - thumbPadPx * 2f).coerceAtLeast(1f)
                    return ((x - thumbPadPx) / travel).coerceIn(0f, 1f)
                }
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent()
                        val firstChange = down.changes.firstOrNull { it.pressed } ?: continue
                        if (!latestEnabled) continue
                        isDragging = true
                        val travel = (size.width - thumbPadPx * 2f).coerceAtLeast(1f)
                        val startFrac = dragFraction.coerceIn(0f, 1f)
                        val currentThumbX = thumbPadPx + startFrac * travel
                        // When attaching finger near the thumb, lock onto current value so it never jumps on touch
                        val attachedToThumb = abs(firstChange.position.x - currentThumbX) <= grabRadiusPx
                        if (!attachedToThumb) {
                            val initialFrac = fractionForX(firstChange.position.x)
                            dragFraction = initialFrac
                            lastDispatchMs = System.currentTimeMillis()
                            latestOnValueChange(initialFrac)
                        }
                        firstChange.consume()

                        val pointerId = firstChange.id
                        val downX = firstChange.position.x
                        var hasMoved = !attachedToThumb
                        while (true) {
                            val event = awaitPointerEvent()
                            val dragChange = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!dragChange.pressed) {
                                dragChange.consume()
                                break
                            }
                            if (!hasMoved && abs(dragChange.position.x - downX) > 3.dp.toPx()) {
                                hasMoved = true
                            }
                            if (hasMoved) {
                                val nextFrac = if (attachedToThumb) {
                                    (startFrac + (dragChange.position.x - downX) / travel).coerceIn(0f, 1f)
                                } else {
                                    fractionForX(dragChange.position.x)
                                }
                                if (abs(nextFrac - dragFraction) > 0.001f) {
                                    dragFraction = nextFrac
                                    val now = System.currentTimeMillis()
                                    if (now - lastDispatchMs >= 45L) {
                                        lastDispatchMs = now
                                        latestOnValueChange(nextFrac)
                                    }
                                }
                            }
                            dragChange.consume()
                        }
                        isDragging = false
                        lastReleaseMs = System.currentTimeMillis()
                        if (hasMoved) {
                            latestOnValueChange(dragFraction)
                        }
                        latestOnFinished()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val thumbRadiusPx = with(density) { 12.dp.toPx() }
        val thumbTravel = (widthPx - thumbRadiusPx * 2f).coerceAtLeast(0f)
        val thumbOffsetPx = activeFraction * thumbTravel
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(13.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        ) {
            drawRoundRect(trackBrush, cornerRadius = CornerRadius(size.height / 2))
        }
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(thumbOffsetPx.roundToInt(), 0) }
                .size(24.dp)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .clip(CircleShape)
                .background(luminousThumb)
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.88f))
            )
        }
    }
}

@Composable fun LedWheelColorPicker(
    color: String,
    brightness: Float,
    isEffectMode: Boolean,
    onColorChange: (String) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onEffectModeChange: (Boolean) -> Unit,
    onRainbowSelected: () -> Unit = {},
    onPowerOff: () -> Unit = {},
    favoriteColors: Set<String> = emptySet(),
    onToggleFavoriteColor: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isInteracting by remember { mutableStateOf(false) }
    var lastInteractMs by remember { mutableLongStateOf(0L) }
    var lastColorEmitMs by remember { mutableLongStateOf(0L) }
    val latestOnColorChange by rememberUpdatedState(onColorChange)
    val latestEnabled by rememberUpdatedState(enabled)

    val parsedHsv = remember(color) {
        FloatArray(3).also {
            val parsed = runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(android.graphics.Color.RED)
            android.graphics.Color.colorToHSV(parsed, it)
        }
    }
    var hue by remember { mutableFloatStateOf(if (parsedHsv[1] > 0.02f) parsedHsv[0] else 30f) }
    var saturation by remember { mutableFloatStateOf(if (parsedHsv[1] > 0.02f) parsedHsv[1] else 0.85f) }

    LaunchedEffect(color, isInteracting) {
        if (!isInteracting && System.currentTimeMillis() - lastInteractMs >= 240L) {
            if (parsedHsv[1] > 0.02f) {
                hue = parsedHsv[0]
            }
            saturation = parsedHsv[1]
        }
    }

    val effectiveBrightness = brightness.coerceIn(0f, 100f)

    fun updateColor(h: Float = hue, s: Float = saturation, forceEmit: Boolean = false) {
        hue = h
        saturation = s.coerceIn(0f, 1f)
        val now = System.currentTimeMillis()
        if (forceEmit || now - lastColorEmitMs >= 28L) {
            lastColorEmitMs = now
            val hex = String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(h, saturation, 1f)))
            latestOnColorChange(hex)
        }
    }

    val pureSelectedColor = Color.hsv(hue, saturation, 1f)
    val luminousSelectedColor = WledCatalogDefaults.luminousLedColor(pureSelectedColor, (effectiveBrightness / 100f).coerceIn(0.28f, 1f))

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Radial Color Wheel (NO BLACK: pure hues on edge, white at center)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        fun updateAt(pos: Offset, force: Boolean) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = min(size.width, size.height) / 2f
                            val dx = pos.x - center.x
                            val dy = pos.y - center.y
                            val dist = sqrt(dx * dx + dy * dy)
                            val sat = (dist / radius).coerceIn(0f, 1f)
                            var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                            if (angleDeg < 0f) angleDeg += 360f
                            if (angleDeg >= 360f) angleDeg -= 360f
                            updateColor(angleDeg, sat, forceEmit = force)
                        }
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitPointerEvent()
                                val first = down.changes.firstOrNull { it.pressed } ?: continue
                                if (!latestEnabled) continue
                                isInteracting = true
                                updateAt(first.position, force = true)
                                first.consume()
                                val pointerId = first.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    updateAt(change.position, force = false)
                                    change.consume()
                                }
                                isInteracting = false
                                lastInteractMs = System.currentTimeMillis()
                                updateColor(hue, saturation, forceEmit = true)
                            }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = min(size.width, size.height) / 2f

                // Sweep gradient: Red at 12 o'clock (-90°)
                rotate(-90f, pivot = center) {
                    val sweep = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFFF0000), // Red (0°)
                            Color(0xFFFF8000), // Orange
                            Color(0xFFFFFF00), // Yellow (60°)
                            Color(0xFF00FF00), // Green (120°)
                            Color(0xFF00FFFF), // Cyan (180°)
                            Color(0xFF0000FF), // Blue (240°)
                            Color(0xFFFF00FF), // Magenta (300°)
                            Color(0xFFFF0000)  // Red (360°)
                        ),
                        center = center
                    )
                    drawCircle(brush = sweep, radius = radius, center = center)
                }

                // Radial gradient: Pure White at center to transparent at edge (NO BLACK!)
                val whiteRadial = Brush.radialGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                )
                drawCircle(brush = whiteRadial, radius = radius, center = center)

                // Outer border ring for clean polish
                drawCircle(Color.White.copy(alpha = 0.22f), radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))

                // Selected Thumb Indicator with luminous halo
                val angleRad = Math.toRadians((hue - 90f).toDouble())
                val thumbX = center.x + saturation * radius * cos(angleRad).toFloat()
                val thumbY = center.y + saturation * radius * sin(angleRad).toFloat()
                val thumbPos = Offset(thumbX, thumbY)

                drawCircle(luminousSelectedColor.copy(alpha = 0.45f), radius = 16.dp.toPx(), center = thumbPos)
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = 13.dp.toPx(), center = thumbPos)
                drawCircle(Color.White, radius = 10.5.dp.toPx(), center = thumbPos, style = Stroke(width = 2.8.dp.toPx()))
                drawCircle(luminousSelectedColor, radius = 8.dp.toPx(), center = thumbPos)
            }
        }

        // 2. Brightness Slider (True WLED luminous color ramp — never dark muddy brown!)
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val lowEmission = WledCatalogDefaults.luminousLedColor(pureSelectedColor, 0.22f)
            val midEmission = WledCatalogDefaults.luminousLedColor(pureSelectedColor, 0.65f)
            val highEmission = WledCatalogDefaults.luminousLedColor(pureSelectedColor, 1f)
            val brightBrush = Brush.horizontalGradient(
                listOf(lowEmission, midEmission, highEmission)
            )
            LedSlider(
                value = effectiveBrightness / 100f,
                onValueChange = { onBrightnessChange(it * 100f) },
                trackBrush = brightBrush,
                contentDescription = "Lighting brightness",
                enabled = enabled,
                thumbColor = luminousSelectedColor
            )
        }

        // 3. Saturation / White Tone Slider
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val satBrush = Brush.horizontalGradient(
                listOf(
                    Color(0xFFFFF4DC),
                    Color.hsv(hue, 0.5f, 1f),
                    Color.hsv(hue, 1f, 1f)
                )
            )
            LedSlider(
                value = saturation,
                onValueChange = {
                    isInteracting = true
                    updateColor(hue, it, forceEmit = false)
                },
                onValueChangeFinished = {
                    isInteracting = false
                    lastInteractMs = System.currentTimeMillis()
                    updateColor(hue, saturation, forceEmit = true)
                },
                trackBrush = satBrush,
                contentDescription = "Colour saturation",
                enabled = enabled,
                thumbColor = pureSelectedColor
            )
        }

        // 4. Color Swatches (Row 1 & Row 2 with lively bounce animations)
        val row1 = listOf(
            "#FF0000" to "Red",
            "#FF7A00" to "Orange",
            "#FFBA00" to "Amber",
            "#FFE0B2" to "Warm White",
            "#FFFFFF" to "White",
            "#000000" to "Off"
        )
        val row2 = listOf(
            "#FF00EE" to "Magenta",
            "#0033FF" to "Blue",
            "#00E5FF" to "Cyan",
            "#00FF2A" to "Green"
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            row1.forEach { (hex, name) ->
                val isOff = hex == "#000000"
                val isSelected = !isOff && color.equals(hex, ignoreCase = true)
                val borderWidth by animateDpAsState(if (isSelected) 3.dp else if (isOff) 1.5.dp else 1.dp, label = "swatchBorder")
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isOff) Color(0xFF121412) else parseColor(hex))
                        .border(
                            width = borderWidth,
                            color = if (isSelected) Color.White else if (isOff) Color(0xFF888888) else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .livelyClick(enabled = enabled, pressedScale = 0.86f) {
                            if (isOff) onPowerOff() else {
                                lastInteractMs = System.currentTimeMillis()
                                val hsvArr = FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), it) }
                                updateColor(hsvArr[0], hsvArr[1], forceEmit = true)
                            }
                        }
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center
                ) {
                    if (isOff) {
                        Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            row2.forEach { (hex, name) ->
                val isSelected = color.equals(hex, ignoreCase = true)
                val borderWidth by animateDpAsState(if (isSelected) 3.dp else 1.dp, label = "swatchBorder2")
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(parseColor(hex))
                        .border(
                            width = borderWidth,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .livelyClick(enabled = enabled, pressedScale = 0.86f) {
                            lastInteractMs = System.currentTimeMillis()
                            val hsvArr = FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), it) }
                            updateColor(hsvArr[0], hsvArr[1], forceEmit = true)
                        }
                        .semantics { contentDescription = name }
                )
            }

            // Rainbow / Dynamic swatch with "R"
            val rainbowBrush = Brush.sweepGradient(
                listOf(
                    Color.Red, Color(0xFFFF8000), Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(rainbowBrush)
                    .border(
                        width = if (isEffectMode) 3.dp else 1.dp,
                        color = if (isEffectMode) Color.White else Color.White.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
                    .livelyClick(enabled = enabled, pressedScale = 0.86f) { onRainbowSelected() }
                    .semantics { contentDescription = "Rainbow effect" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "R",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // 5. Mode Buttons: Fx, Bg, and optional Favorite current Color button
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fxBgColor by animateColorAsState(if (isEffectMode) Color(0xFFFFA000) else Color(0xFF141814), label = "fxBg")
            val bgBgColor by animateColorAsState(if (!isEffectMode) Color(0xFF2B3A24) else Color(0xFF141814), label = "bgBg")

            // Fx Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(fxBgColor)
                    .border(
                        width = if (isEffectMode) 3.dp else 1.5.dp,
                        color = if (isEffectMode) Color(0xFFFFD580) else Color(0xFF666666),
                        shape = CircleShape
                    )
                    .livelyClick(enabled = enabled, pressedScale = 0.88f) { onEffectModeChange(true) }
                    .semantics { contentDescription = "Effects mode" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Fx",
                    color = if (isEffectMode) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // Bg Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(bgBgColor)
                    .border(
                        width = if (!isEffectMode) 3.dp else 1.5.dp,
                        color = if (!isEffectMode) Lime else Color(0xFF666666),
                        shape = CircleShape
                    )
                    .livelyClick(enabled = enabled, pressedScale = 0.88f) { onEffectModeChange(false) }
                    .semantics { contentDescription = "Background solid colour mode" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bg",
                    color = if (!isEffectMode) Lime else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (onToggleFavoriteColor != null) {
                val normalizedHex = color.uppercase()
                val isFav = favoriteColors.any { it.equals(normalizedHex, ignoreCase = true) }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isFav) Lime.copy(alpha = 0.22f) else Color(0xFF141814))
                        .border(
                            width = if (isFav) 2.5.dp else 1.5.dp,
                            color = if (isFav) Lime else Color(0xFF666666),
                            shape = CircleShape
                        )
                        .livelyClick(enabled = enabled, pressedScale = 0.88f) { onToggleFavoriteColor(normalizedHex) }
                        .semantics { contentDescription = "Favorite current color" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = if (isFav) Lime else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable fun CromiaColorPicker(color: String, onColorChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var bright by remember { mutableFloatStateOf(100f) }
    var fxMode by remember { mutableStateOf(false) }
    LedWheelColorPicker(
        color = color,
        brightness = bright,
        isEffectMode = fxMode,
        onColorChange = onColorChange,
        onBrightnessChange = { bright = it },
        onEffectModeChange = { fxMode = it },
        modifier = modifier,
        enabled = enabled
    )
}

data class LimelightNavItem(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badgeColor: Color? = null,
    val onClick: () -> Unit
)

/**
 * Jetpack Compose implementation of `LimelightNav` (`limelight-nav.tsx`):
 * Adaptive-width navigation bar with a sliding top glowing pill (`44.dp x 5.dp`) and a trapezoidal
 * spotlight cone (`polygon(5% 100%, 25% 0, 75% 0, 95% 100%)`) that smoothly animates (`400ms`) across tabs.
 */
@Composable
fun LimelightNavBar(
    items: List<LimelightNavItem>,
    activeIndex: Int,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val safeIndex = activeIndex.coerceIn(0, items.lastIndex)
    val animatedTabFrac by animateFloatAsState(
        targetValue = safeIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.90f,
            stiffness = 1500f
        ),
        label = "limelightNavX"
    )
    val primaryColor = Lime
    val cardColor = Panel
    val borderColor = Line

    Surface(
        color = Ink,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cardColor)
                .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp)
        ) {
            val totalW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val itemCount = items.size.coerceAtLeast(1)
            val slotW = totalW / itemCount.toFloat()
            val centerXPx = (animatedTabFrac + 0.5f) * slotW

            // Limelight Top Bar + Trapezoid Spotlight Cone Canvas (`z-10`)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barW = 44.dp.toPx().coerceAtMost(slotW * 0.72f)
                val barH = 4.dp.toPx()
                val barLeft = (centerXPx - barW / 2f).coerceIn(0f, (size.width - barW).coerceAtLeast(0f))

                val coneW = barW * 1.55f
                val coneLeft = centerXPx - coneW / 2f
                val coneTop = barH
                val coneBottom = size.height
                val conePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(coneLeft + coneW * 0.25f, coneTop)
                    lineTo(coneLeft + coneW * 0.75f, coneTop)
                    lineTo(coneLeft + coneW * 0.95f, coneBottom)
                    lineTo(coneLeft + coneW * 0.05f, coneBottom)
                    close()
                }
                drawPath(
                    path = conePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.26f),
                            primaryColor.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        startY = coneTop,
                        endY = coneBottom
                    )
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(centerXPx, barH + 10.dp.toPx()),
                        radius = (barW * 0.85f).coerceAtLeast(10f)
                    ),
                    radius = (barW * 0.85f).coerceAtLeast(10f),
                    center = Offset(centerXPx, barH + 10.dp.toPx())
                )

                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(barLeft, 0f),
                    size = androidx.compose.ui.geometry.Size(barW, barH),
                    cornerRadius = CornerRadius(barH / 2f, barH / 2f)
                )
            }

            // Interactive Tab Items (`z-20`) — zero-delay instant activation + subtle haptic pulse
            val view = LocalView.current
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == safeIndex
                    val itemColor = if (isSelected) primaryColor else Cream.copy(alpha = 0.50f)
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    performSubtleHaptic(view)
                                    item.onClick()
                                }
                            )
                            .semantics {
                                contentDescription = item.label
                                testTag = "nav_item_${item.label.lowercase()}"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = itemColor,
                                modifier = Modifier.size(22.dp)
                            )
                            if (item.badgeColor != null) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 4.dp, y = (-2).dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(item.badgeColor)
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = itemColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

fun formatClockTime(time24: String, use24Hour: Boolean): String {
    val parts = time24.trim().split(":")
    val h24 = (parts.getOrNull(0)?.toIntOrNull() ?: 7).coerceIn(0, 23)
    val m = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
    if (use24Hour) {
        return String.format("%02d:%02d", h24, m)
    }
    val isPm = h24 >= 12
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    val suffix = if (isPm) "PM" else "AM"
    return String.format("%d:%02d %s", h12, m, suffix)
}

/**
 * Minimalist, hardware-grade Rotary Time Picker (`RotaryTimePicker`) for Routines.
 * Supports both 24-hour (default) and 12-hour (AM/PM) clock systems synced with Settings via DataStore.
 */
@Composable
fun RotaryTimePicker(
    timeString: String,
    onTimeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    use24Hour: Boolean = true,
    onToggle24Hour: ((Boolean) -> Unit)? = null
) {
    val parts = timeString.split(":")
    var hour by remember(timeString) { mutableIntStateOf((parts.getOrNull(0)?.toIntOrNull() ?: 7).coerceIn(0, 23)) }
    var minute by remember(timeString) { mutableIntStateOf((parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)) }
    var activeRing by remember { mutableStateOf("hour") } // "hour" or "minute"
    val latestOnTimeChange by rememberUpdatedState(onTimeChange)

    val isPm = hour >= 12
    val displayHour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    fun emitTime(h: Int = hour, m: Int = minute) {
        val cleanH = ((h % 24) + 24) % 24
        val cleanM = ((m % 60) + 60) % 60
        hour = cleanH
        minute = cleanM
        latestOnTimeChange(String.format("%02d:%02d", cleanH, cleanM))
    }

    fun set12HourPeriod(pm: Boolean) {
        val hMod12 = hour % 12
        val nextH = if (pm) hMod12 + 12 else hMod12
        emitTime(h = nextH, m = minute)
    }

    val view = LocalView.current
    val accent = Lime

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Header Label + Full-Width 12-Hour / 24-Hour Clock System Selection Bar
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Schedule, null, tint = accent, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (use24Hour) "SCHEDULE TIME · 24-HOUR" else "SCHEDULE TIME · 12-HOUR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = Muted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = formatClockTime(String.format("%02d:%02d", hour, minute), use24Hour),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accent,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (onToggle24Hour != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(Panel)
                        .border(1.dp, Line, RoundedCornerShape(11.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        false to "12-Hour (AM / PM)",
                        true to "24-Hour (00:00–23:59)"
                    ).forEach { (is24, modeLabel) ->
                        val activeMode = use24Hour == is24
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (activeMode) accent.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = if (activeMode) 1.dp else 0.dp,
                                    color = if (activeMode) accent else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    performSubtleHaptic(view)
                                    onToggle24Hour(is24)
                                }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeLabel,
                                fontSize = 11.sp,
                                fontWeight = if (activeMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeMode) accent else Cream,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Row 2: Full-Width [ Hours | Minutes ] Selection Bar (never cut off!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Panel)
                    .border(1.dp, Line, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("hour" to "Hours", "minute" to "Minutes").forEach { (key, label) ->
                    val selected = activeRing == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = if (selected) 1.dp else 0.dp,
                                color = if (selected) accent else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                performSubtleHaptic(view)
                                activeRing = key
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = if (selected) accent else Cream,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Row 3: Full-Width Digital Time Readout Boxes (HR : MIN + AM/PM) — ample width so digits are 100% displayed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotaryDigitBox(
                label = if (use24Hour) "HOUR (00–23)" else "HOUR (1–12)",
                value = if (use24Hour) hour else displayHour12,
                maxVal = if (use24Hour) 24 else 12,
                selected = activeRing == "hour",
                accent = accent,
                onSelect = { activeRing = "hour" },
                onStep = { delta ->
                    activeRing = "hour"
                    emitTime(h = hour + delta, m = minute)
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = ":",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            RotaryDigitBox(
                label = "MINUTE (00–59)",
                value = minute,
                maxVal = 60,
                selected = activeRing == "minute",
                accent = accent,
                onSelect = { activeRing = "minute" },
                onStep = { delta ->
                    activeRing = "minute"
                    emitTime(h = hour, m = minute + delta)
                },
                modifier = Modifier.weight(1f)
            )
            if (!use24Hour) {
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Panel)
                        .border(1.dp, Line, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    listOf(false to "AM", true to "PM").forEach { (pmVal, periodText) ->
                        val selectedPeriod = isPm == pmVal
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(27.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selectedPeriod) accent.copy(alpha = 0.22f) else Color.Transparent)
                                .border(
                                    width = if (selectedPeriod) 1.dp else 0.dp,
                                    color = if (selectedPeriod) accent else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .clickable {
                                    performSubtleHaptic(view)
                                    set12HourPeriod(pmVal)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = periodText,
                                fontSize = 11.sp,
                                fontWeight = if (selectedPeriod) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedPeriod) accent else Cream,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Row 4: Centered Interactive Circular Rotary Dial
        Box(
            modifier = Modifier
                .size(136.dp)
                .pointerInput(activeRing, use24Hour, isPm) {
                    fun updateFromOffset(pos: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (deg < 0f) deg += 360f
                        if (activeRing == "hour") {
                            if (use24Hour) {
                                val nextH = ((deg / 360f) * 24f).roundToInt() % 24
                                emitTime(h = nextH, m = minute)
                            } else {
                                val raw12 = ((deg / 360f) * 12f).roundToInt() % 12
                                val nextH = if (isPm) raw12 + 12 else raw12
                                emitTime(h = nextH, m = minute)
                            }
                        } else {
                            val nextM = ((deg / 360f) * 60f).roundToInt() % 60
                            emitTime(h = hour, m = nextM)
                        }
                    }
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            val first = down.changes.firstOrNull { it.pressed } ?: continue
                            updateFromOffset(first.position)
                            first.consume()
                            val pid = first.id
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == pid } ?: break
                                if (!ch.pressed) {
                                    ch.consume()
                                    break
                                }
                                updateFromOffset(ch.position)
                                ch.consume()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val panelBg = Panel
            val lineCol = Line
            val creamCol = Cream
            val mutedCol = Muted
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerR = min(size.width, size.height) / 2f - 4.dp.toPx()
                val trackR = outerR - 11.dp.toPx()

                // Matte outer bezel & subtle track
                drawCircle(color = panelBg, radius = outerR, center = center)
                drawCircle(color = lineCol, radius = outerR, center = center, style = Stroke(width = 1.2.dp.toPx()))
                drawCircle(color = lineCol.copy(alpha = 0.55f), radius = trackR, center = center, style = Stroke(width = 2.8.dp.toPx()))

                // Architectural tick marks (24 ticks in 24H hour mode, 12 ticks otherwise)
                val tickCount = if (activeRing == "hour" && use24Hour) 24 else 12
                for (i in 0 until tickCount) {
                    val isCardinal = if (tickCount == 24) i % 6 == 0 else i % 3 == 0
                    val angleRad = Math.toRadians((i * (360.0 / tickCount)) - 90.0)
                    val rStart = if (isCardinal) outerR - 7.5.dp.toPx() else outerR - 4.5.dp.toPx()
                    val rEnd = outerR - 2.dp.toPx()
                    drawLine(
                        color = if (isCardinal) creamCol.copy(alpha = 0.55f) else mutedCol.copy(alpha = 0.3f),
                        start = Offset(center.x + rStart * cos(angleRad).toFloat(), center.y + rStart * sin(angleRad).toFloat()),
                        end = Offset(center.x + rEnd * cos(angleRad).toFloat(), center.y + rEnd * sin(angleRad).toFloat()),
                        strokeWidth = if (isCardinal) 1.6.dp.toPx() else 1.dp.toPx()
                    )
                }

                // Active progress arc & precision thumb dot on track
                val activeFraction = if (activeRing == "hour") {
                    if (use24Hour) hour / 24f else (hour % 12) / 12f
                } else {
                    minute / 60f
                }
                val angleRad = Math.toRadians((activeFraction * 360f - 90f).toDouble())
                val knobPos = Offset(
                    center.x + trackR * cos(angleRad).toFloat(),
                    center.y + trackR * sin(angleRad).toFloat()
                )

                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = (activeFraction * 360f).coerceAtLeast(4f),
                    useCenter = false,
                    topLeft = Offset(center.x - trackR, center.y - trackR),
                    size = androidx.compose.ui.geometry.Size(trackR * 2f, trackR * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawCircle(color = accent.copy(alpha = 0.25f), radius = 9.5.dp.toPx(), center = knobPos)
                drawCircle(color = accent, radius = 5.5.dp.toPx(), center = knobPos)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (activeRing == "hour") {
                        if (use24Hour) String.format("%02d", hour) else String.format("%02d", displayHour12)
                    } else {
                        String.format("%02d", minute)
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Cream,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = if (activeRing == "hour") {
                        if (use24Hour) "24H DIAL" else if (isPm) "12H · PM" else "12H · AM"
                    } else "MINUTES",
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Row 5: Equal-Width Precision Step Buttons (−15m | −1 | +1 | +15m)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "−15m" to {
                    val totalMins = (hour * 60 + minute - 15 + 1440) % 1440
                    emitTime(h = (totalMins / 60) % 24, m = totalMins % 60)
                },
                "−1" to {
                    if (activeRing == "hour") emitTime(h = hour - 1, m = minute)
                    else emitTime(h = hour, m = minute - 1)
                },
                "+1" to {
                    if (activeRing == "hour") emitTime(h = hour + 1, m = minute)
                    else emitTime(h = hour, m = minute + 1)
                },
                "+15m" to {
                    val totalMins = (hour * 60 + minute + 15) % 1440
                    emitTime(h = (totalMins / 60) % 24, m = totalMins % 60)
                }
            ).forEach { (btnLabel, action) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Panel)
                        .border(1.dp, Line, RoundedCornerShape(9.dp))
                        .livelyClick(onClick = action),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = btnLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Cream,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Row 6: Single Clean 5-Item Equal-Width Time Preset Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "07:00" to (if (use24Hour) "07:00" else "7:00a"),
                "12:00" to (if (use24Hour) "12:00" else "12:00p"),
                "18:30" to (if (use24Hour) "18:30" else "6:30p"),
                "21:30" to (if (use24Hour) "21:30" else "9:30p"),
                "23:30" to (if (use24Hour) "23:30" else "11:30p")
            )
            presets.forEach { (preset24, displayLabel) ->
                val isSelected = String.format("%02d:%02d", hour, minute) == preset24
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.18f) else Panel)
                        .border(1.dp, if (isSelected) accent else Line, RoundedCornerShape(8.dp))
                        .livelyClick {
                            val p = preset24.split(":")
                            emitTime(p[0].toInt(), p[1].toInt())
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayLabel,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accent else Cream,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun RotaryDigitBox(
    label: String,
    value: Int,
    maxVal: Int,
    selected: Boolean,
    accent: Color,
    onSelect: () -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var dragAccum by remember { mutableFloatStateOf(0f) }
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Panel)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else Line, RoundedCornerShape(14.dp))
            .clickable {
                performSubtleHaptic(view)
                onSelect()
            }
            .pointerInput(maxVal) {
                detectDragGestures(
                    onDragStart = {
                        dragAccum = 0f
                        onSelect()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccum += dragAmount.y
                        val thresholdPx = 18.dp.toPx()
                        while (dragAccum <= -thresholdPx) {
                            dragAccum += thresholdPx
                            performSubtleHaptic(view)
                            onStep(1)
                        }
                        while (dragAccum >= thresholdPx) {
                            dragAccum -= thresholdPx
                            performSubtleHaptic(view)
                            onStep(-1)
                        }
                    }
                )
            }
            .padding(vertical = 6.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (selected) accent else Muted,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = String.format("%02d", value),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (selected) accent else Cream,
            maxLines = 1,
            softWrap = false
        )
    }
}

