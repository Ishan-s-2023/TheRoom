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
import kotlin.math.roundToInt

@Composable fun LightingEditor(value: JSONObject, onChange: (JSONObject) -> Unit) {
    val automatic = value.optString("effect", "solid") != "solid"
    var lastReactive by remember { mutableStateOf(if (automatic) value.optString("effect") else "beat") }
    val power = value.optBoolean("power", true)
    val color = value.optString("color", "#FF8800")
    val brightness = value.optInt("brightness", 65).toFloat()
    val speed = value.optInt("speed", 50).toFloat()
    val intensity = value.optInt("intensity", 60).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabelInline("Room lighting")
            LiquidToggle(power, { onChange(value.copyJson().apply { put("power", it) }) }, label = "Lighting power")
        }

        LedWheelColorPicker(
            color = if (power) color else "#000000",
            brightness = if (power) brightness else 0f,
            isEffectMode = automatic,
            onColorChange = { hex ->
                onChange(value.copyJson().apply { put("color", hex); put("effect", "solid"); put("power", true) })
            },
            onBrightnessChange = { b ->
                val intB = b.roundToInt()
                onChange(value.copyJson().apply { put("brightness", intB); put("power", intB > 0) })
            },
            onEffectModeChange = { isFx ->
                onChange(value.copyJson().apply { put("effect", if (isFx) lastReactive else "solid"); put("power", true) })
            },
            onRainbowSelected = {
                lastReactive = "bands"
                onChange(value.copyJson().apply { put("effect", "bands"); put("power", true) })
            },
            onPowerOff = {
                onChange(value.copyJson().apply { put("power", false) })
            },
            enabled = true
        )

        if (automatic) {
            RoomCard(Modifier.fillMaxWidth()) {
                SectionLabel("Music reactive effects")
                Choices(listOf("beat", "bass", "level", "bands"), value.optString("effect")) {
                    lastReactive = it
                    onChange(value.copyJson().put("effect", it).put("power", true))
                }
                SettingSlider("Response speed", speed, { onChange(value.copyJson().put("speed", it.toInt())) })
                SettingSlider("Effect intensity", intensity, { onChange(value.copyJson().put("intensity", it.toInt())) })
            }
        }
    }
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
