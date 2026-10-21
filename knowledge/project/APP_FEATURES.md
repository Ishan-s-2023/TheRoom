# Room app — complete feature catalogue

Current lighting integration (21 October 2026): Room Android 0.3.0 uses WLED for
live lighting through its API and its controller-served interface styled to Room.
See `docs/WLED_INTEGRATION.md` for the current coverage and acceptance limits,
and `artifacts/ESP32-WLED-MIGRATION.md` for the firmware changes still required.
The older status tables below also describe legacy backend/device paths; they do
not establish that the combined WLED/Room firmware has been implemented or flashed.

Created from a new file on 20 October 2026. This catalogue groups the Android app,
local backend, controller firmware, physical controls and connection requirements.
It describes both the available software and the features that still need integration
or verification. It does not certify the finished hardware.

## 1. Reading the feature status

| Label | Meaning |
| --- | --- |
| App | Implemented in the current native Android interface; some actions require the backend or a capable device. |
| Backend | Implemented in the local companion service used by the current Android app. |
| Device | Implemented in the newer Arduino firmware source; compiled, but not tested on physical hardware. Its API still needs Android integration. |
| Required | Confirmed requirement whose complete user workflow or integration remains unfinished. |
| Conditional | Depends on actual modules, electrical configuration, account eligibility, network or appliance support. |

Multiple labels identify the layers involved, not proof of an end-to-end physical test.

### 1.1 Current architecture and integration boundary

- **Current Android path:** Android app → local companion backend → optional ESP-IDF bridge.
  This path currently needs a local computer/host. The ESP-IDF bridge does not implement
  the complete physical peripheral drivers.
- **Newer standalone firmware path:** Android transport still to be adapted → ESP32
  Arduino HTTPS API → implemented device drivers. This firmware has its own token,
  certificate, command schema, settings schema and OTA signing protocol.
- These paths are separate implementations. Their matching screen labels do not make
  their protocols interchangeable. Direct-device pairing, field conversion, status
  handling, backup formats and OTA must be integrated explicitly.
- The Android/backend coverage matrix predates the newer Arduino application. Read
  each firmware's own verification record when assessing device-side implementation.

## 2. App access, navigation and interface

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| APP-01 | Native Android application | App: Kotlin/Jetpack Compose; Android 8.0/API 26 and newer. |
| APP-02 | Home navigation | App: Home, Lights, Remotes, Music and More bottom tabs. |
| APP-03 | Additional categories | App: More opens Routines, Sound and Settings/System. |
| APP-04 | Android Back navigation | App: return from nested More pages, or return to Home from another tab. |
| APP-05 | Scrollable feature pages | App: independent page scroll state and scrollable routine editor. |
| APP-06 | Visual themes | App: Forest, Midnight, Ember, Pearl and System choices; persistent selection updates the whole interface and system bars. |
| APP-07 | Server connection form | App: enter local server URL, owner username and password. |
| APP-08 | Interface preview | App: “Explore the interface” works without a server, ESP32 or account. |
| APP-09 | Explicit review context | Development viewer identifies preview data/no physical outputs; the in-app simulation banner was removed at the user's request. |
| APP-10 | Preview interactions | App: in-memory lighting, presets, appliances, routines, playback/devices/browsing, simulated IR learning, diagnostics and clearly marked sample backup export; no physical commands or real Spotify authorization. |
| APP-11 | Operation feedback | App: busy indicators, notices, validation messages, error feedback and disabled controls where applicable. |
| APP-12 | Empty states | App: guidance when no presets, appliances, commands, routines or playback are available. |
| APP-13 | Confirmation dialogs | App: deletion, routine pause, global automation pause, restore, OTA submission and sign-out. |
| APP-14 | App version | App: version shown in System. |
| APP-15 | Localhost UI viewer | Development: browser streams and controls the actual native APK on the dedicated RoomQA emulator; screen shortcuts, state scenarios, keyboard input and private review notes. See docs/UI_REVIEW.md. |
| APP-16 | Requested component styles | App: native colour area/HSV inputs, liquid-style switches, continuous brightness control and Slider05-style volume; typed React references are included in components/ui. |

## 3. Home dashboard and quick functions

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| HOME-01 | Controller connection summary | App, Backend: connected/offline and interrupted-connection feedback. |
| HOME-02 | Lighting overview | App: saved colour, brightness percentage, power and selected effect. |
| HOME-03 | Brightness control | App: interactive liquid-style brightness slider with numeric percentage; commits on release. |
| HOME-04 | Quick lighting power | App, Backend: send power change from Home. |
| HOME-05 | Lighting shortcut | App: open full Lights settings. |
| HOME-06 | Quick presets | App: apply up to the first three saved presets shown on Home. |
| HOME-07 | Music shortcut | App: open Spotify and playback-device controls. |
| HOME-08 | Favourite IR shortcuts | App: send saved favourite commands directly from Home. |
| HOME-09 | Honest state display | Backend, Required: saved lighting is desired configuration; IR transmission is not confirmed appliance state. Full physical-state acceptance remains pending. |

## 4. Lighting functions

