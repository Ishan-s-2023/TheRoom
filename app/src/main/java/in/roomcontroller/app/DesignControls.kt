package `in`.roomcontroller.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

@Composable fun LiquidToggle(checked: Boolean, onCheckedChange: (Boolean)->Unit, modifier: Modifier=Modifier, enabled: Boolean=true, variant: String="default", label: String="Toggle") {
    val amount by animateFloatAsState(if(checked)1f else 0f,spring(dampingRatio=.65f,stiffness=450f),label="Liquid switch")
    val accent = when(variant) {"success"->Color(0xFF78CF97);"warning"->Color(0xFFF0BB70);"danger"->Color(0xFFF28D8D);else->Lime}
    val border = Line
    val background = Panel
    Box(modifier.size(68.dp,48.dp).alpha(if(enabled)1f else .4f)
        .semantics {contentDescription=label}
        .toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onCheckedChange),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(60.dp,32.dp)) {
            drawRoundRect(if(checked)accent.copy(alpha=.85f) else border,cornerRadius=CornerRadius(size.height/2))
            val center=Offset(16.dp.toPx()+amount*28.dp.toPx(),size.height/2)
            drawCircle(if(checked)accent else background,12.dp.toPx(),center+Offset((.5f-amount)*8.dp.toPx(),0f))
            drawCircle(Color.White,11.dp.toPx(),center)
            drawCircle(accent.copy(alpha=.16f),6.dp.toPx(),center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Slider05(value: Float, onValueChange:(Float)->Unit, modifier:Modifier=Modifier, enabled:Boolean=true, label:String="Volume", onValueChangeFinished:()->Unit={}) {
    val accent=Lime
    val trackColor=Line
    Slider(value=value.coerceIn(0f,100f),onValueChange=onValueChange,valueRange=0f..100f,enabled=enabled,
        onValueChangeFinished=onValueChangeFinished,modifier=modifier.fillMaxWidth().semantics {contentDescription=label},
        thumb={Box(Modifier.size(22.dp,32.dp).clip(RoundedCornerShape(8.dp)).background(if(enabled)accent else trackColor).padding(8.dp,7.dp)) {Box(Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha=.8f))) } },
        track={ state -> Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(trackColor,cornerRadius=CornerRadius(size.height/2))
            drawRoundRect(Brush.horizontalGradient(listOf(accent.copy(alpha=.5f),accent)),size= androidx.compose.ui.geometry.Size(size.width*(state.value/100f),size.height),cornerRadius=CornerRadius(size.height/2))
        }})
}

@Composable fun BrightnessControl(value:Float,onChange:(Float)->Unit,enabled:Boolean=true,onFinished:()->Unit={}) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.LightMode,null,tint=Lime,modifier=Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Brightness",color=Muted,fontSize=13.sp)
            Spacer(Modifier.weight(1f))
            Text("${value.roundToInt()}%",fontFamily=FontFamily.Serif,fontSize=32.sp)
        }
        Slider05(value,onChange,enabled=enabled,label="Brightness",onValueChangeFinished=onFinished)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {SmallLabel("Soft");SmallLabel("Bright")}
    }
}

@Composable fun VolumeControl(value:Float,onChange:(Float)->Unit,enabled:Boolean=true) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.VolumeUp,null,tint=Lime,modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp));Text("Spotify volume",fontSize=12.sp,color=Muted)
            Spacer(Modifier.weight(1f));Text("${value.roundToInt()}%",fontSize=13.sp)
        }
        Slider05(value,onChange,enabled=enabled,label="Spotify volume")
    }
}

