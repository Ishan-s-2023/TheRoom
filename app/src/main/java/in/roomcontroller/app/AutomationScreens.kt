package `in`.roomcontroller.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

@Composable fun RemotesScreen(model: RoomModel, ui: RoomUi) {
    var selected by rememberSaveable {mutableStateOf("")}
    var adding by remember {mutableStateOf(false)}
    var learning by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<JSONObject?>(null)}
    var editing by remember {mutableStateOf<JSONObject?>(null)}
    val appliances=ui.state.items("appliances")
    val active=appliances.firstOrNull {it.optString("id")==selected} ?: appliances.firstOrNull()
    val commands=ui.state.items("commands").filter {it.optString("appliance_id")==active?.optString("id")}
    PageTitle("Universal remote", "Less searching.\nMore living.", "Your IR appliances and learned commands, all within reach.")
    ActionButton("Add appliance",Icons.Outlined.Add,!ui.busy) {adding=true}
    if(appliances.isEmpty()) EmptyCard("Meet your first appliance","Add a fan, speaker, or another compatible infrared device.",Icons.Outlined.SettingsRemote)
    else {
        Choices(appliances.map {it.optString("name")},active?.optString("name").orEmpty()) {name ->selected=appliances.first {it.optString("name")==name}.getString("id")}
        RoomCard {
            SectionLabel(active?.optString("name").orEmpty(),"${commands.size} COMMANDS")
            Text("IR signals have no appliance feedback. A power toggle is sent once and is never automatically retried.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
            commands.forEach {command ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={model.mutate("PUT","/commands/${command.getString("id")}",command.copyJson().apply {remove("id");put("favorite",!command.optBoolean("favorite"))})}) {
                        Icon(if(command.optBoolean("favorite")) Icons.Outlined.Star else Icons.Outlined.StarBorder,"Toggle favorite",tint=Lime)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(command.optString("name"),fontSize=16.sp)
                        Text("${command.optString("protocol")} · ${command.optInt("carrier_khz")} kHz${if(command.optBoolean("toggle")) " · Toggle" else ""}",color=Muted,fontSize=10.sp)
                    }
                    IconButton(onClick={model.mutate("POST","/actions",obj("type" to "ir","command_id" to command.getString("id")),"IR transmitted. Appliance state unconfirmed.")},enabled=!ui.busy) {Icon(Icons.Outlined.PlayArrow,"Transmit ${command.optString("name")}")}
                    IconButton(onClick={editing=command}) {Icon(Icons.Outlined.Edit,"Rename command",Modifier.size(18.dp))}
                    IconButton(onClick={deleting=command}) {Icon(Icons.Outlined.DeleteOutline,"Delete command",Modifier.size(18.dp))}
                }
            }
            ActionButton("Learn a command",Icons.Outlined.Add,!ui.busy) {learning=true}
            OutlinedButton(onClick={editing=active?.copyJson()?.put("is_appliance",true)}) {Text("Rename appliance")}
            TextButton(onClick={deleting=active?.copyJson()?.put("is_appliance",true)}) {Text("Remove appliance",color=Muted)}
        }
    }
    if(adding) ApplianceDialog({adding=false}) {name,kind ->model.mutate("POST","/appliances",obj("name" to name,"kind" to kind),"Appliance added")}
    if(learning && active!=null) LearnDialog(model,active.getString("id"),{learning=false})
    deleting?.let {item ->
        val appliance=item.optBoolean("is_appliance")
        ConfirmDialog("Delete ${item.optString("name")}?",if(appliance) "This removes the appliance and its commands. Automations using them must be removed first." else "The saved signal will be removed. This cannot be undone.",{deleting=null}) {
            model.mutate("DELETE","/${if(appliance) "appliances" else "commands"}/${item.getString("id")}?confirm=true",success="Deleted")
        }
    }
    editing?.let {item ->
        var name by remember(item) {mutableStateOf(item.optString("name"))}
        AlertDialog(onDismissRequest={editing=null},title={Text("Rename")},text={OutlinedTextField(value=name,onValueChange={name=it.take(60)},label={Text("Name")})},
            confirmButton={TextButton(enabled=name.isNotBlank(),onClick={val appliance=item.optBoolean("is_appliance");val data=item.copyJson().apply {remove("id");remove("is_appliance");put("name",name)};model.mutate("PUT","/${if(appliance) "appliances" else "commands"}/${item.getString("id")}",data);editing=null}) {Text("Save")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancel")}})
    }
}

@Composable fun ApplianceDialog(dismiss: ()->Unit, save: (String,String)->Unit) {
    var name by remember {mutableStateOf("")}
    var kind by remember {mutableStateOf("other")}
    AlertDialog(onDismissRequest=dismiss,title={Text("Add an appliance")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value=name,onValueChange={name=it.take(60)},label={Text("Appliance name")},singleLine=true)
        Choices(listOf("fan","speaker","other"),kind) {kind=it}
    }},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={save(name,kind);dismiss()}){Text("Add appliance")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}

@Composable fun LearnDialog(model: RoomModel, applianceId: String, dismiss: ()->Unit) {
    if(model.directDevice) { DeviceLearnDialog(model,applianceId,dismiss); return }
    var name by remember {mutableStateOf("")}
    var toggle by remember {mutableStateOf(true)}
    var session by remember {mutableStateOf<JSONObject?>(null)}
    var failure by remember {mutableStateOf<String?>(null)}
    var waiting by remember {mutableStateOf(false)}
    val sessionId=session?.optString("id").orEmpty()
    LaunchedEffect(sessionId) {
        if(sessionId.isNotBlank()) {
            while(true) {
                val result=runCatching {model.request("GET","/learning/$sessionId")}
                if(result.isFailure) {failure=result.exceptionOrNull()?.message; waiting=false;break}
                session=result.getOrThrow()
                if(session!!.optString("status")!="listening") {waiting=false;break}
                delay(800)
            }
        }
    }
    AlertDialog(onDismissRequest=dismiss,title={Text("Learn an IR command")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Point the original remote at the IR receiver. Start capture, then press the button. Hold briefly if repeat frames are needed.",color=Muted,fontSize=13.sp)
        OutlinedTextField(value=name,onValueChange={name=it.take(60)},label={Text("Command name")},enabled=!waiting,singleLine=true)
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(toggle,{toggle=it});Text("Toggle / disruptive command",fontSize=12.sp)}
        session?.let { Text("Capture: ${it.optString("status")}\n${it.optString("protocol")} ${it.optString("decoded")}",color=Lime,fontSize=13.sp) }
        failure?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        if(waiting) LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={
        if(session?.optString("status")=="captured") TextButton(onClick={model.mutate("POST","/learning/$sessionId/save",obj(),"IR command saved");dismiss()}) {Text("Save signal")}
        else TextButton(enabled=name.isNotBlank()&&!waiting,onClick={
            waiting=true;failure=null;session=null
            model.work {
                try {session=model.request("POST","/learning",obj("appliance_id" to applianceId,"name" to name,"toggle" to toggle))}
                catch(e:Exception) {waiting=false;failure=e.message}
            }
        }) {Text(if(session==null) "Start capture" else "Retry capture")}
    },dismissButton={TextButton(onClick=dismiss){Text("Close")}})
}

@Composable fun RoutinesScreen(model: RoomModel, ui: RoomUi) {
    var edit by remember {mutableStateOf<JSONObject?>(null)}
    var adding by remember {mutableStateOf(false)}
    var disabling by remember {mutableStateOf<JSONObject?>(null)}
    var deleting by remember {mutableStateOf<JSONObject?>(null)}
    PageTitle("Routines", "Let the room\nremember.", "Small rituals, on your schedule or in response to your room.")
    ActionButton("Create routine",Icons.Outlined.Add,!ui.busy) {adding=true}
    Text(if(model.directDevice) "All schedules use the controller timezone set in Settings. Missed events are skipped." else "Schedules use your chosen time zone. Missed events are skipped; old IR commands are never replayed after a restart.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
    if(ui.state.items("rules").isEmpty()) EmptyCard("Room for a ritual","Set your lights for a particular time, or react to a room event.",Icons.Outlined.Schedule)
    ui.state.items("rules").forEach {rule ->
        RoomCard {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text(rule.optString("name"),fontSize=20.sp);Text(if(rule.optString("trigger")=="schedule") "${rule.optString("time")} · ${if(model.directDevice) ui.state.optJSONObject("settings")?.optString("timezone").orEmpty() else rule.optString("timezone")}" else rule.optString("trigger").replace('_',' '),color=Muted,fontSize=12.sp)}
                LiquidToggle(rule.optBoolean("enabled"),{enabled ->if(!enabled) disabling=rule else model.mutate("PUT","/rules/${rule.getString("id")}",rule.copyJson().apply {remove("id");put("enabled",true)})},label="Enable ${rule.optString("name")}")
            }
            Row {TextButton(onClick={edit=rule}){Text("Edit routine")};Spacer(Modifier.weight(1f));IconButton(onClick={deleting=rule}){Icon(Icons.Outlined.DeleteOutline,"Delete routine")}}
        }
    }
    if(adding || edit!=null) RuleDialog(ui,edit,{adding=false;edit=null}) {data ->
        val id=edit?.optString("id")
        model.mutate(if(id==null) "POST" else "PUT",if(id==null) "/rules" else "/rules/$id",data,"Routine saved")
    }
    disabling?.let {rule ->ConfirmDialog("Pause this routine?","The routine will stop running until you enable it again.",{disabling=null}) {model.mutate("PUT","/rules/${rule.getString("id")}?confirm=true",rule.copyJson().apply {remove("id");put("enabled",false)},"Routine paused")} }
    deleting?.let {rule ->ConfirmDialog("Delete routine?","This removes the saved schedule or event rule.",{deleting=null}) {model.mutate("DELETE","/rules/${rule.getString("id")}?confirm=true",success="Routine deleted")} }
}

