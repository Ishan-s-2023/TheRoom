package `in`.roomcontroller.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            val view = androidx.compose.ui.platform.LocalView.current
            commands.forEach {command ->
                val protocol = command.optString("protocol").ifBlank { "NEC" }.let { if (it == "PREVIEW") "NEC" else it }
                val carrier = command.optInt("carrier_khz", 38)
                val decoded = command.optString("decoded").takeIf { it.isNotBlank() && !it.startsWith("Simulated") }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={performSubtleHaptic(view);model.mutate("PUT","/commands/${command.getString("id")}",command.copyJson().apply {remove("id");put("favorite",!command.optBoolean("favorite"))})}) {
                        Icon(if(command.optBoolean("favorite")) Icons.Outlined.Star else Icons.Outlined.StarBorder,"Toggle favorite",tint=Lime)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(command.optString("name"),fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("$protocol · $carrier kHz")
                                if (decoded != null) append(" · $decoded")
                                if (command.optBoolean("toggle")) append(" · Toggle")
                            },
                            color=Muted,
                            fontSize=10.sp,
                            maxLines=1,
                            overflow=TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick={performSubtleHaptic(view, heavy = true);model.mutate("POST","/actions",obj("type" to "ir","command_id" to command.getString("id")),"Transmitted '${command.optString("name")}' ($protocol $carrier kHz)")},enabled=!ui.busy) {Icon(Icons.Outlined.PlayArrow,"Transmit ${command.optString("name")}",tint=Lime)}
                    IconButton(onClick={performSubtleHaptic(view);editing=command}) {Icon(Icons.Outlined.Edit,"Rename command",Modifier.size(18.dp))}
                    IconButton(onClick={performSubtleHaptic(view);deleting=command}) {Icon(Icons.Outlined.DeleteOutline,"Delete command",Modifier.size(18.dp))}
                }
            }
            ActionButton("Learn a command",Icons.Outlined.Add,!ui.busy) {learning=true}
            OutlinedButton(onClick={performSubtleHaptic(view);editing=active?.copyJson()?.put("is_appliance",true)}) {Text("Rename appliance")}
            TextButton(onClick={performSubtleHaptic(view);deleting=active?.copyJson()?.put("is_appliance",true)}) {Text("Remove appliance",color=Muted)}
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

private fun executeSingleAction(model: RoomModel, ui: RoomUi, routineName: String, action: JSONObject) {
    val type = action.optString("type")
    when (type) {
        "lighting" -> {
            val lighting = action.optJSONObject("lighting") ?: ui.state.optJSONObject("lighting") ?: obj()
            model.mutate("POST", "/actions", obj("type" to "lighting", "lighting" to lighting), "Ran '$routineName': Lighting updated")
            action.optString("extra_ir").takeIf { it.isNotBlank() }?.let { cmdId ->
                val cmd = ui.state.items("commands").firstOrNull { it.optString("id") == cmdId }
                val cmdName = cmd?.optString("name") ?: "command"
                model.mutate("POST", "/actions", obj("type" to "ir", "command_id" to cmdId), "Transmitted $cmdName")
            }
            action.optString("extra_spotify").takeIf { it.isNotBlank() }?.let { uri ->
                model.mutate("POST", "/spotify/playback", obj("action" to "play", "uri" to uri))
            }
        }
        "ir" -> {
            val cmdId = action.optString("command_id")
            if (cmdId.isNotBlank()) {
                val cmd = ui.state.items("commands").firstOrNull { it.optString("id") == cmdId }
                val cmdName = cmd?.optString("name") ?: "command"
                model.mutate("POST", "/actions", obj("type" to "ir", "command_id" to cmdId), "Ran '$routineName': Transmitted $cmdName")
            }
        }
        "spotify" -> {
            val spotifyAction = action.optString("action").ifBlank { action.optString("spotify_action", "play") }
            val uri = action.optString("argument").ifBlank { action.optString("uri") }
            val volume = if (action.has("value")) action.optInt("value", -1) else action.optInt("volume", -1)
            if (volume in 0..100) {
                model.spotifyCommand("volume", value = volume)
            }
            if (spotifyAction == "playlist" && uri.isNotBlank()) {
                model.spotifyCommand("playlist", argument = uri)
            } else if (uri.isNotBlank()) {
                model.mutate("POST", "/spotify/playback", obj("action" to "play", "uri" to uri), "Ran '$routineName': Playing Spotify")
            } else {
                model.spotifyCommand(spotifyAction, value = volume.coerceAtLeast(0))
            }
        }
        "sequence" -> {
            val steps = action.optJSONArray("actions")
            if (steps != null && steps.length() > 0) {
                for (i in 0 until steps.length()) {
                    steps.optJSONObject(i)?.let { sub ->
                        executeSingleAction(model, ui, routineName, sub)
                    }
                }
            } else {
                action.optJSONObject("lighting")?.let { lights ->
                    model.mutate("POST", "/actions", obj("type" to "lighting", "lighting" to lights), "Ran '$routineName': Lighting updated")
                }
                action.optString("extra_ir").takeIf { it.isNotBlank() }?.let { cmdId ->
                    model.mutate("POST", "/actions", obj("type" to "ir", "command_id" to cmdId))
                }
                action.optString("extra_spotify").takeIf { it.isNotBlank() }?.let { uri ->
                    model.spotifyCommand("playlist", argument = uri)
                }
            }
        }
        else -> {
            action.optJSONObject("lighting")?.let { lights ->
                model.mutate("POST", "/actions", obj("type" to "lighting", "lighting" to lights), "Ran '$routineName': Lighting updated")
            }
            action.optString("command_id").takeIf { it.isNotBlank() }?.let { cmdId ->
                model.mutate("POST", "/actions", obj("type" to "ir", "command_id" to cmdId))
            }
        }
    }
}

