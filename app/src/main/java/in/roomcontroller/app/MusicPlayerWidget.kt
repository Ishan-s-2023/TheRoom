package `in`.roomcontroller.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

private fun timeLabel(milliseconds:Int):String {val seconds=milliseconds.coerceAtLeast(0)/1000;return "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"}

@Composable fun MusicPlayerWidget(player:JSONObject,preview:Boolean,busy:Boolean,onAction:(String,JSONObject)->Unit) {
    val track=player.optJSONObject("item")
    val playing=player.optBoolean("is_playing")
    val total=track?.optInt("duration_ms",210000)?.coerceAtLeast(1) ?: 210000
    var position by remember(player.toString()) {mutableFloatStateOf(player.optInt("progress_ms",41000).toFloat()/total*100f)}
    var shuffle by remember {mutableStateOf(false)}
    var repeatMode by remember {mutableIntStateOf(0)}
    var zoomed by remember {mutableStateOf(false)}
    val rotation=remember {Animatable(0f)}
    LaunchedEffect(playing) {
        if(playing) while(true) {rotation.animateTo(rotation.value+360f,tween(24000,easing=LinearEasing))}
    }
    val accent=Lime
    val background=Ink
    val text=Cream
    val border=Line
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(if(zoomed)230.dp else 190.dp).clip(RoundedCornerShape(20.dp)).background(Brush.radialGradient(listOf(accent.copy(alpha=.18f),background))),contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(if(zoomed)212.dp else 170.dp).rotate(rotation.value).semantics {contentDescription="Album disc; tap to expand"}.clickable {zoomed=!zoomed}) {
                val radius=size.minDimension/2
                drawCircle(Brush.sweepGradient(listOf(Color(0xFF18252A),accent.copy(alpha=.6f),Color(0xFF393058),Color(0xFF142A31),accent.copy(alpha=.35f),Color(0xFF18252A))))
                for(i in 0..14) drawCircle(Color.White.copy(alpha=.06f),radius*(.35f+i*.042f),style=Stroke(1.dp.toPx()))
                drawCircle(Brush.linearGradient(listOf(accent,Color(0xFFDCD0B8))),radius*.43f)
                drawArc(Color(0xFF1C3F3A),30f,145f,true,style=Stroke(radius*.15f))
                drawCircle(background,radius*.13f)
                drawCircle(text.copy(alpha=.45f),radius*.14f,style=Stroke(1.dp.toPx()))
            }
            Box(Modifier.size(12.dp).clip(CircleShape).background(background))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
            Canvas(Modifier.width(110.dp).height(34.dp)) {
                for(c in 0..9)for(r in 0..4) {
                    val movement=if(playing) sin(rotation.value*.06+c*.8).toFloat() else .4f
                    val strength=(.35f+.65f*((movement+1)/2))*(1-r*.11f)
                    drawCircle(accent.copy(alpha=strength.coerceIn(.15f,1f)),2.dp.toPx(),Offset(c*(size.width/10)+4.dp.toPx(),size.height-r*6.dp.toPx()-4.dp.toPx()))
                }
            }
        }
        Text(track?.items("artists")?.joinToString {it.optString("name")} ?: "Choose something to play",color=Muted,fontSize=12.sp)
        Text(track?.optString("name") ?: "Nothing playing",fontFamily=FontFamily.Serif,fontSize=27.sp)
        SmallLabel(if(playing)"Spotify · Playing" else "Spotify · Paused")
        Slider05(position,{position=it},enabled=preview&&!busy,label="Playback position",onValueChangeFinished={onAction("seek",obj("position_ms" to (position/100*total).toInt()))})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(timeLabel((position/100*total).toInt()),color=Muted,fontSize=11.sp)
            Text(timeLabel(total),color=Muted,fontSize=11.sp)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            IconButton(enabled=preview&&!busy,onClick={shuffle=!shuffle;onAction("shuffle",obj("state" to shuffle))}) {Icon(Icons.Outlined.Shuffle,"Shuffle",tint=if(shuffle)accent else Muted,modifier=Modifier.size(20.dp))}
            IconButton(enabled=!busy,onClick={onAction("previous",obj())}) {Icon(Icons.Outlined.SkipPrevious,"previous",modifier=Modifier.size(28.dp))}
            FilledIconButton(enabled=!busy,onClick={onAction(if(playing)"pause" else "play",obj())},modifier=Modifier.size(58.dp)) {Icon(if(playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(playing)"pause" else "play",modifier=Modifier.size(28.dp))}
            IconButton(enabled=!busy,onClick={onAction("next",obj())}) {Icon(Icons.Outlined.SkipNext,"next",modifier=Modifier.size(28.dp))}
            IconButton(enabled=preview&&!busy,onClick={repeatMode=(repeatMode+1)%3;onAction("repeat",obj("mode" to listOf("off","context","track")[repeatMode]))}) {Icon(if(repeatMode==2)Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,"Repeat",tint=if(repeatMode>0)accent else Muted,modifier=Modifier.size(20.dp))}
        }
        if(!preview) Text("Seek, shuffle and repeat will connect after the UI review.",color=Muted,fontSize=11.sp)
    }
}