@Composable fun CromiaColorPicker(color:String,onColorChange:(String)->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val notify by rememberUpdatedState(onColorChange)
    val initial = remember(color) {FloatArray(3).also {android.graphics.Color.colorToHSV(runCatching {android.graphics.Color.parseColor(color)}.getOrDefault(android.graphics.Color.GREEN),it)}}
    var hue by remember {mutableFloatStateOf(initial[0])}
    var saturation by remember {mutableFloatStateOf(initial[1])}
    var brightness by remember {mutableFloatStateOf(initial[2])}
    var hexDraft by remember(color) {mutableStateOf(color)}
    LaunchedEffect(color) {
        val own=String.format("#%06X",0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,brightness)))
        if(!own.equals(color,true)){hue=initial[0];saturation=initial[1];brightness=initial[2]}
    }
    fun update(h:Float=hue,s:Float=saturation,v:Float=brightness) {
        hue=h;saturation=s;brightness=v
        notify(String.format("#%06X",0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(h,s,v))))
    }
    val border=Line
    val thumbColor=Color.hsv(hue,saturation,brightness)
    Column(modifier.fillMaxWidth().alpha(if(enabled)1f else .45f),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp))
            .semantics {contentDescription="Colour saturation and brightness"}
            .pointerInput(enabled,hue) {detectTapGestures {p->if(enabled)update(s=(p.x/size.width).coerceIn(0f,1f),v=1f-(p.y/size.height).coerceIn(0f,1f))}}
            .pointerInput(enabled,hue) {detectDragGestures {change,_-> if(enabled){change.consume();update(s=(change.position.x/size.width).coerceIn(0f,1f),v=1f-(change.position.y/size.height).coerceIn(0f,1f))}}}) {
            drawRect(Color.hsv(hue,1f,1f))
            drawRect(Brush.horizontalGradient(listOf(Color.White,Color.Transparent)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
            val point=Offset(saturation*size.width,(1f-brightness)*size.height)
            drawCircle(Color.Black.copy(alpha=.3f),10.dp.toPx(),point)
            drawCircle(thumbColor,7.dp.toPx(),point)
            drawCircle(Color.White,8.dp.toPx(),point,style=Stroke(2.dp.toPx()))
        }
        SmallLabel("HUE")
        Box(Modifier.fillMaxWidth().height(36.dp)) {
            Canvas(Modifier.fillMaxWidth().height(12.dp).align(Alignment.Center)
                .clip(CircleShape)
                .pointerInput(enabled,saturation,brightness) {detectTapGestures {p->if(enabled)update(h=(p.x/size.width*359.99f).coerceIn(0f,359.99f))}}
                .pointerInput(enabled,saturation,brightness) {detectDragGestures {change,_->if(enabled){change.consume();update(h=(change.position.x/size.width*359.99f).coerceIn(0f,359.99f))}}}) {
                drawRect(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))
                drawCircle(Color.White,6.dp.toPx(),Offset(hue/360f*size.width,size.height/2))
            }
        }
        OutlinedTextField(value=hexDraft,onValueChange={hexDraft=it.take(7);if(it.matches(Regex("#[0-9a-fA-F]{6}")))onColorChange(it.uppercase())},
            label={Text("HEX colour")},enabled=enabled,singleLine=true,isError=!hexDraft.matches(Regex("#[0-9a-fA-F]{6}")),
            leadingIcon={Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(thumbColor))},modifier=Modifier.fillMaxWidth())
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf("#EF4444","#F97316","#EAB308","#22C55E","#06B6D4","#3B82F6","#8B5CF6","#EC4899","#FFFFFF","#B5F36A").forEach {hex->
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(parseColor(hex))
                    .border(if(color.equals(hex,true))2.dp else 0.dp,border,RoundedCornerShape(7.dp))
                    .semantics {contentDescription="Choose $hex"}.clickable(enabled=enabled){onColorChange(hex)})
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(Triple("H",hue,360f),Triple("S",saturation*100,100f),Triple("B",brightness*100,100f)).forEachIndexed {index,item->
                var field by remember(item.second.roundToInt()) {mutableStateOf(item.second.roundToInt().toString())}
                OutlinedTextField(value=field,onValueChange={value->field=value.take(3);value.toFloatOrNull()?.takeIf {it in 0f..item.third}?.let {v->when(index){0->update(h=v.coerceAtMost(359.99f));1->update(s=v/100);else->update(v=v/100)}}},label={Text(item.first)},singleLine=true,enabled=enabled,modifier=Modifier.weight(1f))
            }
        }
    }
}
