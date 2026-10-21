package `in`.roomcontroller.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

val morePages = setOf("Routines", "Sound", "System")

@Composable fun MoreSettingsScreen(navigate: (String) -> Unit) {
    PageTitle("Make it yours", "A room that listens.", "Routines, sound tuning, and everything you need to connect your room.")
    listOf(
        Triple("Routines", "Schedules & room events", Icons.Outlined.Schedule),
        Triple("Sound", "Bass, mids, treble & microphone tuning", Icons.Outlined.GraphicEq),
        Triple("Settings", "Connection guides, appearance & system", Icons.Outlined.Settings)
    ).forEach { (label, detail, icon) ->
        RoomCard(Modifier.clickable { navigate(if (label == "Settings") "System" else label) }) {
            Icon(icon, null, tint = Lime)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 21.sp)
                    Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                Icon(Icons.Outlined.ArrowOutward, null)
            }
        }
    }
}

internal data class GuideStep(val title: String, val body: String, val command: String = "")
internal data class ConnectionGuide(val id: String, val title: String, val summary: String, val steps: List<GuideStep>, val links: List<Pair<String, String>> = emptyList())

internal fun connectionGuides(endpoint: String): List<ConnectionGuide> {
    val server = runCatching {
        val uri = URI(endpoint)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty().isEmpty())
        endpoint.trimEnd('/')
    }.getOrDefault("https://192.168.1.50:8443")
    return listOf(
        ConnectionGuide("device", "Connect directly to your ESP32", "Your controller is preconfigured · no file import", listOf(
            GuideStep("Open your controller setup", "This APK already contains the connection identity for your ESP32. Tap Connect my ESP32 on the opening screen or in More → Settings while exploring the demo. No pairing file, computer server, or certificate installation is needed."),
            GuideStep("Join the setup Wi-Fi", "Power on the ESP32. In ESP32 connection, note Setup network and tap Show setup password. Open phone Wi-Fi settings and join that network. Stay connected if Android warns there is no internet. Return to Room and tap Connect through setup Wi-Fi. If the ESP32 already joins your home router, use its home IP and Reconnect on home Wi-Fi instead."),
            GuideStep("Save home Wi-Fi", "Enter your compatible 2.4 GHz home Wi-Fi name/password and tap Save home Wi-Fi to ESP32. The setup network closes after joining home Wi-Fi. Find the ESP32 home IP on its display or your router's connected-device list. Switch your phone to the same home LAN, enter https:// followed by that IP in Home HTTPS address (no :8443), then tap Reconnect on home Wi-Fi. Room remembers the address. The hostname works only if your network resolves it."),
            GuideStep("Set the clock and test modules", "Settings → Automation & clock uses POSIX time zones: for India enter IST-5:30 and a reachable NTP server such as pool.ntp.org, then save. Verify hardware drivers and manual controls before enabling routines."),
            GuideStep("Reconnect later", "Room remembers your controller and home address. If your router changes the ESP32 address, update Home HTTPS address. A DHCP reservation in your router can keep it stable. If the router is unavailable, join the protected setup Wi-Fi and update home credentials. Disconnect this phone leaves the built-in controller identity available through Connect my ESP32."),
            GuideStep("If connection fails", "Check power, phone Wi-Fi, IP address and guest-network isolation. If Android routes over mobile data, temporarily turn mobile data off. Certificate or token errors mean the flashed controller identity differs from the one configured in this APK; keep the firmware identity unchanged or rebuild this APK for the replacement identity.")
        )),
        ConnectionGuide("spotify-direct", "Spotify on the ESP32", "Connect on your phone Â· optional computer helper", listOf(
            GuideStep("Prepare your account and controller", "Complete direct ESP32 pairing and home Wi-Fi setup first. You need internet on the computer and ESP32, a synchronized device clock, an eligible Spotify developer app, and a Premium account for playback control. The ESP32 sends commands to an existing Spotify playback device; it does not output Spotify audio. New Development Mode apps allow up to five authorized users and require the app owner's Premium subscription."),
            GuideStep("Register the Android callback", "For Connect Spotify on this phone, the current APK uses Client ID 564aae7414ea4cb58d45c479f6f9f795. Its Spotify app owner must register exactly in.roomcontroller.app://spotify/callback in that application dashboard and authorize your Spotify user in Development Mode. If you use your own different developer application, use the computer helper below or rebuild the APK with that Client ID. The callback belongs to the Android app, not to the ESP32 HTTPS address. No Client Secret is needed."),
            GuideStep("Connect from the main Music tab", "Keep this phone on your home Wi-Fi with internet and the paired ESP32 reachable. Open Music, scroll to Spotify account, and tap Connect Spotify. Sign in on Spotify, review permissions and approve; allow the browser to return to Room. Wait for Spotify connected, tap Return to Room, and refresh playback. Complete within ten minutes. The app sends refresh credentials to the paired ESP32 over pinned HTTPS. Linking a different Spotify account replaces the room account for all paired phones. Preview mode cannot authorize a real account."),
            GuideStep("Optional: authorize on your computer", "For the firmware helper register its separate exact callback http://127.0.0.1:8765/callback in your chosen Spotify app. Connect the computer to the ESP32's home LAN. Open PowerShell in SmartRoomController_Complete_Source/SmartRoomController with its matching .device-private/pairing.json. Replace YOUR_CLIENT_ID and 192.168.1.60 with your app Client ID and ESP32 Home IP. The helper opens the browser, validates the callback, obtains a refresh token, and sends it directly to the ESP32 using pinned HTTPS. Finish in five minutes. It does not print tokens; no Client Secret or persistent Python backend is needed.", "python -m pip install -r tools/requirements.txt\npython tools/spotify_authorize.py --client-id YOUR_CLIENT_ID --host 192.168.1.60"),
            GuideStep("Pick a real playback device", "Open Spotify on the phone, computer or compatible Connect speaker using the authorized account and start a track once. Open Room's main Music tab, refresh playback, request Devices, and select the returned target. Test Play/Pause, Next and volume. A Bluetooth speaker pairs to your playback phone/computer, not to this ESP32. You never need to paste a refresh token into the app; either authorization flow provisions it securely."),
            GuideStep("Troubleshoot and revoke", "Invalid redirect: phone sign-in requires in.roomcontroller.app://spotify/callback registered against the APK's Client ID; the computer helper instead uses http://127.0.0.1:8765/callback. Return-to-app failed: allow the browser to open Room and retry from Music. Port already in use: close only your previous helper using that port, then retry. No callback: finish authorization in the helper's computer browser, not on a phone. Pin mismatch or local HTTPS failure: check IP and matching pairing file. Spotify TLS or refresh errors: verify NTP and internet. 403: check Premium, allowed user, app access, scopes and device support. No active device: start Spotify manually first. 429: wait for the rate-limit cooldown. Revoke access from Spotify account â†’ Apps if needed, then use Connect Spotify again (or the helper) to reconnect.")
        ), listOf("Spotify Developer Dashboard" to "https://developer.spotify.com/dashboard", "Redirect URI rules" to "https://developer.spotify.com/documentation/web-api/concepts/redirect_uri", "Development mode requirements" to "https://developer.spotify.com/documentation/web-api/concepts/quota-modes", "Spotify account apps" to "https://www.spotify.com/account/apps/")),
        ConnectionGuide("server", "Companion server: phone setup", "Alternative development route Â· local Python server", listOf(
            GuideStep("Understand this alternative connection", "Only use this route with the companion-backend firmware and server login flow. Your phone talks to a Python server on your computer, and the ESP32's optional ESP-IDF bridge talks to that server. Keep the computer awake and the server running. This older route does not use pairing.json or the direct-device setup AP. Preview mode uses sample data only."),
            GuideStep("Prepare the computer", "Open PowerShell in the project folder. Install Python and the backend packages. Run setup once; choose an owner password of at least 12 characters. Save the device key shown once in a private password manager. If an owner already exists, use that installation; do not run setup again.", "python -m venv .venv\n.venv\\Scripts\\Activate.ps1\npython -m pip install -r backend/requirements.txt\npython -m backend.cli setup"),
            GuideStep("Choose the LAN address", "Run ipconfig on the computer and find the IPv4 address for the Wi-Fi/Ethernet adapter connected to your home router. Reserve this address in the router's DHCP settings. The example 192.168.1.50 must be replaced with your own address everywhere below. Connect your phone and ESP32 to the same LAN; avoid guest-network isolation. ESP32 Wi-Fi needs a compatible 2.4 GHz network.", "ipconfig"),
            GuideStep("Create and trust HTTPS", "Generate a certificate for the computer's actual LAN address. The helper requires an empty .certs directory and never overwrites certificates: if files already exist, check their hostnames and reuse valid ones, or privately archive them before replacing. Copy only .certs/room-ca.cer to the phone and install it through Android Settings â†’ Security â†’ Encryption & credentials â†’ Install a certificate â†’ CA certificate (wording varies). Install it in the computer's trusted root store too if you authorize Spotify there. Keep ca.key and server.key private.", "python scripts/create-local-certs.py --host 192.168.1.50"),
            GuideStep("Start one server", "For a real ESP32 omit -Simulator. To explore without hardware add -Simulator; this intentionally produces simulated results. Allow inbound TCP 8443 on your computer's private-network firewall for your LAN only. Run one backend instance. The browser UI at port 8767 is only an emulator viewer.", "powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -ListenAddress 0.0.0.0 -Port 8443"),
            GuideStep("Sign in on the phone", "Install the Room APK, leave preview mode using Settings â†’ Disconnect & sign out, and enter your HTTPS server origin, username (owner unless changed), and setup password. Use the computer's LAN address, never localhost or 127.0.0.1 on the phone. Open the server's /docs page in the phone browser to check reachability and certificate trust first. A connected phone does not mean the ESP32 or peripheral drivers are ready. Example origin: " + server),
            GuideStep("If it does not connect", "Timeout: check the computer is awake, the server listens on 0.0.0.0, private firewall rule, LAN address, and guest isolation. Certificate error: install your CA, match the URL exactly to a certificate host, and correct both clocks. Login rejected: use the owner password; local recovery is python -m backend.cli reset-password, which revokes phone sessions. Controller offline after login: follow the ESP32 guide.")
        )),
        ConnectionGuide("spotify", "Companion server: Spotify", "Alternative development route Â· server OAuth", listOf(
            GuideStep("What you need", "Finish Phone & local server first. You need internet, eligible Spotify developer access, a Premium account for playback control, and Spotify running on a phone, computer, or supported Connect speaker. The ESP32 is a controller, not a Spotify audio player. Development-mode app owners currently need Premium and new apps allow up to five authorized users; Spotify can change eligibility."),
            GuideStep("Register your application", "Open the Spotify Developer Dashboard, sign in, create or open your own application, and select Web API if asked. Copy its Client ID. Under its settings, register your server's exact HTTPS callback, including port and path: " + server + "/spotify/callback. Replace the example address with your actual LAN address. Add the Spotify account you will use under the app's user/access management if required in Development Mode."),
            GuideStep("Set server configuration", "In the PowerShell window that will start the backend, activate your virtual environment and set these values. Replace YOUR_CLIENT_ID and the example server URL. Generate the vault key once with the first command, save it privately, and use that same value across restarts. This integration uses PKCE; a Client Secret is not needed in the APK or ESP32.", "python -c \"import secrets; print(secrets.token_urlsafe(48))\"\n\$env:ROOM_SPOTIFY_CLIENT_ID = 'YOUR_CLIENT_ID'\n\$env:ROOM_SPOTIFY_REDIRECT_URI = '" + server + "/spotify/callback'\n\$env:ROOM_VAULT_KEY = 'PASTE_YOUR_GENERATED_KEY_HERE'"),
            GuideStep("Restart with those values", "Stop and restart only your own backend in that same PowerShell window so it inherits the variables. Ordinary \$env settings disappear when the window closes; restore them from a private secret store next time. Do not generate a new vault key for an existing linked account: the old key unlocks its encrypted refresh token. To test real Spotify alongside simulated room hardware you may add -Simulator.", "powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -ListenAddress 0.0.0.0 -Port 8443"),
            GuideStep("Authorize from Music", "In the main Music tab tap Connect Spotify. Sign in on accounts.spotify.com, review permissions, and approve. Complete this in the browser that can reach and trust your local server. The callback must load successfully. Return to Room and tap I've connected Â· refresh. Preview mode cannot link an account. The requested permissions cover playback state/control, private playlists, and recent listening; your Spotify password stays with Spotify."),
            GuideStep("Choose where audio plays", "Open Spotify on your desired playback device using the same account and play a track once. In Room, refresh playback, select the device under Play it here, then tap Select device. Test play/pause and volume. A Bluetooth speaker receives audio from your phone/computer after pairing there; it does not pair to this ESP32. For an IR speaker, learn volume commands in Remotes and choose speaker_ir in Settings â†’ Preferences."),
            GuideStep("Fix common Spotify errors", "Invalid redirect: compare dashboard and server URI character for character, including scheme, port and /spotify/callback. Phone callbacks cannot use localhost; HTTP is allowed by Spotify only for literal loopback addresses on the same machine. Expired authorization/state: start Connect again and finish within ten minutes. 403: check Premium, allowed user, API access and device restrictions. No active device: start music in Spotify first, then refresh. 429: wait for the cooldown. Tokens cannot be unlocked: restore the original vault key or authorize again."),
            GuideStep("Disconnect your account", "Signing out of Room only disconnects that phone. To revoke Spotify authorization, open your Spotify account's Apps page and remove access for your application. Restore authorization with Connect Spotify when needed.")
        ), listOf("Spotify Developer Dashboard" to "https://developer.spotify.com/dashboard", "Redirect URI rules" to "https://developer.spotify.com/documentation/web-api/concepts/redirect_uri", "Development mode requirements" to "https://developer.spotify.com/documentation/web-api/concepts/quota-modes", "Spotify account apps" to "https://www.spotify.com/account/apps/")),
        ConnectionGuide("esp32", "Companion server: ESP32 bridge", "Alternative development route Â· ESP-IDF firmware", listOf(
            GuideStep("Choose the matching bridge firmware", "Only for the companion-server route, use esp32-firmware/ with the optional local companion-backend bridge. Direct pairing.json support instead uses SmartRoomController_Complete_Source/SmartRoomController/. These APIs and identities differ. The ESP-IDF bridge profile targets a classic ESP32 DevKit / WROOM via esp32dev. Confirm your exact board before compiling or flashing; S2/S3/C3 variants need a matching profile."),
            GuideStep("Start with USB only", "Connect the board using a data-capable USB cable. In Windows Device Manager â†’ Ports (COM & LPT), note its COM port. If absent, check the cable and install the USB-serial driver matching your board's actual chip from its manufacturer. Initially leave LEDs, IR, microphone, and the panel disconnected. Power and module specifications must be checked before wiring."),
            GuideStep("Prepare a private build folder", "Run these commands from the project root. The source path contains spaces, so prepare an isolated path without spaces. The helper intentionally disables the bridge every time; use it only before configuring menuconfig, not again after. PlatformIO downloads its toolchain on first run.", "python -m pip install platformio==6.2.0\npowershell -ExecutionPolicy Bypass -File scripts/build-firmware.ps1 -PrepareOnly -BuildDirectory D:\\RoomEsp32Build\nCopy-Item -LiteralPath .certs/ca.pem -Destination D:\\RoomEsp32Build/main/backend_ca.pem\nSet-Location D:\\RoomEsp32Build\npython -m platformio run -e esp32dev -t menuconfig"),
            GuideStep("Enter the real connection details", "In menuconfig open Room development bridge. Enable Enable local companion-backend bridge. Set Local HTTPS backend origin to " + server + " (no trailing slash); Unique device key to the one printed by backend.cli setup; Home Wi-Fi SSID/password to your 2.4 GHz network; NTP server to pool.ntp.org or a reachable LAN NTP server. Save and exit. The public CA file copied above must be from this same backend installation. These settings enter the firmware: keep the build folder and binaries private. -BridgeCheck uses dummy credentials and cannot connect your controller."),
            GuideStep("Build, then flash your board", "Keep the backend running without -Simulator. Replace COM5 below with the actual port. Close other serial monitors first. Upload writes firmware to the selected USB board; inspect the board and port before running it. If upload cannot enter bootloader mode, use the BOOT/EN sequence specified for your board. Do not use an empty firmware file or the compile-check binary.", "python -m platformio run -e esp32dev\npython -m platformio run -e esp32dev -t upload --upload-port COM5\npython -m platformio device monitor --port COM5 --baud 115200"),
            GuideStep("Confirm the bridge is online", "Look for Wi-Fi/HTTPS or clock errors in the serial monitor. In Room â†’ More â†’ Settings, Connection should report a current firmware version, Wi-Fi RSSI and synchronized clock. Refresh Diagnostics. If offline, check SSID/password, 2.4 GHz, server origin, firewall, CA, NTP and device key. 127.0.0.1 on an ESP32 refers to the board, not the computer. Changing the backend device key requires updating firmware configuration and reflashing."),
            GuideStep("Understand what still needs hardware work", "This firmware advertises no peripheral capabilities until drivers are implemented and verified. A working heartbeat proves the Wi-Fi bridge only. LED output, IR learning/transmit, microphone capture/reactive DSP, OLED and on-device OTA are pending hardware integration; the app rejects unsupported actions. There is no implemented phone Wi-Fi provisioning or recovery AP wizard. Wi-Fi changes currently require USB reconfiguration and flashing. Do not advertise working capabilities just to hide an unavailable error.")
        ), listOf("PlatformIO ESP32 development board" to "https://docs.platformio.org/en/latest/boards/espressif32/esp32dev.html")),
        ConnectionGuide("hardware", "Lights, microphone & panel", "Approved pins & the remaining wiring details", listOf(
            GuideStep("Check parts before connecting power", "The approved pins below are an implementation map, not a verified circuit. You still need the exact ESP32 board variant, LED strip protocol/voltage/pixel count/current, microphone and OLED module specifications, IR driver/receiver circuit, supply and protection design. Switch off all supplies before wiring. Avoid feeding 5 V into a 3.3 V GPIO. Do not power a strip from an ESP32 GPIO."),
            GuideStep("Addressable lighting", "Approved LED data: GPIO 23. A verified circuit must provide the strip's rated external supply, adequate current capacity/protection, common signal ground, and level shifting/data protection where required by the strip. Pixel count, power injection and renderer must match the actual strip. The app controls the saved profile in Lights; the ESP-IDF bridge still needs its LED driver. The direct Arduino firmware requires a verified hardware profile and enabled LED driver."),
            GuideStep("IÂ²S microphone", "Approved BCLK 26, WS/LRCLK 25, microphone data into GPIO 35. Confirm the exact microphone's power voltage, ground, channel-select and timing requirements. GPIO 35 is input-only. Sound uses the room microphone for reactive lights; it is not a direct Spotify audio stream. The direct firmware requires the supported microphone driver to be enabled and tested; IÂ²S integration remains pending in the ESP-IDF bridge. Bass/Mids/Treble faders are 0â€“200% response weights, with 100% neutral; they do not equalize Spotify audio."),
            GuideStep("OLED and physical controls", "Approved OLED SDA/SCL: 21/22. Encoder A/B/push: 32/33/4. Buttons 1/2/3/4: 13/14/16/17. Check voltage, pull-ups, encoder detents/debounce and your board's reserved/boot pins before wiring; some ESP32 modules reserve GPIO 16/17. Rotation navigates/adjusts; short press selects; long press returns back/home. Buttons open Lighting, Music, Appliances and Favourites. The direct firmware supports its documented panel/OLED hardware after configuration; the ESP-IDF bridge still needs those adapters. Verify actual input, rendering and wiring."),
            GuideStep("Test one verified module at a time", "After the correct drivers and circuit are integrated, confirm the advertised capability and test each module independently before enabling routines. Start lighting at low brightness, inspect stability/current, calibrate the room microphone, then learn individual IR commands. An online controller or preview animation alone cannot verify the circuit.")
        ), listOf("Approved ESP32 GPIO constraints" to "https://docs.espressif.com/projects/esp-idf/en/stable/esp32/api-reference/peripherals/gpio.html")),
        ConnectionGuide("ir", "Fans, speakers & other remotes", "Learn original IR commands, then test & favorite", listOf(
            GuideStep("Check the appliance and IR hardware", "The appliance must accept infrared commands. Bluetooth, RF-only, Wi-Fi-only and mains relay devices are not supported by this IR flow. Approved transmitter driver GPIO: 18; receiver signal GPIO: 27. Use a suitable IR LED driver and voltage-compatible receiver with a verified circuit, not a bare high-current LED on a GPIO. Direct firmware learning/transmission require its verified IR driver to be enabled. The ESP-IDF bridge still needs physical IR integration."),
            GuideStep("Create an appliance", "Open Remotes â†’ Add appliance. Give it a recognizable name and choose fan, speaker, or other. Choose that appliance and tap Learn a command. Enter a specific label such as Fan power, Speed up, Speaker volume up, Volume down, or Mute."),
            GuideStep("Capture the original remote", "Mark Toggle / disruptive command for power toggles or other actions that should not repeat. Point the original remote at the receiver, tap Start capture, then press its button. On the companion route wait for Captured, inspect the protocol and save. Direct firmware saves a successful capture on the ESP32; close the dialog to refresh its library. If it times out, reposition the remote and retry. In preview the captured signal is synthetic."),
            GuideStep("Test once, then organize", "Aim the IR transmitter toward the appliance. Tap the saved command's transmit button once and observe the appliance. IR provides no state feedback, so Transmitted does not mean the appliance changed. Do not repeatedly retry a power toggle. Rename commands clearly, favorite frequent ones, and learn additional buttons separately."),
            GuideStep("Connect speaker volume and routines", "On the companion-server route, in More â†’ Settings â†’ Preferences select speaker_ir as Volume action target and save. Music â†’ Choose speaker IR command lets you select a learned speaker volume command; Spotify volume stays a separate control path. Create schedules in More â†’ Routines only after manual commands work. Choose the time zone and weekdays explicitly. Unsupported hardware errors require driver integration, not repeated capture attempts.")
        ))
    )
}