@Composable fun RuleDialog(ui: RoomUi, existing: JSONObject?, dismiss: ()->Unit, save: (JSONObject)->Unit) {
    var name by remember {mutableStateOf(existing?.optString("name").orEmpty())}
    var trigger by remember {mutableStateOf(existing?.optString("trigger") ?: "schedule")}
    var time by remember {mutableStateOf(existing?.optString("time") ?: "19:00")}
    var zone by remember {mutableStateOf(existing?.optString("timezone") ?: ZoneId.systemDefault().id)}
    var days by remember {mutableStateOf(existing?.optJSONArray("weekdays")?.let {a ->(0 until a.length()).map {a.getInt(it)}.toSet()} ?: (0..6).toSet())}
    var actionType by remember {mutableStateOf(existing?.optJSONObject("action")?.optString("type") ?: "lighting")}
    var commandId by remember {mutableStateOf(existing?.optJSONObject("action")?.optString("command_id").orEmpty())}
    var cooldown by remember {mutableStateOf((existing?.optInt("cooldown_seconds",60) ?: 60).toFloat())}
    var chosenPreset by remember {mutableStateOf("")}
    val presets=ui.state.items("presets")
    val commands=ui.state.items("commands")
    val valid=name.isNotBlank() && (trigger!="schedule" || (time.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")) && days.isNotEmpty() && (ui.state.has("runtime") || runCatching {ZoneId.of(zone)}.isSuccess))) && (actionType!="ir" || commandId.isNotBlank())
    AlertDialog(onDismissRequest=dismiss,title={Text(if(existing==null) "Create routine" else "Edit routine")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value=name,onValueChange={name=it.take(80)},label={Text("Routine name")},singleLine=true)
        SmallLabel("TRIGGER")
        Choices(listOf("schedule","sound_started","sound_stopped","ir_received"),trigger) {trigger=it}
        if(trigger=="schedule") {
            OutlinedTextField(value=time,onValueChange={time=it},label={Text("24-hour time · HH:mm")},singleLine=true)
            if(ui.state.has("runtime")) Text("Uses controller timezone: ${ui.state.optJSONObject("settings")?.optString("timezone")}. Set it in Settings.",color=Muted)
            else OutlinedTextField(value=zone,onValueChange={zone=it},label={Text("IANA time zone")},singleLine=true)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEachIndexed {i,d ->FilterChip(selected=i in days,onClick={days=if(i in days)days-i else days+i},label={Text(d,fontSize=10.sp)})} }
        } else SettingSlider("Cooldown",cooldown,{cooldown=it},10f..600f," s")
        SmallLabel("ACTION")
        Choices(listOf("lighting","ir"),actionType) {actionType=it}
        if(actionType=="lighting") {
            Text("Uses the current lighting settings unless you choose a preset below.",color=Muted,fontSize=12.sp)
            Choices(presets.map {it.optString("name")},chosenPreset) {chosenPreset=it}
        } else {
            if(commands.isEmpty()) Text("Learn an IR command first.",color=Muted)
            commands.forEach {c ->FilterChip(selected=commandId==c.optString("id"),onClick={commandId=c.optString("id")},label={Text(c.optString("name"))})}
        }
    }},confirmButton={TextButton(enabled=valid,onClick={
        val action=if(actionType=="ir") obj("type" to "ir","command_id" to commandId) else action("lighting",presets.firstOrNull {it.optString("name")==chosenPreset}?.optJSONObject("lighting") ?: existing?.optJSONObject("action")?.optJSONObject("lighting") ?: ui.state.getJSONObject("lighting"))
        save(obj("name" to name,"enabled" to (existing?.optBoolean("enabled") ?: true),"trigger" to trigger,"time" to time,"timezone" to zone,"weekdays" to JSONArray(days.sorted()),"cooldown_seconds" to cooldown.toInt(),"action" to action));dismiss()
    }){Text("Save routine")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
