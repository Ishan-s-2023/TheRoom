# ESP32 migration: current Room controller → WLED with Room services

Prepared 21 October 2026 for Room Android 0.3.0.

## Deliverable and boundary

The user will update the ESP32 separately. This file specifies firmware work still required;
it is not a claim that stock WLED contains the Room features. No ESP32 was flashed for this integration.

Target: the **same classic dual-core ESP32, 4 MB flash**, running WLED as the application,
with the existing Room IR, Spotify, physical controls and combined routines added as usermods/adapters.
The Android app keeps Room's navigation and themes. Its complete WLED control panel is the
real web interface served by the device, with presentation-only Room styling; its quick controls
send real WLED commands. There is no bundled fake WLED engine or hardcoded effect catalogue.

Current canonical firmware baseline:
`SmartRoomController_Complete_Source/SmartRoomController/Config.h` reports `1.1.3-controls`.
Use the current files in that folder, not earlier copies in `app/`, old ZIP files or
`artifacts/spotify-latency-fix`. Recheck the version before implementing if another chat changes it.

WLED reviewed at `3cdb5108b924ec26dec81d2c92962b70b185bd01` (main/development).
This is a research reference, **not an instruction to deploy a development build**.
Choose an appropriate tagged release, pin its exact commit and module dependencies, and
verify the endpoints and hooks below against that release. Features depend on the selected build.

Sources:

- Firmware and web UI: https://github.com/wled/WLED
- Android integration reference: https://github.com/Moustachauve/WLED-Android
- JSON contract: https://kno.wled.ge/interfaces/json-api/
- WebSocket contract: https://kno.wled.ge/interfaces/websocket/
- AudioReactive: https://github.com/wled/WLED/tree/main/usermods/audioreactive
- Usermod hooks: https://github.com/wled/WLED/blob/main/wled00/um_manager.cpp

## 1. Architecture: one owner for each subsystem

Use WLED's `setup()`/`loop()` and lifecycle. Do **not** compile the old Room sketch's
entry points as a second application. Introduce a Room usermod with setup, nonblocking loop,
configuration, state and button callbacks.

| Subsystem | Owner after migration | Work required |
| --- | --- | --- |
| LED driver, rendering, segments, transitions, palettes, effects | WLED | Remove Room's separate LED output path; route all requests to WLED |
| I²S acquisition, FFT and sound-reactive effects | WLED AudioReactive | Remove Room's second I²S/audio task; adapt telemetry/tuning |
| Wi-Fi, setup AP, hostname, mDNS | WLED | Replace Room's competing connection/retry/AP management |
| Clock/NTP/timezone | One shared clock controlled by WLED | Adapt Room settings; no concurrent SNTP restart logic |
| Appliance IR learning/transmitting/library | Room usermod | Port validation, queues, carrier metadata, repeat policies |
| Spotify OAuth refresh/playback/browser data | Room worker | Port current fixes; bound work and keep it off the render loop |
| OLED, encoder, four shortcuts | One Room panel adapter | Reuse or adapt WLED display/encoder modules, with one input/display owner |
| Mixed lighting/IR/Spotify automations | Room routine adapter | Dispatch lighting through WLED, other actions through their workers |
| WLED lighting presets/playlists | WLED filesystem | Convert old lighting presets with an explicit migration map |
| Room appliance/rule data | Room namespaced storage | Retain compatible validation and atomic writes |
| Firmware update | One coordinated update implementation | Preserve Room protections; prevent an uncoordinated second OTA path |

Do not copy the whole old `RoomApp::begin()`/`tick()` unchanged. They initialize resources
that WLED also owns. Reserve GPIOs through WLED's pin manager and account for RMT/I²S,
interrupt and task resources, not only duplicate pin numbers.

## 2. File-by-file changes from the current codebase