### 4.1 Manual lighting and presets

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| LIGHT-01 | Addressable lighting | Required, Device, Conditional: replaces the Gesto/Magic Home lighting integration requirement. |
| LIGHT-02 | Strip size definition | Confirmed: approximately 15 m, 60 physical LEDs/m, three LEDs/pixel; 20 pixels/m and 300 pixels/900 LEDs at exactly 15 m. Verify installed length/count. |
| LIGHT-03 | Power on/off | App, Backend, Device: manual lighting power selection. |
| LIGHT-04 | Brightness | App, Backend, Device: 0–100% setting. |
| LIGHT-05 | Colour palette | App: selectable swatches and continuous saturation/brightness colour area with rainbow hue control. |
| LIGHT-06 | Custom RGB colour | App, Backend, Device: validated six-digit HEX colour; native hue/saturation/brightness numeric inputs. React reference additionally supports alpha. |
| LIGHT-07 | Colour preview | App: preview selected colour before applying. |
| LIGHT-08 | Explicit apply | App: edit a lighting draft, then apply; loading a preset on Lights first loads its values into that draft. |
| LIGHT-09 | Save named preset | App, Backend, Device: persist colour, power, brightness, effect, speed and intensity together. |
| LIGHT-10 | List and recall presets | App, Backend, Device: load/apply saved configurations. |
| LIGHT-11 | Delete preset | App, Backend: deliberate confirmation before removal. Device API can delete collection entries with confirmation. |
| LIGHT-12 | Physical favourites | Device: save current lighting as an automatically named favourite; recall from lighting/favourites menus. Custom naming is a client/API function. |
| LIGHT-13 | Restore last lighting configuration | Backend, Device, Conditional: persist settings across restart; actual output depends on configured drivers. |
| LIGHT-14 | Full preset editor | App: independently edit preset name, power, colour, brightness, automatic music mode, effect speed/intensity; create or update without applying lights. |
| LIGHT-15 | Manual versus automatic colour | App: manual picker available only with Move with music off; the same rule applies inside the preset editor. |

### 4.2 Effects and audio response

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| EFFECT-01 | Solid colour mode | App, Backend, Device: non-reactive steady colour. |
| EFFECT-02 | Beat mode | App, Backend, Device: room-microphone beat/pulse response. |
| EFFECT-03 | Bass mode | App, Backend, Device: low-frequency response. |
| EFFECT-04 | Level mode | App, Backend, Device: overall sound-level response. |
| EFFECT-05 | Bands mode | App, Backend, Device: bass, midrange and treble response across addressable pixels. |
| EFFECT-06 | Response speed | App, Backend, Device: 0–100 control; Arduino implementation adjusts pulse decay. |
| EFFECT-07 | Effect intensity | App, Backend, Device: 0–100 response strength. |
| EFFECT-08 | Microphone-driven effects | Required, Device: listen to sound in the room independently of Spotify's playback metadata. |
| EFFECT-09 | Configurable quiet behaviour | App, Backend, Device: turn off, dim, or hold when the room becomes quiet. |
| EFFECT-10 | Configurable quiet timeout | App, Backend: 1–300 seconds before quiet behaviour; separate from OLED timeout. |
| EFFECT-11 | Hardware enablement | Device, Conditional: physical LED output starts disabled until a verified profile is explicitly supplied. |
| EFFECT-12 | Supported driver families | Device, Conditional: WS2811/WS2812B implementation, selectable supported data rate and RGB/GRB/BRG ordering; must match actual strip. |
| EFFECT-13 | Software current budget | Device, Conditional: brightness budget using verified full-white current per pixel; an estimate, not measured electrical current limiting. |
| EFFECT-14 | Advanced effects scope | Required: configurable selected effects; additional animations are not silently included. Ambient transitions and strobe were not selected. |

## 5. Microphone and sound functions

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| SOUND-01 | Room microphone input | Required, Device, Conditional: INMP441 I²S implementation; actual module/channel/supply still to verify. |
| SOUND-02 | Automatic sensitivity | App, Backend, Device: automatic gain/sensitivity mode. |
| SOUND-03 | Manual sensitivity | App, Backend, Device: manual override; Android range 1–100. |
| SOUND-04 | Noise floor | App, Backend, Device: suppress unwanted reactions to background sound. |
| SOUND-05 | Smoothing | App, Backend, Device: tune changes in the audio response. |
| SOUND-06 | Quiet-room calibration | App, Backend: request calibration; Device: five-second quiet-room calibration implementation. |
| SOUND-07 | Calibration from panel | Device: start calibration locally with a quiet-room prompt. |
| SOUND-08 | Frequency-band display | App: bass/mids/treble telemetry bars; live values require connected device telemetry. |
| SOUND-09 | DSP and beat detection | Device: I²S acquisition, FFT, filtering, band levels and beat detection code. |
| SOUND-10 | Audio setting persistence | Backend, Device: save tuning and quiet policy. |
| SOUND-11 | Audio schema conversion | Required: Android/backend noise floor and smoothing use 0–100 integers; Arduino uses fractional fields. Direct-device integration must convert and validate them. |
| SOUND-12 | Independent local response | Required, Device, Conditional: reactive lighting can run without the phone or internet once hardware is configured. |

