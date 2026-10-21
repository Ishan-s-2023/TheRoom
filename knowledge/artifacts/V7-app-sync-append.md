## 10. V7 final APK inventory, versions and ESP32 synchronization

**App release ID: `ROOM-ANDROID-0.3.0+5`. Project documentation delivery: V7.**
Recorded 21 October 2026, Asia/Calcutta. This section is appended after the complete
previous document; no earlier text, tables, embedded documents or source blocks have
been edited. It updates the app inventory/release record without rewriting the earlier snapshot.

### 10.1 Exact final APK and source identity

| Item | Verified value |
| --- | --- |
| App label / package | Room / `in.roomcontroller.app` |
| Android versionName / versionCode | **0.3.0 / 5**, read from both Gradle source and compiled APK metadata |
| Human-readable release ID | **ROOM-ANDROID-0.3.0+5** |
| Project delivery containing this record | **V7**, documentation/tracking; APK bytes remain the already delivered release |
| Latest APK alias | `artifacts/RoomController-final.apk` |
| Immutable versioned APK | `artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5.apk` |
| APK SHA-256 | `917a4bdba23b4f7c9e2b7042abb365e52204fbe8752b0b5d9a0407d2d2d076c4` |
| Versioned source archive | `artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5-source.zip` |
| Versioned build report | `artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5-verification.json` |
| Versioned release manifest | `artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5-release.json` |
| Original build snapshot | `20261021T114733Z` — 21 October 2026, 17:17:33 IST; this identifies snapshot creation, not device installation |
| Build directory | `artifacts/final-apk-verification/20261021T114733Z/android/` |
| Android requirements | Android 8/API 26 or newer; compile/target SDK 35; debug-signed personal build |
| Canonical Android source | Root `android/`, not `app/android/` or old review exports |
| APK input verification | All **48** recorded Android input files match the current source; no changed, missing or extra inputs in this audit |
| Embedded provenance | `assets/final-build-manifest.json` inside the APK; contains source hashes and the original build snapshot identifier |
| Installation status | This record does not install the APK or establish which version is currently on the user's phone |

The versioned APK is a byte-identical copy, not a rebuilt or re-signed package. The
matching source ZIP and build report are preserved with the same release identity.
Do not label it Android 0.3.1, code 6, or app version 7 just because this documentation
delivery is V7. `versions.md` and `artifacts/android-release-history.json` track it.

The APK is personal to the configured controller. Its `room-device.config` asset and
matching source ZIP include device connection material. Keep these private; this section
lists filenames and hashes, never bearer tokens, setup passwords, private keys or refresh tokens.

### 10.2 What is in this APK

All Android implementation described below is present in the verified final APK.
UI previews, implemented client requests and physically accepted hardware behaviour are
different things; the dependencies and limits remain explicit in the tables.

