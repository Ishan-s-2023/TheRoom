package `in`.roomcontroller.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable fun DeviceLearnDialog(model: RoomModel, applianceId: String, dismiss: () -> Unit) {
    val view = LocalView.current
    var name by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(listening) {
        if (listening) {
            repeat(40) {
                delay(800)
                val state = runCatching { model.request("GET", "/state") }.getOrNull()
                if (state != null) {
                    val runtime = state.optJSONObject("runtime")
                    val runtimeMsg = runtime?.optString("message").orEmpty()
                        .ifBlank { runtime?.optString("ir_error").orEmpty() }
                        .ifBlank { state.optString("error").orEmpty() }
                    if (runtimeMsg.isNotBlank()) {
                        message = runtimeMsg
                    }
                    if (runtime?.optBoolean("learning") == false) {
                        val saved = state.items("commands").any {
                            it.optString("appliance_id") == applianceId && it.optString("name") == name
                        }
                        message = when {
                            saved -> "Capture saved on ESP32. Close to refresh the library."
                            runtimeMsg.isNotBlank() -> runtimeMsg
                            else -> "Capture stopped. Check the device status before retrying."
                        }
                        listening = false
                        return@LaunchedEffect
                    }
                }
            }
            message = "Capture did not finish within 32s. Close to cancel or check IR receiver wiring."
            listening = false
        }
    }
    fun close() {
        performSubtleHaptic(view)
        model.work {
            model.request("POST", "/v1/command", obj("op" to "cancel_learning"))
            model.refresh()
        }
        dismiss()
    }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("Learn an IR command") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Point the remote at the ESP32 receiver, start capture, then press its button. The firmware saves captures directly and defaults to safe toggle handling.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Command name") },
                    enabled = !listening,
                    singleLine = true
                )
                if (message.isNotBlank()) {
                    Text(message, color = Muted)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !listening && name.isNotBlank(),
                onClick = {
                    performSubtleHaptic(view, heavy = true)
                    model.work {
                        val resp = model.request("POST", "/v1/command", obj("op" to "learn", "appliance_id" to applianceId, "name" to name))
                        val respMsg = resp.optString("message").ifBlank { resp.optString("detail") }
                        message = respMsg.ifBlank { "Listening... Point remote at ESP32 and press button." }
                        listening = true
                    }
                }
            ) { Text("Start capture") }
        },
        dismissButton = {
            TextButton(onClick = { close() }) { Text("Close") }
        }
    )
}

