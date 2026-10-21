# Room 0.3.0 — WLED integration

The installable artifact is `artifacts/RoomController-final.apk` (version code 5).
The firmware work is specified separately in `artifacts/ESP32-WLED-MIGRATION.md`.
No ESP32 was changed or flashed for this release.

## Using it

1. Install the APK as an update to Room. Existing Room pairing/theme data is retained.
2. After installing compatible WLED-based firmware, open **Lights → Discover** or enter
   the controller's address, for example `192.168.1.105` (HTTP port 80).
3. Room verifies WLED's state and MAC identity before saving it. No WLED pairing file is imported.
4. **Open lighting** opens the controller's complete upstream WLED interface inside Room.
   Room's colours, typography, controls and background styling are applied at runtime.
5. Use native Home/Lighting power, brightness and preset shortcuts, or the complete device
   panel for colours, effects, palettes, segments, playlists, timers, live preview and settings.
6. Use the device panel's backup/restore/update pages for file operations. Android's file
   picker supplies selected files; Android's save picker exports downloads to a chosen location.
7. Connect the retained Room HTTPS services for Remotes, Music and mixed Routines. Those
   require the combined firmware described in the handover. Stock WLED alone does not implement them.

Standalone WLED access is available from the opening screen without a Room backend login.
The existing paired Room host is also probed on HTTP for a same-board WLED service, without
sending the Room token. WLED setup AP detection probes its normal `4.3.2.1` address.

## Implemented app behaviour

| Area | Implementation |
| --- | --- |
| Full WLED control UI | The live UI and scripts are served by the controller, as in WLED Android; no hardcoded fake effects or bundled firmware simulator |
| Appearance | Forest, Midnight, Ember, Pearl and System palettes feed native controls and the embedded WLED appearance; colour/effect canvases retain their real colours |
| Controller library | Verified add/select, persisted list, custom local names, hide/show/remove and reconnect |
| Discovery | `_wled._tcp.` Android NSD, serialized resolution for older Android, multicast lock and setup AP fallback |
| Quick controls | Real `/json/state` POST and `/json/si` readback; no optimistic display of unconfirmed output |
| Preset shortcuts | Actual named entries from `/presets.json`, applied using their WLED preset IDs |
| Live state | Persistent `/ws` listener, with HTTP recovery/refresh; controller switching isolates stale responses |
| Offline behaviour | Retains last received state and disables native actions when offline; reports errors rather than simulating success |
| Local Wi-Fi | Native requests bind to the available Wi-Fi network; the embedded panel temporarily binds the process for no-Internet setup networks and restores the prior binding when closed |
| File upload | Android file chooser with content URI grants, used by upstream restore/firmware forms |
| File export | Save-document picker, same-origin export bridge including Blob/data downloads, bounded file sizes and HTTP errors |
| JavaScript dialogs | WLED alert/confirm/prompt callbacks render using Room-themed dialogs |
| Navigation | Room bottom navigation, panel Back/Done/Refresh, web history and saved WebView state for activity recreation |
| Tablet support | WLED's responsive layout remains available; the panel receives the actual available viewport |
| Home screen widget | Configure a saved controller and power or preset action; real command/readback and explicit unconfirmed-result state; uses Room palette |
| Existing Room features | Remotes/Music/Routines/System screens and pinned transport retained; their firmware contract is preserved in the migration document |

Firmware updates use the selected controller's actual update/upload workflow. Room does not
automatically fetch and install a generic stock WLED binary over the combined Room firmware.
Any retained signed-update/physical-approval policy must be reconciled on the firmware side,
as specified in the migration document. The app cannot enforce an ESP32 policy that firmware bypasses.

All advanced capabilities depend on the selected WLED build and hardware. For example,
AudioReactive must be included/configured, and physical microphone/strip/IR operation must
be verified on the board. Loading a settings page is not proof of those hardware capabilities.

## Source ownership and references

The app integration is original Room client code; it does not modify either reviewed upstream checkout.
The complete WLED UI comes from the actual controller and keeps the selected firmware's feature set.
The optional preview for the older Room screens remains explicitly a preview; the WLED route
never seeds a simulated controller or reports a fake successful connection.

- Protocol reference: https://kno.wled.ge/interfaces/json-api/
- State update reference: https://kno.wled.ge/interfaces/websocket/
- Mobile integration reference: https://github.com/Moustachauve/WLED-Android
- Firmware/UI source reference: https://github.com/wled/WLED

Main implementation files are `WledProtocol.kt`, `WledModel.kt`, `WledScreens.kt`,
`WledBrowser.kt`, `WledTheme.kt` and `WledWidget.kt` in the Android Room package.

## Validation and limits

Final APK SHA-256:
`917a4bdba23b4f7c9e2b7042abb365e52204fbe8752b0b5d9a0407d2d2d076c4`

- Built from a frozen, hash-verified Android source snapshot; source ZIP and build/test/lint
  reports are published beside the APK through the existing final-build script.
- All **27 JVM tests pass**, including nine new WLED protocol tests covering URL/origin
  validation, state identification, POST/readback, rejection, redirect prevention and no POST replay.
- Lint: **zero errors**, 27 warnings and four informational findings. No new errors were suppressed.
- Emulator checks use `scripts/wled-qa-server.py`, a **localhost-only contract fixture**, and
  the reviewed upstream WLED web files served read-only. The fixture and QA helpers are not
  APK assets and are not ESP32 firmware.
- On the packaged candidate, native preset selection sent a real HTTP `ps` command; the
  actual upstream panel sent power/effect commands to the fixture; Room styling was inspected.
- Backup export opened Android's save picker and wrote the exact fixture JSON to Downloads.
  Restore selected that JSON through Android's picker, exposed the correct 101 file bytes to
  WLED's form and sent its multipart upload through the original WLED upload function.
- No board output, real appliance reception, live Spotify account, new combined firmware,
  physical buttons/OLED, on-device OTA or router mDNS discovery was certified by these tests.
  The firmware handover contains the hardware acceptance checklist.

The current old Room-only ESP32 firmware cannot serve these WLED features until migrated.
An unsuccessful WLED connection before migration is expected and is reported as an error.