| Area | Shipped app behaviour | Dependency / boundary |
| --- | --- | --- |
| Navigation | Home, Lights, Remotes, Music and More; Routines, Sound and Settings under More; Back/navigation handling and page scrolling | Native Room interface |
| Appearance | Forest, Midnight, Ember, Pearl and System; persistent native palette, system-bar styling, cards, typography and controls | WLED panel also receives Room palette styling; real LED colour/effect canvases keep their meaningful colours |
| Opening/connection | Personal built-in Room controller connection and standalone WLED connection; saved settings; no WLED pairing-file selection | Room identity is preconfigured; WLED is separately verified by its JSON info/MAC |
| Home live lighting | Actual WLED power, brightness and named preset shortcuts; readback instead of simulated success | Requires a WLED-capable controller; does not use the current Room-only lighting endpoint |
| Lights | Native WLED quick controls plus the complete controller-served interface styled to Room | Actual WLED JS/HTML comes from the ESP32, not a fake lighting engine bundled in the APK |
| WLED effects and colour | Colour picker, palettes, effects, speed/intensity and other sliders supported by the installed WLED UI | Supported effects/parameters come from that firmware build; there is no hardcoded list claiming unsupported hardware features |
| WLED segments/presets | Segment controls, transitions, named presets, playlists and preview through the original WLED interface | Capability and physical layout depend on firmware and strip configuration |
| WLED advanced settings | Real controller settings, timers/time, LED configuration, sync and integration settings available in its build; Sound opens usermod settings | These remain WLED routes, not translations into the old Room API |
| WLED device library | Verified add/select, custom local name, persisted addresses, hide/show/remove/reconnect | Removing a shortcut does not factory-reset a controller |
| Discovery and setup | Android `_wled._tcp.` discovery, serialized older-Android resolution, multicast lock and default setup-AP fallback probe | Actual router mDNS and board setup AP still need device/network acceptance |
| State and responsiveness | WLED WebSocket state updates with HTTP recovery/readback; cached Wi-Fi-bound client; stale-device response isolation | No automatic replay of uncertain state-changing POSTs; reports offline/unconfirmed results |
| No-Internet Wi-Fi | Native WLED requests use Wi-Fi; panel temporarily binds to Wi-Fi and restores prior binding when closed | Local lighting can work without Internet; Spotify has separate Internet/time requirements |
| Backup/export | Controller download links and generated exports open Android's save-document picker | Same-origin, bounded export handling; exported files reflect actual controller data |
| Restore/update files | Android file chooser supplies selected content URI to original WLED forms, including JSON restore and firmware upload pages | No automatic installation of generic stock WLED over the required combined Room build |
| WLED web navigation | Native Back/Done/Refresh, web history and saved page state; Room-themed JS alert/confirm/prompt handling | Full panel is hosted inside Room; it is not a separate newly themed firmware UI image |
| Home-screen widget | Select a saved WLED controller and power/preset action; real commands and readback; Room palette | Not a live physical-output sensor; unconfirmed actions are labelled accordingly |
| Remotes | Appliance and saved-command screens, favourites, guided capture, explicit send and cancellation paths | Uses retained Room `/v1` API in direct mode; a sent IR waveform does not prove appliance state |
| Spotify account | Phone browser authorization, PKCE callback handling and credential handoff to the configured Room controller | Spotify password is not collected by Room; depends on preserved device identity, valid time, Internet and supported account/player |
| Spotify controls | Actual direct-device previous/play/pause/next, supported volume and refresh; track/artist/device display | Runs through the ESP32 Spotify worker, not WLED AudioReactive; ESP32 is not the Spotify audio speaker |
| Spotify browse | Direct-device devices/transfer, playlists, recent listening, track/playlist search and selecting returned playable entries | Do not infer every optional UI capability in legacy preview has a live direct-device transport; direct-page pagination/seek/shuffle/repeat remain limited |
| Operation completion | Job/boot-qualified tracking and pending feedback for Spotify; meaningful terminal errors and unknown outcomes | Queue acceptance is not completion; never blindly retry disruptive actions after uncertain results |
| Routines | Existing native rule editing and automation actions through Room's API | The actual rule types/action translation and full physical acceptance depend on Room firmware; WLED lighting integration needs the adapter below |
| Room Settings | Existing secure device connection, Wi-Fi handoff, clock/timezone, automation and diagnostics/settings paths exposed by the shipped screens | Some firmware maintenance functions exist at API/tool level without a complete native app editor; do not claim UI coverage solely because firmware implements an op |
| Preview | Existing Room interface exploration and simulated legacy lighting/music/remote interactions | No physical commands; WLED connection never seeds a fake controller as a successful live connection |
| Optional companion path | Existing backend transport and legacy screens remain compiled in the app | A separately running Python backend is not contained in the APK and is not required for normal direct Room/WLED connections |

The React/component-lab references, Python services, ESP32 C++ source, compiler tools,
QA fixture and reviewed upstream repository checkouts are **not** Android runtime assets.
The APK contains the native app/client implementation; it cannot supply missing ESP32 firmware,
simulate successful device capabilities, or turn unverified hardware into accepted output.

### 10.3 Android implementation map

Package: `android/app/src/main/java/in/roomcontroller/app/`.

