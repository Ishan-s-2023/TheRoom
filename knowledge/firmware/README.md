> **Delivery V6; current source: 1.1.4-music.** See [V6 integration notes](RELEASE_V6_1_1_4_MUSIC.md) and [build verification](BUILD_VERIFICATION.md). No upload performed.

> **Previous source: 1.1.3-controls.** See [RELEASE_1_1_3_CONTROLS.md](RELEASE_1_1_3_CONTROLS.md) for control-response handling, live NTP findings and post-upload playback checks.

> **Earlier request-framing patch: 1.1.2-http.** See [RELEASE_1_1_2_HTTP.md](RELEASE_1_1_2_HTTP.md) for the observed Spotify HTTP 411 failure and request-framing fix. Existing responsive-worker and NTP fixes are retained.

> **Earlier clock-patch record: 1.1.1.** See [RELEASE_1_1_1.md](RELEASE_1_1_1.md) for the NTP lifetime/reconnection fix. See [RELEASE_1_1_0.md](RELEASE_1_1_0.md), [COMPLETION_TODO.md](COMPLETION_TODO.md), [REQUIREMENTS_COVERAGE.md](REQUIREMENTS_COVERAGE.md), [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md) and [SECURITY_PROFILE.md](SECURITY_PROFILE.md). Keep the existing per-device identity and partition layout when upgrading.

# SmartRoomController — Arduino application

This is the application source, replacing the earlier diagnostic sketch. `setup()`
starts `RoomApp`; `loop()` services panel input, IR, rules, lighting and network
recovery. Hardware, panel, security and Spotify code live in companion `.cpp` files.
Open `SmartRoomController.ino` with the entire folder intact.

## Implemented code paths

- Approved GPIO/control mappings; EC11 quadrature interrupts and debounced buttons.
- SSD1306 menus, value editing, shortcuts, library/favourites, learning flow,
  confirmations, track/artist/device display and configurable display timeout.
- WS2811/WS2812B output for 300 pixels; colour/brightness/presets; beat, bass, level
  and 16-band spectrum effects with gamma correction and configured current-budget estimation.
- INMP441 I²S acquisition, FFT, noise-floor calibration, automatic/manual gain,
  filtering, beat detection and dim/off/hold quiet behavior.
- IR decoded metadata plus raw capture/replay, named appliances/commands,
  separately learned repeat frames, favourites, overflow/timeout diagnostics,
  and asynchronous RMT transmission with a verified carrier input.
- Transactional JSON snapshots with a previous copy, persistent IR/presets/rules,
  validated restore and optional encrypted client backups.
- Daily/selected-weekday rules and sound/IR events; deliberate arming and no catch-up IR.
- Local TLS API, unique token/AP credential, failure lockout, setup and recovery AP.
- Signed OTA application upload, digest verification and increasing versions.
- Spotify PKCE helper, token refresh, playback, volume, devices, playlist/recent
  browsing, search, playback transfer and rate-limit feedback.

**Implemented code is not proof of hardware compatibility or perfect behavior.**
No real strip, microphone, OLED, remote, Spotify account or Android device has
been exercised here. Check BUILD_VERIFICATION.md for exactly what compiled/tested.

## Hardware configuration

The GPIOs and control actions in `hardware_profile.hpp` are approved. Candidate
module identities and electrical parameters were not all confirmed. Consequently,
physical drivers are initially disabled in persisted configuration; they are fully
implemented and enabled by the confirmed `hardware` command, rather than by
editing dummy placeholder functions. See API.md for fields and supported families.

Lighting is confirmed as ~15 m, 60 LEDs/m and three LEDs/pixel: 20 pixels/m, 300
pixels at exactly 15 m. Verify the installed length, voltage, protocol, RGB order,
driver interface, supply/injection plan and per-pixel draw before enabling output.
The controller does not power the strip. IR requires a suitable LED driver.

## Arduino build