## 6. Appliances, IR remote and learning functions

### 6.1 Appliance and command library

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| IR-01 | Expandable appliance library | App, Backend, Device: user-created compatible IR appliances, not only a fixed fan/speaker list. |
| IR-02 | Appliance categories | App, Backend: fan, speaker and other. |
| IR-03 | Add and name appliance | App, Backend; Device API and panel can create an appliance. Panel uses an automatic name. |
| IR-04 | Select appliance | App: filter commands by the selected appliance. |
| IR-05 | Rename appliance | App, Backend; Device API: replace the corresponding library object. |
| IR-06 | Delete appliance | App, Backend: confirmed removal with commands; referenced automation must be resolved. Device API rejects dangling references. |
| IR-07 | Named command library | App, Backend, Device: commands belong to an appliance. |
| IR-08 | Rename command | App, Backend; Device API: update saved command metadata. |
| IR-09 | Favourite command | App, Backend, Device: toggle favourites for quick access. |
| IR-10 | Delete command | App, Backend, Device: explicit confirmation; prevent invalid rule references. |
| IR-11 | Manual transmit/test | App, Backend, Device: send a selected saved signal once. |
| IR-12 | Toggle/disruptive marking | App, Backend, Device: flag commands that must not be blindly repeated. |
| IR-13 | Protocol metadata | App, Backend, Device: retain supported protocol/decoded information and carrier metadata. |
| IR-14 | Raw timing storage | Backend, Device: preserve validated raw mark/space sequences for supported signals. |
| IR-15 | Separately captured repeat frames | Backend stores repeat timings; Device can learn/replay separately captured repeat frames. Android has no dedicated verified-repeat editor/button. |
| IR-16 | Safe repeat gating | Device: repeat requires a non-toggle command explicitly marked repeat-safe; no universal hold/repeat guarantee. |
| IR-17 | Transmission result | Backend: job acknowledgement; Device: busy/last-transmitted identifiers. Neither proves that the appliance responded. |

### 6.2 Guided learning

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| LEARN-01 | Learning instructions | App: name the command, select disruptive/toggle behaviour and point the original remote at the receiver. |
| LEARN-02 | Start capture | App, Backend, Device: begin a bounded learning session. |
| LEARN-03 | Listening feedback | App: progress and capture state polling. Device panel shows a capture prompt. |
| LEARN-04 | Captured-data feedback | App: protocol and decoded capture information. |
| LEARN-05 | Timeout/failure feedback | App, Backend, Device: report no capture, invalid signal or unsupported/overflow conditions where available. |
| LEARN-06 | Retry capture | App: explicit user retry rather than repeated disruptive transmission. |
| LEARN-07 | Save capture | App, Backend: save the captured signal to the selected appliance. |
| LEARN-08 | Relearn existing command | Device: API/panel can replace a single-press signal. Android does not expose a dedicated relearn action. |
| LEARN-09 | Repeat learning | Device: capture a repeat/held frame against an existing saved command. |
| LEARN-10 | Cancel learning | Device: explicit cancel operation and panel action. Android closes its dialog; immediate device-session cancellation is not exposed by that Close button. |
| LEARN-11 | Carrier qualification | Device, Conditional: a demodulated receiver cannot measure carrier frequency; use a separately verified configured carrier. |
| LEARN-12 | Simulation distinction | App preview and backend simulator generate synthetic captures; these do not validate a real remote. |
| LEARN-13 | Range and protocol compatibility | Conditional: receiver/emitter/driver, carrier, line of sight and appliance protocol determine real support. Arbitrary long/stateful IR is not guaranteed. |

## 7. Music, Spotify and volume functions

### 7.1 Account and playback

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| MUSIC-01 | Spotify account connection | App, Backend: launch supported browser authorization; do not ask for a Spotify password in Room. |
| MUSIC-02 | PKCE authorization | Backend: S256 PKCE, expiring single-use authorization state and token exchange. Device tooling provides a separate PKCE helper. |
| MUSIC-03 | Authorization refresh | App: “I've connected · refresh” updates connection state after browser completion. |
| MUSIC-04 | Token refresh | Backend, Device: refresh authorized access with the configured credentials. |
| MUSIC-05 | Current track | App, Backend, Device: available track title metadata. |
| MUSIC-06 | Artist | App, Backend, Device: available artist metadata. |
| MUSIC-07 | Playback status | App, Backend, Device: playing, paused or nothing playing. |
| MUSIC-08 | Current playback device | App, Backend, Device: show the active device name when available. |
| MUSIC-09 | Play/resume | App, Backend, Device: supported eligible playback device required. |
| MUSIC-10 | Pause | App, Backend, Device. |
| MUSIC-11 | Next track | App, Backend, Device. |
| MUSIC-12 | Previous track | App, Backend, Device. |
| MUSIC-13 | Refresh playback | App: explicit playback/device refresh. |
| MUSIC-14 | Internet/account dependencies | Conditional: Spotify Premium is confirmed; live API eligibility, configured client, authorization and actual playback devices remain to test. |
| MUSIC-15 | Spotify limitations feedback | Backend, Device: unavailable/restricted device, account/API, network and rate-limit errors; no disruptive automatic retry. |
| MUSIC-16 | Disc-style player | App: spinning local record artwork, expand/collapse, visualizer, track/artist, transport controls and timeline. Artwork/visualizer are UI presentation, not measured microphone data. |
| MUSIC-17 | Seek, shuffle and repeat UI | App: interactive preview controls; live transport integration remains pending and those controls stay disabled outside preview. |