fun executeRoutine(model: RoomModel, ui: RoomUi, rule: JSONObject) {
    val action = rule.optJSONObject("action") ?: return
    val routineName = rule.optString("name", "Routine")
    executeSingleAction(model, ui, routineName, action)
}

private fun parseHexColor(hex: String, fallback: Color = Color(0xFFB5F36A)): Color {
    return try {
        val clean = hex.removePrefix("#")
        val parsed = clean.toLong(16)
        if (clean.length == 6) Color(0xFF000000 or parsed)
        else Color(parsed)
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Curated catalog of unique, minimalistic vector icons for Routine cards.
 */
val ROUTINE_ICON_CATALOG: List<Pair<String, ImageVector>> = listOf(
    "sunrise" to Icons.Outlined.WbSunny,
    "twilight" to Icons.Outlined.WbTwilight,
    "evening" to Icons.Outlined.NightsStay,
    "bedtime" to Icons.Outlined.Bedtime,
    "equalizer" to Icons.Outlined.GraphicEq,
    "music" to Icons.Outlined.MusicNote,
    "remote" to Icons.Outlined.SettingsRemote,
    "sparkle" to Icons.Outlined.AutoAwesome,
    "bolt" to Icons.Outlined.Bolt,
    "focus" to Icons.Outlined.SelfImprovement,
    "coffee" to Icons.Outlined.LocalCafe,
    "air" to Icons.Outlined.Air,
    "movie" to Icons.Outlined.Movie,
    "timer" to Icons.Outlined.Timer,
    "palette" to Icons.Outlined.Palette,
    "lightbulb" to Icons.Outlined.Lightbulb
)

fun routineDistinctIcon(rule: JSONObject, indexHint: Int = 0): ImageVector {
    val explicitKey = rule.optString("icon").lowercase().trim()
    ROUTINE_ICON_CATALOG.firstOrNull { it.first == explicitKey }?.let { return it.second }

    val name = rule.optString("name").lowercase()
    val category = rule.optString("category").lowercase()
    val trigger = rule.optString("trigger").lowercase()
    val action = rule.optJSONObject("action")
    val actionType = action?.optString("type").orEmpty().lowercase()
    val lighting = action?.optJSONObject("lighting")
    val power = lighting?.optBoolean("power", true) ?: true
    val effect = lighting?.optString("effect").orEmpty().lowercase()

    return when {
        name.contains("wake") || name.contains("morning") || name.contains("sunrise") || category == "morning" ->
            Icons.Outlined.WbSunny
        name.contains("wind") || name.contains("sunset") || name.contains("dusk") || name.contains("relax") ->
            Icons.Outlined.WbTwilight
        name.contains("bed") || name.contains("sleep") || (!power && actionType == "lighting") || category == "night" ->
            Icons.Outlined.Bedtime
        name.contains("evening") || category == "evening" ->
            Icons.Outlined.NightsStay
        name.contains("bass") || name.contains("beat") || effect in setOf("bass", "beat", "bands", "level") || trigger == "sound_started" ->
            Icons.Outlined.GraphicEq
        name.contains("quiet") || name.contains("silence") || trigger == "sound_stopped" ->
            Icons.Outlined.HearingDisabled
        name.contains("music") || name.contains("spotify") || name.contains("playlist") || actionType == "spotify" || category == "music" ->
            Icons.Outlined.MusicNote
        name.contains("movie") || name.contains("cinema") || name.contains("tv") || category == "movie" ->
            Icons.Outlined.Movie
        name.contains("fan") || name.contains("breeze") || name.contains("cool") || name.contains("ac") ->
            Icons.Outlined.Air
        name.contains("focus") || name.contains("work") || name.contains("study") || name.contains("read") ->
            Icons.Outlined.SelfImprovement
        name.contains("coffee") || name.contains("tea") || name.contains("desk") ->
            Icons.Outlined.LocalCafe
        actionType == "sequence" || name.contains("ritual") || name.contains("scene") || name.contains("glow") ->
            Icons.Outlined.AutoAwesome
        actionType == "ir" || trigger == "ir_received" ->
            Icons.Outlined.SettingsRemote
        category == "power" || trigger == "manual" ->
            Icons.Outlined.Bolt
        else -> ROUTINE_ICON_CATALOG[ kotlin.math.abs((rule.optString("id").hashCode() + indexHint)) % ROUTINE_ICON_CATALOG.size ].second
    }
}

/**
 * Guarantees every routine card in a list receives a visually distinct minimalistic icon.
 */
fun assignUniqueRoutineIcons(rules: List<JSONObject>): Map<String, ImageVector> {
    val used = mutableSetOf<ImageVector>()
    val result = mutableMapOf<String, ImageVector>()
    rules.forEachIndexed { idx, rule ->
        val key = rule.optString("id").ifBlank { "${rule.optString("name")}_$idx" }
        val preferred = routineDistinctIcon(rule, idx)
        val chosen = if (used.add(preferred)) {
            preferred
        } else {
            ROUTINE_ICON_CATALOG.map { it.second }.firstOrNull { used.add(it) } ?: preferred
        }
        result[key] = chosen
    }
    return result
}

private data class RoutineTemplate(
    val title: String,
    val icon: ImageVector,
    val category: String,
    val description: String,
    val presetData: JSONObject
)

private val defaultTemplates = listOf(
    RoutineTemplate(
        title = "Morning Wakeup",
        icon = Icons.Outlined.WbSunny,
        category = "morning",
        description = "07:00 Weekdays · 85% Warm White",
        presetData = obj(
            "name" to "Morning Wakeup",
            "category" to "morning",
            "trigger" to "schedule",
            "time" to "07:00",
            "weekdays" to JSONArray(listOf(0, 1, 2, 3, 4)),
            "action" to obj(
                "type" to "lighting",
                "lighting" to obj(
                    "power" to true,
                    "color" to "#FFF0D0",
                    "brightness" to 85,
                    "effect" to "solid",
                    "speed" to 50,
                    "intensity" to 60
                )
            )
        )
    ),
    RoutineTemplate(
        title = "Evening Wind-Down",
        icon = Icons.Outlined.WbTwilight,
        category = "evening",
        description = "19:00 Daily · 50% Amber Glow",
        presetData = obj(
            "name" to "Evening Wind-Down",
            "category" to "evening",
            "icon" to "twilight",
            "trigger" to "schedule",
            "time" to "19:00",
            "weekdays" to JSONArray((0..6).toList()),
            "action" to obj(
                "type" to "lighting",
                "lighting" to obj(
                    "power" to true,
                    "color" to "#FF9E3B",
                    "brightness" to 50,
                    "effect" to "solid",
                    "speed" to 50,
                    "intensity" to 60
                )
            )
        )
    ),
    RoutineTemplate(
        title = "Music Bass Sync",
        icon = Icons.Outlined.GraphicEq,
        category = "music",
        description = "Sound Started · Neon Lime 95%",
        presetData = obj(
            "name" to "Music Bass Sync",
            "category" to "music",
            "icon" to "equalizer",
            "trigger" to "sound_started",
            "cooldown_seconds" to 60,
            "action" to obj(
                "type" to "lighting",
                "lighting" to obj(
                    "power" to true,
                    "color" to "#B5F36A",
                    "brightness" to 95,
                    "effect" to "bass",
                    "speed" to 75,
                    "intensity" to 80
                )
            )
        )
    ),
    RoutineTemplate(
        title = "Bedtime All Off",
        icon = Icons.Outlined.Bedtime,
        category = "night",
        description = "23:30 Daily · Power off lights",
        presetData = obj(
            "name" to "Bedtime All Off",
            "category" to "night",
            "icon" to "bedtime",
            "trigger" to "schedule",
            "time" to "23:30",
            "weekdays" to JSONArray((0..6).toList()),
            "action" to obj(
                "type" to "lighting",
                "lighting" to obj(
                    "power" to false,
                    "color" to "#000000",
                    "brightness" to 0,
                    "effect" to "solid"
                )
            )
        )
    )
)

private fun formatTriggerSummary(
    rule: JSONObject,
    defaultZone: String,
    commands: List<JSONObject> = emptyList(),
    use24Hour: Boolean = true
): String {
    val trigger = rule.optString("trigger", "schedule")
    return when (trigger) {
        "schedule" -> {
            val time = rule.optString("time", "19:00")
            val formattedTime = formatClockTime(time, use24Hour)
            val weekdays = rule.optJSONArray("weekdays")
            val daysSet = if (weekdays != null) (0 until weekdays.length()).map { weekdays.getInt(it) }.toSet() else (0..6).toSet()
            val dayLabel = when {
                daysSet.size == 7 -> "Daily"
                daysSet == setOf(0, 1, 2, 3, 4) -> "Weekdays"
                daysSet == setOf(5, 6) -> "Weekends"
                else -> {
                    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    daysSet.sorted().mapNotNull { names.getOrNull(it) }.joinToString(",")
                }
            }
            "⏰ $dayLabel at $formattedTime"
        }
        "sound_started" -> "🔊 When sound/music starts · ${rule.optInt("cooldown_seconds", 60)}s cooldown"
        "sound_stopped" -> "🔇 When room goes quiet · ${rule.optInt("cooldown_seconds", 60)}s cooldown"
        "ir_received" -> {
            val irCmdId = rule.optString("trigger_ir_id")
            val matchedCmd = commands.firstOrNull { it.optString("id") == irCmdId }
            val sigName = matchedCmd?.let { "${it.optString("name")} (${it.optString("protocol", "NEC")} ${it.optInt("carrier_khz", 38)}kHz)" } ?: "Any IR signal (38kHz)"
            "📡 IR: $sigName · ${rule.optInt("cooldown_seconds", 60)}s cooldown"
        }
        "manual" -> "👆 Manual one-tap ritual"
        else -> trigger.replace('_', ' ')
    }
}

private fun formatActionSummary(rule: JSONObject, commands: List<JSONObject>): Pair<String, String?> {
    val action = rule.optJSONObject("action") ?: return Pair("No action set", null)
    val type = action.optString("type")
    return when (type) {
        "lighting" -> {
            val lights = action.optJSONObject("lighting")
            val power = lights?.optBoolean("power", true) ?: true
            if (!power) {
                Pair("💡 Turn lights OFF", null)
            } else {
                val colorHex = lights?.optString("color", "#FF8800") ?: "#FF8800"
                val brightness = lights?.optInt("brightness", 70) ?: 70
                val effect = lights?.optString("effect", "solid") ?: "solid"
                val extraIr = action.optString("extra_ir")
                val irSuffix = if (extraIr.isNotBlank()) {
                    val cmd = commands.firstOrNull { it.optString("id") == extraIr }
                    " + IR: ${cmd?.optString("name") ?: "command"}"
                } else ""
                Pair("💡 Turn ON · $brightness% · $effect$irSuffix", colorHex)
            }
        }
        "ir" -> {
            val cmdId = action.optString("command_id")
            val cmd = commands.firstOrNull { it.optString("id") == cmdId }
            val name = cmd?.optString("name") ?: "command"
            Pair("📡 IR: $name", null)
        }
        "spotify" -> {
            val sub = action.optString("action").ifBlank { action.optString("spotify_action", "play") }
            val uri = action.optString("argument").ifBlank { action.optString("uri") }
            Pair(if (uri.isNotBlank()) "🎵 Play playlist" else "🎵 Spotify: $sub", null)
        }
        "sequence" -> {
            val steps = action.optJSONArray("actions")
            val lights = action.optJSONObject("lighting")
                ?: (0 until (steps?.length() ?: 0)).mapNotNull { steps?.optJSONObject(it)?.optJSONObject("lighting") }.firstOrNull()
            val colorHex = lights?.optString("color", "#B5F36A")
            val count = steps?.length() ?: 2
            Pair("✨ Multi-step sequence ($count actions)", colorHex)
        }
        else -> Pair("Action: $type", null)
    }
}

@Composable fun RoutinesScreen(model: RoomModel, ui: RoomUi) {
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var adding by remember { mutableStateOf(false) }
    var templateToOpen by remember { mutableStateOf<JSONObject?>(null) }
    var disabling by remember { mutableStateOf<JSONObject?>(null) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var pauseConfirm by remember { mutableStateOf(false) }

    val rules = ui.state.items("rules")
    val commands = ui.state.items("commands")
    val runtime = ui.state.optJSONObject("runtime")
    val settings = ui.state.optJSONObject("settings") ?: obj()
    val system = ui.state.optJSONObject("system") ?: obj()
    val masterEnabled = when {
        ui.state.has("runtime") -> runtime?.optBoolean("armed", true) == true
        ui.state.has("settings") -> settings.optBoolean("automation_enabled", true)
        else -> system.optBoolean("automation_enabled", true)
    }
    val activeCount = rules.count { it.optBoolean("enabled", true) }
    val isArmed = masterEnabled && (rules.isEmpty() || activeCount > 0)
    val timezone = if (model.directDevice) settings.optString("timezone", "UTC")
                   else system.optString("timezone", "UTC")

    PageTitle("Routines", "Let the room\nremember.", "Small rituals and schedules that make your space feel alive.")

    // Automation Master Status Card
    RoomCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isArmed) Lime.copy(alpha = 0.2f) else Line),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isArmed) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = null,
                        tint = if (isArmed) Lime else Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        if (isArmed) "Automations active" else "Automations paused",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${if (masterEnabled) activeCount else 0} of ${rules.size} routines active · $timezone",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
            LiquidToggle(
                checked = isArmed,
                enabled = !ui.busy,
                onCheckedChange = { nextArmed ->
                    model.setAllRoutinesEnabled(nextArmed)
                },
                label = "Master automation power"
            )
        }
    }

    // Informative status callout when automations are paused
    if (!isArmed) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Line.copy(alpha = 0.35f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Outlined.PauseCircleOutline,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "All automations and triggers are paused. Toggle the master switch above to resume schedules, reactions, and rituals.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }

    // Quick Action Bar
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            text = "Create routine",
            icon = Icons.Outlined.Add,
            enabled = isArmed && !ui.busy
        ) {
            adding = true
        }
    }

    // Quick-Start Templates Carousel
    Column(
        modifier = Modifier.alpha(if (isArmed) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel("Quick-start templates", if (isArmed) "1-TAP SETUP" else "PAUSED")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            defaultTemplates.forEach { t ->
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isArmed) Panel else Panel.copy(alpha = 0.5f))
                        .border(1.dp, if (isArmed) Line else Line.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .clickable(enabled = isArmed && !ui.busy) {
                            templateToOpen = t.presetData.copyJson()
                        }
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Line),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    t.icon,
                                    contentDescription = null,
                                    tint = if (isArmed) Lime else Muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                Icons.Outlined.AddCircleOutline,
                                contentDescription = "Add template",
                                tint = if (isArmed) Muted else Muted.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            t.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = if (isArmed) Cream else Muted
                        )
                        Text(
                            t.description,
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }

    // Saved Routines List
    SectionLabel("Your routines", if (!isArmed) "PAUSED · ${rules.size} CONFIGURED" else "${rules.size} CONFIGURED")

    if (rules.isEmpty()) {
        EmptyCard(
            "Room for a ritual",
            "Set your lights for a particular time, react to sound, or trigger an IR device. Choose a quick-start template above or tap Create routine.",
            Icons.Outlined.Schedule
        )
    } else {
        val uniqueIcons = remember(rules) { assignUniqueRoutineIcons(rules) }
        val view = androidx.compose.ui.platform.LocalView.current
        rules.forEachIndexed { idx, rule ->
            val enabled = rule.optBoolean("enabled", true)
            val ruleKey = rule.optString("id").ifBlank { "${rule.optString("name")}_$idx" }
            val icon = uniqueIcons[ruleKey] ?: routineDistinctIcon(rule, idx)
            val (actionLabel, dotColorHex) = formatActionSummary(rule, commands)
            val triggerLabel = formatTriggerSummary(rule, timezone, commands, ui.use24HourFormat)
            val isRuleActive = masterEnabled && enabled

            RoomCard(modifier = Modifier.alpha(if (isRuleActive) 1f else 0.72f)) {
                // Header row: Icon, Title + single-line status badge, & LiquidToggle (strictly constant height)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isRuleActive) Panel else Line.copy(alpha = 0.5f))
                            .border(1.dp, if (isRuleActive) Lime.copy(alpha = 0.4f) else Line, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isRuleActive) Lime else Muted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rule.optString("name", "Routine"),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isRuleActive) Cream else Muted,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            val badgeText = when {
                                !masterEnabled -> "PAUSED"
                                !enabled -> "OFF"
                                else -> "ON"
                            }
                            val badgeBg = if (isRuleActive) Lime.copy(alpha = 0.16f) else Line
                            val badgeFg = if (isRuleActive) Lime else Muted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 9.sp,
                                    color = badgeFg,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = triggerLabel,
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    LiquidToggle(
                        checked = isRuleActive,
                        enabled = !ui.busy,
                        onCheckedChange = { isChecked ->
                            if (!isChecked) {
                                model.mutate(
                                    "PUT",
                                    "/rules/${rule.getString("id")}?confirm=true",
                                    rule.copyJson().apply { remove("id"); put("enabled", false) },
                                    "Routine paused"
                                )
                            } else {
                                if (!masterEnabled) {
                                    if (model.directDevice) {
                                        model.mutate("POST", "/v1/command", obj("op" to "automation", "enabled" to true), "Automation enabled")
                                    } else {
                                        model.mutate("PUT", "/settings", system.copyJson().apply { put("automation_enabled", true) }, "Automation enabled")
                                    }
                                }
                                model.mutate(
                                    "PUT",
                                    "/rules/${rule.getString("id")}",
                                    rule.copyJson().apply { remove("id"); put("enabled", true) },
                                    "Routine enabled"
                                )
                            }
                        },
                        label = "Enable ${rule.optString("name")}"
                    )
                }

                // Action Summary Row (dimmed when routine is OFF, strictly single-line so card height never shifts)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isRuleActive) 1f else 0.45f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Line.copy(alpha = if (isRuleActive) 0.4f else 0.2f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dotColorHex != null) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isRuleActive) parseHexColor(dotColorHex) else parseHexColor(dotColorHex).copy(alpha = 0.4f))
                        )
                    }
                    Text(
                        text = actionLabel,
                        fontSize = 12.sp,
                        color = if (isRuleActive) Cream.copy(alpha = 0.9f) else Muted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Footer row: Run Now button, Edit, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prominent Run Now button - disabled when automations paused or routine disabled
                    FilledTonalButton(
                        onClick = {
                            performSubtleHaptic(view, heavy = true)
                            executeRoutine(model, ui, rule)
                        },
                        enabled = isRuleActive && !ui.busy,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isRuleActive) Lime.copy(alpha = 0.15f) else Line.copy(alpha = 0.3f),
                            contentColor = if (isRuleActive) Lime else Muted,
                            disabledContainerColor = Line.copy(alpha = 0.2f),
                            disabledContentColor = Muted.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Run now", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ruleId = rule.optString("id")
                        val isFavRoutine = ruleId in ui.favoriteRoutines
                        IconButton(
                            onClick = {
                                performSubtleHaptic(view)
                                model.toggleFavoriteRoutine(ruleId)
                            },
                            enabled = !ui.busy
                        ) {
                            Icon(
                                if (isFavRoutine) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite ${rule.optString("name")}",
                                tint = if (isFavRoutine) Lime else Muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        TextButton(
                            onClick = {
                                performSubtleHaptic(view)
                                edit = rule
                            },
                            enabled = isArmed && !ui.busy
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = {
                                performSubtleHaptic(view)
                                deleting = rule
                            },
                            enabled = isArmed && !ui.busy
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, "Delete routine", tint = if (isArmed) Muted else Muted.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // Create / Edit Dialog
    val activeDialogRule = edit ?: templateToOpen ?: if (adding) JSONObject() else null
    if (activeDialogRule != null) {
        RuleDialog(
            ui = ui,
            existing = if (edit != null) edit else if (templateToOpen != null) templateToOpen else null,
            dismiss = {
                adding = false
                edit = null
                templateToOpen = null
            },
            save = { data ->
                val id = edit?.optString("id")
                model.mutate(
                    if (id == null) "POST" else "PUT",
                    if (id == null) "/rules" else "/rules/$id",
                    data,
                    "Routine saved"
                )
                adding = false
                edit = null
                templateToOpen = null
            },
            onTest = { data ->
                executeRoutine(model, ui, data)
            },
            onToggle24Hour = { enabled ->
                model.setUse24HourFormat(enabled)
            }
        )
    }

    // Confirmation dialogs
    if (pauseConfirm) {
        ConfirmDialog(
            "Pause all automations?",
            "Schedules and event rules will stop running until you re-enable them.",
            { pauseConfirm = false }
        ) {
            if (model.directDevice) {
                model.mutate("POST", "/v1/command", obj("op" to "automation", "enabled" to false, "confirm" to true), "Automation paused")
            } else {
                model.mutate("PUT", "/settings?confirm=true", settings.copyJson().apply { put("automation_enabled", false) }, "Automation paused")
            }
        }
    }

    disabling?.let { rule ->
        ConfirmDialog(
            "Pause ${rule.optString("name")}?",
            "This routine will stop running automatically until you turn it back on.",
            { disabling = null }
        ) {
            model.mutate(
                "PUT",
                "/rules/${rule.getString("id")}?confirm=true",
                rule.copyJson().apply { remove("id"); put("enabled", false) },
                "Routine paused"
            )
        }
    }

    deleting?.let { rule ->
        ConfirmDialog(
            "Delete ${rule.optString("name")}?",
            "This schedule or event trigger will be removed completely.",
            { deleting = null }
        ) {
            model.mutate("DELETE", "/rules/${rule.getString("id")}?confirm=true", success = "Routine deleted")
        }
    }
}

@Composable fun RuleDialog(
    ui: RoomUi,
    existing: JSONObject?,
    dismiss: () -> Unit,
    save: (JSONObject) -> Unit,
    onTest: (JSONObject) -> Unit,
    onToggle24Hour: ((Boolean) -> Unit)? = null
) {
    var name by remember { mutableStateOf(existing?.optString("name").orEmpty()) }
    var category by remember { mutableStateOf(existing?.optString("category") ?: "custom") }
    var iconKey by remember { mutableStateOf(existing?.optString("icon").orEmpty()) }
    var trigger by remember { mutableStateOf(existing?.optString("trigger") ?: "schedule") }
    var time by remember { mutableStateOf(existing?.optString("time") ?: "19:00") }
    var zone by remember {
        mutableStateOf(
            existing?.optString("timezone")?.takeIf { it.isNotBlank() }
                ?: ui.state.optJSONObject("settings")?.optString("timezone")?.takeIf { it.isNotBlank() }
                ?: ZoneId.systemDefault().id
        )
    }
    var days by remember {
        mutableStateOf(
            existing?.optJSONArray("weekdays")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: (0..6).toSet()
        )
    }
    var cooldown by remember { mutableStateOf((existing?.optInt("cooldown_seconds", 60) ?: 60).toFloat()) }
    var triggerIrId by remember { mutableStateOf(existing?.optString("trigger_ir_id").orEmpty()) }

    // Action state
    val existingAction = existing?.optJSONObject("action")
    val rawActionType = existingAction?.optString("type") ?: "lighting"
    val initialActionType = if (rawActionType == "sequence") "compound" else rawActionType
    var actionType by remember { mutableStateOf(initialActionType) }

    val existingSeq = existingAction?.optJSONArray("actions")
    val seqLighting = remember(existingAction) {
        existingAction?.optJSONObject("lighting")
            ?: (0 until (existingSeq?.length() ?: 0)).mapNotNull { existingSeq?.optJSONObject(it)?.optJSONObject("lighting") }.firstOrNull()
    }
    val seqIrCmdId = remember(existingAction) {
        existingAction?.optString("command_id")?.takeIf { it.isNotBlank() }
            ?: existingAction?.optString("extra_ir")?.takeIf { it.isNotBlank() }
            ?: (0 until (existingSeq?.length() ?: 0)).mapNotNull { existingSeq?.optJSONObject(it)?.optString("command_id")?.takeIf { s -> s.isNotBlank() } }.firstOrNull()
            .orEmpty()
    }
    val seqSpotifyUri = remember(existingAction) {
        existingAction?.optString("argument")?.takeIf { it.isNotBlank() }
            ?: existingAction?.optString("uri")?.takeIf { it.isNotBlank() }
            ?: existingAction?.optString("extra_spotify")?.takeIf { it.isNotBlank() }
            ?: (0 until (existingSeq?.length() ?: 0)).mapNotNull { existingSeq?.optJSONObject(it)?.optString("argument")?.takeIf { s -> s.isNotBlank() } }.firstOrNull()
            .orEmpty()
    }

    // Lighting action properties
    var power by remember { mutableStateOf(seqLighting?.optBoolean("power", true) ?: true) }
    var colorHex by remember { mutableStateOf(seqLighting?.optString("color") ?: "#FFF0D0") }
    var brightness by remember { mutableStateOf((seqLighting?.optInt("brightness", 80) ?: 80).toFloat()) }
    var effect by remember { mutableStateOf(seqLighting?.optString("effect", "solid") ?: "solid") }
    var speed by remember { mutableStateOf((seqLighting?.optInt("speed", 50) ?: 50).toFloat()) }
    var intensity by remember { mutableStateOf((seqLighting?.optInt("intensity", 60) ?: 60).toFloat()) }
    var chosenPreset by remember { mutableStateOf("") }

    // IR action properties
    var commandId by remember { mutableStateOf(seqIrCmdId) }

    // Spotify action properties
    var spotifyAction by remember {
        mutableStateOf(
            existingAction?.optString("action")?.takeIf { it.isNotBlank() }
                ?: existingAction?.optString("spotify_action")
                ?: "play"
        )
    }
    var spotifyUri by remember { mutableStateOf(seqSpotifyUri) }
    var spotifyVolume by remember {
        mutableStateOf(
            (if (existingAction?.has("value") == true) existingAction.optInt("value", 50)
            else existingAction?.optInt("volume", 50) ?: 50).toFloat()
        )
    }

    val presets = ui.state.items("presets")
    val appliances = ui.state.items("appliances")
    val commands = ui.state.items("commands")

    val colorSwatches = listOf(
        Pair("Warm White", "#FFF0D0"),
        Pair("Daylight", "#FFFFFF"),
        Pair("Sunset Amber", "#FF9E3B"),
        Pair("Neon Lime", "#B5F36A"),
        Pair("Ocean Cyan", "#00E5FF"),
        Pair("Electric Violet", "#AE9CFF"),
        Pair("Rose Pink", "#FF6584"),
        Pair("Crimson", "#EF4444")
    )

    fun currentRuleData(): JSONObject {
        val lightingPayload = obj(
            "power" to power,
            "color" to colorHex,
            "brightness" to (if (power) brightness.toInt() else 0),
            "effect" to effect,
            "speed" to speed.toInt(),
            "intensity" to intensity.toInt()
        )
        val actionObj = when (actionType) {
            "lighting" -> obj(
                "type" to "lighting",
                "lighting" to lightingPayload
            )
            "ir" -> obj(
                "type" to "ir",
                "command_id" to commandId
            )
            "spotify" -> obj(
                "type" to "spotify",
                "action" to spotifyAction,
                "argument" to spotifyUri,
                "value" to spotifyVolume.toInt(),
                "spotify_action" to spotifyAction,
                "uri" to spotifyUri,
                "volume" to spotifyVolume.toInt()
            )
            "compound" -> {
                val seqArr = JSONArray().apply {
                    put(obj("type" to "lighting", "lighting" to lightingPayload))
                    if (commandId.isNotBlank()) {
                        put(obj("type" to "ir", "command_id" to commandId))
                    }
                    if (spotifyUri.isNotBlank()) {
                        put(
                            obj(
                                "type" to "spotify",
                                "action" to "playlist",
                                "argument" to spotifyUri,
                                "value" to spotifyVolume.toInt()
                            )
                        )
                    }
                }
                obj(
                    "type" to "sequence",
                    "actions" to seqArr,
                    "lighting" to lightingPayload,
                    "extra_ir" to commandId,
                    "extra_spotify" to spotifyUri
                )
            }
            else -> obj("type" to "lighting", "lighting" to lightingPayload)
        }

        return obj(
            "name" to name.ifBlank {
                when (trigger) {
                    "schedule" -> "Routine at $time"
                    "sound_started" -> "Music Reaction"
                    "sound_stopped" -> "Quiet Room"
                    "ir_received" -> "Remote Action"
                    else -> "Custom Routine"
                }
            },
            "category" to category,
            "icon" to iconKey,
            "enabled" to (existing?.optBoolean("enabled") ?: true),
            "trigger" to trigger,
            "time" to time,
            "timezone" to zone,
            "weekdays" to JSONArray(days.sorted()),
            "cooldown_seconds" to cooldown.toInt(),
            "trigger_ir_id" to triggerIrId,
            "action" to actionObj
        )
    }

    val isTimeValid = trigger != "schedule" || time.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d"))
    val isDaysValid = trigger != "schedule" || days.isNotEmpty()
    val isIrValid = (actionType != "ir" && actionType != "compound") || commandId.isNotBlank() || commands.isEmpty()
    val isValid = isTimeValid && isDaysValid && isIrValid

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Panel)
                    .border(1.dp, Line, RoundedCornerShape(26.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (existing == null) "Create routine" else "Edit routine",
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Automate rituals across lighting, remotes & music", color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = dismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close dialog")
                    }
                }

                // Section 1: Routine Identity
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Routine name") },
                    placeholder = { Text("e.g. Evening Wind-Down") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Name Suggestions
                val view = androidx.compose.ui.platform.LocalView.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Morning Glow", "Evening Relax", "Music Sync", "Bedtime", "Movie Time", "Focus Work").forEach { suggestion ->
                        SuggestionChip(
                            onClick = {
                                performSubtleHaptic(view)
                                name = suggestion
                            },
                            label = { Text(suggestion, fontSize = 11.sp) }
                        )
                    }
                }

                // Minimalistic Routine Icon Selector
                SmallLabel("ROUTINE ICON")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ROUTINE_ICON_CATALOG.forEach { (key, vec) ->
                        val selectedIcon = iconKey == key
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (selectedIcon) Lime.copy(alpha = 0.2f) else Ink)
                                .border(
                                    width = if (selectedIcon) 1.5.dp else 1.dp,
                                    color = if (selectedIcon) Lime else Line,
                                    shape = RoundedCornerShape(11.dp)
                                )
                                .livelyClick { iconKey = if (selectedIcon) "" else key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vec,
                                contentDescription = key,
                                tint = if (selectedIcon) Lime else Cream,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Section 2: Trigger Selection ("WHEN THIS HAPPENS")
                SmallLabel("WHEN THIS HAPPENS")
                Choices(
                    listOf("schedule", "sound_started", "sound_stopped", "ir_received", "manual"),
                    trigger
                ) {
                    trigger = it
                    if (it == "morning" || it == "evening") category = it
                }

                if (trigger == "schedule") {
                    RotaryTimePicker(
                        timeString = time,
                        onTimeChange = { time = it },
                        use24Hour = ui.use24HourFormat,
                        onToggle24Hour = onToggle24Hour
                    )

                    // Equal-width Day Group Presets (Every day | Weekdays | Weekends)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Every day" to (0..6).toSet(),
                            "Weekdays" to setOf(0, 1, 2, 3, 4),
                            "Weekends" to setOf(5, 6)
                        ).forEach { (label, presetSet) ->
                            val isSelected = days == presetSet
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Lime.copy(alpha = 0.18f) else Panel)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) Lime else Line,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .livelyClick { days = presetSet },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) Lime else Cream,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Single-row 7-day selector (Mon..Sun aligned evenly)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, d ->
                            val isSelected = i in days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Lime.copy(alpha = 0.18f) else Panel)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) Lime else Line,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .livelyClick { days = if (i in days) days - i else days + i },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = d,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Lime else Muted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (ui.state.has("runtime")) {
                        Text("Uses controller timezone: ${ui.state.optJSONObject("settings")?.optString("timezone") ?: "UTC"}", color = Muted, fontSize = 11.sp)
                    } else {
                        OutlinedTextField(
                            value = zone,
                            onValueChange = { zone = it },
                            label = { Text("Timezone (e.g. Asia/Kolkata)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (trigger == "ir_received") {
                    IrTriggerDefinitionSection(
                        commands = commands,
                        appliances = appliances,
                        selectedIrId = triggerIrId,
                        onSelectIrId = { triggerIrId = it },
                        cooldown = cooldown,
                        onCooldownChange = { cooldown = it }
                    )
                } else if (trigger == "sound_started" || trigger == "sound_stopped") {
                    SettingSlider("Cooldown", cooldown, { cooldown = it }, 10f..300f, "s")
                    Text(
                        "Waits at least ${cooldown.toInt()} seconds before triggering again to avoid rapid loops.",
                        color = Muted,
                        fontSize = 11.sp
                    )
                } else if (trigger == "manual") {
                    Text("This routine triggers only when you tap 'Run now' or press its quick button.", color = Muted, fontSize = 12.sp)
                }

                // Section 3: Action Selection ("THEN DO THIS")
                SmallLabel("THEN DO THIS")
                Choices(listOf("lighting", "ir", "spotify", "compound"), actionType) {
                    actionType = it
                }

                if (actionType == "lighting" || actionType == "compound") {
                    // Power toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lighting power", fontSize = 14.sp)
                        LiquidToggle(power, { power = it }, label = "Power")
                    }

                    if (power) {
                        // Presets
                        if (presets.isNotEmpty()) {
                            Text("Pick from saved preset:", color = Muted, fontSize = 12.sp)
                            Choices(listOf("Custom") + presets.map { it.optString("name") }, chosenPreset.ifBlank { "Custom" }) { selected ->
                                chosenPreset = selected
                                if (selected != "Custom") {
                                    presets.firstOrNull { it.optString("name") == selected }?.optJSONObject("lighting")?.let { pLights ->
                                        colorHex = pLights.optString("color", colorHex)
                                        brightness = pLights.optInt("brightness", brightness.toInt()).toFloat()
                                        effect = pLights.optString("effect", effect)
                                    }
                                }
                            }
                        }

                        // Quick Color Swatches
                        Text("Color palette:", color = Muted, fontSize = 12.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            colorSwatches.forEach { (cName, hex) ->
                                val isSelected = colorHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(hex))
                                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Line, CircleShape)
                                        .clickable {
                                            colorHex = hex
                                            chosenPreset = "Custom"
                                        }
                                )
                            }
                        }

                        // Brightness Slider
                        SettingSlider("Brightness", brightness, { brightness = it }, 1f..100f, "%")

                        // Effect selector
                        Text("Lighting effect:", color = Muted, fontSize = 12.sp)
                        Choices(listOf("solid", "bass", "beat", "level", "bands"), effect) {
                            effect = it
                        }

                        if (effect != "solid") {
                            SettingSlider("Response speed", speed, { speed = it }, 1f..100f, "%")
                            SettingSlider("Effect intensity", intensity, { intensity = it }, 1f..100f, "%")
                        }
                    }
                }

                if (actionType == "ir" || actionType == "compound") {
                    if (actionType == "compound") {
                        SmallLabel("ALSO TRANSMIT IR COMMAND")
                    }
                    val allIrOptions = remember(commands, appliances) {
                        buildCompleteIrOptionList(commands, appliances, includeAnyOption = false)
                    }
                    Text("Select command to transmit:", color = Muted, fontSize = 12.sp)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allIrOptions.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowItems.forEach { (id, label) ->
                                    val isSelected = commandId == id
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Lime.copy(alpha = 0.18f) else Panel)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) Lime else Line,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .livelyClick { commandId = id }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isSelected) Lime else Cream,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (actionType == "spotify") {
                    Text("Spotify action:", color = Muted, fontSize = 12.sp)
                    Choices(listOf("play", "pause", "playlist"), spotifyAction) {
                        spotifyAction = it
                    }
                    if (spotifyAction == "playlist") {
                        Text("Choose playlist:", color = Muted, fontSize = 12.sp)
                        listOf("spotify:playlist:preview-desk" to "Desk Sessions", "spotify:playlist:preview-evening" to "Slow Evenings").forEach { (uri, label) ->
                            FilterChip(
                                selected = spotifyUri == uri,
                                onClick = { spotifyUri = uri },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    SettingSlider("Spotify volume", spotifyVolume, { spotifyVolume = it }, 0f..100f, "%")
                }

                // Live Test Button inside Dialog
                FilledTonalButton(
                    onClick = {
                        performSubtleHaptic(view, heavy = true)
                        onTest(currentRuleData())
                    },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test this action right now", fontWeight = FontWeight.SemiBold)
                }

                // Save and Cancel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        performSubtleHaptic(view)
                        dismiss()
                    }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            performSubtleHaptic(view, heavy = true)
                            save(currentRuleData())
                        },
                        enabled = isValid,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save routine")
                    }
                }
            }
        }
    }
}