@Composable fun ConnectionGuides(endpoint: String, preview: Boolean) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    RoomCard {
        SectionLabel("Connect your room")
        Text(if (preview) "Read the guides in preview, then connect your real device. Start with your preconfigured ESP32; companion-server instructions cover the alternative development route." else "Use the guide matching your firmware: direct ESP32 pairing or the alternative companion-server route.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        connectionGuides(endpoint).forEach { guide ->
            OutlinedButton(onClick = { selected = guide.id }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(guide.title, fontSize = 14.sp)
                    Text(guide.summary, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
            }
        }
    }
    selected?.let { ConnectionGuideDialog(it, endpoint) { selected = null } }
}

@Composable fun ConnectionHelpButton(id: String, endpoint: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Icon(Icons.Outlined.HelpOutline, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (id.startsWith("spotify")) "How to connect Spotify" else "Connection guide")
    }
    if (open) ConnectionGuideDialog(id, endpoint) { open = false }
}

@Composable private fun ConnectionGuideDialog(id: String, endpoint: String, dismiss: () -> Unit) {
    val guide = connectionGuides(endpoint).first { it.id == id }
    val links = LocalUriHandler.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(guide.title) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            guide.steps.forEachIndexed { index, step ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${step.title}", color = Lime, fontSize = 15.sp)
                    SelectionContainer { Text(step.body, fontSize = 13.sp, lineHeight = 21.sp) }
                    if (step.command.isNotBlank()) SelectionContainer {
                        Text(step.command, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 19.sp, color = Lime)
                    }
                }
            }
            guide.links.forEach { (label, url) -> TextButton(onClick = { links.openUri(url) }) { Text(label + " â†—", fontSize = 12.sp) } }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}