### 7.2 Devices, browsing and search

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| BROWSE-01 | Playback-device list | App, Backend, Device: fetch supported available devices. |
| BROWSE-02 | Restricted-device indication | App: mark a device as restricted when Spotify reports it. |
| BROWSE-03 | Device selection/transfer | App, Backend, Device: select the intended output device. |
| BROWSE-04 | Saved playlists | App, Backend, Device: browse available user playlists. |
| BROWSE-05 | Recently played tracks | App, Backend, Device: browse recent listening. |
| BROWSE-06 | Search | App, Backend, Device: implemented Android search covers tracks and playlists. Broader “full music search” remains unimplemented beyond those categories. |
| BROWSE-07 | Play a result | App, Backend, Device: request playback of a selected supported track/playlist URI. |
| BROWSE-08 | Pagination | App, Backend: load more search/playlists/recent results; Device panel: next-page browsing. |
| BROWSE-09 | Spotify attribution | App: identify Spotify as the content/playback provider. |

### 7.3 Separate volume targets

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| VOLUME-01 | Spotify volume | App, Backend, Device: set supported playback-device volume, 0–100%. |
| VOLUME-02 | Speaker IR volume | App: choose a learned command belonging to a speaker appliance. Device: configured volume-up/down command IDs. |
| VOLUME-03 | Volume-target preference | App, Backend, Device: select Spotify or speaker IR. |
| VOLUME-04 | Physical volume adjustment | Device: encoder can adjust the chosen volume target from the music menu. |
| VOLUME-05 | No inferred IR percentage | Required: sending IR volume does not establish the speaker's actual volume percentage. |

## 8. Routines, schedules and event functions

### 8.1 Routine editor

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| RULE-01 | Routine list | App, Backend, Device: named saved rules with enabled state. |
| RULE-02 | Create routine | App, Backend; Device API accepts rule objects. |
| RULE-03 | Edit routine | App, Backend; Device API can replace a rule. |
| RULE-04 | Enable routine | App, Backend, Device. |
| RULE-05 | Pause routine | App, Backend, Device: deliberate confirmation. |
| RULE-06 | Delete routine | App, Backend, Device: deliberate confirmation. |
| RULE-07 | Global automation switch | App, Backend, Device: enable or deliberately pause all rule execution. |
| RULE-08 | Lighting action | App, Backend, Device: save a complete lighting-state action. |
| RULE-09 | Preset-derived action | App: use current lighting or a selected preset's values when saving the routine. This copies values, not a live reference to future preset changes. |
| RULE-10 | IR action | App, Backend, Device: select a saved command. |
| RULE-11 | Calibration action | Backend model and Device API support calibration actions; Android routine editor exposes lighting and IR only. |
| RULE-12 | Reference validation | Backend, Device: prevent rules/commands with invalid appliance or command references. |

### 8.2 Schedule functions and timekeeping

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| TIME-01 | Scheduled time | App, Backend, Device: validated 24-hour HH:mm. |
| TIME-02 | Selected weekdays | App, Backend, Device: Monday–Sunday selection; all days provides daily recurrence. |
| TIME-03 | Time zone per routine | App, Backend: validated IANA time-zone identifier. |
| TIME-04 | Controller time zone | Device: global POSIX TZ string; conversion from the Android/backend model is required. |
| TIME-05 | Network time | Device, Conditional: configured NTP/SNTP; no hardware RTC assumed. |
| TIME-06 | Clock-validity status | App, Backend, Device: distinguish synchronized from unsynchronized time. |
| TIME-07 | Invalid-time handling | Backend, Device: scheduling must not execute against an invalid clock. |
| TIME-08 | Missed events | Backend, Device: skip missed windows instead of replaying old actions after an outage. |
| TIME-09 | First-minute baseline | Backend, Device: establish the current minute before running later scheduled work. |
| TIME-10 | DST handling | Backend: skip nonexistent spring times, run first fall occurrence once. Device: avoid repeated local-time execution after rollback; physical tests pending. |
| TIME-11 | Duplicate suppression | Backend, Device: minute/run markers prevent repeated dispatch; bridge ownership separates server/device scheduling. |
| TIME-12 | Offline schedules | ESP-IDF bridge: bounded nine-date cached schedule horizon. Arduino: local selected-weekday recurrence after configuration/time/arming. They are distinct paths. |
| TIME-13 | Other recurrence types | Required decision/work: one-time dates, calendar recurrence and policies beyond selected weekdays are not implemented. |