| Current Room source | Required migration |
| --- | --- |
| `SmartRoomController.ino`, `Application.cpp` | Replace entry-point ownership with WLED; instantiate Room through a usermod |
| `RoomApp.h` | Remove Room pixel framebuffer/LED driver ownership and duplicate mic acquisition state. Retain bounded job, appliance, music and panel service state |
| `RoomApp.cpp` | Split lifecycle from data/commands. Preserve Room state and command contract; project live lighting from WLED. Delegate Wi-Fi and time changes to the shared owners |
| `Hardware.cpp` — `setupHardware`, `lightingTick`, `audioTask` | Delete/disable duplicate LED and I²S initialization/rendering; bridge WLED state and AudioReactive data instead |
| `Hardware.cpp` — IR/learning/send worker | Port appliance capture and transmission with pin/resource arbitration; keep capture and transmit mutually coordinated |
| `Hardware.cpp` — `action`, `event`, `automationTick` | Translate legacy lighting actions to WLED state/preset calls. Retain validated IR references and guarded routine execution |
| `Panel.cpp` | Drive existing approved menus using WLED lighting state and Room IR/music services; do not run two encoder decoders or OLED renderers |
| `Network.cpp` | Retain authenticated Room HTTPS API on 443 alongside WLED HTTP/WS on 80. Check server sockets, tasks and heap; do not put Room secrets on WLED's public JSON endpoints |
| `Spotify.cpp` | Preserve the latest 1.1.3 controls fixes and earlier TLS/HTTP framing fixes. Retain worker priority, bounded waits, queue expiry, rate-limit handling and job outcomes |
| `SpotifyData.cpp/.h` | Keep bounded browse/search/device parsing and current field compatibility |
| `ClockConfig.cpp/.h`, `WifiRetry.h`, `connectivity.*` | Remove conflicting runtime ownership. Translate Room settings into the selected WLED release's network/time mechanisms |
| `room_core.*`, `DatabaseValidation.*`, `state_store.*` | Keep compatible Room data validation; add a versioned WLED preset/routine conversion; remove duplicate lighting persistence as authority |
| `OtaHealth.cpp`, `RuntimePolicies.h`, `JobData.h` | Port health checks, operation IDs, boot IDs, guarded repeat/update policies and protected-storage decisions explicitly |
| `Diagnostics.cpp` | Report actual WLED/Room initialization and runtime status; never synthesize successful driver flags |
| `Config.h`, `hardware_profile.hpp` | Keep the verified physical mapping; translate into WLED/module configuration, adjust capacities after measuring the combined binary and heap |
| `DeviceSecrets.h` | Keep private and preserve identity. Never paste its contents into documentation, repositories, WLED config exports or public build logs |

The old project uses ArduinoJson APIs that may differ from the chosen WLED dependency.
Resolve that at the adapter boundary; do not introduce two incompatible global ArduinoJson
versions or assume the sketches can simply be linked together. Likewise reconcile Arduino-ESP32,
ESP-IDF, IR and display library versions in one PlatformIO environment.

## 3. Endpoints required by Room Android 0.3.0

### WLED service: HTTP port 80

| Method/path | Used for |
| --- | --- |
| `GET /` and its local assets | Complete WLED controls: colours, effects, segments, presets, playlists, live preview |
| `GET /json/si` | Native quick controls and identity validation; JSON must contain `state` and `info` |
| `POST /json/state` | Real power, brightness and preset actions, followed by authoritative state readback |
| WebSocket `/ws` | State/info updates using WLED's normal JSON protocol |
| `GET /presets.json` | Native Home preset shortcuts; full preset management stays in WLED's panel |
| `/settings` and `/settings/*` | All settings available in the selected firmware: LEDs, sync, MQTT, time, security, UI, usermods, Wi-Fi, etc. |
| `/settings/um` | AudioReactive and other module settings |
| `/settings/time` | WLED schedules and shared clock configuration |
| `/settings/sec`, `/update`, associated upload/download routes | Backups, restores and firmware update UI, subject to the unified update policy |

Quick controls need `state.on` boolean and `state.bri` integer 0–255.
Identity checks need `info.ver`, `info.leds` object and `info.mac`.
Keep `info.mac` stable across DHCP address changes; don't substitute a generated session identifier.
Use normal WLED preset IDs 1–250 and the `ps` command. Do not hardcode effect numeric IDs
from the old Room effects or from a different WLED version.

Advertise `_wled._tcp.` with the actual HTTP service port so Android discovery works.
Keep the WLED setup AP route at `4.3.2.1` if using WLED's default. The app also accepts
a manually entered address/port and silently probes the Room HTTPS host's HTTP port for
same-board discovery. No WLED `pairing.json` import is required.

Examples:

```json
{"on":true,"bri":128,"v":true}
```

```json
{"ps":3,"v":true}
```