| Files / group | Responsibility |
| --- | --- |
| `MainActivity.kt`, `Appearance.kt` | Root navigation, Room theme selection and integration of native/embedded WLED screens |
| `RoomModel.kt` | Room session/state, direct/companion routing, mutation/polling and operation orchestration; Room polling is suspended while using WLED lighting/sound |
| `DeviceTransport.kt`, `DeviceJobs.kt` | Pinned HTTPS device transport, schema projection and bounded job/boot-qualified completion |
| `DeviceScreens.kt` | Personal pairing/connection, direct-device system, IR learning and direct Spotify screens |
| `WledProtocol.kt`, `WledModel.kt` | WLED origin validation, HTTP command/readback, identity, saved devices, discovery, WebSocket and reconnect handling |
| `WledScreens.kt` | WLED Home/Lighting quick controls, preset shortcuts and controller management |
| `WledBrowser.kt`, `WledTheme.kt` | Live upstream panel, presentation styling, dialogs, file export/upload, Wi-Fi binding and web lifecycle |
| `WledWidget.kt` and widget resources | Widget provider, configuration and real controller actions |
| `ControlScreens.kt`, `LightingEditor.kt`, `DesignControls.kt` | Existing Room controls and component styling; legacy lighting editor remains for appropriate legacy/preview paths, not a replacement WLED renderer |
| `AutomationScreens.kt`, `ServiceScreens.kt`, `SoundTuning.kt` | Existing routine/backend/service/tuning screens; live Sound navigation now opens WLED usermod settings |
| `SpotifyPkce.kt`, `SpotifyConnectActivity.kt` | Secure authorization state/verifier/callback and controller account handoff |
| `MusicPlayerWidget.kt`, `PreviewData.kt` | Existing music presentation and explicitly simulated preview data/behaviour |
| `ConnectionGuides.kt`, `BoundedInput.kt` | Help/navigation and bounded response/input utilities |
| `AndroidManifest.xml`, resource XML/layout/drawables | Activities, permissions, HTTP support for WLED, widget registration and native resources |
| `android/app/build.gradle.kts` | Actual Android versionName/code, SDK levels and dependencies |

The matching source ZIP is the complete Android source for this APK. For exact
per-file SHA-256 values use the embedded source manifest or immutable verification report;
do not substitute the older embedded Android source blocks above or `app/android/`.

### 10.4 Exact app-to-ESP32 boundaries

**Path A — existing Room API:** phone → pinned HTTPS port 443 → `/v1/state`,
`/v1/command`, `/v1/jobs` and the existing Room command/schema contract. The app has
this direct implementation; earlier statements that it is wholly missing are historical.
Retain the configured certificate identity/token, saved object fields, boot/job IDs,
terminal statuses and Spotify browse/state fields when changing firmware.

**Path B — WLED lighting:** phone → WLED HTTP port 80 and `/ws`. Native controls use
`GET /json/si`, `POST /json/state` with state readback, and `GET /presets.json`.
The full live panel and assets load from the controller's origin; settings use `/settings/*`
and the installed WLED build's upload/download routes. WLED info must include a stable
`mac`, `ver` and `leds`; state includes `on` and 0–255 `bri`.
No Room bearer token or Spotify refresh credential is sent to these WLED endpoints.

| Current component pair | Source-level status | What must not be claimed |
| --- | --- | --- |
| Android 0.3.0/code 5 + canonical Room 1.1.4-music | Retained Room `/v1` contract is the integration baseline for IR, music and routines; V6 notes describe API preservation | No new on-device compatibility/performance test or fresh installed-version query was performed for this record |
| Android live Home/Lighting/Sound + Room-only 1.1.4-music | **Not a complete matching pair for those WLED routes**; this firmware does not become WLED because it has its own DSP/LED engine | Do not mark WLED controls as functional on the current Room-only firmware or equate its five effects/16-band DSP with WLED APIs |
| Android + stock WLED | WLED controls can connect to supported WLED routes | Stock WLED alone does not implement Room appliance learning, Spotify and combined-room API/services |
| Android + future combined WLED/Room build | Intended complete architecture; implementation and physical acceptance remain pending | No existing combined binary or passed board acceptance is established by this app inventory |