### 8.3 Event rules and reboot behaviour

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| EVENT-01 | Sound-start trigger | App, Backend, Device: transition into active room sound. |
| EVENT-02 | Sound-stop trigger | App, Backend, Device: transition into quiet/inactive sound. |
| EVENT-03 | IR-received trigger | App, Backend, Device: received-signal event; not appliance-state confirmation. |
| EVENT-04 | Event cooldown | App: 10–600 seconds in editor; Backend allows 10–86,400 seconds; Device enforces configured cooldown. |
| EVENT-05 | Startup transition protection | Backend: suppress initial sound-state false transitions; real device behaviour requires integration verification. |
| EVENT-06 | No IR replay on restart | Required, Backend, Device: do not blindly issue stored or missed IR commands after boot/restore. |
| EVENT-07 | Deliberate automation resumption | Device: explicit arming/resume setting; restore disarms automation. ESP-IDF bridge waits for fresh authenticated synchronization after reboot. |
| EVENT-08 | Persist-before-send protection | Device: scheduled IR attempt markers stored before transmission to reduce repeat risk after restart. |

## 9. Physical controls and OLED functions

### 9.1 Approved control scheme

| Control | Approved function | Implementation |
| --- | --- | --- |
| Encoder rotation | Navigate menus and adjust values | Device: quadrature decoding. |
| Encoder short press | Select / confirm | Device: debounced switch input. |
| Encoder long press | Back / Home | Device: back from current page; portable ESP-IDF panel policy separately cancels an active edit/confirmation first. |
| Button 1 | Lighting shortcut | Device. |
| Button 2 | Spotify / music shortcut | Device. |
| Button 3 | Appliances / IR shortcut | Device. |
| Button 4 | Favourites shortcut | Device. |

The assignments are explicitly approved for implementation. Electrical verification
of the board, modules and wiring remains pending.

### 9.2 Panel functions

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| PANEL-01 | OLED home/menu navigation | Device: lighting, music, IR, favourites, rules and system/audio pages. |
| PANEL-02 | Local lighting adjustment | Device: power, brightness, RGB channels, effect, speed and intensity. |
| PANEL-03 | Local preset access | Device: recall saved lighting; save current values as a favourite. |
| PANEL-04 | Local playback | Device: play/pause, next/previous, selected volume target. Spotify still needs internet. |
| PANEL-05 | OLED music metadata | Device: track, artist and device name; constrained text is clipped to the display width. Full Unicode fonts/scrolling are not claimed. |
| PANEL-06 | Local music browsing | Device: devices, saved playlists, recent tracks, refresh and next page. Text search is a client/API function. |
| PANEL-07 | Local appliance creation | Device: automatically named appliance; custom rename through an integrated client/API. |
| PANEL-08 | Local command management | Device: transmit, favourite, relearn, learn/send verified repeat, delete with confirmation. |
| PANEL-09 | Local learning control | Device: choose appliance, capture prompt, cancel capture. |
| PANEL-10 | Local rule management | Device: list rules, enable/pause/delete; full rule creation/editing is a client/API function. |
| PANEL-11 | Local microphone tuning | Device: calibration, sensitivity, automatic gain and quiet behaviour. |
| PANEL-12 | OLED timeout | App preference: 5–600 seconds. Device setting/menu uses its own field and supports disabling timeout; integration must map these. |
| PANEL-13 | Display wake | Device: input activity resets inactivity and wakes the timed-out display. |
| PANEL-14 | Status and notifications | Device: Wi-Fi/AP/offline, automation/manual, learning and short messages. |
| PANEL-15 | Local confirmation | Device: Cancel selected first for disruptive menu confirmations. |
| PANEL-16 | Input timing | Device implementation defaults: 30 ms debounce and 800 ms encoder long press; these are software defaults, not new user-approved mappings. |
| PANEL-17 | Local OTA permission window | Device: allow signed OTA for 60 seconds from panel. |
| PANEL-18 | Storage recovery | Device: initialize filesystem only after mount failure and explicit confirmation. |
| PANEL-19 | Phone-independent use | Required, Device, Conditional: local lighting, IR and configured rules remain accessible without the phone; verify physical operation. |

### 9.3 Approved GPIO profile

| Signal | ESP32 GPIO |
| --- | --- |
| OLED SDA / SCL | 21 / 22 |
| Addressable LED data | 23 |
| IR transmitter driver / receiver | 18 / 27 |
| Microphone BCLK / WS-LRCLK / data | 26 / 25 / 35 |
| Encoder A / B / push | 32 / 33 / 4 |
| Buttons 1 / 2 / 3 / 4 | 13 / 14 / 16 / 17 |