Do not translate these into Spotify/IR jobs. Lighting is a WLED state change, and its
readback is independent of a network-dependent music action.

### Room service: pinned HTTPS port 443

Retain these current routes, request/response fields and authentication for the existing
Remotes, Music, Routines and System screens:

- `GET /v1/state`
- `GET /v1/backup`
- `GET /v1/jobs`
- `POST /v1/command`
- `POST /v1/ota` with the existing signed-manifest/body protocol, if retained

Keep `op=job`/`jobs`, Spotify and IR response `job_id`/`boot_id`, and terminal states.
An accepted/queued job is not a successful appliance/playback action.
Retain the existing certificate, private key and owner token if the current APK's
Room connection is to continue working. Replacing those requires an explicit pairing migration;
disabling certificate checks is not a migration solution.

Consult the current `API.md`, `DeviceTransport.kt`, `DeviceJobs.kt` and `RoomModel.kt`
for complete schemas. Keep the old fields used by those screens until their migration is deliberate.
Optionally publish WLED capability/version details in the Room runtime object, but
do not expose credentials in `info`, WebSocket messages, presets or downloadable WLED config.

## 4. Hardware configuration to preserve and verify

| Signal | Current approved GPIO |
| --- | --- |
| LED data | 23 |
| Mic BCLK / WS / data | 26 / 25 / 35 |
| IR TX driver / RX | 18 / 27 |
| OLED SDA / SCL | 21 / 22 |
| Encoder A / B / push | 32 / 33 / 4 |
| Lighting / Music / Appliances / Favourites buttons | 13 / 14 / 16 / 17 |

WLED AudioReactive supports INMP441; configure its actual I²S pins/channel. Its documented
default pins and the encoder module's defaults **do not match this board** and can conflict
with our buttons/IR. Override them before enabling modules.

The current software plans 300 addressable pixels for an approximately 15 m strip with
three physical LEDs per pixel. Confirm installed count, chipset, voltage, colour order,
level shifting and power injection before enabling output. Configure WLED's current estimate
for the actual strip; it is not measured overcurrent protection. Existing approved GPIOs
are a software allocation, not proof of completed wiring. Confirm board-specific use of 16/17.

## 5. Lighting, sound and data conversion

1. Export a private Room backup and inventory preset/rule IDs before changing firmware.
2. Map Room brightness 0–100 to WLED brightness 0–255; convert RGB HEX to WLED colour arrays.
3. Convert named Room presets to WLED presets with a persistent old-ID → new-ID table.
4. Map old effects by intended behaviour using the actual effect list from the selected
   WLED build; preserve an explicit report for unsupported mappings. Do not pretend Beat,
   Bass, Level and Bands are identical to arbitrary WLED effect IDs.
5. Convert lighting routine actions into WLED preset/state operations. Update references
   transactionally; validate segment indices and lengths after LED count changes.
6. WLED becomes the authority for lighting state. Changes from its web UI, other apps,
   buttons, IR or schedules must also appear in Room's projected lighting state.
7. Use AudioReactive for sound acquisition and FFT. Its gain, AGC, squelch and timing
   values are not direct copies of Room's old percentage/fraction fields.
8. Port our custom quiet timeout/off/dim/hold behaviour and exact bass/mid/treble weighting
   only if required, using WLED audio data and state/preset calls. Keep one audio engine.
9. Our Android Sound page now opens WLED usermod settings. Ported custom sound controls
   must be exposed there or intentionally added to Room later; old Room tuning is not
   silently advertised as functional WLED calibration.
10. Preserve IR metadata/raw timing/repeat safety, appliance IDs, favourites and rule references.
    IR send success means a waveform was transmitted, not that the appliance acknowledged it.

## 6. Keep Spotify independent of LED responsiveness

- Port the **current** Spotify implementation, including 1.1.2 empty-request `Content-Length: 0`
  framing and 1.1.3 controls changes, not just the older responsive snapshot.
- Preserve validated Spotify root certificates, token refresh, TLS verification and shared
  valid-time prerequisite. Never switch to insecure HTTPS to work around NTP.
- Keep TLS handshakes/HTTP/parsing off WLED's rendering and web-server callbacks.
- Bound each network operation, cap response/JSON sizes, drain user commands before idle polling,
  keep the existing stale-command expiry and do not automatically repeat disruptive actions.
