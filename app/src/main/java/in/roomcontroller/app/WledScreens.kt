package `in`.roomcontroller.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable fun WledQuickControls(model: WledModel, ui: WledUi, open: (String) -> Unit) {
    val device = ui.device
    RoomCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SmallLabel("YOUR LIGHTING")
                Text(device?.name ?: "Connect your WLED controller", fontSize = 20.sp)
                Text(if (ui.online) "Connected · WLED ${ui.snapshot?.info?.optString("ver")}" else if (device == null)
                    "All effects, colours and presets in Room" else "Offline · check controller Wi-Fi", color = Muted, fontSize = 12.sp)
            }
            LiquidToggle(checked = ui.snapshot?.state?.optBoolean("on") == true,
                onCheckedChange = { model.command(obj("on" to it)) }, enabled = ui.online && !ui.busy, label = "WLED lighting power")
        }
        val actual = (ui.snapshot?.state?.optInt("bri") ?: 0) * 100f / 255f
        var brightness by remember(device?.id, actual) { mutableFloatStateOf(actual) }
        BrightnessControl(brightness, { brightness = it }, enabled = ui.online && !ui.busy,
            onFinished = { model.command(obj("bri" to wledByte(brightness))) })
        ActionButton(if (device == null) "Connect lighting" else "Open lighting", Icons.Outlined.LightMode) { open("/") }
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (ui.online) {
        val ids = ui.presets.keys().asSequence().filter { it.toIntOrNull() in 1..250 }
            .sortedBy { it.toInt() }.take(6).toList()
        if (ids.isNotEmpty()) RoomCard {
            SectionLabel("A mood for every moment", "WLED PRESETS")
            ids.forEach { id ->
                val preset = ui.presets.optJSONObject(id) ?: return@forEach
                OutlinedButton(onClick = { model.command(obj("ps" to id.toInt())) }, enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(preset.optString("n", "Preset $id"))
                }
            }
        }
    }
}

@Composable fun WledLightsScreen(model: WledModel, ui: WledUi, open: (String) -> Unit) {
    PageTitle("Lighting", "Find your glow.", "WLED's lighting controls, with the feeling of Room.")
    if (ui.device == null) { WledDevicesCard(model, ui); return }
    WledQuickControls(model, ui, open)
    if (ui.device != null) {
        RoomCard {
            SectionLabel("Make it yours")
            Text("Colours, palettes, effects, segments, presets and playlists use the controls running on your ESP32.", color = Muted, fontSize = 13.sp)
            ActionButton("Colours, effects & segments", Icons.Outlined.Palette) { open("/") }
            OutlinedButton(onClick = { open("/settings") }) { Text("Controller settings") }
            OutlinedButton(onClick = { open("/settings/um") }) { Text("Sound & usermods") }
            OutlinedButton(onClick = { open("/settings/time") }) { Text("Timers & time") }
            OutlinedButton(onClick = { open("/settings/sec") }) { Text("Backup, restore & updates") }
        }
    }
    WledDevicesCard(model, ui)
}

@Composable fun WledDevicesCard(model: WledModel, ui: WledUi) {
    var address by rememberSaveable { mutableStateOf("") }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WledDevice?>(null) }
    var deleting by remember { mutableStateOf<WledDevice?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    RoomCard {
        SectionLabel("Lighting controllers")
        Text("After installing WLED-based firmware, discover your ESP32 or enter its Wi-Fi address. No pairing file is needed for WLED.", color = Muted, fontSize = 13.sp)
        OutlinedTextField(address, { address = it }, label = { Text("WLED address") }, placeholder = { Text("192.168.1.105 or wled.local") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Connect", Icons.Outlined.Link, !ui.busy && address.isNotBlank()) { model.add(address) }
            OutlinedButton(onClick = model::discover, enabled = !ui.discovering) { Text(if (ui.discovering) "Searching…" else "Discover") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(showHidden, { showHidden = it }); Text("Show hidden controllers", color = Muted, fontSize = 12.sp)
        }
        ui.devices.filter { !it.hidden || showHidden }.forEach { device ->
            Column(Modifier.fillMaxWidth().clickable { model.select(device.id) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ui.selected == device.id, onClick = { model.select(device.id) })
                    Column(Modifier.weight(1f)) {
                        Text(device.name, fontSize = 16.sp)
                        Text(device.origin, color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { editing = device; name = device.name }) { Icon(Icons.Outlined.Edit, "Rename ${device.name}") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.hide(device.id, !device.hidden) }) { Text(if (device.hidden) "Show" else "Hide") }
                    TextButton(onClick = { deleting = device }) { Text("Remove") }
                    if (ui.selected == device.id) TextButton(onClick = { model.select(device.id) }) { Text("Reconnect") }
                }
                HorizontalDivider(color = Line)
            }
        }
    }
    editing?.let { device ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Name your lighting") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name in Room") }) },
            confirmButton = { TextButton(onClick = { model.rename(device.id, name); editing = null }, enabled = name.trim().length in 1..64) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
    deleting?.let { device -> ConfirmDialog("Remove ${device.name}?", "This removes its shortcut from Room. The controller settings stay on the ESP32.",
        { deleting = null }) { model.remove(device.id) } }
}
