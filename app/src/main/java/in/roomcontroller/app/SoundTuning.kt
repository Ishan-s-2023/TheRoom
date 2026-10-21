package `in`.roomcontroller.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Native adaptation of the requested IsoFader interaction; no WebView is needed. */
@Composable fun IsoFader(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val accent = Lime
    val line = Line
    val ink = Ink
    val text = Cream
    val latestChange by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Canvas(Modifier.fillMaxWidth().height(190.dp)
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
            }) {
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
            polygon(ink, listOf(Offset(w*.12f,h*.91f), Offset(w*.43f,h*.05f), Offset(w*.91f,h*.10f), Offset(w*.60f,h*.96f)))
            polygon(line.copy(alpha=.6f), listOf(Offset(w*.12f,h*.91f), Offset(w*.60f,h*.96f), Offset(w*.60f,h*.98f), Offset(w*.12f,h*.93f)))
            val start = Offset(w*.65f,h*.14f)
            val end = Offset(w*.35f,h*.86f)
            val t = 1f - value.coerceIn(0f,200f)/200f
            val thumb = start + (end-start)*t
            drawLine(line, start, end, 7.dp.toPx(), StrokeCap.Round)
            drawLine(accent.copy(alpha=.55f*opacity), thumb, end, 3.dp.toPx(), StrokeCap.Round)
            for (tick in 0..4) {
                val point = start + (end-start)*(tick/4f)
                drawLine(if(tick==2) accent.copy(alpha=.6f) else line, point+Offset(w*.20f,0f), point+Offset(w*.28f,h*.006f), 1.dp.toPx())
            }
            val half = minOf(w*.29f, 25.dp.toPx())
            val depth = 9.dp.toPx()
            val tilt = 5.dp.toPx()
            val a = thumb + Offset(-half,-8.dp.toPx()-tilt)
            val b = thumb + Offset(half,-8.dp.toPx()+tilt)
            val c = thumb + Offset(half,8.dp.toPx()+tilt)
            val d = thumb + Offset(-half,8.dp.toPx()-tilt)
            polygon(Color.Black.copy(alpha=.22f), listOf(a+Offset(3.dp.toPx(),depth), b+Offset(3.dp.toPx(),depth), c+Offset(3.dp.toPx(),depth*2), d+Offset(3.dp.toPx(),depth*2)))
            polygon(accent.copy(alpha=.55f), listOf(d,c,c+Offset(0f,depth),d+Offset(0f,depth)))
            polygon(accent.copy(alpha=.75f), listOf(b,c,c+Offset(0f,depth),b+Offset(0f,depth)))
            polygon(accent, listOf(a,b,c,d))
            drawLine(ink,thumb+Offset(-half*.7f,-tilt*.7f),thumb+Offset(half*.7f,tilt*.7f),2.dp.toPx(),StrokeCap.Round)
            drawCircle(text.copy(alpha=.15f),1.5.dp.toPx(),a+Offset(5.dp.toPx(),4.dp.toPx()))
        }
        Text("${value.roundToInt()}%", color = if(enabled) accent else Muted, fontSize = 18.sp)
    }
}