@Composable fun PairDeviceScreen(model: RoomModel, busy: Boolean) {
    val context = LocalContext.current
    var manualHost by remember { mutableStateOf("192.168.1.50") }
    var manualToken by remember { mutableStateOf("") }
    var rawJsonConfig by remember { mutableStateOf("") }
    var showJsonImport by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        PageTitle(
            "Local room control",
            "Pair your ESP32.",
            "Join the ESP32 Wi-Fi -> connect or enter your controller IP & token -> control your room directly over your local network."
        )
        RoomCard {
            SectionLabel("Quick Connect", if (model.configuredDevice != null) "CONFIGURED" else "SETUP")
            Text(
                if (model.configuredDevice != null) {
                    "This app includes a built-in controller profile. Tap Connect my ESP32 to join its setup network or connect on your LAN."
                } else {
                    "Connect using a built-in controller profile, or pair manually below with your ESP32 IP/hostname and API token."
                },
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            ActionButton("Open Wi-Fi settings", enabled = !busy) {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            ActionButton("Connect my ESP32", enabled = !busy) {
                model.useBuiltInDevice()
            }
        }

        RoomCard {
            SectionLabel("Manual LAN Pairing", "HTTP / HTTPS")
            Text(
                "Enter your ESP32 controller's IP address or .local hostname and its device token (or API key) to pair directly without requiring a bundled room-device.config asset.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            OutlinedTextField(
                value = manualHost,
                onValueChange = { manualHost = it },
                label = { Text("ESP32 IP or Hostname (e.g. 192.168.1.50 or theroom.local)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = manualToken,
                onValueChange = { manualToken = it },
                label = { Text("Device Token / API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ActionButton(
                text = "Pair & Connect on LAN",
                enabled = !busy && manualHost.isNotBlank() && manualToken.isNotBlank()
            ) {
                model.pairManualDevice(manualHost, manualToken)
            }
            TextButton(onClick = { showJsonImport = !showJsonImport }, enabled = !busy) {
                Text(if (showJsonImport) "Hide pairing.json import" else "Import pairing.json / room-device.config")
            }
            if (showJsonImport) {
                OutlinedTextField(
                    value = rawJsonConfig,
                    onValueChange = { rawJsonConfig = it },
                    label = { Text("Paste pairing.json content") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                ActionButton(
                    text = "Import Configuration JSON",
                    enabled = !busy && rawJsonConfig.isNotBlank()
                ) {
                    model.importPairingJson(rawJsonConfig)
                }
            }
        }

        Text(
            "Normal control connects directly to your ESP32 over verified HTTPS or local LAN HTTP. No computer or external cloud server is needed.",
            color = Muted,
            fontSize = 12.sp
        )
        TextButton(onClick = { model.demo() }, enabled = !busy) {
            Text("Explore the interface")
        }
    }
}

@Composable fun DeviceConnectionCard(model: RoomModel, ui: RoomUi, wled: WledModel? = null, wledUi: WledUi = WledUi()) {
    val context = LocalContext.current
    val pairing = model.pairing
    if (pairing == null) {
        var manualHost by remember { mutableStateOf("192.168.1.50") }
        var manualToken by remember { mutableStateOf("") }
        RoomCard {
            SectionLabel("ESP32 Room Connection", "UNPAIRED")
            Text(
                "Pair your ESP32 controller by entering its local IP/hostname and token, or tap Connect my ESP32 if a built-in config is present.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            OutlinedTextField(
                value = manualHost,
                onValueChange = { manualHost = it },
                label = { Text("ESP32 IP or Hostname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = manualToken,
                onValueChange = { manualToken = it },
                label = { Text("Device Token / API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ActionButton(
                text = "Pair Controller",
                enabled = !ui.busy && manualHost.isNotBlank() && manualToken.isNotBlank()
            ) {
                model.pairManualDevice(manualHost, manualToken)
            }
            if (model.configuredDevice != null) {
                ActionButton("Use Built-in Controller Profile", enabled = !ui.busy) {
                    model.useBuiltInDevice()
                }
            }
        }
        return
    }
    val runtime = ui.state.optJSONObject("runtime")
    val homeIp = runtime?.optString("ip").orEmpty().takeUnless { it == "0.0.0.0" }
        ?: wledUi.snapshot?.ip?.takeUnless { it == "0.0.0.0" }.orEmpty()
    var address by remember(model.endpoint, homeIp) {
        mutableStateOf(
            when {
                model.endpoint.isNotBlank() -> model.endpoint
                homeIp.isNotEmpty() -> "https://$homeIp"
                else -> "https://${pairing.hostname}"
            }
        )
    }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showSetupPassword by remember { mutableStateOf(false) }
    RoomCard {
        SectionLabel("ESP32 Room connection", if (ui.stale) "OFFLINE" else "PAIRED")
        Text(
            "Setup network: ${pairing.ssid} (${pairing.setupIp} · WLED HTTP 4.3.2.1)\nCurrent address: ${model.endpoint}",
            color = Muted
        )
        TextButton(onClick = { showSetupPassword = !showSetupPassword }) {
            Text(if (showSetupPassword) "Hide setup password" else "Show setup password")
        }
        if (showSetupPassword) Text(pairing.apPassword)
        ActionButton("Open phone Wi-Fi settings", enabled = !ui.busy) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        ActionButton("Connect through setup Wi-Fi", enabled = !ui.busy) {
            model.connectDevice("https://${pairing.setupIp}")
            wled?.probeRoomPeer("https://${pairing.setupIp}")
        }
        Text(
            "Home IP: ${homeIp.ifEmpty { "check the ESP32 display or router device list" }}\nDevice hostname: ${pairing.hostname}",
            color = Muted
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Home HTTP/HTTPS address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ActionButton("Reconnect on home Wi-Fi", enabled = !ui.busy) {
            model.connectDevice(address)
            wled?.probeRoomPeer(address)
        }
        Text(
            "Use a 2.4 GHz home network. Saving updates the active WLED/combined controller Wi-Fi configuration so the ESP32 joins your home network.",
            color = Muted
        )
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("Home Wi-Fi name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Home Wi-Fi password (blank for open network)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ActionButton(
            "Save home Wi-Fi to ESP32",
            enabled = !ui.busy && !wledUi.busy && ssid.isNotBlank() && (password.isEmpty() || password.length in 8..63)
        ) {
            if (wled != null && wledUi.online) {
                val ins0 = obj("ssid" to ssid.trim(), "psk" to password)
                wled.saveConfig(obj("nw" to obj("ins" to org.json.JSONArray().put(ins0))), "Home Wi-Fi saved to WLED controller")
            }
            if (!ui.stale || wled == null || !wledUi.online) {
                model.provisionWifi(ssid.trim(), password)
            }
            password = ""
        }
    }
}

@Composable fun DeviceSystemScreen(model: RoomModel, ui: RoomUi, wled: WledModel? = null, wledUi: WledUi = WledUi()) {
    PageTitle("Settings", "Your room, connected.", "Direct ESP32 control on your local Wi-Fi.")
    DeviceConnectionCard(model, ui, wled, wledUi)
    SpotifyAccountSettingsCard(model = model, ui = ui)
    DataStoreBackupRestoreCard(model = model, ui = ui, wled = wled)
    if (wled != null) {
        WledControllerSettingsSection(wled, wledUi, model, ui)
    }
    RoomThreeBandEqSettingsCard(model = model, ui = ui)
    ThemeChooser()
    val settings = ui.state.optJSONObject("settings") ?: ui.state.optJSONObject("system") ?: obj()
    var zone by remember(settings.toString()) {
        mutableStateOf(settings.optString("timezone").ifBlank { "IST-5:30" })
    }
    var ntp by remember(settings.toString()) {
        mutableStateOf(settings.optString("ntp_server").ifBlank { "pool.ntp.org" })
    }
    val isArmed = ui.state.optJSONObject("runtime")?.optBoolean("armed", true) == true
    RoomCard {
        SectionLabel("Automation & clock", if (isArmed) "ARMED" else "PAUSED")
        ClockFormatSettingSelector(
            use24HourFormat = ui.use24HourFormat,
            onSelect24Hour = { model.setUse24HourFormat(it) }
        )
        Text(
            "All routines use the controller's POSIX timezone. For India use IST-5:30. Configure the clock before enabling schedules or Spotify.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        OutlinedTextField(
            value = zone,
            onValueChange = { zone = it },
            label = { Text("POSIX timezone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = ntp,
            onValueChange = { ntp = it },
            label = { Text("NTP server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ActionButton(
            text = "Save clock settings",
            enabled = !ui.busy && zone.isNotBlank()
        ) {
            val payload = settings.copyJson()
                .put("timezone", zone.trim().ifBlank { "IST-5:30" })
                .put("ntp_server", ntp.trim().ifBlank { "pool.ntp.org" })
                .put("use_24_hour", ui.use24HourFormat)
            model.mutate(
                "POST",
                "/v1/command",
                obj("op" to "settings", "value" to payload),
                "Clock settings saved"
            )
        }
        SettingToggleRow(
            title = "Automation master switch",
            subtitle = if (isArmed) "All routines and triggers armed" else "All routines and triggers paused",
            checked = isArmed,
            enabled = !ui.busy,
            onCheckedChange = { nextArmed -> model.setAllRoutinesEnabled(nextArmed) }
        )
    }
    RoomCard {
        SectionLabel("Device status")
        Text(
            ui.state.optJSONObject("runtime")?.toString(2) ?: "Connect to read status",
            color = Muted,
            fontSize = 12.sp
        )
        ActionButton("Refresh status", enabled = !ui.busy) {
            model.work { model.refresh() }
        }
    }
    OutlinedButton(
        onClick = { model.logout() },
        enabled = !ui.busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Disconnect this phone")
    }
}

@Composable fun DeviceMusicScreen(model: RoomModel, ui: RoomUi, navigate: (String) -> Unit = {}) {
    PageTitle(
        "Music",
        "Your soundtrack.",
        "Spotify commands run on the ESP32. Internet, a synchronized clock, and eligible Spotify playback are required."
    )
    val music = ui.state.optJSONObject("spotify") ?: obj()
    val systemState = ui.state.optJSONObject("system") ?: obj()
    val connected = music.optBoolean("connected") || ui.state.optBoolean("spotify_connected") || ui.spotifyOAuthConnected
    var query by remember { mutableStateOf("") }

    val currentSpotifyVol = music.optInt("volume_percent", 55).toFloat().coerceIn(0f, 100f)
    var volume by remember { mutableFloatStateOf(currentSpotifyVol) }
    var lastVolEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentSpotifyVol) {
        if (System.currentTimeMillis() - lastVolEditMs > 750L) {
            volume = currentSpotifyVol
        }
    }

    val currentSpeakerVol = systemState.optInt("speaker_volume_percent", ui.state.optInt("speaker_volume_percent", 60)).toFloat().coerceIn(0f, 100f)
    var speakerVolume by remember { mutableFloatStateOf(currentSpeakerVol) }
    var lastSpeakerVolEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentSpeakerVol) {
        if (System.currentTimeMillis() - lastSpeakerVolEditMs > 750L) {
            speakerVolume = currentSpeakerVol
        }
    }

    fun command(action: String, argument: String = "", value: Int = 0) {
        model.spotifyCommand(action, argument, value)
    }

    if (!connected) {
        RoomCard {
            SectionLabel("Spotify Account", "SETTINGS")
            Text(
                "Spotify account linking is managed in Settings. Open Settings to connect or switch your Spotify account.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = { navigate("System") },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Open Settings to link Spotify", maxLines = 1, softWrap = false)
            }
        }
    }

    val trackName = music.optString("track").ifBlank { "" }
    val artistName = music.optString("artist").ifBlank { "" }
    val imageUrl = music.optString("image_url").takeIf { it.isNotBlank() }
        ?: music.optString("artwork").takeIf { it.isNotBlank() }
        ?: (if (trackName.isNotBlank()) defaultCoverForTrackName(trackName) else "")
    val activeDeviceName = music.optString("device").ifBlank { "" }
    val deviceList = remember(music.toString()) {
        val raw = music.items("devices")
        if (raw.isNotEmpty()) raw
        else music.optJSONObject("browse")?.items("items")?.filter { it.optString("kind") == "transfer" } ?: emptyList()
    }

    val progressMs = music.optInt("progress_ms", 0).coerceAtLeast(0)
    val durationMs = music.optInt("duration_ms", 214000).coerceAtLeast(1000)
    val shuffleState = music.optBoolean("shuffle_state", music.optBoolean("shuffle", false))
    val repeatState = music.optString("repeat_state", music.optString("repeat", "off")).ifBlank { "off" }

    val effectivePlayer = remember(music.toString(), trackName, artistName, imageUrl, progressMs, durationMs, shuffleState, repeatState) {
        obj(
            "is_playing" to music.optBoolean("playing", false),
            "progress_ms" to progressMs,
            "shuffle_state" to shuffleState,
            "repeat_state" to repeatState,
            "image_url" to imageUrl,
            "track" to trackName,
            "artist" to artistName,
            "item" to obj(
                "name" to trackName,
                "duration_ms" to durationMs,
                "image_url" to imageUrl,
                "artists" to org.json.JSONArray().put(obj("name" to artistName))
            )
        )
    }

    RoomCard {
        MusicPlayerWidget(effectivePlayer, ui.demo, ui.busy) { act, payload ->
            when (act) {
                "play", "pause", "next", "previous" -> command(act)
                "seek" -> command("seek", value = payload.optInt("position_ms", 0))
                "shuffle" -> {
                    val mode = payload.optString("mode", "")
                    val v = payload.optInt("value", if (payload.optBoolean("state", false)) 1 else 0)
                    command("shuffle", argument = mode, value = v)
                }
                "repeat" -> {
                    val nextMode = payload.optString("mode", "off")
                    command("repeat", argument = nextMode)
                }
                "like" -> command("like", argument = payload.optString("id", payload.optString("argument", "")))
                "unlike" -> command("unlike", argument = payload.optString("id", payload.optString("argument", "")))
            }
        }
        if (ui.pendingAction == null && music.optString("error").isNotBlank()) {
            Text(music.optString("error"), color = Muted, fontSize = 12.sp)
        }
        ui.pendingAction?.let { label ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(label, color = Muted, fontSize = 12.sp)
            }
        }

        // Clickable Active Device Speaker Dropdown to switch where the song plays
        SpotifyDeviceDropdownSelector(
            activeDeviceName = activeDeviceName,
            activeDeviceId = activeDeviceName,
            devices = deviceList,
            enabled = !ui.busy,
            onSelectDevice = { devId, devName ->
                command("transfer", devId.ifBlank { devName })
            }
        )

        // 1. Instantaneous Spotify Volume Control (no Set Volume or Refresh button required)
        VolumeControl(
            value = volume,
            onChange = { nextVol ->
                lastVolEditMs = System.currentTimeMillis()
                volume = nextVol
                model.coalescedVolume(nextVol.toInt())
            },
            enabled = true,
            onFinished = {
                lastVolEditMs = System.currentTimeMillis()
            }
        )

        HorizontalDivider(color = Line)

        // 2. Physical Speaker / Device Volume Control (via IR)
        val speakerMuted = systemState.optBoolean("speaker_muted", ui.state.optBoolean("speaker_muted", false))
        SpeakerIrVolumeControl(
            value = speakerVolume,
            onChange = { nextSpkVol ->
                lastSpeakerVolEditMs = System.currentTimeMillis()
                speakerVolume = nextSpkVol
                model.coalescedSpeakerIrVolume(nextSpkVol.toInt())
            },
            onIrStep = { kind ->
                lastSpeakerVolEditMs = System.currentTimeMillis()
                model.triggerSpeakerIrCommand(kind)
            },
            enabled = true,
            initialMuted = speakerMuted,
            deviceName = activeDeviceName,
            onFinished = {
                lastSpeakerVolEditMs = System.currentTimeMillis()
            }
        )
    }
    RoomCard {
        SectionLabel("Browse & Search")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search tracks and playlists") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ActionButton("Search", enabled = !ui.busy && query.isNotBlank()) { command("search", query) }
        Row {
            TextButton(onClick = { command("devices") }, enabled = !ui.busy) { Text("Devices") }
            TextButton(onClick = { command("playlists") }, enabled = !ui.busy) { Text("Playlists") }
            TextButton(onClick = { command("recent") }, enabled = !ui.busy) { Text("Recent") }
        }
        music.optJSONObject("browse")?.items("items")?.forEach { item ->
            val itemImage = item.optString("image_url").takeIf { it.isNotBlank() }
                ?: item.optJSONObject("album")?.optJSONArray("images")?.optJSONObject(0)?.optString("url")
            TextButton(onClick = { command(item.getString("kind"), item.getString("id")) }, enabled = !ui.busy) {
                if (!itemImage.isNullOrBlank()) {
                    AsyncImage(
                        model = itemImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(item.optString("name"))
            }
        }
        Text("Results update when Spotify responds.", color = Muted, fontSize = 11.sp)
    }
}
