package `in`.roomcontroller.app

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable fun DeviceLearnDialog(model: RoomModel, applianceId: String, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(listening) {
        if(listening) {
            repeat(40) {
                delay(800)
                val state = runCatching { model.request("GET","/state") }.getOrNull()
                if(state != null && state.optJSONObject("runtime")?.optBoolean("learning") == false) {
                    val saved = state.items("commands").any { it.optString("appliance_id")==applianceId && it.optString("name")==name }
                    message = if(saved) "Capture saved on ESP32. Close to refresh the library." else "Capture stopped. Check the device status before retrying."
                    listening=false
                    return@LaunchedEffect
                }
            }
            message="Capture did not finish. Close to cancel or check device status."; listening=false
        }
    }
    fun close() { model.work { model.request("POST","/v1/command",obj("op" to "cancel_learning")); model.refresh() }; dismiss() }
    AlertDialog(onDismissRequest={close()},title={Text("Learn an IR command")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Point the remote at the ESP32 receiver, start capture, then press its button. The firmware saves captures directly and defaults to safe toggle handling.")
        OutlinedTextField(name,{name=it.take(60)},label={Text("Command name")},enabled=!listening,singleLine=true)
        Text(message,color=Muted)
    }},confirmButton={TextButton(enabled=!listening && name.isNotBlank(),onClick={model.work {
        model.request("POST","/v1/command",obj("op" to "learn","appliance_id" to applianceId,"name" to name))
        message="ListeningÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦"; listening=true
    }}){Text("Start capture")}},dismissButton={TextButton(onClick={close()}){Text("Close")}})
}

@Composable fun PairDeviceScreen(model: RoomModel, busy: Boolean) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement=Arrangement.spacedBy(20.dp)) {
        PageTitle("Local room control", "Pair your ESP32.", "Join the ESP32 Wi-Fi ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ import pairing.json ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ enter home Wi-Fi details ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ switch your phone to home Wi-Fi ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ control your room.")
        RoomCard {
            Text("This app is configured for your ESP32. Tap Connect my ESP32 to view its setup network and connect.")
            ActionButton("Open Wi-Fi settings", enabled=!busy) { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            ActionButton("Connect my ESP32", enabled=!busy) { model.useBuiltInDevice() }
        }
        Text("Normal control connects directly to your ESP32 over verified HTTPS. No computer or backend server is needed.", color=Muted)
        TextButton(onClick={model.demo()}, enabled=!busy) { Text("Explore the interface") }
    }
}