- Report queued/running/succeeded/failed with boot-qualified IDs, including meaningful
  token, network, unavailable-device and rate-limit failures.
- Preserve eligibility/device checks, browser PKCE flow, track/device/search data and IR volume
  distinction. ESP32 is a playback **controller**, not a Spotify audio-streaming speaker.
- Local lighting/audio effects must remain usable without Internet or a synchronized clock;
  Spotify and wall-clock schedules must report their own missing prerequisites.

## 7. Shared settings, storage, update and recovery

- WLED owns Wi-Fi/AP/mDNS and system time. Route old Room Wi-Fi/clock commands to that
  owner or return a specific unsupported response. Do not let Room's retry logic close WLED's AP.
- Reserve distinct file namespaces for Room data and WLED config/presets. Mount the filesystem
  once through the selected firmware owner; never format automatically on mount failure.
- Keep a full private backup before any partition-layout change. A WLED binary is not a drop-in
  signed Room update. Verify flash size, app slot capacity, filesystem size and actual partition
  table before choosing a flashing command. Do not reuse old offset assumptions without checking.
- Preserve NVS secrets and existing appliance/rule data or perform a documented export/import.
  Never erase flash as an incidental installation step.
- WLED's stock HTTP/WS path is separate from Room's pinned HTTPS path. Never send Room's
  bearer token, Spotify refresh token, Wi-Fi password or signing material to WLED JSON endpoints.
- Reconcile WLED's OTA page and ArduinoOTA with Room's signed-update, physical approval,
  counter and post-boot health policy. Do not leave an unprotected alternate updater bypassing
  a retained policy. If the final build changes that policy, document it explicitly in its release.
- Custom Room usermods must be included in replacement firmware. Installing a generic WLED
  release can remove Room services. Use the verified **combined** binary in the update page;
  do not auto-select a stock binary just because its board family matches.
- Measure worst-case free/minimum heap, task stacks, sockets and frame timing while microphone,
  web UI/WS, Spotify TLS and IR operate together. A stock WLED compile alone is not proof
  that the combined application fits a 4 MB board with OTA.

## 8. Acceptance tests before calling it complete

Record real results and the exact firmware commit/build, not only compilation success.

- [ ] WLED plus required usermods builds for the exact board and fits both configured OTA slots.
- [ ] Existing identity, Room appliance library and rules survive migration and reboot.
- [ ] Room discovers `_wled._tcp.` or connects by IP without selecting a pairing file.
- [ ] Room HTTPS services still authenticate with the existing embedded identity.
- [ ] Native power/brightness/preset actions reach WLED and agree with `/json/si` readback.
- [ ] WLED panel changes propagate to native Home controls and other WS clients.
- [ ] Colour/palette/effect sliders, segment edit/create/delete, preset save/delete,
      playlists and live preview all operate on the actual LED output.
- [ ] LED and microphone settings use the approved pins; actual sound causes reactive output.
- [ ] Device settings, timers, backup download and restore upload operate through the Room panel.
- [ ] WLED setup Wi-Fi works on Android with mobile data present and no Internet on the AP.
- [ ] IR learning/relearning/cancel/timeout and explicit transmit work with a real appliance;
      toggle/repeat policies remain intact.
- [ ] Encoder, OLED, all four shortcuts and their approved press/navigation behaviours work.
- [ ] Lighting and microphone continue responding during a failed or slow Spotify request.
- [ ] Spotify pause/play/next/previous/volume each produces a terminal operation result and
      verified playback behaviour; unavailable Internet/time/device yields a bounded error.
- [ ] Combined routines use WLED for lighting, retain IR/Spotify actions and do not double-run.
- [ ] Clock/timezone, Wi-Fi changes, reboot restoration and pause/resume automation behave correctly.
- [ ] Combined firmware update, signature/approval checks if retained, post-boot health,
      failed-update recovery and data restoration are tested deliberately.

## 9. App verification boundary

Room's WLED controls make real API calls and load the live controller interface. They cannot
make WLED features exist on firmware that only serves the old Room API. Before this firmware
migration, the existing board is expected to fail WLED identification; that must remain an
honest connection error, not a simulated successful controller.

App unit/build/emulator tests cover software integration, not physical strip output,
appliance reception or a live Spotify account. The completed app release notes should state
which of the hardware acceptance checks above were actually run.