@Composable fun SoundTuningScreen(model: RoomModel, ui: RoomUi) {
    val current = ui.state.optJSONObject("microphone") ?: obj()
    val directDevice = ui.state.has("runtime") && !ui.demo
    var draft by remember(current.toString()) { mutableStateOf(current.copyJson().apply {
        listOf("bass_gain", "mid_gain", "treble_gain").forEach { put(it, current.optInt(it, 100)) }
    }) }
    val capabilities = ui.state.optJSONArray("capabilities")
    val microphoneReady = ui.demo || (0 until (capabilities?.length() ?: 0)).any { capabilities?.optString(it) == "microphone" }
    PageTitle("Room microphone", "In tune with you.", "Shape the bass, mids, and sparkle your lights respond to.")
    RoomCard {
        SectionLabel("Band response", if(ui.demo) "PREVIEW" else "MICROPHONE")
        Text("Slide up to bring a band forward. 100% is neutral; 0% mutes its response.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Bass" to "bass_gain", "Mids" to "mid_gain", "Treble" to "treble_gain").forEach { (label, key) ->
                IsoFader(label, draft.optInt(key,100).toFloat(), { draft=draft.copyJson().put(key,it.roundToInt()) }, Modifier.weight(1f), !ui.busy)
            }
        }
        TextButton(onClick={ draft=draft.copyJson().put("bass_gain",100).put("mid_gain",100).put("treble_gain",100) },enabled=!ui.busy) { Text("Reset bands to neutral") }
        SmallLabel(if(ui.demo) "SAMPLE INPUT · MIX AUDITION" else "TELEMETRY · MIX AUDITION")
        val bands=ui.state.optJSONObject("telemetry")?.optJSONArray("bands")
        listOf("Bass" to "bass_gain", "Mids" to "mid_gain", "Treble" to "treble_gain").forEachIndexed { i, (label,key) ->
            val level=((bands?.optDouble(i,0.0) ?: 0.0)*draft.optInt(key,100)/100.0).coerceIn(0.0,1.0).toFloat()
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,color=Muted,fontSize=11.sp);Text("${(level*100).roundToInt()}%",color=Muted,fontSize=11.sp) }
                LinearProgressIndicator(progress={level},modifier=Modifier.fillMaxWidth().height(5.dp),color=Lime,trackColor=Line)
            }
        }
        Text(if(directDevice) "Band weights audition the mix on this screen. This firmware saves sensitivity and quiet settings; independent band gains still need device support. Spotify audio is unchanged." else "This audition weights the displayed microphone bands. Saved gains are part of the companion reactive-lighting profile; physical DSP and output still need firmware integration. Spotify audio is unchanged.",color=Muted,fontSize=11.sp,lineHeight=18.sp)
    }
    RoomCard {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Automatic sensitivity",modifier=Modifier.weight(1f),fontSize=14.sp)
            LiquidToggle(draft.optBoolean("automatic",true),{draft=draft.copyJson().put("automatic",it)},enabled=!ui.busy,label="Automatic sensitivity")
        }
        if(!draft.optBoolean("automatic",true)) SettingSlider("Sensitivity",draft.optInt("sensitivity",50).toFloat(),{draft=draft.copyJson().put("sensitivity",it.toInt())},1f..100f)
        SettingSlider("Noise floor",draft.optInt("noise_floor",15).toFloat(),{draft=draft.copyJson().put("noise_floor",it.toInt())})
        SettingSlider("Smoothing",draft.optInt("smoothing",60).toFloat(),{draft=draft.copyJson().put("smoothing",it.toInt())})
        SmallLabel("WHEN THE ROOM GOES QUIET")
        Choices(listOf("off","dim","hold"),draft.optString("quiet_behavior","dim")) {draft=draft.copyJson().put("quiet_behavior",it)}
        SettingSlider("Quiet timeout",draft.optInt("quiet_seconds",10).toFloat(),{draft=draft.copyJson().put("quiet_seconds",it.toInt())},1f..300f," s")
        if(!microphoneReady) Text("Microphone hardware is unavailable. You can audition the mix here; saving and calibration need a verified microphone driver.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        ActionButton("Save microphone settings",Icons.Outlined.Check,!ui.busy && microphoneReady) {
            val payload=draft.copyJson().apply { if(directDevice) { remove("bass_gain");remove("mid_gain");remove("treble_gain") } }
            model.mutate("POST","/actions",action("microphone",payload),if(ui.demo) "Preview microphone settings saved" else "Microphone settings submitted")
        }
        OutlinedButton(onClick={model.mutate("POST","/actions",obj("type" to "calibrate"),"Calibration requested")},enabled=!ui.busy && microphoneReady) {Text("Calibrate room noise")}
    }
}