## 10. Connecting functions and network behaviour

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| CONNECT-01 | Local Wi-Fi app control | Required, App, Backend: control on the home/local network. |
| CONNECT-02 | Local-only controller scope | Confirmed: remote internet control, VPN and cloud relay are excluded. Spotify's internet use is separate. |
| CONNECT-03 | Manual backend address | App: configured server URL; automatic controller discovery is not implemented. |
| CONNECT-04 | Initial setup AP | Required, Device: temporary ESP32 access point for initial provisioning. No native Android provisioning wizard is implemented. |
| CONNECT-05 | Protected recovery AP | Required, Device: recovery access after router loss using unique protected credentials. |
| CONNECT-06 | Wi-Fi credential configuration | Device API: authenticated SSID/password command, local persistence and connection attempt. Android currently lacks this direct-device configuration screen. |
| CONNECT-07 | Home reconnection | Device and ESP-IDF policy/bridge: reconnect attempts following connection loss. |
| CONNECT-08 | Stop unnecessary AP | Required, Device: shut setup/recovery AP after home connection returns. |
| CONNECT-09 | Status separation | Required, Device: distinguish home station connection, AP access and internet-dependent availability. |
| CONNECT-10 | Phone-independent operation | Required, Device: phone loss does not itself stop supported local control. |
| CONNECT-11 | Internet-independent local operation | Required, Device, Conditional: configured local lighting/IR/rules work without internet; Spotify cannot. Valid time remains necessary for schedules. |
| CONNECT-12 | Periodic state refresh | App: backend polling at approximately four-second intervals. |
| CONNECT-13 | Stale/offline feedback | App: preserve honest stale state and show interrupted connection without repeated error-message spam. |
| CONNECT-14 | Device heartbeat | Backend, ESP-IDF bridge: authenticated capabilities, firmware, uptime, RSSI, free heap, time validity, bands and sound state. |
| CONNECT-15 | Capability gating | Backend, Device: unavailable hardware is reported/rejected rather than simulated as success. |
| CONNECT-16 | Direct-device Android integration | Required: certificate/token pairing, `/v1` commands, state conversion and physical acknowledgements. Current app is not automatically compatible. |
| CONNECT-17 | USB maintenance | Device: trusted physical serial JSON interface at 115200 baud; not a replacement Android connection method. |

## 11. Authentication, security and command delivery

### 11.1 Access and transport

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| SEC-01 | Owner account setup | Backend: local setup command creates owner credentials; no default owner password. |
| SEC-02 | Password protection | Backend: scrypt hashing; password input minimum 12 characters. |
| SEC-03 | Login throttling | Backend: per-IP failed-attempt window. Device: separate five-failure, 30-second temporary authentication lockout. |
| SEC-04 | Authenticated sessions | Backend: revocable, hashed bearer sessions with 12-hour lifetime. |
| SEC-05 | Android token storage | App: Android Keystore-backed AES-GCM encryption; owner password is not saved. |
| SEC-06 | Sign out | App, Backend: deliberate sign-out, local token removal and session revocation. |
| SEC-07 | Expired/invalid session handling | App: unauthorized responses clear the session and require login. |
| SEC-08 | Local account recovery | Backend: console password reset revokes sessions; device-key rotation command. |
| SEC-09 | Separate device identity | Backend: device key distinct from owner access. Device firmware: its own unique pairing token. |
| SEC-10 | HTTPS | App release requires HTTPS; backend TLS setup supports local CA certificates. Debug HTTP is for local development. |
| SEC-11 | Certificate validation | App: trust configured system/user CA; ESP-IDF bridge validates its CA. Device helper pins the exact generated device certificate; native Android pin integration pending. |
| SEC-12 | Unique provisioning credentials | Device tooling: generate unique setup/AP credentials, TLS identity and device token; generated secrets excluded from source ZIP. |
| SEC-13 | Protected configuration access | Required, Backend, Device: authenticate before configuration/control; avoid secrets in status/backup/log output. |
| SEC-14 | Spotify secret protection | Backend: encrypted token storage with separately supplied persistent vault key. Device credential storage has its own flash-security limitations. |
| SEC-15 | Request limits | Backend and Device: bounded request bodies, collection counts and signal timings. |
| SEC-16 | Android OS backup exclusions | App: exclude sensitive app data from automatic cloud backup/device transfer. Explicit encrypted export is separate. |
| SEC-17 | Secure boot / flash encryption | Required evaluation only: exact module and security lifecycle must be assessed; no eFuse provisioning or enabled flash encryption is claimed. |

### 11.2 Reliable action delivery

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| DELIVERY-01 | Capability/online check | Backend: reject unavailable/offline operations instead of retaining them for later surprise execution. |
| DELIVERY-02 | Expiring actions | Backend: normal jobs expire after 15 seconds; learning 30 seconds; OTA submission 60 seconds. |
| DELIVERY-03 | At-most-once delivery | Backend: atomically mark delivered jobs; no blind repeat of ambiguous physical actions. |
| DELIVERY-04 | Idempotency | Backend: repeated matching request/key returns its job; conflicting payload/key rejected. App generates a fresh key per POST. |
| DELIVERY-05 | Device acknowledgement | Backend: applied, transmitted, failed, unsupported or uncertain with detail. |
| DELIVERY-06 | Ambiguous-result handling | Backend: outstanding work becomes uncertain after restart rather than replayed. |
| DELIVERY-07 | No automatic disruptive retry | App, Backend, Device: explicit user action needed where IR/playback outcome could be ambiguous. |
| DELIVERY-08 | Queue acceptance versus completion | Device: accepted IR/Spotify request is not completion; inspect transmission identifiers or refreshed playback/error state. |