For one ESP32 to expose everything used by this APK, implement the combined firmware:
WLED owns LEDs, microphone processing, Wi-Fi and shared time; Room services preserve IR,
Spotify, panel and mixed routines without competing driver ownership. Preserve relevant
V6 DSP/quiet/calibration requirements deliberately rather than silently discarding them.
The existing `artifacts/ESP32-WLED-MIGRATION.md` is a starting migration plan based on the
then-current 1.1.3 source; **rebase it against current 1.1.4-music/V6 changes before implementing**.
Its earlier baseline/version references do not supersede the current canonical source.

Do not flash a generic WLED image merely to make the app connect and then assume the
Room features were retained. Record the chosen architecture, API change, data migration,
identity/update policy and actual compiled/installed pair first.

### 10.5 Build and verification evidence

- The delivered APK's CRC/archive check passed; SHA-256 matches its existing successful-build report.
- Its 48 recorded Android source inputs were compared against the current root `android/`
  tree again for V7: no mismatches, missing inputs or additional release inputs.
- Compiled metadata independently reports package `in.roomcontroller.app`, versionName
  `0.3.0`, versionCode `5`, minimum API 26 and target API 35.
- The recorded frozen build passed **27 JVM tests**, including nine WLED protocol cases.
  Lint recorded **zero errors**, 27 warnings and four informational findings. This V7
  documentation audit does not pretend those tests were rerun on changed app code.
- Existing emulator acceptance covered native preset command/readback, actual upstream
  WLED power/effect requests against a localhost contract fixture, dark/light Room styling,
  exact JSON export through Android's save picker, and JSON selection/multipart restore upload.
- Evidence: `artifacts/WLED-INTEGRATION-VERIFICATION.json`, versioned build report,
  `docs/WLED_INTEGRATION.md`, frozen test/lint reports and recorded emulator artifacts.
- These tests did **not** certify physical LEDs/microphone/IR/OLED/encoder/buttons, router
  discovery, a live Spotify account, a combined firmware binary or an on-device OTA update.
- No app was rebuilt, re-signed or installed and no ESP32 was flashed/queried for V7.
  The shared handoff's last recorded board version remains `1.1.2-http`, not a fresh observation.
  OTA NVS monotonic counter remains unqueried/unchanged. Source version is `1.1.4-music`.

### 10.6 Future version and synchronization workflow

1. Keep `VERSION.md` for sequential project deliveries and `versions.md` for the app/firmware ledger.
   After V7, the next changed-codebase delivery is V8; component versions remain independent.
2. Before generating a new APK, increase `versionCode` in `android/app/build.gradle.kts`.
   Code **5 is permanently assigned to this release**; the next available code is **6**.
   Update versionName for app implementation changes (for example a patch may be 0.3.1).
3. Build with `python scripts/build-final-apk.py`. The preflight rejects a reused/lower
   app code or an app delivery that has not advanced the project delivery record. Successful
   releases retain the existing checks and archive version-labelled APK/source/evidence copies.
4. The script records future app/project versions in the new APK's source manifest,
   appends the exact release to `versions.md` and updates the machine-readable
   `artifacts/android-release-history.json`. Existing release artifacts are not overwritten.
5. A documentation/firmware-only change must not falsely renumber or regenerate an unchanged APK.
   Use its release ID/hash when retaining compatibility; a renamed file alone is not a new app version.
6. For each source/API/UI change append a new synchronization addendum here containing:
   project delivery, exact app version/code/hash, firmware source version and expected API,
   changed features/schema, immutable artifacts, checks performed, known mismatches, and
   independently observed phone/board installation versions if actually queried.
7. Review which app route is affected: Room `/v1`, WLED `/json`/`/ws`, or preview/companion.
   Update both sides of changed contracts, migrate persisted data where necessary, and
   test actual end-to-end actions before claiming the pair is functionally synchronized.
8. Preserve certificate/token identity and secrets; never publish private config or assume
   app versionCode/project delivery is the firmware's OTA anti-rollback counter.

**Current conclusion:** the final APK is precisely identified and its app source is
accounted for. The records are synchronized at V7; the remaining WLED-versus-Room
firmware architecture gap is explicitly tracked, not silently marked completed.
