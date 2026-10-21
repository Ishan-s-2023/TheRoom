package `in`.roomcontroller.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.sin

fun parseColor(hex: String) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFFB5F36A))
fun action(type: String, data: JSONObject) = obj("type" to type, type to data)

@Composable fun HomeScreen(model: RoomModel, ui: RoomUi, wled: WledModel, wledUi: WledUi, openWled: (String)->Unit, navigate: (String)->Unit) {
    val state=ui.state
    val lights=state.optJSONObject("lighting") ?: obj()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        SmallLabel("ROOM / CONTROL CENTER")
        Icon(Icons.Outlined.GraphicEq,null,tint=Lime,modifier=Modifier.size(22.dp))
    }
    PageTitle("Make yourself at home", "Set the feeling.", "A little light. The right sound. Your space, in sync.")
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if(state.optBoolean("connected") && !ui.stale) Lime else Color(0xFFF6BC7B)))
        Text(if(ui.stale) "Connection interrupted" else if(state.optBoolean("connected")) "Controller connected" else "Controller offline",fontSize=12.sp,color=Muted)
        Spacer(Modifier.weight(1f)); SmallLabel("Wi-Fi")
    }
    if (ui.demo) {
    RoomCard {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            SmallLabel("YOUR LIGHTING")
            LiquidToggle(checked=lights.optBoolean("power"),onCheckedChange={model.mutate("POST","/actions",action("lighting",lights.copyJson().put("power",it)),"Lighting updated")},enabled=!ui.busy,label="Lighting power")
        }
        var brightness by remember(lights.toString()) {mutableFloatStateOf(lights.optInt("brightness",65).toFloat())}
        BrightnessControl(brightness,{brightness=it},enabled=!ui.busy,onFinished={model.mutate("POST","/actions",action("lighting",lights.copyJson().put("brightness",brightness.toInt())),"Brightness updated")})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column {Text("Room lighting",fontSize=20.sp);Text(lights.optString("effect","solid").replaceFirstChar {it.uppercase()},color=Muted,fontSize=12.sp)}
            FilledTonalIconButton(onClick={navigate("Lights")}) {Icon(Icons.Outlined.ArrowOutward,"Open lighting")}
        }
    }
    SectionLabel("A mood for every moment", "PRESETS")
    val presets=state.items("presets")
    if(presets.isEmpty()) EmptyCard("Make it yours", "Save your first lighting preset in Lights.",Icons.Outlined.BookmarkBorder)
    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        presets.take(3).forEach { preset ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Panel).clickable(enabled=!ui.busy) {model.mutate("POST","/actions",action("lighting",preset.getJSONObject("lighting")),"Preset applied")}.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.WbSunny,null,tint=parseColor(preset.getJSONObject("lighting").optString("color")))
                Text(preset.optString("name"),fontSize=13.sp,maxLines=2)
            }
        }
    }
    } else WledQuickControls(wled,wledUi,openWled)
    RoomCard {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Lime.copy(alpha=.12f)),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.MusicNote,null,tint=Lime)}
            Column(Modifier.weight(1f)) {Text("Give the room a soundtrack",fontSize=15.sp); Text("Spotify & playback devices",fontSize=12.sp,color=Muted)}
            IconButton(onClick={navigate("Music")}) {Icon(Icons.Outlined.ChevronRight,"Open music")}
        }
    }
    SectionLabel("Within reach", "IR FAVORITES")
    val favorites=state.items("commands").filter {it.optBoolean("favorite")}
    if(favorites.isEmpty()) Text("Your favorite remote commands will appear here.",color=Muted,fontSize=13.sp)
    favorites.forEach { command ->
        OutlinedButton(onClick={model.mutate("POST","/actions",obj("type" to "ir","command_id" to command.getString("id")),"IR transmitted; appliance state unconfirmed")},enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.SettingsRemote,null,Modifier.size(18.dp)); Spacer(Modifier.width(10.dp));Text(command.optString("name"));Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp))
        }
    }
}

@Composable fun EmptyCard(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
            val value=preset.getJSONObject("lighting")
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(parseColor(value.optString("color"))))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable {draft=value.copyJson()}) {
                    Text(preset.optString("name"),fontSize=15.sp)
                    Text("${if(value.optBoolean("power")) "On" else "Off"} · ${value.optInt("brightness")}% · ${value.optString("effect")}",color=Muted,fontSize=11.sp)
                }
                IconButton(onClick={editing=preset}) {Icon(Icons.Outlined.Edit,"Edit ${preset.optString("name")}",Modifier.size(18.dp))}
                IconButton(onClick={deleting=preset.optString("id")}) {Icon(Icons.Outlined.DeleteOutline,"Delete preset")}
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
        RoomCard(Modifier.clickable {navigate(name)}) {
            Icon(icon,null,tint=Lime)
            Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){Text(name,fontSize=21.sp);Text(detail,color=Muted,fontSize=12.sp,lineHeight=19.sp)};Icon(Icons.Outlined.ArrowOutward,null)}
        }
    }
}