## 12. Saved data, backup and restore functions

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| DATA-01 | Persist room data | Backend, Device: settings, lighting, audio, appliances, IR commands, presets and routines. |
| DATA-02 | Backend transactional storage | Backend: SQLite transactions and WAL. |
| DATA-03 | Device snapshots | Device: validated JSON snapshot with previous copy; ESP-IDF bridge uses separate versioned NVS snapshots. |
| DATA-04 | Storage failure feedback | Backend, Device: fail explicitly when persistence/limits prevent a write. |
| DATA-05 | Encrypted export | App, Backend: passphrase-protected AES-256-GCM backup; 12+ character passphrase. Device tooling offers its own encrypted client backup workflow. |
| DATA-06 | User-chosen backup file | App: Android document picker saves `room-backup.json` to a selected location. |
| DATA-07 | Backup contents | Backend: IR library, presets, routines and settings; credentials/session/Spotify secrets excluded. |
| DATA-08 | User-chosen restore file | App: bounded-size document import, passphrase and explicit replacement confirmation. |
| DATA-09 | Integrity and password checks | Backend/tooling: reject tampered backups and incorrect passphrases. |
| DATA-10 | Schema/reference validation | Backend, Device: reject invalid restored objects and dangling references. |
| DATA-11 | Atomic replacement | Backend: validate before replacing data in a transaction. |
| DATA-12 | Paused restore | Backend, Device: restored automation remains disarmed/paused; no replayed IR. |
| DATA-13 | Preserve electrical profile | Device restore: do not overwrite the verified hardware profile with imported client data. |
| DATA-14 | Backup-format compatibility | Required: backend encrypted archive and Arduino backup/client workflow are different; do not import interchangeably without an adapter. |
| DATA-15 | Storage bounds | Device defaults: 128 commands, 48 rules, 48 presets, 1,024 raw entries, 100,000-byte database/request limits; not unlimited capacity. |
| DATA-16 | Bridge offline cache limit | ESP-IDF bridge: approximately 60 KB subject to NVS/flash availability; distinct from Arduino storage. |

## 13. System settings, diagnostics and firmware updates

### 13.1 Preferences and diagnostics

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| SYSTEM-01 | Connection details | App: endpoint, online/offline state and preview indication. |
| SYSTEM-02 | Firmware version | App, Backend, Device: reported device firmware information. |
| SYSTEM-03 | Wi-Fi signal | App, Backend, Device: RSSI where supplied. |
| SYSTEM-04 | Time synchronization | App, Backend, Device: valid/invalid clock feedback. |
| SYSTEM-05 | Default time-zone preference | App, Backend: validate a chosen IANA zone; individual backend routines retain their own zone. |
| SYSTEM-06 | OLED timeout preference | App, Backend, Device: control display inactivity, with protocol mapping still required. |
| SYSTEM-07 | Volume-target preference | App, Backend, Device: Spotify versus speaker IR. |
| SYSTEM-08 | Automation status | App, Backend, Device: global enabled/paused state. |
| SYSTEM-09 | Refresh diagnostics | App, Backend: recent job outcomes and events. |
| SYSTEM-10 | Resource/device diagnostics | Backend/Device state: uptime, heap, capabilities and error details where produced; Android does not render every field as a separate widget. |
| SYSTEM-11 | IR diagnostics | Device: capture overflow, carrier/profile validation, busy/transmission status and errors. |
| SYSTEM-12 | Audio diagnostics | Device, Conditional: capture/calibration processing and resulting sound/band state; physical noise/latency acceptance pending. |

### 13.2 OTA update functions

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| OTA-01 | Signed-manifest import | App: choose a JSON manifest and review version/board before confirmation. |
| OTA-02 | Backend signature validation | Backend: Ed25519 manifest validation and capability gating. ESP-IDF path does not implement actual OTA flashing. |
| OTA-03 | Standalone device OTA upload | Device: authenticated signed application-binary upload with its own helper/API. |
| OTA-04 | Device signature/digest verification | Device: RSA-3072 PKCS#1 v1.5 SHA-256 signature and binary digest validation. This differs from the backend Ed25519 manifest. |
| OTA-05 | Local update authorization | Device: physical-panel 60-second permission window. |
| OTA-06 | Version monotonicity | Device: reject non-increasing signed versions. |
| OTA-07 | Application-image validation | Device: SDK validation before changing the boot selection; upload application binary, not bootloader/merged image. |
| OTA-08 | Interrupted-write handling | Device design: current selected app remains if inactive-slot write does not complete; physical power-loss testing pending. |
| OTA-09 | Retain user data | Device design: do not automatically erase filesystem or credentials during update. |
| OTA-10 | Rollback/recovery | Conditional: automatic rollback depends on actual core/bootloader/partition configuration; keep a known-good USB recovery build. No physical rollback test completed. |
| OTA-11 | Unified Android OTA | Required: reconcile signing, upload, permission and status workflows before current Android can update standalone Arduino firmware. |

## 14. Hardware-dependent configuration and physical requirements

These are dependencies of the app/controller features, not completed electrical design.

