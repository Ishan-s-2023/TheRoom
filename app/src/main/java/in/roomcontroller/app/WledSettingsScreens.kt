package `in`.roomcontroller.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private data class ConfigTabSpec(
    val id: String,
    val shortTitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val CONFIG_TABS = listOf(
    ConfigTabSpec("Wi-Fi Setup", "Wi-Fi", Icons.Outlined.Wifi),
    ConfigTabSpec("LED Preferences", "LEDs", Icons.Outlined.Lightbulb),
    ConfigTabSpec("2D Configuration", "2D", Icons.Outlined.GridOn),
    ConfigTabSpec("User Interface", "UI", Icons.Outlined.DashboardCustomize),
    ConfigTabSpec("Sync Interfaces", "UDP", Icons.Outlined.Sync),
    ConfigTabSpec("Time & Macros", "Time", Icons.Outlined.Schedule),
    ConfigTabSpec("Usermods", "Audio", Icons.Outlined.GraphicEq),
    ConfigTabSpec("Security & Updates", "Security", Icons.Outlined.Security)
)

@Composable
fun WledControllerSettingsSection(
    wled: WledModel,
    wledUi: WledUi,
    roomModel: RoomModel? = null,
    roomUi: RoomUi? = null
) {
    val device = wledUi.device
    LaunchedEffect(device?.id, wledUi.online) {
        if (wledUi.online && wledUi.config == null) {
            wled.loadConfig()
        }
    }

    val section = wledUi.configSection

    RoomCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                SectionLabel(
                    "Controller Config",
                    if (wledUi.online) "LIVE /JSON/CFG" else "SAVED CONFIG"
                )
                Text(
                    text = if (device != null)
                        "${device.name} (${device.origin}) · Select a category below."
                    else
                        "Changes save locally and sync to /json/cfg on your ESP32.",
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { wled.loadConfig() }, enabled = !wledUi.busy) {
                Icon(Icons.Outlined.Refresh, "Reload WLED configuration")
            }
        }

        // Clean 2-row x 4-column grid of Config Category Pills so nothing truncates or wraps awkwardly
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CONFIG_TABS.chunked(4).forEach { rowTabs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowTabs.forEach { tab ->
                        val isSelected = section == tab.id
                        val tagSlug = tab.id.lowercase().replace(' ', '_')
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Lime.copy(alpha = 0.18f) else Ink.copy(alpha = 0.65f))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Lime else Line,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .livelyClick(pressedScale = 0.94f) { wled.setConfigSection(tab.id) }
                                .padding(horizontal = 6.dp, vertical = 10.dp)
                                .testTag("wled_settings_tab_$tagSlug"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Lime else Cream,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = tab.shortTitle,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Lime else Cream,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val cfg = wledUi.effectiveConfig
    when (section) {
        "Wi-Fi Setup" -> WledWifiSettingsCard(wled, wledUi, cfg, roomModel)
        "LED Preferences" -> WledLedSettingsCard(wled, wledUi, cfg)
        "2D Configuration" -> Wled2dSettingsCard(wled, wledUi, cfg)
        "User Interface" -> WledUiSettingsCard(wled, wledUi, cfg)
        "Sync Interfaces" -> WledSyncSettingsCard(wled, wledUi, cfg)
        "Time & Macros" -> WledTimeSettingsCard(wled, wledUi, cfg, roomModel, roomUi)
        "Usermods" -> WledUsermodsSettingsCard(wled, wledUi, cfg)
        "Security & Updates" -> WledButtonsAndBackupCard(wled, wledUi, cfg)
    }
}

@Composable
private fun WledWifiSettingsCard(
    wled: WledModel,
    wledUi: WledUi,
    cfg: JSONObject,
    roomModel: RoomModel?
) {
    val nwIns = cfg.optJSONObject("nw")?.optJSONArray("ins")?.optJSONObject(0) ?: obj()
    val idObj = cfg.optJSONObject("id") ?: obj()
    val apObj = cfg.optJSONObject("ap") ?: obj()
    val wifiObj = cfg.optJSONObject("wifi") ?: obj()

    var ssid by remember(nwIns.toString()) { mutableStateOf(nwIns.optString("ssid", "Home-WiFi-2.4G")) }
    var password by remember { mutableStateOf("") }
    var clearPassword by remember { mutableStateOf(false) }
    var staticIp by remember(nwIns.toString()) {
        val arr = nwIns.optJSONArray("ip")
        mutableStateOf(if (arr != null && arr.length() == 4) "${arr.optInt(0)}.${arr.optInt(1)}.${arr.optInt(2)}.${arr.optInt(3)}" else "0.0.0.0")
    }
    var gatewayIp by remember(nwIns.toString()) {
        val arr = nwIns.optJSONArray("gw")
        mutableStateOf(if (arr != null && arr.length() == 4) "${arr.optInt(0)}.${arr.optInt(1)}.${arr.optInt(2)}.${arr.optInt(3)}" else "0.0.0.0")
    }
    var subnetMask by remember(nwIns.toString()) {
        val arr = nwIns.optJSONArray("sn")
        mutableStateOf(if (arr != null && arr.length() == 4) "${arr.optInt(0)}.${arr.optInt(1)}.${arr.optInt(2)}.${arr.optInt(3)}" else "255.255.255.0")
    }
    var mdns by remember(idObj.toString()) { mutableStateOf(idObj.optString("mdns", "wled-room")) }
    var disableWifiSleep by remember(wifiObj.toString()) { mutableStateOf(wifiObj.optBoolean("sleep", false)) }
    var force80211g by remember(wifiObj.toString()) { mutableStateOf(wifiObj.optBoolean("phy", false)) }

    var apSsid by remember(apObj.toString()) { mutableStateOf(apObj.optString("ssid", "WLED-AP")) }
    var apPassword by remember { mutableStateOf("") }
    var apChannel by remember(apObj.toString()) { mutableFloatStateOf(apObj.optInt("chan", 1).coerceIn(1, 13).toFloat()) }
    var apHide by remember(apObj.toString()) { mutableStateOf(apObj.optInt("hide", 0) == 1) }
    var apBehavior by remember(apObj.toString()) { mutableIntStateOf(apObj.optInt("apbeh", 0).coerceIn(0, 3)) }

    RoomCard {
        SectionLabel("Client Wi-Fi", "2.4 GHZ")
        Text(
            "Leave password blank to keep the saved password, or enable Open Network to clear it.",
            color = Muted,
            fontSize = 12.sp
        )
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it.take(32) },
            label = { Text("Network SSID (2.4 GHz)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("wled_wifi_ssid_input")
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(63); if (it.isNotEmpty()) clearPassword = false },
            label = { Text("Wi-Fi password (blank = keep current)") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !clearPassword,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SettingToggleRow(
            title = "Open network (clear saved password)",
            checked = clearPassword,
            onCheckedChange = { clearPassword = it; if (it) password = "" }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = staticIp,
                onValueChange = { staticIp = it.take(15) },
                label = { Text("Static IP (0.0.0.0=DHCP)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = gatewayIp,
                onValueChange = { gatewayIp = it.take(15) },
                label = { Text("Gateway IP") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = subnetMask,
                onValueChange = { subnetMask = it.take(15) },
                label = { Text("Subnet mask") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = mdns,
                onValueChange = { mdns = it.filter { c -> c.isLetterOrDigit() || c == '-' }.take(32) },
                label = { Text("mDNS (.local)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        SettingToggleRow(
            title = "Disable Wi-Fi sleep",
            subtitle = "Prevents UDP / WebSocket latency spikes",
            checked = disableWifiSleep,
            onCheckedChange = { disableWifiSleep = it }
        )
        SettingToggleRow(
            title = "Force 802.11g mode",
            checked = force80211g,
            onCheckedChange = { force80211g = it }
        )
    }

    RoomCard {
        SectionLabel("Fallback Access Point", "4.3.2.1")
        OutlinedTextField(
            value = apSsid,
            onValueChange = { apSsid = it.take(32) },
            label = { Text("AP SSID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = apPassword,
            onValueChange = { apPassword = it.take(63) },
            label = { Text("AP password (8–63 chars, blank = keep)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SettingSlider("AP channel", apChannel, { apChannel = it }, 1f..13f, "")
        SmallLabel("WHEN AP OPENS")
        val apModes = listOf(
            "No conn after boot" to 0,
            "Disconnected" to 1,
            "Always" to 2,
            "Never" to 3
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            apModes.forEach { (label, code) ->
                FilterChip(
                    selected = apBehavior == code,
                    onClick = { apBehavior = code },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
        SettingToggleRow(
            title = "Hide AP SSID broadcast",
            checked = apHide,
            onCheckedChange = { apHide = it }
        )

        val validClientPsk = password.isEmpty() || password.length in 8..63
        val validApPsk = apPassword.isEmpty() || apPassword.length in 8..63
        ActionButton(
            text = "Save Wi-Fi Settings",
            icon = Icons.Outlined.Save,
            enabled = !wledUi.busy && ssid.isNotBlank() && validClientPsk && validApPsk,
            modifier = Modifier.fillMaxWidth()
        ) {
            fun parseIpv4Array(text: String, fallback: List<Int>): JSONArray {
                val parts = text.trim().split('.').mapNotNull { it.toIntOrNull()?.coerceIn(0, 255) }
                return JSONArray(if (parts.size == 4) parts else fallback)
            }
            val ins0 = obj(
                "ssid" to ssid.trim(),
                "ip" to parseIpv4Array(staticIp, listOf(0, 0, 0, 0)),
                "gw" to parseIpv4Array(gatewayIp, listOf(0, 0, 0, 0)),
                "sn" to parseIpv4Array(subnetMask, listOf(255, 255, 255, 0))
            ).apply {
                if (clearPassword) put("psk", "")
                else if (password.isNotEmpty()) put("psk", password)
            }
            val apPatch = obj(
                "ssid" to apSsid.trim(),
                "chan" to apChannel.roundToInt(),
                "hide" to if (apHide) 1 else 0,
                "apbeh" to apBehavior
            ).apply {
                if (apPassword.isNotEmpty()) put("psk", apPassword)
            }
            val patch = obj(
                "nw" to obj("ins" to JSONArray().put(ins0)),
                "id" to obj("mdns" to mdns.trim()),
                "wifi" to obj("sleep" to disableWifiSleep, "phy" to force80211g),
                "ap" to apPatch
            )
            wled.saveConfig(patch, "Wi-Fi & network settings saved")
            if (roomModel != null && roomModel.directDevice && (password.isNotEmpty() || clearPassword)) {
                roomModel.provisionWifi(ssid.trim(), if (clearPassword) "" else password)
            }
            password = ""
            apPassword = ""
        }
    }
}

@Composable
private fun WledLedSettingsCard(
    wled: WledModel,
    wledUi: WledUi,
    cfg: JSONObject
) {
    val hwLed = cfg.optJSONObject("hw")?.optJSONObject("led") ?: obj()
    val bus0 = hwLed.optJSONArray("ins")?.optJSONObject(0) ?: obj()
    val defObj = cfg.optJSONObject("def") ?: obj()
    val lightObj = cfg.optJSONObject("light") ?: obj()
    val gcObj = lightObj.optJSONObject("gc") ?: obj()
    val trObj = lightObj.optJSONObject("tr") ?: obj()
    val nlObj = lightObj.optJSONObject("nl") ?: obj()

    var totalLeds by remember(hwLed.toString()) { mutableStateOf(hwLed.optInt("total", wledUi.effectiveSnapshot.ledCount).toString()) }
    var maxPowerMa by remember(hwLed.toString()) { mutableStateOf(hwLed.optInt("maxpwr", wledUi.effectiveSnapshot.maxPowerMa).toString()) }
    var perLedMa by remember(hwLed.toString()) { mutableIntStateOf(hwLed.optInt("ledma", 55)) }
    var targetFps by remember(hwLed.toString()) { mutableFloatStateOf(hwLed.optInt("fps", 42).coerceIn(1, 120).toFloat()) }
    var rgbwMode by remember(hwLed.toString()) { mutableIntStateOf(hwLed.optInt("rgbwm", 0).coerceIn(0, 4)) }
    var cctBlending by remember(hwLed.toString()) { mutableStateOf(hwLed.optBoolean("cct", false)) }

    val currentPin = bus0.optJSONArray("pin")?.optInt(0, RoomHardwarePins.LED_DATA) ?: RoomHardwarePins.LED_DATA
    var gpioPin by remember(bus0.toString()) { mutableStateOf(currentPin.toString()) }
    var stripType by remember(bus0.toString()) { mutableIntStateOf(bus0.optInt("type", 22)) }
    var colorOrder by remember(bus0.toString()) { mutableIntStateOf(bus0.optInt("order", 0)) }
    var busStart by remember(bus0.toString()) { mutableStateOf(bus0.optInt("start", 0).toString()) }
    var reverseStrip by remember(bus0.toString()) { mutableStateOf(bus0.optBoolean("rev", false)) }
    var skipFirst by remember(bus0.toString()) { mutableStateOf(bus0.optInt("skip", 0).toString()) }
    var offRefresh by remember(bus0.toString()) { mutableStateOf(bus0.optBoolean("ref", false)) }

    var scaleBri by remember(lightObj.toString()) { mutableFloatStateOf(lightObj.optInt("scale-bri", 100).coerceIn(1, 100).toFloat()) }
    var autoSegments by remember(lightObj.toString()) { mutableStateOf(lightObj.optBoolean("aseg", false)) }

    var bootOn by remember(defObj.toString()) { mutableStateOf(defObj.optBoolean("on", true)) }
    var bootBri by remember(defObj.toString()) { mutableFloatStateOf(defObj.optInt("bri", 180).toFloat()) }
    var bootPreset by remember(defObj.toString()) { mutableStateOf(defObj.optInt("ps", 0).toString()) }

    var trEnabled by remember(trObj.toString()) { mutableStateOf(trObj.optBoolean("mode", true)) }
    var trDurationTenths by remember(trObj.toString()) { mutableFloatStateOf(trObj.optInt("dur", 7).toFloat()) }
    var trPalette by remember(trObj.toString()) { mutableStateOf(trObj.optInt("pal", 0) == 1) }

    var nlDefDur by remember(nlObj.toString()) { mutableFloatStateOf(nlObj.optInt("dur", 60).toFloat()) }
    var nlDefTbri by remember(nlObj.toString()) { mutableFloatStateOf(nlObj.optInt("tbri", 0).toFloat()) }
    var nlDefMode by remember(nlObj.toString()) { mutableIntStateOf(nlObj.optInt("mode", 1).coerceIn(0, 3)) }

    var gammaColor by remember(gcObj.toString()) { mutableFloatStateOf(gcObj.optDouble("col", 2.8).toFloat()) }
    var gammaBri by remember(gcObj.toString()) { mutableFloatStateOf(gcObj.optDouble("bri", 1.0).toFloat()) }

    val pinInt = gpioPin.toIntOrNull() ?: -1
    val countInt = totalLeds.toIntOrNull() ?: 0
    val maxPwrInt = maxPowerMa.toIntOrNull() ?: -1
    val pinError = RoomHardwarePins.validatePinForRole(pinInt, "led", requireOutput = true)

    RoomCard {
        SectionLabel("LED Bus & Power", "GPIO ${RoomHardwarePins.LED_DATA}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = totalLeds,
                onValueChange = { totalLeds = it.filter(Char::isDigit).take(4) },
                label = { Text("LED count") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("wled_led_count_input")
            )
            OutlinedTextField(
                value = gpioPin,
                onValueChange = { gpioPin = it.filter(Char::isDigit).take(2) },
                label = { Text("Data GPIO") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("wled_led_pin_input")
            )
            OutlinedTextField(
                value = busStart,
                onValueChange = { busStart = it.filter(Char::isDigit).take(4) },
                label = { Text("Start px") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        if (pinError != null) {
            Text(pinError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = maxPowerMa,
                onValueChange = { maxPowerMa = it.filter(Char::isDigit).take(5) },
                label = { Text("Max current (mA)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = skipFirst,
                onValueChange = { skipFirst = it.filter(Char::isDigit).take(2) },
                label = { Text("Skip first LEDs") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        SmallLabel("CURRENT PER LED & PROTOCOL")
        val maOptions = listOf("55 mA (5V)" to 55, "30 mA (12V)" to 30, "15 mA (WS2815)" to 15, "255 mA (High)" to 255)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            maOptions.forEach { (label, ma) ->
                FilterChip(selected = perLedMa == ma, onClick = { perLedMa = ma }, label = { Text(label, fontSize = 11.sp) })
            }
        }

        val typeOptions = listOf(
            "WS281x" to 22,
            "SK6812 RGBW" to 30,
            "TM1814" to 31,
            "400kHz" to 24,
            "TM1829" to 25,
            "UCS8903" to 26,
            "APA106" to 27,
            "FW1906" to 32
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            typeOptions.forEach { (label, code) ->
                FilterChip(selected = stripType == code, onClick = { stripType = code }, label = { Text(label, fontSize = 11.sp) })
            }
        }

        SmallLabel("COLOUR ORDER & AUTO-WHITE")
        val orderOptions = listOf("GRB" to 0, "RGB" to 1, "BRG" to 2, "RBG" to 3, "BGR" to 4, "GBR" to 5)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            orderOptions.forEach { (label, code) ->
                FilterChip(selected = colorOrder == code, onClick = { colorOrder = code }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        val rgbwModes = listOf("None" to 0, "Brighter" to 1, "Accurate" to 2, "Dual" to 3, "Max" to 4)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rgbwModes.forEach { (label, code) ->
                FilterChip(selected = rgbwMode == code, onClick = { rgbwMode = code }, label = { Text(label, fontSize = 11.sp) })
            }
        }

        SettingSlider("Target FPS", targetFps, { targetFps = it }, 10f..120f, " FPS")
        SettingSlider("Global brightness scale", scaleBri, { scaleBri = it }, 1f..100f, "%")

        SettingToggleRow("Reverse strip direction", checked = reverseStrip, onCheckedChange = { reverseStrip = it })
        SettingToggleRow("Refresh LEDs when off", checked = offRefresh, onCheckedChange = { offRefresh = it })
        SettingToggleRow("White balance / CCT correction", checked = cctBlending, onCheckedChange = { cctBlending = it })
        SettingToggleRow("Auto-create segment per bus", checked = autoSegments, onCheckedChange = { autoSegments = it })
    }

    RoomCard {
        SectionLabel("Boot & Transitions", "DEFAULTS")
        SettingToggleRow(
            title = "Turn LEDs on after power-up",
            checked = bootOn,
            onCheckedChange = { bootOn = it }
        )
        SettingSlider("Boot brightness", bootBri, { bootBri = it }, 0f..255f, " /255")
        OutlinedTextField(
            value = bootPreset,
            onValueChange = { bootPreset = it.filter(Char::isDigit).take(3) },
            label = { Text("Boot preset ID (0 = none)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SettingToggleRow(
            title = "Enable crossfade transitions",
            checked = trEnabled,
            onCheckedChange = { trEnabled = it }
        )
        SettingSlider("Transition duration (${String.format("%.1f", trDurationTenths / 10f)} s)", trDurationTenths, { trDurationTenths = it }, 0f..200f, " ×100ms")
        SettingToggleRow(
            title = "Smooth palette transitions",
            checked = trPalette,
            onCheckedChange = { trPalette = it }
        )
        SettingSlider("Nightlight duration", nlDefDur, { nlDefDur = it }, 1f..255f, " min")
        SettingSlider("Nightlight target brightness", nlDefTbri, { nlDefTbri = it }, 0f..255f, " /255")
        SmallLabel("NIGHTLIGHT MODE")
        val nlModes = listOf("Instant" to 0, "Fade" to 1, "Color fade" to 2, "Sunrise" to 3)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nlModes.forEach { (label, m) ->
                FilterChip(selected = nlDefMode == m, onClick = { nlDefMode = m }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        SettingSlider("Colour gamma (${String.format("%.1f", gammaColor)})", gammaColor, { gammaColor = it }, 1.0f..3.0f, "")
        SettingSlider("Brightness gamma (${String.format("%.1f", gammaBri)})", gammaBri, { gammaBri = it }, 1.0f..3.0f, "")

        ActionButton(
            text = "Save LED Preferences",
            icon = Icons.Outlined.Check,
            enabled = !wledUi.busy && pinError == null && countInt in 1..1500 && maxPwrInt in 0..65000,
            modifier = Modifier.fillMaxWidth()
        ) {
            val ins0 = obj(
                "start" to (busStart.toIntOrNull() ?: 0).coerceAtLeast(0),
                "len" to countInt,
                "pin" to JSONArray().put(pinInt),
                "order" to colorOrder,
                "rev" to reverseStrip,
                "skip" to (skipFirst.toIntOrNull() ?: 0).coerceIn(0, 255),
                "type" to stripType,
                "ref" to offRefresh,
                "rgbwm" to rgbwMode
            )
            val patch = obj(
                "hw" to obj(
                    "led" to obj(
                        "total" to countInt,
                        "maxpwr" to maxPwrInt,
                        "ledma" to perLedMa,
                        "cct" to cctBlending,
                        "fps" to targetFps.roundToInt(),
                        "rgbwm" to rgbwMode,
                        "ins" to JSONArray().put(ins0)
                    )
                ),
                "def" to obj(
                    "on" to bootOn,
                    "bri" to bootBri.roundToInt(),
                    "ps" to (bootPreset.toIntOrNull() ?: 0).coerceIn(0, 250)
                ),
                "light" to obj(
                    "scale-bri" to scaleBri.roundToInt(),
                    "aseg" to autoSegments,
                    "gc" to obj(
                        "bri" to (gammaBri * 10).roundToInt() / 10.0,
                        "col" to (gammaColor * 10).roundToInt() / 10.0
                    ),
                    "tr" to obj(
                        "mode" to trEnabled,
                        "dur" to trDurationTenths.roundToInt(),
                        "pal" to if (trPalette) 1 else 0
                    ),
                    "nl" to obj(
                        "dur" to nlDefDur.roundToInt(),
                        "tbri" to nlDefTbri.roundToInt(),
                        "mode" to nlDefMode
                    )
                )
            )
            wled.saveConfig(patch, "LED preferences saved")
        }
    }
}

@Composable
private fun Wled2dSettingsCard(
    wled: WledModel,
    wledUi: WledUi,
    cfg: JSONObject
) {
    val hwLed = cfg.optJSONObject("hw")?.optJSONObject("led") ?: obj()
    val matrixObj = hwLed.optJSONObject("matrix")
    var is2dMatrix by remember(hwLed.toString()) { mutableStateOf(matrixObj != null) }
    var panelWidth by remember(hwLed.toString()) { mutableStateOf((matrixObj?.optInt("mpc", 16) ?: 16).toString()) }
    var panelHeight by remember(hwLed.toString()) { mutableStateOf((matrixObj?.optInt("mph", 16) ?: 16).toString()) }
    var serpentine by remember(hwLed.toString()) { mutableStateOf(matrixObj?.optBoolean("serp", true) ?: true) }
    var vertical by remember(hwLed.toString()) { mutableStateOf(matrixObj?.optBoolean("vert", false) ?: false) }

    RoomCard {
        SectionLabel("Strip vs 2D Matrix", if (is2dMatrix) "2D MATRIX" else "1D STRIP")
        Text(
            "Choose whether your LEDs are arranged as a 1D strip (300-LED Room strip) or a 2D matrix panel.",
            color = Muted,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !is2dMatrix,
                onClick = { is2dMatrix = false },
                label = { Text("1D Strip", fontSize = 12.sp) }
            )
            FilterChip(
                selected = is2dMatrix,
                onClick = { is2dMatrix = true },
                label = { Text("2D Matrix Panel", fontSize = 12.sp) }
            )
        }
        if (is2dMatrix) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = panelWidth,
                    onValueChange = { panelWidth = it.filter(Char::isDigit).take(3) },
                    label = { Text("Panel width (px)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = panelHeight,
                    onValueChange = { panelHeight = it.filter(Char::isDigit).take(3) },
                    label = { Text("Panel height (px)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            SettingToggleRow("Serpentine wiring layout", checked = serpentine, onCheckedChange = { serpentine = it })
            SettingToggleRow("Vertical panel orientation", checked = vertical, onCheckedChange = { vertical = it })
        }
        ActionButton(
            text = "Save 2D Configuration",
            icon = Icons.Outlined.Check,
            enabled = !wledUi.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            val matrixPatch = if (is2dMatrix) {
                obj(
                    "mpc" to (panelWidth.toIntOrNull() ?: 16).coerceIn(1, 256),
                    "mph" to (panelHeight.toIntOrNull() ?: 16).coerceIn(1, 256),
                    "serp" to serpentine,
                    "vert" to vertical
                )
            } else JSONObject.NULL
            wled.saveConfig(obj("hw" to obj("led" to obj("matrix" to matrixPatch))), "2D configuration saved")
        }
    }
}

@Composable
private fun WledUiSettingsCard(
    wled: WledModel,
    wledUi: WledUi,
    cfg: JSONObject
) {
    val idObj = cfg.optJSONObject("id") ?: obj()
    var serverName by remember(idObj.toString()) { mutableStateOf(idObj.optString("name", "Bedroom WLED")) }
    var alexaName by remember(idObj.toString()) { mutableStateOf(idObj.optString("inv", "Room Light")) }

    RoomCard {
        SectionLabel("Device Identity", "WLED UI")
        OutlinedTextField(
            value = serverName,
            onValueChange = { serverName = it.take(32) },
            label = { Text("Controller display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = alexaName,
            onValueChange = { alexaName = it.take(32) },
            label = { Text("Voice assistant invocation name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ActionButton(
            text = "Save Identity Settings",
            icon = Icons.Outlined.Check,
            enabled = !wledUi.busy && serverName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            wled.saveConfig(
                obj("id" to obj("name" to serverName.trim(), "inv" to alexaName.trim())),
                "Controller name & UI settings saved"
            )
        }
    }
}

@Composable
private fun WledTimeSettingsCard(
    wled: WledModel,
    wledUi: WledUi,
    cfg: JSONObject,
    roomModel: RoomModel?,
    roomUi: RoomUi?
) {
    val ntpObj = cfg.optJSONObject("ntp") ?: obj()
    val timersIns = cfg.optJSONObject("timers")?.optJSONArray("ins")?.objects().orEmpty()
    val btnIns = cfg.optJSONObject("hw")?.optJSONObject("btn")?.optJSONArray("ins")?.objects().orEmpty()
    val roomSettings = roomUi?.state?.optJSONObject("settings") ?: obj()

    var ntpEnabled by remember(ntpObj.toString()) { mutableStateOf(ntpObj.optBoolean("en", true)) }
    var ntpHost by remember(ntpObj.toString(), roomSettings.toString()) {
        mutableStateOf(ntpObj.optString("host", roomSettings.optString("ntp_server", "pool.ntp.org")).ifBlank { "pool.ntp.org" })
    }
    var use24h by remember(ntpObj.toString(), roomUi?.use24HourFormat) {
        mutableStateOf(roomUi?.use24HourFormat ?: !ntpObj.optBoolean("ampm", false))
    }
    var utcOffsetMinutes by remember(ntpObj.toString()) {
        mutableStateOf((ntpObj.optInt("offset", 19800) / 60).toString())
    }
    var latitude by remember(ntpObj.toString()) { mutableStateOf(ntpObj.optDouble("lt", 28.6).toString()) }
    var longitude by remember(ntpObj.toString()) { mutableStateOf(ntpObj.optDouble("ln", 77.2).toString()) }
    var posixTz by remember(roomSettings.toString()) {
        mutableStateOf(roomSettings.optString("timezone", "IST-5:30").ifBlank { "IST-5:30" })
    }

    val firstTimer = timersIns.getOrNull(0) ?: obj()
    var timer1Enabled by remember(firstTimer.toString()) { mutableStateOf(firstTimer.optInt("en", 0) == 1) }
    var timer1Hour by remember(firstTimer.toString()) { mutableStateOf(firstTimer.optInt("hour", 19).toString()) }
    var timer1Min by remember(firstTimer.toString()) { mutableStateOf(firstTimer.optInt("min", 0).toString()) }
    var timer1Preset by remember(firstTimer.toString()) { mutableStateOf(firstTimer.optInt("ps", firstTimer.optInt("macro", 1)).toString()) }

    val secondTimer = timersIns.getOrNull(1) ?: obj()
    var timer2Enabled by remember(secondTimer.toString()) { mutableStateOf(secondTimer.optInt("en", 0) == 1) }
    var timer2Hour by remember(secondTimer.toString()) { mutableStateOf(secondTimer.optInt("hour", 23).toString()) }
    var timer2Min by remember(secondTimer.toString()) { mutableStateOf(secondTimer.optInt("min", 30).toString()) }
    var timer2Preset by remember(secondTimer.toString()) { mutableStateOf(secondTimer.optInt("ps", secondTimer.optInt("macro", 2)).toString()) }

    val btn0Macros = btnIns.firstOrNull()?.optJSONArray("macros")
    var btnShortMacro by remember(btnIns.toString()) { mutableStateOf((btn0Macros?.optInt(0, 1) ?: 1).toString()) }
    var btnLongMacro by remember(btnIns.toString()) { mutableStateOf((btn0Macros?.optInt(1, 2) ?: 2).toString()) }
    var btnDoubleMacro by remember(btnIns.toString()) { mutableStateOf((btn0Macros?.optInt(2, 0) ?: 0).toString()) }

    RoomCard {
        SectionLabel("Time & NTP Clock", "WLED + ROOM")
        SettingToggleRow("Sync time from NTP server", checked = ntpEnabled, onCheckedChange = { ntpEnabled = it })
        SettingToggleRow(
            "Use 24-hour clock format",
            checked = use24h,
            onCheckedChange = {
                use24h = it
                roomModel?.setUse24HourFormat(it)
            }
        )
        OutlinedTextField(
            value = ntpHost,
            onValueChange = { ntpHost = it.take(64) },
            label = { Text("NTP server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = posixTz,
                onValueChange = { posixTz = it.take(40) },
                label = { Text("POSIX TZ (IST-5:30)") },
                singleLine = true,
                modifier = Modifier.weight(1.3f)
            )
            OutlinedTextField(
                value = utcOffsetMinutes,
                onValueChange = { utcOffsetMinutes = it.filter { c -> c.isDigit() || c == '-' }.take(5) },
                label = { Text("UTC offset (min)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { posixTz = "IST-5:30"; utcOffsetMinutes = "330" }, label = { Text("India (IST-5:30)", fontSize = 11.sp) })
            AssistChip(onClick = { posixTz = "UTC0"; utcOffsetMinutes = "0" }, label = { Text("UTC (+0m)", fontSize = 11.sp) })
        }
        SmallLabel("COORDINATES (SUNRISE / SUNSET)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(8) },
                label = { Text("Latitude") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(8) },
                label = { Text("Longitude") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }

    RoomCard {
        SectionLabel("Macros & Schedules", "PRESETS")
        SmallLabel("BUTTON PRESS PRESET ID")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = btnShortMacro,
                onValueChange = { btnShortMacro = it.filter(Char::isDigit).take(3) },
                label = { Text("Short") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = btnLongMacro,
                onValueChange = { btnLongMacro = it.filter(Char::isDigit).take(3) },
                label = { Text("Long") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = btnDoubleMacro,
                onValueChange = { btnDoubleMacro = it.filter(Char::isDigit).take(3) },
                label = { Text("Double") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = Line)
        SettingToggleRow("Enable Time Macro #1", checked = timer1Enabled, onCheckedChange = { timer1Enabled = it })
        if (timer1Enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(timer1Hour, { timer1Hour = it.filter(Char::isDigit).take(2) }, label = { Text("Hour") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(timer1Min, { timer1Min = it.filter(Char::isDigit).take(2) }, label = { Text("Min") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(timer1Preset, { timer1Preset = it.filter(Char::isDigit).take(3) }, label = { Text("Preset ID") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
        SettingToggleRow("Enable Time Macro #2", checked = timer2Enabled, onCheckedChange = { timer2Enabled = it })
        if (timer2Enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(timer2Hour, { timer2Hour = it.filter(Char::isDigit).take(2) }, label = { Text("Hour") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(timer2Min, { timer2Min = it.filter(Char::isDigit).take(2) }, label = { Text("Min") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(timer2Preset, { timer2Preset = it.filter(Char::isDigit).take(3) }, label = { Text("Preset ID") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }

        ActionButton(
            text = "Save Time & Macro Settings",
            icon = Icons.Outlined.Check,
            enabled = !wledUi.busy && ntpHost.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val offsetSec = (utcOffsetMinutes.toIntOrNull() ?: 330).coerceIn(-720, 840) * 60
            val tSlot1 = obj(
                "en" to if (timer1Enabled) 1 else 0,
                "hour" to (timer1Hour.toIntOrNull() ?: 19).coerceIn(0, 23),
                "min" to (timer1Min.toIntOrNull() ?: 0).coerceIn(0, 59),
                "dow" to 127,
                "ps" to (timer1Preset.toIntOrNull() ?: 1).coerceIn(1, 250),
                "macro" to (timer1Preset.toIntOrNull() ?: 1).coerceIn(1, 250)
            )
            val tSlot2 = obj(
                "en" to if (timer2Enabled) 1 else 0,
                "hour" to (timer2Hour.toIntOrNull() ?: 23).coerceIn(0, 23),
                "min" to (timer2Min.toIntOrNull() ?: 30).coerceIn(0, 59),
                "dow" to 127,
                "ps" to (timer2Preset.toIntOrNull() ?: 2).coerceIn(1, 250),
                "macro" to (timer2Preset.toIntOrNull() ?: 2).coerceIn(1, 250)
            )
            val btn0 = obj(
                "macros" to JSONArray(
                    listOf(
                        (btnShortMacro.toIntOrNull() ?: 0).coerceIn(0, 250),
                        (btnLongMacro.toIntOrNull() ?: 0).coerceIn(0, 250),
                        (btnDoubleMacro.toIntOrNull() ?: 0).coerceIn(0, 250)
                    )
                )
            )
            val patch = obj(
                "ntp" to obj(
                    "en" to ntpEnabled,
                    "host" to ntpHost.trim(),
                    "offset" to offsetSec,
                    "ampm" to !use24h,
                    "lt" to (latitude.toDoubleOrNull() ?: 28.6),
                    "ln" to (longitude.toDoubleOrNull() ?: 77.2)
                ),
                "hw" to obj("btn" to obj("ins" to JSONArray().put(btn0))),
                "timers" to obj("ins" to JSONArray().put(tSlot1).put(tSlot2))
            )
            wled.saveConfig(patch, "Time & macro configuration saved")
            if (roomModel != null && roomModel.directDevice && roomSettings.length() > 0) {
                roomModel.mutate(
                    "POST",
                    "/v1/command",
                    obj("op" to "settings", "value" to roomSettings.copyJson().put("timezone", posixTz.trim()).put("ntp_server", ntpHost.trim())),
                    "Synchronized Room & WLED clock settings"
                )
            }
        }
    }
}

@Composable
private fun WledSyncSettingsCard(
    wled: WledModel,
    wledUi: WledUi,
    cfg: JSONObject
) {
    val snap = wledUi.effectiveSnapshot
    val udpn = snap.udpSync
    val ifObj = cfg.optJSONObject("if") ?: obj()
    val syncObj = ifObj.optJSONObject("sync") ?: obj()
    val recvObj = syncObj.optJSONObject("recv") ?: obj()
    val sendObj = syncObj.optJSONObject("send") ?: obj()
    val liveObj = ifObj.optJSONObject("live") ?: obj()
    val dmxObj = liveObj.optJSONObject("dmx") ?: obj()
    val vaObj = ifObj.optJSONObject("va") ?: obj()
    val mqttObj = ifObj.optJSONObject("mqtt") ?: obj()
    val hueObj = ifObj.optJSONObject("hue") ?: obj()

    var udpSend by remember(udpn) { mutableStateOf(udpn.send) }
    var udpRecv by remember(udpn) { mutableStateOf(udpn.recv) }
    var udpPort by remember(syncObj.toString()) { mutableStateOf(syncObj.optInt("port0", 21324).toString()) }
    var udpPort2 by remember(syncObj.toString()) { mutableStateOf(syncObj.optInt("port1", 65506).toString()) }
    var recvBri by remember(recvObj.toString()) { mutableStateOf(recvObj.optBoolean("bri", true)) }
    var recvCol by remember(recvObj.toString()) { mutableStateOf(recvObj.optBoolean("col", true)) }
    var recvFx by remember(recvObj.toString()) { mutableStateOf(recvObj.optBoolean("fx", true)) }
    var recvSeg by remember(recvObj.toString()) { mutableStateOf(recvObj.optBoolean("seg", false)) }
    var sendDir by remember(sendObj.toString()) { mutableStateOf(sendObj.optBoolean("dir", false)) }
    var sendBtn by remember(sendObj.toString()) { mutableStateOf(sendObj.optBoolean("btn", false)) }
    var sendAlexa by remember(sendObj.toString()) { mutableStateOf(sendObj.optBoolean("va", false)) }

    var livePort by remember(liveObj.toString()) { mutableStateOf(liveObj.optInt("port", 5568).toString()) }
    var liveMulticast by remember(liveObj.toString()) { mutableStateOf(liveObj.optBoolean("mc", false)) }
    var dmxUniverse by remember(dmxObj.toString()) { mutableStateOf(dmxObj.optInt("uni", 1).toString()) }
    var dmxStartAddr by remember(dmxObj.toString()) { mutableStateOf(dmxObj.optInt("addr", 1).toString()) }
    var liveTimeout by remember(liveObj.toString()) { mutableFloatStateOf(liveObj.optInt("timeout", 25).toFloat()) }
    var forceMaxBri by remember(liveObj.toString()) { mutableStateOf(liveObj.optBoolean("maxbri", false)) }
    var disableLiveGamma by remember(liveObj.toString()) { mutableStateOf(liveObj.optBoolean("no-gc", true)) }

    var alexaEn by remember(vaObj.toString()) { mutableStateOf(vaObj.optBoolean("alexa", false)) }
    var alexaPresets by remember(vaObj.toString()) { mutableFloatStateOf(vaObj.optInt("p", 0).toFloat()) }

    var mqttEn by remember(mqttObj.toString()) { mutableStateOf(mqttObj.optBoolean("en", false)) }
    var mqttBroker by remember(mqttObj.toString()) { mutableStateOf(mqttObj.optString("broker", "")) }
    var mqttPort by remember(mqttObj.toString()) { mutableStateOf(mqttObj.optInt("port", 1883).toString()) }
    var mqttUser by remember(mqttObj.toString()) { mutableStateOf(mqttObj.optString("user", "")) }
    var mqttPassword by remember { mutableStateOf("") }
    var mqttDeviceTopic by remember(mqttObj.toString()) {
        val t = mqttObj.optJSONObject("topics")?.optString("device") ?: mqttObj.optString("topics", "wled/room")
        mutableStateOf(t)
    }

    var hueEn by remember(hueObj.toString()) { mutableStateOf(hueObj.optBoolean("en", false)) }
    var hueLightId by remember(hueObj.toString()) { mutableStateOf(hueObj.optInt("id", 1).toString()) }

    RoomCard {
        SectionLabel("UDP Broadcast Sync", "PORT ${udpPort.ifBlank { "21324" }}")
        SettingToggleRow(
            title = "Send UDP broadcast on state change",
            subtitle = "Notifies other WLED controllers on your LAN",
            checked = udpSend,
            onCheckedChange = {
                udpSend = it
                wled.command(obj("udpn" to obj("send" to it, "recv" to udpRecv)))
            }
        )
        SettingToggleRow(
            title = "Receive WLED UDP broadcast",
            subtitle = "Follows state packets from peer WLED nodes",
            checked = udpRecv,
            onCheckedChange = {
                udpRecv = it
                wled.command(obj("udpn" to obj("send" to udpSend, "recv" to it)))
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = udpPort,
                onValueChange = { udpPort = it.filter(Char::isDigit).take(5) },
                label = { Text("UDP port") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = udpPort2,
                onValueChange = { udpPort2 = it.filter(Char::isDigit).take(5) },
                label = { Text("2nd UDP port") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        SmallLabel("RECEIVE SYNC GROUPS")
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(recvBri, { recvBri = !recvBri }, label = { Text("Brightness", fontSize = 11.sp) })
            FilterChip(recvCol, { recvCol = !recvCol }, label = { Text("Color", fontSize = 11.sp) })
            FilterChip(recvFx, { recvFx = !recvFx }, label = { Text("Effects", fontSize = 11.sp) })
            FilterChip(recvSeg, { recvSeg = !recvSeg }, label = { Text("Segments", fontSize = 11.sp) })
        }
        SmallLabel("SEND TRIGGERS")
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(sendDir, { sendDir = !sendDir }, label = { Text("Direct change", fontSize = 11.sp) })
            FilterChip(sendBtn, { sendBtn = !sendBtn }, label = { Text("Button press", fontSize = 11.sp) })
            FilterChip(sendAlexa, { sendAlexa = !sendAlexa }, label = { Text("Alexa change", fontSize = 11.sp) })
        }
    }

    RoomCard {
        SectionLabel("Realtime & Integrations", "DMX · MQTT · HUE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = livePort,
                onValueChange = { livePort = it.filter(Char::isDigit).take(5) },
                label = { Text("UDP port") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = dmxUniverse,
                onValueChange = { dmxUniverse = it.filter(Char::isDigit).take(4) },
                label = { Text("Universe") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = dmxStartAddr,
                onValueChange = { dmxStartAddr = it.filter(Char::isDigit).take(3) },
                label = { Text("DMX addr") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        SettingToggleRow("Multicast E1.31 / Art-Net", checked = liveMulticast, onCheckedChange = { liveMulticast = it })
        SettingToggleRow("Force max brightness in realtime", checked = forceMaxBri, onCheckedChange = { forceMaxBri = it })
        SettingToggleRow("Disable realtime gamma", checked = disableLiveGamma, onCheckedChange = { disableLiveGamma = it })
        SettingSlider("Realtime timeout (${String.format("%.1f", liveTimeout / 10f)} s)", liveTimeout, { liveTimeout = it }, 5f..250f, " ×100ms")

        HorizontalDivider(color = Line)
        SettingToggleRow("Emulate Alexa device", checked = alexaEn, onCheckedChange = { alexaEn = it })
        if (alexaEn) {
            SettingSlider("Alexa presets count", alexaPresets, { alexaPresets = it }, 0f..9f, "")
        }
        SettingToggleRow("Enable MQTT broker", checked = mqttEn, onCheckedChange = { mqttEn = it })
        if (mqttEn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(mqttBroker, { mqttBroker = it.take(64) }, label = { Text("Broker host") }, singleLine = true, modifier = Modifier.weight(1.5f))
                OutlinedTextField(mqttPort, { mqttPort = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(mqttUser, { mqttUser = it.take(32) }, label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(mqttPassword, { mqttPassword = it.take(64) }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(mqttDeviceTopic, { mqttDeviceTopic = it.take(64) }, label = { Text("Device topic") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        SettingToggleRow("Philips Hue poll sync", checked = hueEn, onCheckedChange = { hueEn = it })
        if (hueEn) {
            OutlinedTextField(hueLightId, { hueLightId = it.filter(Char::isDigit).take(3) }, label = { Text("Hue light ID (1–99)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        ActionButton(
            text = "Save Sync Interfaces",
            icon = Icons.Outlined.Check,
            enabled = !wledUi.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            val mqttPatch = obj(
                "en" to mqttEn,
                "broker" to mqttBroker.trim(),
                "port" to (mqttPort.toIntOrNull() ?: 1883).coerceIn(1, 65535),
                "user" to mqttUser.trim()
            ).apply {
                if (mqttPassword.isNotEmpty()) put("psk", mqttPassword)
            }
            val patch = obj(
                "if" to obj(
                    "sync" to obj(
                        "port0" to (udpPort.toIntOrNull() ?: 21324).coerceIn(1, 65535),
                        "port1" to (udpPort2.toIntOrNull() ?: 65506).coerceIn(1, 65535),
                        "recv" to obj("bri" to recvBri, "col" to recvCol, "fx" to recvFx, "seg" to recvSeg),
                        "send" to obj("dir" to sendDir, "btn" to sendBtn, "va" to sendAlexa)
                    ),
                    "live" to obj(
                        "port" to (livePort.toIntOrNull() ?: 5568).coerceIn(1, 65535),
                        "mc" to liveMulticast,
                        "timeout" to liveTimeout.roundToInt(),
                        "maxbri" to forceMaxBri,
                        "no-gc" to disableLiveGamma,
                        "dmx" to obj(
                            "uni" to (dmxUniverse.toIntOrNull() ?: 1).coerceIn(1, 63999),
                            "addr" to (dmxStartAddr.toIntOrNull() ?: 1).coerceIn(1, 510)
                        )
                    ),
                    "va" to obj("alexa" to alexaEn, "p" to alexaPresets.roundToInt()),
                    "mqtt" to mqttPatch,
                    "hue" to obj("en" to hueEn, "id" to (hueLightId.toIntOrNull() ?: 1).coerceIn(1, 99))
                )
            )
            wled.saveConfig(patch, "Sync interfaces saved")
            mqttPassword = ""
        }
    }
}

@Composable
private fun WledUsermodsSettingsCard(
    wled: WledModel,
    wledUi: WledUi,
    cfg: JSONObject
) {
    val ar = cfg.optJSONObject("um")?.optJSONObject("AudioReactive") ?: obj()
    val dmic = ar.optJSONObject("dmic") ?: obj()
    val res = ar.optJSONObject("resources") ?: obj()
    val dyn = ar.optJSONObject("dynamics") ?: obj()
    val freq = ar.optJSONObject("frequency") ?: obj()
    val sync = ar.optJSONObject("sync") ?: obj()

    var arEnabled by remember(ar.toString()) { mutableStateOf(ar.optBoolean("enabled", true)) }
    var dmicType by remember(dmic.toString()) { mutableIntStateOf(dmic.optInt("type", 1)) }
    var pinSd by remember(res.toString()) { mutableStateOf(res.optInt("pin-sd", RoomHardwarePins.MIC_SD).toString()) }
    var pinWs by remember(res.toString()) { mutableStateOf(res.optInt("pin-ws", RoomHardwarePins.MIC_WS).toString()) }
    var pinSck by remember(res.toString()) { mutableStateOf(res.optInt("pin-sck", RoomHardwarePins.MIC_BCLK).toString()) }
    var squelch by remember(ar.toString()) { mutableFloatStateOf(ar.optInt("squelch", 10).toFloat()) }
    var gain by remember(ar.toString()) { mutableFloatStateOf(ar.optInt("gain", 64).toFloat()) }
    var agcMode by remember(ar.toString()) { mutableIntStateOf(ar.optInt("agc", 1)) }
    var limiter by remember(dyn.toString()) { mutableStateOf(dyn.optBoolean("limiter", true)) }
    var riseMs by remember(dyn.toString()) { mutableFloatStateOf(dyn.optInt("rise", 80).toFloat()) }
    var fallMs by remember(dyn.toString()) { mutableFloatStateOf(dyn.optInt("fall", 1400).toFloat()) }
    var freqScale by remember(freq.toString()) { mutableIntStateOf(freq.optInt("scale", 3)) }
    var syncMode by remember(sync.toString()) { mutableIntStateOf(sync.optInt("mode", 0)) }
    var syncPort by remember(sync.toString()) { mutableStateOf(sync.optInt("port", 11988).toString()) }

    val sdInt = pinSd.toIntOrNull() ?: -1
    val wsInt = pinWs.toIntOrNull() ?: -1
    val sckInt = pinSck.toIntOrNull() ?: -1
    val pinError = RoomHardwarePins.validatePinForRole(sdInt, "mic-sd", false)
        ?: RoomHardwarePins.validatePinForRole(wsInt, "mic-ws", true)
        ?: RoomHardwarePins.validatePinForRole(sckInt, "mic-sck", true)

    RoomCard {
        SectionLabel("AudioReactive DSP", "INMP441 I2S")
        SettingToggleRow(
            title = "Enable AudioReactive usermod",
            subtitle = "Configures WLED I²S microphone pins, squelch, AGC, and FFT scaling",
            checked = arEnabled,
            onCheckedChange = { arEnabled = it }
        )
        Column(
            modifier = Modifier.alpha(if (arEnabled) 1f else 0.42f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val childEn = arEnabled && !wledUi.busy
            SettingSlider("Sound squelch (noise gate)", squelch, { squelch = it }, 0f..255f, "", enabled = childEn)
            SettingSlider("Sample gain", gain, { gain = it }, 0f..255f, "", enabled = childEn)
            SmallLabel("AUTOMATIC GAIN CONTROL (AGC)")
            val agcOptions = listOf("Off" to 0, "Normal" to 1, "Vivid" to 2, "Lazy" to 3)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                agcOptions.forEach { (label, code) ->
                    FilterChip(selected = agcMode == code, onClick = { agcMode = code }, enabled = childEn, label = { Text(label, fontSize = 11.sp) })
                }
            }
            SmallLabel("FREQUENCY SCALE")
            val scaleOptions = listOf("None" to 0, "Logarithmic" to 1, "Linear" to 2, "Square root" to 3)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                scaleOptions.forEach { (label, code) ->
                    FilterChip(selected = freqScale == code, onClick = { freqScale = code }, enabled = childEn, label = { Text(label, fontSize = 11.sp) })
                }
            }
            SettingToggleRow(
                title = "Dynamics limiter",
                checked = limiter,
                enabled = childEn,
                onCheckedChange = { limiter = it }
            )
            SettingSlider("Rise time (attack)", riseMs, { riseMs = it }, 0f..1000f, " ms", enabled = childEn && limiter)
            SettingSlider("Fall time (decay)", fallMs, { fallMs = it }, 0f..5000f, " ms", enabled = childEn && limiter)

            SmallLabel("DIGITAL MICROPHONE & I2S PINS")
            val micTypes = listOf("Generic I2S (INMP441)" to 1, "Analog ADC" to 0, "ES7243" to 2, "SPH0645" to 3, "I2S+MCLK" to 4, "PDM" to 5)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                micTypes.forEach { (label, code) ->
                    FilterChip(selected = dmicType == code, onClick = { dmicType = code }, enabled = childEn, label = { Text(label, fontSize = 11.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pinSck, { pinSck = it.filter(Char::isDigit).take(2) }, label = { Text("SCK/BCLK") }, singleLine = true, enabled = childEn, modifier = Modifier.weight(1f))
                OutlinedTextField(pinWs, { pinWs = it.filter(Char::isDigit).take(2) }, label = { Text("WS/LRCLK") }, singleLine = true, enabled = childEn, modifier = Modifier.weight(1f))
                OutlinedTextField(pinSd, { pinSd = it.filter(Char::isDigit).take(2) }, label = { Text("SD/DOUT") }, singleLine = true, enabled = childEn, modifier = Modifier.weight(1f))
            }
            if (pinError != null) {
                Text(pinError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            SmallLabel("AUDIO SYNC UDP")
            val syncModes = listOf("Off" to 0, "Transmit" to 1, "Receive" to 2)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                syncModes.forEach { (label, code) ->
                    FilterChip(selected = syncMode == code, onClick = { syncMode = code }, enabled = childEn, label = { Text(label, fontSize = 11.sp) })
                }
            }
            if (syncMode != 0) {
                OutlinedTextField(syncPort, { syncPort = it.filter(Char::isDigit).take(5) }, label = { Text("Audio UDP port") }, singleLine = true, enabled = childEn, modifier = Modifier.fillMaxWidth())
            }
        }
        ActionButton(
            text = "Save Usermods Settings",
            icon = Icons.Outlined.Check,
            enabled = !wledUi.busy && pinError == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            val patch = obj(
                "um" to obj(
                    "AudioReactive" to obj(
                        "enabled" to arEnabled,
                        "squelch" to squelch.roundToInt(),
                        "gain" to gain.roundToInt(),
                        "agc" to agcMode,
                        "dmic" to obj("type" to dmicType),
                        "resources" to obj("pin-sd" to sdInt, "pin-ws" to wsInt, "pin-sck" to sckInt),
                        "dynamics" to obj("limiter" to limiter, "rise" to riseMs.roundToInt(), "fall" to fallMs.roundToInt()),
                        "frequency" to obj("scale" to freqScale),
                        "sync" to obj("port" to (syncPort.toIntOrNull() ?: 11988).coerceIn(1, 65535), "mode" to syncMode)
                    )
                )
            )
            wled.saveConfig(patch, "Usermods configuration saved")
        }
    }

    RoomCard {
        SectionLabel("Hardware Pin Map", "GPIOS")
        RoomHardwarePins.RESERVED_ROLES.entries.forEach { (pin, role) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("GPIO $pin", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Lime, modifier = Modifier.weight(0.32f))
                Text(role, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(0.68f))
            }
        }
    }
}

@Composable
private fun WledButtonsAndBackupCard(
    wled: WledModel,
    wledUi: WledUi,
    cfg: JSONObject
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val otaObj = cfg.optJSONObject("ota") ?: obj()
    var otaLock by remember(otaObj.toString()) { mutableStateOf(otaObj.optBoolean("lock", false)) }
    var wifiLock by remember(otaObj.toString()) { mutableStateOf(otaObj.optBoolean("lock-wifi", false)) }
    var aotaEnabled by remember(otaObj.toString()) { mutableStateOf(otaObj.optBoolean("aota", false)) }
    var settingsPin by remember { mutableStateOf("") }
    var confirmReboot by remember { mutableStateOf(false) }

    var pendingExportText by remember { mutableStateOf("") }
    var pendingRestoreKind by remember { mutableStateOf("presets") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && pendingExportText.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(pendingExportText.toByteArray(Charsets.UTF_8))
                    }
                }
                pendingExportText = ""
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBounded(1_048_576) }
                    }.getOrNull()
                }
                if (raw != null) {
                    wled.importBackup(pendingRestoreKind, raw)
                }
            }
        }
    }

    RoomCard {
        SectionLabel("Backup & Security", "JSON & OTA")
        Text(
            "Export or restore WLED presets (/presets.json) and controller configuration (/cfg.json).",
            color = Muted,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    wled.exportBackup("presets") { fileName, json ->
                        pendingExportText = json
                        exportLauncher.launch(fileName)
                    }
                },
                enabled = !wledUi.busy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Backup presets.json", fontSize = 12.sp, maxLines = 1, softWrap = false) }
            OutlinedButton(
                onClick = {
                    pendingRestoreKind = "presets"
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                },
                enabled = !wledUi.busy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Restore presets.json", fontSize = 12.sp, maxLines = 1, softWrap = false) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    wled.exportBackup("cfg") { fileName, json ->
                        pendingExportText = json
                        exportLauncher.launch(fileName)
                    }
                },
                enabled = !wledUi.busy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Backup cfg.json", fontSize = 12.sp, maxLines = 1, softWrap = false) }
            OutlinedButton(
                onClick = {
                    pendingRestoreKind = "cfg"
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                },
                enabled = !wledUi.busy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Restore cfg.json", fontSize = 12.sp, maxLines = 1, softWrap = false) }
        }

        HorizontalDivider(color = Line)
        SmallLabel("SECURITY LOCK & REBOOT")
        SettingToggleRow(
            title = "Lock wireless (OTA) updates & settings",
            subtitle = "Requires passphrase to modify controller config",
            checked = otaLock,
            onCheckedChange = { otaLock = it }
        )
        SettingToggleRow(
            title = "Deny access to Wi-Fi settings if locked",
            checked = wifiLock,
            onCheckedChange = { wifiLock = it }
        )
        SettingToggleRow(
            title = "Enable ArduinoOTA",
            checked = aotaEnabled,
            onCheckedChange = { aotaEnabled = it }
        )
        OutlinedTextField(
            value = settingsPin,
            onValueChange = { settingsPin = it.take(32) },
            label = { Text("OTA / Settings passphrase (write-only)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                text = "Save Lock",
                icon = Icons.Outlined.Lock,
                enabled = !wledUi.busy,
                modifier = Modifier.weight(1f)
            ) {
                val otaPatch = obj(
                    "lock" to otaLock,
                    "lock-wifi" to wifiLock,
                    "aota" to aotaEnabled
                ).apply {
                    if (settingsPin.isNotEmpty()) put("pskl", settingsPin)
                }
                wled.saveConfig(obj("ota" to otaPatch), "Security settings saved")
                settingsPin = ""
            }
            OutlinedButton(
                onClick = { confirmReboot = true },
                enabled = !wledUi.busy,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.RestartAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reboot WLED", fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
        }
        Text(
            "Stock WLED /update binary uploads are blocked so Room's IR, Spotify, OLED, and HTTPS services remain protected.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = "Reboot ${wledUi.device?.name ?: "WLED controller"}?",
            description = "The ESP32 will restart and resume its configured boot preset. Unsaved RAM lighting changes will be reset.",
            onDismiss = { confirmReboot = false }
        ) {
            wled.rebootController()
        }
    }
}