@Composable fun DeviceConnectionCard(model: RoomModel, ui: RoomUi) {
    val context = LocalContext.current
    val pairing = model.pairing ?: return
    val runtime = ui.state.optJSONObject("runtime")
    val homeIp = runtime?.optString("ip").orEmpty().takeUnless { it == "0.0.0.0" }.orEmpty()
    var address by remember(model.endpoint, homeIp) { mutableStateOf(if(homeIp.isNotEmpty()) "https://$homeIp" else "https://${pairing.hostname}") }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showSetupPassword by remember { mutableStateOf(false) }
    RoomCard {
        SectionLabel("ESP32 connection", if(ui.stale) "OFFLINE" else "PAIRED")
        Text("Setup network: ${pairing.ssid}\nCurrent address: ${model.endpoint}", color=Muted)
        TextButton(onClick={showSetupPassword=!showSetupPassword}) { Text(if(showSetupPassword) "Hide setup password" else "Show setup password") }
        if(showSetupPassword) Text(pairing.apPassword)
        ActionButton("Open phone Wi-Fi settings", enabled=!ui.busy) { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        ActionButton("Connect through setup Wi-Fi", enabled=!ui.busy) { model.connectDevice("https://${pairing.setupIp}") }
        Text("Home IP: ${homeIp.ifEmpty { "check the ESP32 display or router device list" }}\nDevice hostname: ${pairing.hostname}", color=Muted)
        OutlinedTextField(address,{address=it},label={Text("Home HTTPS address")},singleLine=true,modifier=Modifier.fillMaxWidth())
        ActionButton("Reconnect on home Wi-Fi", enabled=!ui.busy) { model.connectDevice(address) }
        Text("Use a 2.4 GHz home network. After saving, the setup Wi-Fi closes when the ESP32 connects. Switch your phone to home Wi-Fi, enter its IP from the display or router, then reconnect. The hostname works only if your network resolves it.", color=Muted)
        OutlinedTextField(ssid,{ssid=it},label={Text("Home Wi-Fi name")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Home Wi-Fi password (blank for open network)")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        ActionButton("Save home Wi-Fi to ESP32", enabled=!ui.busy && ssid.isNotBlank()) { model.provisionWifi(ssid,password); password="" }
    }
}

@Composable fun DeviceSystemScreen(model: RoomModel, ui: RoomUi) {
    PageTitle("Settings", "Your room, connected.", "Direct ESP32 control on your local Wi-Fi.")
    DeviceConnectionCard(model,ui)
    ThemeChooser()
    val settings = ui.state.optJSONObject("settings") ?: obj()
    var zone by remember(settings.toString()) { mutableStateOf(settings.optString("timezone")) }
    var ntp by remember(settings.toString()) { mutableStateOf(settings.optString("ntp_server")) }
    var pause by remember { mutableStateOf(false) }
    RoomCard {
        SectionLabel("Automation & clock")
        Text("All routines use the controller's POSIX timezone. For India use IST-5:30. Configure the clock before enabling schedules or Spotify.", color=Muted)
        OutlinedTextField(zone,{zone=it},label={Text("POSIX timezone")},singleLine=true)
        OutlinedTextField(ntp,{ntp=it},label={Text("NTP server")},singleLine=true)
        ActionButton("Save clock settings", enabled=!ui.busy && settings.length()>0) { model.mutate("POST","/v1/command",obj("op" to "settings","value" to settings.copyJson().put("timezone",zone).put("ntp_server",ntp)),"Clock settings saved") }
        Text("Automation: ${if(ui.state.optJSONObject("runtime")?.optBoolean("armed")==true) "armed" else "paused"}")
        ActionButton("Enable automation",enabled=!ui.busy) { model.mutate("POST","/v1/command",obj("op" to "automation","enabled" to true),"Automation enabled") }
        TextButton(onClick={pause=true},enabled=!ui.busy) { Text("Pause automation") }
    }
    RoomCard {
        SectionLabel("Device status")
        Text(ui.state.optJSONObject("runtime")?.toString(2) ?: "Connect to read status", color=Muted)
        ActionButton("Refresh status",enabled=!ui.busy) { model.work { model.refresh() } }
    }
    OutlinedButton(onClick={model.logout()},enabled=!ui.busy) { Text("Disconnect this phone") }
    if(pause) ConfirmDialog("Pause all automation?","Schedules and event rules will stop.",{pause=false}) { model.mutate("POST","/v1/command",obj("op" to "automation","enabled" to false,"confirm" to true),"Automation paused") }
}

@Composable fun DeviceMusicScreen(model: RoomModel, ui: RoomUi) {
    PageTitle("Music", "Your soundtrack.", "Spotify commands run on the ESP32. Internet, a synchronized clock, and eligible Spotify playback are required.")
    val music = ui.state.optJSONObject("spotify") ?: obj()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var volume by remember(music.optInt("volume_percent",50)) { mutableStateOf(music.optInt("volume_percent",50).toFloat()) }
    fun command(action: String, argument: String = "", value: Int = 0) {
        model.spotifyCommand(action, argument, value)
    }
    RoomCard {
        Text("${music.optString("track")}\n${music.optString("artist")}\n${music.optString("device")}")
        if(ui.pendingAction == null) Text(music.optString("error"),color=Muted)
        ui.pendingAction?.let { label ->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier=Modifier.size(18.dp), strokeWidth=2.dp)
                Text(label, color=Muted)
            }
        }
        Row { TextButton(onClick={command("previous")},enabled=!ui.busy){Text("Previous")}; TextButton(onClick={command(if(music.optBoolean("playing")) "pause" else "play")},enabled=!ui.busy){Text(if(music.optBoolean("playing")) "Pause" else "Play")}; TextButton(onClick={command("next")},enabled=!ui.busy){Text("Next")} }
        SettingSlider("Spotify volume",volume,{volume=it})
        ActionButton("Set volume",enabled=!ui.busy){command("volume",value=volume.toInt())}
        ActionButton("Refresh playback",enabled=!ui.busy){command("refresh")}
    }
    RoomCard {
        OutlinedTextField(query,{query=it},label={Text("Search tracks and playlists")},singleLine=true)
        ActionButton("Search",enabled=!ui.busy && query.isNotBlank()){command("search",query)}
        Row { TextButton(onClick={command("devices")},enabled=!ui.busy){Text("Devices")}; TextButton(onClick={command("playlists")},enabled=!ui.busy){Text("Playlists")}; TextButton(onClick={command("recent")},enabled=!ui.busy){Text("Recent")} }
        music.optJSONObject("browse")?.items("items")?.forEach { item ->
            TextButton(onClick={command(item.getString("kind"),item.getString("id"))},enabled=!ui.busy){Text(item.optString("name"))}
        }
        Text("Results update when Spotify responds.",color=Muted)
    }
    RoomCard {
        SectionLabel("Spotify account")
        Text("Sign in securely with Spotify on this phone. Access is saved to your paired room so other paired phones can control playback too. Connecting another Spotify account replaces the room's current account.",color=Muted)
        ActionButton("Connect Spotify",enabled=!ui.busy && !ui.stale) {
            context.startActivity(Intent(context, SpotifyConnectActivity::class.java))
        }
    }
}