| ID | Feature / dependency | Status / defined behaviour |
| --- | --- | --- |
| HW-01 | Main board | Selected family: classic ESP32 DevKit V1 / ESP-WROOM-32; exact variant/flash capacity remains to verify. |
| HW-02 | OLED | Candidate SSD1306 I²C 0.96-inch 128×64; verify address, supply and actual module. |
| HW-03 | Encoder and buttons | EC11 candidate and four normally-open momentary buttons; verify module/pull-ups, detents and mounting. |
| HW-04 | Microphone | Candidate INMP441 I²S module; verify actual breakout/channel/supply. |
| HW-05 | IR receiver | Candidate TSOP38438; supported carrier band limits appliance compatibility. |
| HW-06 | IR transmitter and driver | Candidate TSAL6200 with suitable transistor/current design; emitter range and optics not verified. |
| HW-07 | Strip electrical profile | Protocol, voltage, data rate, RGB order, installed pixel count and current must be verified before enabling output. |
| HW-08 | Data interface | Appropriate verified logic-level buffering, common reference and signal routing as needed. |
| HW-09 | Power distribution | Correct supplies/rails, injection intervals, fusing, wire/connector ratings and grounding; never power the strip from a GPIO. |
| HW-10 | Software hardware confirmation | Device API requires explicit complete verified profile and confirmation; reboot initializes enabled drivers. |
| HW-11 | Compact enclosure | Requested desk-mounted box with electronics/wiring inside; exact dimensions, thermal design and PCB/module layout remain undecided. |
| HW-12 | Power recovery | Restore supported saved lighting/settings; suppress old IR; deliberately control automation resumption. |
| HW-13 | Reliability qualification | Required: real brownout, flash/storage failure, IR timing, audio noise, Wi-Fi recovery, TLS/heap and concurrent-work testing. |

## 15. Development, preview and delivery functions

| ID | Feature | Status / defined behaviour |
| --- | --- | --- |
| DEV-01 | Installable development APK | Included in `artifacts/RoomController-debug.apk`; UI preview available immediately. |
| DEV-02 | Android Studio source | `android/` contains native project, Gradle wrapper and resources. |
| DEV-03 | Backend source and API definition | `backend/`, requirements, setup commands and `docs/openapi.json`. |
| DEV-04 | Device source alternatives | ESP-IDF bridge/core and newer Arduino application are included with separate build instructions. |
| DEV-05 | Setup/build helpers | Scripts acquire isolated tooling and build Android/firmware; credentials remain private. |
| DEV-06 | Local simulator | Backend simulator and isolated QA fixtures for development; not evidence of hardware operation. |
| DEV-07 | Automated checks | Android build/lint/unit checks, backend tests, portable C++ tests and Arduino tooling/validation tests are documented. |
| DEV-08 | UI evidence | Included screenshots cover login, main categories and connected test flows. |
| DEV-09 | One-folder project package | `app/` and `app.zip` contain portable source, references and outputs; machine caches/private runtime data are excluded. |
| DEV-10 | Integrity manifest | `SOURCE_SHA256.json` inside package and `app.zip.sha256` alongside archive. |
| DEV-11 | Production release | Required work: final direct-device integration, phone/hardware acceptance and private Android release signing before production distribution. |

## 16. Explicit exclusions and features not currently provided

- Bluetooth app connection, RF remote transmission and direct wired audio input.
- Public internet control, VPN access and a cloud control relay.
- Gesto/Magic Home retention/integration as a project requirement; replaced by addressable lighting.
- Mandatory browser/desktop control UI; current primary client is Android.
- Guaranteed support for every IR protocol, appliance, carrier or stateful remote.
- Confirmed appliance power/volume state from an ordinary one-way IR transmission.
- One independently controllable pixel per physical LED on the selected grouped strip.
- Strobe, ambient transitions or an unlimited catalogue of unrequested animations.
- Arbitrary calendar/one-time recurrence beyond implemented selected weekdays.
- Artist/album search, downloads or ESP32 audio playback. Seek/shuffle/repeat now have preview UI; their live transport is still pending.
- Automatic device discovery, native Wi-Fi provisioning wizard, multiple-owner roles or account management screens.
- Guaranteed unlimited command/storage capacity, complete Unicode OLED fonts or arbitrary long IR sequences.
- A completed circuit, final supply selection, confirmed flash/partition layout or physically tested OTA rollback.

## 17. Source references and verification boundaries

This catalogue is based on the following local project material:

- [Original requirements with superseding update](ESP32_Smart_Room_Controller_Requirements.md).
- [Confirmed decisions](esp32-firmware/docs/CONFIRMED_UPDATES.md).
- [Approved control and GPIO mapping](docs/APPROVED_PROFILE.md).
- [Android/backend coverage matrix](docs/IMPLEMENTATION_STATUS.md) and [validation](docs/VALIDATION.md).
- [Android interface source](android/app/src/main/java/in/roomcontroller/app/).
- [Backend models](backend/models.py) and [API implementation](backend/main.py).
- [Current Arduino application](esp32-firmware/arduino/SmartRoomController/README.md).
- [Arduino API and schema](esp32-firmware/arduino/SmartRoomController/API.md).
- [Arduino build/verification and remaining limits](esp32-firmware/arduino/SmartRoomController/BUILD_VERIFICATION.md).

Software compilation and simulator tests establish only the behaviour they exercised.
No complete Android → standalone Arduino → physical room acceptance test has been
performed. Every Conditional or Required item remains subject to its stated dependency.