private fun buildCompleteIrOptionList(
    commands: List<JSONObject>,
    appliances: List<JSONObject>,
    includeAnyOption: Boolean
): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    if (includeAnyOption) {
        result.add("" to "Any IR signal")
    }
    val seenIds = mutableSetOf<String>()
    commands.forEach { cmd ->
        val id = cmd.optString("id")
        if (id.isNotBlank() && seenIds.add(id)) {
            val appName = appliances.firstOrNull { it.optString("id") == cmd.optString("appliance_id") }?.optString("name").orEmpty()
            val cmdName = cmd.optString("name").ifBlank { id }
            val label = if (appName.isNotBlank()) "$appName · $cmdName" else cmdName
            result.add(id to label)
        }
    }
    return result
}

@Composable
private fun IrTriggerDefinitionSection(
    commands: List<JSONObject>,
    appliances: List<JSONObject>,
    selectedIrId: String,
    onSelectIrId: (String) -> Unit,
    cooldown: Float,
    onCooldownChange: (Float) -> Unit
) {
    val irOptions = remember(commands, appliances) {
        buildCompleteIrOptionList(commands, appliances, includeAnyOption = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Select IR command:",
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        // Static 2-column grid of recorded IR commands — zero horizontal or vertical scrolling required
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            irOptions.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { (id, label) ->
                        val isSelected = selectedIrId == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Lime.copy(alpha = 0.18f) else Panel)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Lime else Line,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .livelyClick { onSelectIrId(id) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Lime else Cream,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        SettingSlider("Cooldown", cooldown, onCooldownChange, 10f..300f, "s")
    }
}

