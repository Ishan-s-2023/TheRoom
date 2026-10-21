package `in`.roomcontroller.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.ZoneId

@Composable fun MusicScreen(model: RoomModel, ui: RoomUi) {
    val context=LocalContext.current
    var player by remember {mutableStateOf(JSONObject())}
    var devices by remember {mutableStateOf(emptyList<JSONObject>())}
    var results by remember {mutableStateOf(emptyList<JSONObject>())}
    var selected by rememberSaveable {mutableStateOf("")}
    var query by rememberSaveable {mutableStateOf("")}
    var volume by remember {mutableStateOf(50f)}
    var irVolume by remember {mutableStateOf(false)}
    var resultKind by remember {mutableStateOf("search")}
    var resultOffset by remember {mutableIntStateOf(0)}
    var before by remember {mutableStateOf("")}
    var moreResults by remember {mutableStateOf(false)}
    val track=player.optJSONObject("item")
    val device=player.optJSONObject("device")
    val connected=ui.state.optBoolean("spotify_connected")
    fun load() {model.work {
        player=model.request("GET","/spotify/player")
        devices=model.request("GET","/spotify/devices").items("devices")
        volume=(player.optJSONObject("device")?.optInt("volume_percent",50) ?: 50).toFloat()
    }}
    fun browse(kind: String, append: Boolean=false) {model.work {
        val offset=if(append) resultOffset else 0
        val cursor=if(append) before else ""
        val data=model.request("GET","/spotify/$kind?q="+URLEncoder.encode(query,"UTF-8")+"&offset=$offset&before=$cursor")
        val batch=when(kind) {
            "search" -> (data.optJSONObject("tracks")?.items("items") ?: emptyList()) + (data.optJSONObject("playlists")?.items("items") ?: emptyList())
            "recent" -> data.items("items").mapNotNull {it.optJSONObject("track")}
            else -> data.items("items")
        }
        results=if(append) results+batch else batch
        resultKind=kind
        resultOffset=offset+(if(kind=="search")10 else 20)
        before=data.optJSONObject("cursors")?.optString("before").orEmpty()
        moreResults=if(kind=="search") (data.optJSONObject("tracks")?.opt("next") is String || data.optJSONObject("playlists")?.opt("next") is String) else data.opt("next") is String
    }}
    LaunchedEffect(connected) {if(connected) load()}
    PageTitle("Music", "The right soundtrack.", "Choose what plays, where it plays, and how it feels.")
    ConnectionHelpButton(if(ui.demo) "spotify-direct" else "spotify",model.endpoint)
    if(ui.demo) RoomCard {
        Text("Connect your Spotify",fontSize=23.sp,fontFamily=FontFamily.Serif)
        Text("You're exploring sample music. Your Spotify account isn't connected. First connect Room to your ESP32, then choose Connect Spotify in Music to sign in.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
        ActionButton("Connect Spotify",Icons.Outlined.OpenInNew,!ui.busy) {model.exitPreview()}
        Text("Opens setup for your preconfigured ESP32.",color=Muted,fontSize=11.sp)
    }
    if(!connected && !ui.demo) RoomCard {
        Icon(Icons.Outlined.MusicNote,null,tint=Lime,modifier=Modifier.size(38.dp))
        Text("Connect Spotify",fontSize=23.sp,fontFamily=FontFamily.Serif)
        Text("Link your own account using Spotify authorization. Playback needs internet, eligible API access, and a supported active device.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
        ActionButton("Connect Spotify",Icons.Outlined.OpenInNew,!ui.busy) {model.work {
            val url=model.request("GET","/spotify/connect").getString("url")
            require(Uri.parse(url).host=="accounts.spotify.com") {"Invalid authorization URL"}
            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
        }}
        TextButton(onClick={model.work {model.refresh()}}) {Text("I've connected Â· refresh")}
    } else if(connected) {
        RoomCard {
            MusicPlayerWidget(player,ui.demo,ui.busy) {name,data->model.work {
                model.request("POST","/spotify/playback",data.copyJson().put("action",name).put("device_id",selected))
                player=model.request("GET","/spotify/player")
            }}
            Text(device?.optString("name") ?: "No active playback device",color=Lime,fontSize=12.sp)
            if(ui.state.optJSONObject("system")?.optString("volume_target")!="speaker_ir") VolumeControl(volume,{volume=it},enabled=!ui.busy)
            OutlinedButton(onClick={if(ui.state.optJSONObject("system")?.optString("volume_target")=="speaker_ir") irVolume=true else model.mutate("POST","/spotify/playback",obj("action" to "volume","volume" to volume.toInt(),"device_id" to selected),"Spotify volume updated")},enabled=!ui.busy) {Text(if(ui.state.optJSONObject("system")?.optString("volume_target")=="speaker_ir") "Choose speaker IR command" else "Set playback volume")}
            TextButton(onClick={load()},enabled=!ui.busy){Text("Refresh playback")}
        }
        RoomCard {
            SectionLabel("Play it here")
            devices.forEach {d ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=(selected.ifBlank {device?.optString("id").orEmpty()})==d.optString("id"),onClick={selected=d.optString("id")})
                    Text(d.optString("name"),modifier=Modifier.weight(1f),fontSize=13.sp)
                    if(d.optBoolean("is_restricted")) Text("Restricted",color=Muted,fontSize=10.sp)
                }
            }
            ActionButton("Select device",Icons.Outlined.Speaker,selected.isNotBlank()&&!ui.busy) {model.mutate("POST","/spotify/playback",obj("action" to "transfer","device_id" to selected),"Playback device selected")}
        }
        RoomCard {
            SectionLabel("Find your next listen")
            OutlinedTextField(value=query,onValueChange={query=it},label={Text("Tracks & playlists")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            ActionButton("Search",Icons.Outlined.Search,query.isNotBlank()&&!ui.busy) {browse("search")}
            Row {
                TextButton(onClick={browse("playlists")},enabled=!ui.busy){Text("Your playlists")}
                TextButton(onClick={browse("recent")},enabled=!ui.busy){Text("Recent")}
            }
            results.forEach {item ->TextButton(onClick={model.mutate("POST","/spotify/playback",obj("action" to "play","uri" to item.optString("uri"),"device_id" to selected),"Playback requested")},enabled=!ui.busy) {Icon(Icons.Outlined.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(item.optString("name"),modifier=Modifier.weight(1f),fontSize=13.sp)} }
            if(moreResults) TextButton(enabled=!ui.busy && resultOffset<=1000,onClick={browse(resultKind,true)}) {Text("Load more")}
            Text("Spotify content and playback are provided by Spotify.",color=Muted,fontSize=10.sp)
        }
    }
    if(irVolume) AlertDialog(onDismissRequest={irVolume=false},title={Text("Speaker volume via IR")},text={Column {
        ui.state.items("commands").filter {c ->ui.state.items("appliances").any {it.optString("id")==c.optString("appliance_id") && it.optString("kind")=="speaker"}}.forEach {c ->TextButton(onClick={model.mutate("POST","/actions",obj("type" to "ir","command_id" to c.optString("id")),"IR transmitted; speaker state unconfirmed");irVolume=false}) {Text(c.optString("name"))} }
    }},confirmButton={TextButton(onClick={irVolume=false}){Text("Close")}})
}

@Composable fun SystemScreen(model: RoomModel, ui: RoomUi) {
    val context=LocalContext.current
    val current=ui.state.optJSONObject("system") ?: obj()
    var zone by remember(current.toString()) {mutableStateOf(current.optString("timezone","UTC"))}
    var timeout by remember(current.toString()) {mutableStateOf(current.optInt("display_timeout",30).toFloat())}
    var volumeTarget by remember(current.toString()) {mutableStateOf(current.optString("volume_target","spotify"))}
    var pauseConfirm by remember {mutableStateOf(false)}
    var diagnostics by remember {mutableStateOf<JSONObject?>(null)}
    var passphrase by remember {mutableStateOf("")}
    var archiveToWrite by remember {mutableStateOf("")}
    var restoreArchive by remember {mutableStateOf<String?>(null)}
    var ota by remember {mutableStateOf<JSONObject?>(null)}
    var signout by remember {mutableStateOf(false)}
    val backupFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri ->
        if(uri!=null) model.work {withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.use {it.write(archiveToWrite.toByteArray())} ?: error("Could not create backup file")};archiveToWrite="";passphrase=""}
    }
    val restoreFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) model.work {
            val text=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {it.readBounded(4_000_000)} ?: error("Could not read backup")}
            restoreArchive=JSONObject(text).getString("archive")
        }
    }
    val otaFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri!=null) model.work {
            val text=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {it.readBounded(16000)} ?: error("Could not read firmware manifest")}
            ota=JSONObject(text)
        }
    }
    PageTitle("Settings", "Everything in place.", "Connect your room, follow the setup guides, and make it yours.")
    ConnectionGuides(model.endpoint,ui.demo)
    ThemeChooser()
    RoomCard {
        SectionLabel("Connection",if(ui.state.optBoolean("connected")&&!ui.stale) "ONLINE" else "OFFLINE")
        Text(if(ui.demo) "Interface preview" else model.endpoint,color=Muted,fontSize=13.sp)
        if(ui.demo) ActionButton("Connect my ESP32", enabled=!ui.busy) { model.useBuiltInDevice() }
        val telemetry=ui.state.optJSONObject("telemetry")
        Text("Firmware: ${telemetry?.optString("firmware") ?: "Not connected"}\nWi-Fi: ${telemetry?.optInt("rssi") ?: "â€”"} dBm\nClock: ${if(telemetry?.optBoolean("time_valid")==true) "Synchronized" else "Not synchronized"}",color=Muted,fontSize=13.sp,lineHeight=23.sp)
        Text("Addressable lighting replaces Gesto. Control actions and GPIO assignments are approved for implementation; module specifications and electrical compatibility still need verification.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
    }
    RoomCard {
        SectionLabel("Preferences")
        OutlinedTextField(value=zone,onValueChange={zone=it},label={Text("Default time zone")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Text("Each routine keeps its own time zone.",color=Muted,fontSize=11.sp)
        SettingSlider("OLED timeout",timeout,{timeout=it},5f..600f," s")
        SmallLabel("VOLUME ACTION TARGET")
        Choices(listOf("spotify","speaker_ir"),volumeTarget) {volumeTarget=it}
        ActionButton("Save preferences",Icons.Outlined.Check,!ui.busy && runCatching {ZoneId.of(zone)}.isSuccess) {
            model.mutate("PUT","/settings",obj("automation_enabled" to current.optBoolean("automation_enabled",true),"timezone" to zone,"display_timeout" to timeout.toInt(),"volume_target" to volumeTarget))
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Automation enabled",Modifier.weight(1f),fontSize=14.sp)
            LiquidToggle(current.optBoolean("automation_enabled",true),{if(!it) pauseConfirm=true else model.mutate("PUT","/settings",current.copyJson().apply {remove("id");put("automation_enabled",true)})},label="Automation enabled")
        }
    }
    RoomCard {
        SectionLabel("Backup & restore")
        Text("Encrypted backup of your IR library, presets, routines, and settings. Keep the passphrase somewhere safe; it cannot be recovered.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        OutlinedTextField(value=passphrase,onValueChange={passphrase=it},label={Text("Backup passphrase Â· 12+ characters")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=passphrase.length>=12&&!ui.busy,onClick={model.work {
                archiveToWrite=model.request("POST","/backup",obj("passphrase" to passphrase)).toString()
                backupFile.launch("room-backup.json")
            }}) {Text("Export backup")}
            OutlinedButton(enabled=passphrase.length>=12&&!ui.busy,onClick={restoreFile.launch(arrayOf("application/json","text/plain"))}) {Text("Restore")}
        }
    }
    RoomCard {
        SectionLabel("Firmware updates")
        Text("Import a signed manifest after verifying the board and stable power. The server validates its signature. Updates stay disabled until the hardware adapter supports verified OTA.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        OutlinedButton(onClick={otaFile.launch(arrayOf("application/json"))},enabled=!ui.busy) {Text("Import signed manifest")}
    }
    RoomCard {
        SectionLabel("Diagnostics")
        OutlinedButton(onClick={model.work {diagnostics=model.request("GET","/diagnostics")}},enabled=!ui.busy) {Text("Refresh diagnostics")}
        diagnostics?.items("jobs")?.take(8)?.forEach {job ->Text("${job.optString("status")} Â· ${job.optString("detail")}",color=Muted,fontSize=11.sp)}
        diagnostics?.items("events")?.take(12)?.forEach {event ->Text("${event.optString("category")} Â· ${event.optString("message")}",color=Muted,fontSize=11.sp,lineHeight=17.sp)}
        Text("Toggle and disruptive IR commands are never automatically retried. Transmitted signals do not confirm appliance state.",color=Muted,fontSize=11.sp,lineHeight=17.sp)
    }
    OutlinedButton(onClick={signout=true},modifier=Modifier.fillMaxWidth()) {Text("Disconnect & sign out")}
    Text("ROOM Â· v${BuildConfig.VERSION_NAME}",color=Muted,fontSize=10.sp,modifier=Modifier.fillMaxWidth())
    if(pauseConfirm) ConfirmDialog("Pause all automation?","Schedules and event-triggered rules will stop until you turn automation back on.",{pauseConfirm=false}) {model.mutate("PUT","/settings?confirm=true",current.copyJson().apply {remove("id");put("automation_enabled",false)},"Automation paused")}
    restoreArchive?.let {archive ->ConfirmDialog("Replace saved room data?","This replaces the saved IR library, settings, and routines. Restored routines stay paused. No IR commands will be replayed.",{restoreArchive=null}) {model.mutate("POST","/restore",obj("archive" to archive,"passphrase" to passphrase,"confirm" to true),"Backup restored; routines paused");passphrase=""} }
    ota?.let {manifest ->ConfirmDialog("Install firmware?","Version ${manifest.optString("version")} for ${manifest.optString("board")}. Keep stable power during the update.",{ota=null}) {model.mutate("POST","/ota",manifest.copyJson().put("confirm",true),"Firmware update submitted")} }
    if(signout) ConfirmDialog("Disconnect this phone?","Your saved routines and controller settings will remain available.",{signout=false}) {model.logout()}
}