Test target: original ESP32, **ESP32 Dev Module**, Espressif core **3.3.1**.
Library versions: ArduinoJson 7.4.3, Adafruit NeoPixel 1.15.5, Adafruit SSD1306 2.5.17,
Adafruit GFX 1.12.6, Adafruit BusIO 1.17.4, arduinoFFT 2.0.4, IRremote 4.7.1.
Install these through Library Manager. On this machine the old OneDrive
sketchbook contains unreadable library placeholders. Set File > Preferences >
Sketchbook location to a local folder before installing; the copies used for
verification are in `D:/PC DEFAULT/Room Automation/.arduino-deps/libraries`.
Selecting `D:/PC DEFAULT/Room Automation/.arduino-deps` as the sketchbook location
lets this installation use those readable copies. Alternatively, `tools/build.ps1`
uses isolated dependencies without changing your IDE settings.
Do not install IRremoteESP8266 in place of
IRremote; this code uses Arduino-IRremote's current API.

The complete application needs a larger app partition than the diagnostic sketch.
For a **verified 4 MB flash board**, select **Minimal SPIFFS (1.9MB APP with OTA)**.
LittleFS uses the filesystem partition despite its SPIFFS label. Do not use the
No OTA partition scheme if OTA is wanted. A different flash size needs a reviewed
partition table. No physical flash capacity has been assumed or verified here.

## First device setup

1. Install Python tool dependencies: `python -m pip install -r tools/requirements.txt`.
2. Run `python tools/prepare_device.py` once. It creates unique credentials,
   certificate/private identity and OTA signing keys. Keep `DeviceSecrets.h` and
   `.device-private/` private; the public ZIP deliberately excludes them.
3. Compile in Arduino IDE with all companion files. Upload only to the intended
   ESP32 after confirming board/flash settings. Uploading replaces its current app.
4. Open Serial Monitor at 115200 baud. If the data partition is new/unformatted,
   send `{"op":"format_storage","confirm":true}`. Existing data is never
   silently formatted on a mount error.
5. Use the `hardware` command in API.md to select the verified modules. Save and
   reset the controller to initialize those drivers. USB JSON works before Wi-Fi.
6. Join the unique setup SSID using the password in the private pairing file.
   Use the pinned HTTPS client to provision Wi-Fi (a JSON file keeps credentials
   out of process command lines). Recovery uses the same protected AP if the
   router fails. Once home connectivity returns, the AP stops.
7. Set an approved POSIX timezone and NTP server, then configure/arm rules.
8. Register a Spotify developer app/approved account and the exact loopback URI
   `http://127.0.0.1:8765/callback`. Run `tools/spotify_authorize.py --client-id ...
   --host <device-LAN-IP>`. Account/API/device restrictions remain external limits.

Client examples, from the sketch folder:

```powershell
python tools/device_client.py --port COM5 --json my-command.json
python tools/device_client.py --host 192.168.4.1 --json wifi-private.json
python tools/device_client.py --host DEVICE_LAN_IP --backup room-backup.json --encrypted
python tools/device_client.py --host DEVICE_LAN_IP --restore room-backup.json --encrypted --confirm
python tools/device_client.py --host DEVICE_LAN_IP --ota application.bin --version 2
```

For OTA, approve the short maintenance window on the panel first. Keep the signing
private key off the ESP32 and retain a USB recovery path. Secure boot, flash
encryption/eFuses and hardware-specific automatic rollback are not enabled here.
NVS credentials/compiled identity are not protected against physical flash extraction
without separately configured device encryption.

## Boundaries requiring real acceptance tests

IR carrier frequency is a verified input, not measured by a demodulated receiver.
Stateful/rolling and unusual protocols may not replay reliably. Raw hold learning
stores a repeat frame, not arbitrary indefinitely long button sequences. Disruptive
commands are never blindly retried. The selected font renders ASCII rather than
complete Unicode track titles. The hardware implementation uses a global POSIX
timezone and selected-weekday recurrence; calendar/one-off rule UX was not specified.

The separate existing Android/server folders are not altered or declared compatible
with this device protocol. Their Android transport still needs integration with
API.md. This codebase does not manufacture Spotify eligibility, electrical
verification or real-device test results.

Sources: [Arduino-IRremote](https://github.com/Arduino-IRremote/Arduino-IRremote),
[Espressif HTTPS server](https://docs.espressif.com/projects/esp-idf/en/v5.5/esp32/api-reference/protocols/esp_https_server.html),
[Spotify PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow),
[Spotify API restrictions](https://developer.spotify.com/documentation/web-api/references/changes/february-2026).
