package `in`.roomcontroller.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

@Composable fun LightingEditor(value:JSONObject,onChange:(JSONObject)->Unit) {
    val automatic=value.optString("effect","solid")!="solid"
    var lastReactive by remember {mutableStateOf(if(automatic)value.optString("effect") else "beat")}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        SectionLabelInline("Room lighting")
        LiquidToggle(value.optBoolean("power"),{onChange(value.copyJson().put("power",it))},label="Lighting power")
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Text("Move with music",fontSize=15.sp);Text("Automatic room-microphone response",color=Muted,fontSize=11.sp)}
        LiquidToggle(automatic,{onChange(value.copyJson().put("effect",if(it)lastReactive else "solid"))},label="Move with music")
    }
    if(automatic) {
        Choices(listOf("beat","bass","level","bands"),value.optString("effect")) {lastReactive=it;onChange(value.copyJson().put("effect",it))}
        Text("Music controls the colour. Turn off Move with music to choose a manual colour.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        SettingSlider("Response speed",value.optInt("speed",50).toFloat(),{onChange(value.copyJson().put("speed",it.toInt()))})
        SettingSlider("Effect intensity",value.optInt("intensity",60).toFloat(),{onChange(value.copyJson().put("intensity",it.toInt()))})
    } else {
        SmallLabel("CHOOSE YOUR COLOUR")
        CromiaColorPicker(value.optString("color","#B5F36A"),{onChange(value.copyJson().put("color",it))})
    }
    BrightnessControl(value.optInt("brightness",65).toFloat(),{onChange(value.copyJson().put("brightness",it.toInt()))})
}

@Composable fun PresetEditor(existing:JSONObject?,current:JSONObject,dismiss:()->Unit,save:(String,JSONObject)->Unit) {
    var name by remember {mutableStateOf(existing?.optString("name").orEmpty())}
    var value by remember {mutableStateOf((existing?.optJSONObject("lighting") ?: current).copyJson())}
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.93f).padding(horizontal=16.dp).clip(RoundedCornerShape(24.dp)).background(Panel)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(existing==null)"Create preset" else "Edit preset",fontSize=22.sp,modifier=Modifier.weight(1f))
                IconButton(onClick=dismiss) {Icon(Icons.Outlined.Close,"Close preset editor")}
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value=name,onValueChange={name=it.take(60)},label={Text("Preset name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Text("Choose the settings this preset will apply. Saving does not change the lights.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
                LightingEditor(value,{value=it})
                Text("Includes: power, colour, brightness, music mode, effect speed and intensity.",color=Muted,fontSize=11.sp,lineHeight=17.sp)
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick=dismiss,modifier=Modifier.weight(1f)) {Text("Cancel")}
                Button(enabled=name.isNotBlank(),onClick={save(name.trim(),value.copyJson());dismiss()},modifier=Modifier.weight(1f)) {Text("Save preset")}
            }
        }
    }
}
