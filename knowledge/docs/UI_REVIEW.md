# Android UI review

Open http://localhost:8767 after running `scripts/start-ui-preview.ps1`.
The browser displays and controls the actual native Android APK on the dedicated
RoomQA emulator. It is not an HTML recreation of the app. Screen capture introduces
a short delay; it is suitable for reviewing layout and flows, not animation performance.

Click/tap, drag sliders, scroll using the mouse wheel or buttons, and select fields
before typing. Android Back closes dialogs/keyboard or navigates back. Viewer screen
shortcuts require that any open dialog is closed first. ASCII text can be sent directly;
the Android keyboard remains available for other input.

## UI-first scope

The current task is visual review. No real room backend, hardware control, Spotify
authorization, firmware upload or physical appliance operation is started by this viewer.
The app uses in-memory sample data for playback, devices, playlists, search, learning,
routines and diagnostic states. Sample backup export is explicitly marked as preview
data and is not a real encrypted backup. Resetting a scenario discards sample edits.

Validation for this preview revision: Android debug build, all three existing unit
tests and lint passed (zero lint errors; 18 warnings in the frozen UI revision). Native navigation
through the main feature screens passed. Edge loaded the live APK stream without
script exceptions at desktop and narrow viewport sizes. Local request protections
and sample playback/pause were exercised. Complete hardware/Spotify acceptance and
every possible dialog/state combination remain outside these checks.

The latest UI review adds persistent Forest/Midnight/Ember/Pearl/System themes,
native colour-area/HSV controls, manual-colour exclusion during reactive music mode,
complete preset creation/editing, liquid-style switches, brightness/volume controls
and a disc-style player. Theme persistence, the mode exclusion and saving/reopening
a preset were exercised on the native emulator. The companion backend passed 27
tests, including updating preset behaviour without applying it to the active lights.
Focused native checks also exercised the disc player's pause, shuffle and repeat
controls, and confirmed the selected Pearl theme before restoring Forest/Home.
Fresh emulator screenshots are included as `artifacts/room-ui-*.png`.

Use `artifacts/RoomController-final.apk` for this revision. Its frozen Android
source and SHA-256 manifest are in `artifacts/RoomController-final-source.zip`.
Other chats may continue editing the working tree; that ZIP records what was built.
`scripts/start-ui-preview.ps1` prefers this reviewed APK when it exists. The viewer
uses 1536 MB emulator RAM and two virtual CPU cores; heavy host load can still delay it.
The requested React component reference at http://localhost:8767/components renders
without depending on the emulator and is explicitly labelled as a separate reference.

Use Full preview for all current main screens and controls; Spotify disconnected for
the authorization entry screen; controller-offline and connection-interrupted states
for status banners; Login for the actual login form. The preview launcher does not
enter any real login credentials.

## Current UI coverage to inspect

- Home: status, lighting power/brightness overview, presets, music shortcut, IR favourites.
- Lights: power, palette, custom HEX, brightness, five effects, speed/intensity, preset save/delete.
- Revised lighting: continuous colour area, hue/HSV/HEX inputs and swatches; manual
  colour selection only when Move with music is off. The full preset editor can
  create/edit a complete lighting action independently of the active lights.
- Remotes: appliance selection/add/rename/remove; command favourites/rename/delete;
  learning name/toggle/listening/captured/save states with simulated capture.
- Routines: sample schedule/event cards, creation/edit, weekday/time-zone controls,
  cooldown, lighting/preset or IR action, pause/delete confirmations.
- Music: track/artist/device, play/pause/next/previous, volume, device selection,
  restrictions, playlists/recent/search, load more and speaker-IR volume selection.
- Revised music: disc presentation, timeline, preview shuffle/repeat, and shaped
  volume slider. Live seek/shuffle/repeat are not wired yet.
- Sound: frequency bars, automatic/manual sensitivity, noise floor, smoothing,
  quiet mode/timeout and calibration entry control.
- System: connection/firmware/RSSI/time, preferences, global automation switch,
  document-picker backup/restore and OTA import, diagnostics, sign-out confirmation.
- Connection/entry states: login, Spotify disconnected, controller offline, stale banner.
- Settings: persistent Forest, Midnight, Ember, Pearl and System appearance choices.
  Liquid-style switches are used for on/off controls. The in-app simulation strip
  was removed; the surrounding browser viewer still identifies sample data.

## UI gaps remain visible in the feature catalogue

The current UI does not yet expose every requested device function. Pending UI work
includes native device pairing/certificate and token setup; initial/recovery Wi-Fi
provisioning; verified hardware profile setup; an IR relearn/repeat editor and repeat-safe
controls; immediate learning cancellation; device resume/NTP settings; broader search
scope if selected; and a unified direct-device OTA workflow. The Arduino panel also
needs its own hardware review. These gaps are not reported as complete by the viewer.

See `APP_FEATURES.md` for per-feature statuses and protocol differences. Agree on
the screen structure and requested changes first, then implement the remaining UI
and connect the chosen data/device transport after review.

## Restart and stop

Run `scripts/start-ui-preview.ps1` to restart the local viewer and open the browser.
Logs and process metadata are stored in `.tools/`. `scripts/stop-ui-preview.ps1`
stops only this viewer and its dedicated emulator; it does not stop the shared ADB
server or touch USB-connected phones.
