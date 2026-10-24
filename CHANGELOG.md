# SmartRoom version and Android release ledger

Current synchronization/documentation delivery: **Version 30 (V30)**, recorded
25 October 2026, Asia/Calcutta. `VERSION.md` records the project delivery;
this file records the exact app releases and firmware baselines alongside them.

## Current baseline

| Version type | Recorded value | Meaning |
| --- | --- | --- |
| Project delivery | **V30** | Native Android codebase import, Spotify developer Client ID removal & PKCE security protection |
| Android versionName | **0.5.6** | Updated semantic version in android/app/build.gradle.kts |
| Android versionCode | **27** | Strictly increasing Android build code |
| App release ID | **ROOM-ANDROID-0.5.6+27** | Version and build identifying this codebase |
| Canonical ESP32 source | **2.0.2-wled-room** | Version 11 WLED+Room firmware with native AudioReactive DSP & Spotify controls |
| Secondary ESP32 mirror | **2.0.1-wled-room** | Previous V10 firmware build |
| Installed ESP32 | **2.0.1-wled-room** | Confirmed on COM5 board at 192.168.1.103 |
| Installed phone app | **Not queried** | Codebase delivery is not phone installation |
| OTA monotonic counter | **Not queried or changed** | Independent device-side counter, not Android versionCode |
| WLED/Room combined firmware | **Ready** | Dual OTA slots populated; 2.0.2-wled-room compiled |

The final APK alias remains `artifacts/RoomController-final.apk`. Use the immutable
versioned path in the release entry below when identifying or retaining this exact release.
Its SHA-256 is `917a4bdba23b4f7c9e2b7042abb365e52204fbe8752b0b5d9a0407d2d2d076c4`.

## Numbering rules for future work

1. Increase the project delivery sequentially for each changed codebase delivery: V7 → V8 → V9.
   Update `VERSION.md`, the main sync handoff and the delivery entry point together.
2. Every newly generated/released APK must use a **strictly larger Android versionCode**.
   The next available code after this release is **6**. Do not give different APK bytes code 5.
3. Update Android versionName when app code changes: patch `0.3.1` for fixes, minor `0.4.0`
   for an appropriate feature release, major for an intentionally breaking app release.
   Choose the actual scope before bumping; these next names are examples, not releases.
4. Set both fields in `android/app/build.gradle.kts` before building. Changing a Markdown
   label or APK filename does not change the version installed on Android.
5. Keep project delivery, app versionName/code, firmware runtime version and OTA counter separate.
   A firmware-only delivery does not require rebuilding or falsely renumbering an unchanged APK.
6. Use `python scripts/build-final-apk.py` for new app releases. It now rejects a reused/lower
   app code before building, records the app/project versions in the new embedded manifest,
   performs its existing checks, and preserves a versioned APK, source ZIP and verification report.
7. The machine-readable app history is `artifacts/android-release-history.json`.
   `scripts/app_release_version.py` appends the exact release entry to this file after a successful build.
   It does not automatically assign a firmware compatibility claim or mark devices as installed.
8. Append app/ESP32 contract changes, required firmware baseline and actual acceptance results
   to `ESP32_APP_SYNC_CONTEXT.md`. Preserve its existing content and clearly distinguish
   implementation, compiled evidence, installation and physical validation.
9. Use `python scripts/app_release_version.py --record-existing` only to archive an already
   verified existing final APK, as done for the baseline below. Re-recording the same known
   bytes is not a new build; different bytes under an existing code are rejected.

## Historical delivery context

- V4: earlier named codebase and synchronization handoff.
- V5: obsolete ESP32 copies archived without implementation changes.
- V6: canonical ESP32 source advanced to `1.1.4-music`; Android remained `0.3.0` / code 5.
- V7: current documentation/release-tracking delivery; component source versions remain unchanged.

Full app feature inventory, endpoint requirements, verification and current compatibility
limits are appended as section 10 in `ESP32_APP_SYNC_CONTEXT.md`.

## Recorded APK: ROOM-ANDROID-0.3.0+5 — project delivery V7

- Android versionName: `0.3.0`; versionCode: `5`.
- Immutable APK: `artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5.apk`.
- SHA-256: `917a4bdba23b4f7c9e2b7042abb365e52204fbe8752b0b5d9a0407d2d2d076c4`.
- Frozen build snapshot: `20261021T114733Z`; tests: 27; lint: {'Warning': 27, 'Information': 4}.
- Firmware source when registered: `1.1.4-music`. This does not assert installation or hardware compatibility.
- Source and build evidence: `artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5-release.json`.

## V8 — standalone TheRoom repository organization

Project delivery **V8**. Android remains **0.3.0 / code 5**; ESP32 source remains **1.1.4-music**. No component rebuild or installation. `android/` is the standalone Git root, `apks/` holds Room APK copies, and `knowledge/` holds indexed reference copies. Personal APKs retain their original hashes and credentials; repository must remain private. The next newly released Android build still needs code 6 or higher. See section 11 of the sync handoff.

## V9 — AI Studio preview compatibility and missing-asset resilience

Project delivery **V9**. Android source updated to **0.3.1 / versionCode 6**. When `app/src/main/assets/room-device.config` is absent (such as in unconfigured clones or the AI Studio cloud emulator), `RoomModel` no longer throws `FileNotFoundException` on startup; instead it leaves `configuredDevice` null without inventing board identity and enters the built-in in-memory UI preview (`demo()`), while preserving full pairing behavior when `room-device.config` is present. ESP32 source remains **1.1.4-music**; no board flash or phone installation performed. See section 12 of the sync handoff.

## V10 — LED strip color wheel control with zero black

Project delivery **V10**. Android source updated to **0.3.2 / versionCode 7**. Implemented `LedWheelColorPicker` matching the reference LED hardware controller UI: circular Hue-Saturation disc without black (white center, pure saturated perimeter), brightness and saturation gradient sliders, two rows of quick color swatches (including black/off and dynamic rainbow "R"), and circular `Fx` (music reactive effects) and `Bg` (solid color) mode buttons. ESP32 source remains **1.1.4-music**; no board flash or phone installation performed. See section 13 of the sync handoff.

## V11 — Spotify track artwork and album photo rendering

Project delivery **V11**. Android source updated to **0.3.3 / versionCode 8**. Integrated Coil (`AsyncImage`) for track and album artwork display across all Spotify views. Added multi-path cover image resolution (`image_url`, `artwork_url`, `artwork`, nested `album.images`), modern rounded cover card in `MusicPlayerWidget` with gradient depth overlay and interactive tap-to-zoom (190.dp / 230.dp), vinyl canvas fallback when offline or missing artwork, track cover thumbnail in `DeviceMusicScreen`, item thumbnails in Spotify browse/search lists, and rich album artwork fixtures in `PreviewData`. ESP32 source remains **1.1.4-music**; no board flash or phone installation performed. See section 14 of the sync handoff.

## V12 — Fully functional Routines menu redesign and rich customization

Project delivery **V12**. Android source updated to **0.3.4 / versionCode 9**. Redesigned `RoutinesScreen` and `RuleDialog` into a comprehensive, responsive automation suite. Key updates:
- Added interactive "Run now" execution directly on routine cards, executing lighting, IR appliance signals, and Spotify music with instant visual feedback and state refresh.
- Added live "Test this action right now" testing inside the routine creator dialog so users can preview physical changes before saving.
- Added a 1-tap quick-start templates carousel (Morning Wakeup, Evening Wind-Down, Music Bass Sync, Bedtime All Off).
- Added comprehensive lighting customization: power toggle, 8 vibrant color palette swatches, 1-100% brightness slider, dynamic effects (solid, bass reactive, beat reactive, level reactive, bands), speed and intensity controls, or saved presets.
- Added infrared appliance command actions and Spotify music actions.
- Added rich trigger options: schedule with time chips and weekday presets (Daily, Weekdays, Weekends), sound started, sound stopped, IR remote signal received, and one-tap manual rituals.
- Added master automation status banner with Arm / Pause toggle and controller timezone indicator.
ESP32 source remains **1.1.4-music**; no board flash or phone installation performed. See section 15 of the sync handoff.

## V13 — Paused automation visual disabling and settings cleanup

Project delivery **V13**. Android source updated to **0.3.5 / versionCode 10**.
- When automations are paused, all routine cards and quick-start templates are visibly grayed out (`alpha(0.45f)`), and all interactive controls (routine toggle, "Run now", "Edit", "Delete", and template cards) are disabled to prevent accidental execution. An informative pause callout banner and distinct "PAUSED" badges are shown.
- Removed connection method instructions (`ConnectionGuides`) from the lower section of Settings across direct device and service screens, streamlining the Settings view to core connection credentials, preferences, backup, updates, and diagnostics.
ESP32 source remains **1.1.4-music**; no board flash or phone installation performed. See section 16 of the sync handoff.

## V14 — Complete native in-app WLED integration, WebView removal, and ESP32 HTTPS framing repair

Project delivery **V14**. Android source updated to **0.4.0 / versionCode 11**.
- Removed `WledBrowser.kt`, `WledTheme.kt`, and `androidx.webkit` dependency; zero WLED WebView, Custom Tab, or external browser redirects remain reachable.
- Added exhaustive control parity inventory in `knowledge/docs/WLED_PARITY_MATRIX.md` and `artifacts/WLED_PARITY_INVENTORY.md`.
- Implemented full native WLED lighting workspace (`WledScreens.kt`, `WledProtocol.kt`, `WledConfig.kt`, `WledModel.kt`), native WLED `AudioReactive` + Room quiet-behavior tuning (`SoundTuning.kt`), and native WLED `/json/cfg` controller settings & backup/restore (`WledSettingsScreens.kt`, `DeviceScreens.kt`).
- Repaired ESP32 HTTPS incomplete-read / socket-pool timeout handling in `DeviceTransport.kt` and `BoundedInput.kt` (`Connection: close`, serialized request mutex, and `Content-Length`-bounded stream reads).
- See section 17 of `ESP32_APP_SYNC_CONTEXT.md` for full architecture, parity coverage, verification results, and remaining physical hardware checks.

## V15 — WLED UI/UX parity polish, persistent tab/preset state, luminous LED strip peek, universal Home favorites, and centered Light nav

Project delivery **V15**. Android source updated to **0.4.1 / versionCode 12**.
- Reordered bottom navigation bar to `1. Home`, `2. Music`, `3. Light` (centered), `4. Remote`, `5. More`.
- Persisted all active tabs, sub-tabs (`Colour`, `Effects`, `Segments`, `Presets`, `Controllers`), color slots, effect filters, selected segments, WLED config sections, and optimistic ESP32 lighting state/presets across navigation and app restarts.
- Implemented `luminousLedColor` and `computeStripPreviewPixels` for bright, hardware-accurate WLED LED strip peek and palette previews, compact `SegmentTargetSelectorCard` pills without blank space gaps, spring-animated buttons/chips (`livelyClick` / `livelyPressScale`), and universal Favorites across WLED Presets, Playlists, Effects, Palettes, Colour Swatches, Room Presets, and IR Commands with automatic jump to Home's Favorites Within Reach section.
- See section 18 of `ESP32_APP_SYNC_CONTEXT.md`.

## V16 — Jetpack DataStore local persistence layer and Home preset category selector

Project delivery **V16**. Android source updated to **0.4.2 / versionCode 13**.
- Added Jetpack DataStore (`androidx.datastore:datastore-preferences:1.1.1`) via `RoomDataStore.kt` and integrated it with `WledModel` and `RoomModel` so the selected `Light` control tab (`activeTab`), `pcMode`, `colorSlot`, `effectFilter`, `configSection`, top-level navigation page (`selectedPage`), selected Home preset category (`homePresetCategory`), custom preset category assignments (`presetCategories`), user-defined WLED snapshot/presets/config, Room state, and favorites persist reliably across app restarts.
- Implemented `HomePresetCategorySection` on the `Home` screen (`ControlScreens.kt`) and category assignment on `WledPresetsAndPlaylistsTab` (`WledScreens.kt`) to organize saved WLED presets, playlists, and Room scenes into `All`, `Favorites`, `Ambient`, `Vibrant & FX`, `Music & Sound`, `Focus & Work`, `Playlists`, and `Room Scenes`.
- See section 19 of `ESP32_APP_SYNC_CONTEXT.md`.

## V17 — Animated navigation bar, zero-jitter smooth sliders, uncluttered Home screen redesign, and inbuilt ESP32 music response pipeline in Lights

Project delivery **V17**. Android source updated to **0.4.3 / versionCode 14**.
- Added subtle spring scale (`animateFloatAsState`) and color transition (`animateColorAsState`) animations to the 5 bottom navigation bar items (`Home`, `Music`, `Light`, `Remote`, `More`) in `MainActivity.kt`.
- Eliminated slider lag and thumb jitter across `LedSlider`, `Slider05`, `BrightnessControl`, `VolumeControl`, `SettingSlider`, and `LedWheelColorPicker` using local drag state, unified `awaitPointerEventScope` touch tracking, `IntOffset` placement-phase thumb positioning, 24 ms outbound throttling, 220 ms post-release settle lockouts, and debounced persistence in `WledModel` and `RoomModel`.
- Redesigned `HomeScreen` (`ControlScreens.kt`) with an uncluttered hierarchy prioritizing (1) Light Brightness & Music Volume at the top (`HomeBrightnessAndVolumeHeroCard`), (2) Color Correcting Studio (`HomeColorCorrectingCard` with Hue, Kelvin CCT white balance, Saturation, and 6 calibrated swatches), and (3) strictly capped Favorites (`MAX_HOME_FAVORITES = 4`) and Presets (`MAX_HOME_PRESETS = 4`).
- Added the inbuilt native ESP32 music response pipeline selector and `Esp32NativeMusicPipelineTab` in `WledScreens.kt` for `SmartRoomController` `1.1.4-music` (`MusicAudioDSP` Core 0 INMP441 I²S 16 kHz / 512 samples + `MusicLightingEngine` Core 1 300 px @ 25 ms: `beat`, `bass`, `bands`, `level`, `solid`, `speed` pulse decay, `intensity` response, and INMP441 quiet-room policy), distinct from WLED's AudioReactive effects.
- See section 20 of `ESP32_APP_SYNC_CONTEXT.md`.

## V18 — Spotify local network control component, LiquidToggle & Slider05 wave slider, WS2812B hardware strip visualizer, interactive segment zone/step selectors, fixed card layouts, disabled child states, and 3-band EQ in Settings

Project delivery **V18**. Android source updated to **0.4.4 / versionCode 15**.
- Added `SpotifyLocalNetworkControlCard` (`DesignControls.kt`, `DeviceScreens.kt`, `ServiceScreens.kt`) for local network Spotify playback (`play`, `pause`, `previous`, `next`, `volume`, `refresh`) and integrated `Slider05` (44-bar wave slider with floating tooltip) for Music Volume on Home and Music screens.
- Upgraded `LiquidToggle` (`DesignControls.kt`) to the exact 52x32 2-circle gooey morph + `dropCircle` design (`default`, `success`, `warning`, `danger`, `lime` variants) and applied it to all toggle switches across the app.
- Fixed `Vol 100%` chip wrapping on Home (`ControlScreens.kt`), fixed Routine cards changing height when toggled off (`AutomationScreens.kt`), fixed `Esp32MusicPipelineQuickBanner` changing height when switching modes (`WledScreens.kt`), and fixed the `Delete` button squishing in `WledSegmentsTab` (`WledScreens.kt`).
- Added interactive **Zone Boundary Presets** and **Step-Determination Mode Selectors** (`Grouping`, `Spacing`, `Reverse`, `Mirror` with live logical step formula readout) to `WledSegmentsTab`, and upgraded `WledAnimatedStripCanvas` to a discrete SMD5050 WS2812B hardware strip representation.
- Disabled and dimmed child controls whenever a parent section toggle is OFF, preserved `bass_gain`, `mid_gain`, and `treble_gain` in `RoomModel` and `SoundTuning`, and added `RoomThreeBandEqSettingsCard` (Bass, Mid, Treble `0–200%` + presets) to Settings and `Esp32NativeMusicPipelineTab`.
- See section 21 of `ESP32_APP_SYNC_CONTEXT.md`.

## V19 — Spotify linking in Settings, master Automation Active toggle for all routines, theme-colored LiquidToggle without stray dot, redesigned Config page, and full layout/truncation fixes

Project delivery **V19**. Android source updated to **0.4.5 / versionCode 16**.
- Moved the Connect Spotify setup card out of `MusicScreen` / `DeviceMusicScreen` into Settings (`SystemScreen` and `DeviceSystemScreen` via `SpotifyAccountSettingsCard`).
- Updated the `Automations active` toggle in `RoutinesScreen` (`AutomationScreens.kt`, `RoomModel.kt`) to turn ON and OFF all routines simultaneously via `setAllRoutinesEnabled(enabled)`.
- Updated `LiquidToggle` (`DesignControls.kt`) to use the active theme accent color (`Lime`), removed the white `dropCircle` dot above the white thumb circle, and locked `Modifier.requiredSize(52.dp, 32.dp)` to prevent toggle deformation.
- Redesigned `WledControllerSettingsSection` (`WledSettingsScreens.kt`) with a 2x4 category grid and `SettingToggleRow` across all toggles, fixed all 6 screenshot layout/button/truncation issues in `WledScreens.kt`, `WledSettingsScreens.kt`, `ControlScreens.kt`, and `SoundTuning.kt`, and ensured 100% end-to-end functionality across all `/v1/command` and `/actions` operations.
- See section 22 of `ESP32_APP_SYNC_CONTEXT.md`.

## V20 — Slider thumb touch protection, glitch-free Home brightness, 4-favorite limit & warning dialog, unified 300-LED strip preview, single Music controller with active device dropdown, synced artwork, instant volume & IR speaker volume control

Project delivery **V20**. Android source updated to **0.4.6 / versionCode 17**.
- Added relative-delta slider thumb attachment (`DesignControls.kt`) so touching a slider thumb never jumps the value, extended post-drag settle lockout to `750ms`, eliminated the DataStore echo collect loop in `WledModel` and `RoomModel`, and stabilized the top progress bar in `MainActivity.kt` so dragging Home brightness never glitches.
- Surfaced all favorited items (WLED Presets, Room Scenes, Effects, Sound Modes, Palettes, Colors) in Home Quick Presets (`HomePresetCategorySection`) and category chips, capped displayed presets at `4` (with `Page X/Y` cycling), and added a `Favorite Limit Reached (4/4)` warning dialog when trying to add a 5th favorite.
- Fixed Screenshot 1 (`"Color Studio"` header), Screenshot 2 (concise `"ESP32 Music"` / `"WLED FX"` boxes without verbose explanation), Screenshot 3 (equal-width single-line `"Start Timer"` and `"Stop Timer"` buttons), Screenshot 4 (shortened `"Wi-Fi"`, `"LEDs"`, `"2D"`, `"UI"`, `"UDP"`, `"Time"`, `"Audio"`, `"Security"` config tabs), and Screenshot 5 (unified 300-LED strip preview so the second half is no longer stuck pink).
- Removed the duplicate first music controller card on the Music screen, added an interactive active-device dropdown (`SpotifyDeviceDropdownSelector`) on the speaker chip inside `MusicPlayerCard`, synced album cover art (`AsyncImage`) with the active track, removed `"Set volume"` and `"Refresh"` buttons in favor of instantaneous volume updates on drag, and added `SpeakerIrVolumeControl` for physical speaker/device volume via IR (`IR Vol −`, `Mute`, `IR Vol +`, and slider).
- See section 23 of `ESP32_APP_SYNC_CONTEXT.md`.

## V21 — Mute-locked volume slider, Obsidian black & Pearl light theme contrast fixes, all 8 screenshot fixes, controllable 3-band EQ sliders, scrollable Quick Presets, editable playlists, Reactor Power button, LimelightNav bar, Rotary Time Picker & IR Receive signal/reaction telemetry

Project delivery **V21**. Android source updated to **0.4.7 / versionCode 18**.
- Locked the speaker volume slider (`Slider05`) and `IR Vol −` / `IR Vol +` buttons whenever `Mute` is active (`speaker_muted`), added the pure-black high-contrast `Black` (`obsidian`) theme in `Appearance.kt`, and made `Slider05` theme-aware so the volume wave rocker has crisp contrast in Light (`Pearl`) theme.
- Resolved all 8 screenshot issues: removed the 2 extra DSP/Engine info rows and made `Bass`, `Mid`, and `Treble` interactive `0–200%` sliders in `Esp32NativeMusicPipelineTab` (Screenshot 1); fixed the `100%` brightness preset button, redesigned `ESP32 Music` vs `WLED FX` into a zero-gap segmented pill switcher, and put sub-tabs in a single-row `horizontalScroll` (Screenshot 2); shortened `"Apply to ES..."` to `"Apply ESP32"` (Screenshot 3); fixed `"Quick Presets"` heading and replaced pagination with smooth vertical scrolling + horizontal category scroll (Screenshot 4); removed `"PRIMARY CONTROL"` labels, added `SpeakerIrVolumeControl` directly to the Home hero card, and fixed `Color Studio` hex badge (Screenshot 5); enabled saving and editing playlists in `WledPresetsAndPlaylistsTab` (Screenshot 6); shortened `"Save Security"` to `"Save Lock"` (Screenshot 7); and fixed `"Show all 170"` vertical character wrapping in `WledEffectsTab` (Screenshot 8).
- Redesigned the `Light` section power button into an illuminated reactor bar, integrated `/components/ui/limelight-nav.tsx` + `/components/ui/demo.tsx` and `LimelightNavBar` in Compose, replaced the schedule time text box with `RotaryTimePicker` (drum wheels + circular rotary jog dial), and added `IrReceiverTelemetryCard` & `IrTriggerDefinitionSection` in `AutomationScreens.kt`.
- See section 24 of `ESP32_APP_SYNC_CONTEXT.md`.

## V22 — Instantaneous bottom navigation, minimalist hardware RotaryTimePicker, complete static 2-column IR command grid without GPIO noise, aligned Segment/Music DSP/WLED Top Bar layouts, dismissible Snackbar cross button, Deep Black + Champagne Gold theme, and full ESP32 command & connection specification

Project delivery **V22**. Android source updated to **0.4.8 / versionCode 19**.
- Made bottom navigation (`LimelightNavBar` in `DesignControls.kt` and `MainActivity.kt`) instantaneous with zero delay by decoupling page selection from DataStore I/O (`LaunchedEffect(page)`), retaining per-page `ScrollState`s, and using a critically damped spring (`stiffness = 1500f`).
- Fixed all 5 screenshot issues:
  - Screenshot 1 (`WledSegmentsTab` in `WledScreens.kt`): Replaced truncated `"HOW LOGICAL STEPS ARE DETERMINED ON THIS ..."` with `"LOGICAL STEP MODE"`, arranged Zone Boundary Presets (3×2), Step-Determination Modes (2-column + full-width), and Segment Flags (2×2) in equal-width non-wrapping grids, fixed `"75 logical steps"` 3-line wrapping into a single-line `"75 steps"` pill, and placed `Start LED`, `Stop LED`, `Grouping`, `Spacing` in a 2×2 grid so labels never clip.
  - Screenshot 2 (`RotaryTimePicker` in `DesignControls.kt` & `RuleDialog` in `AutomationScreens.kt`): Fixed `"Minutes"` wrapping into 3 lines (`Mi / nu / tes`), removed the duplicate outer preset row, aligned `Every day / Weekdays / Weekends` (3 equal columns) and `Mon..Sun` (7 equal columns), and redesigned `RotaryTimePicker` into a minimalist, luxury hardware time setter with interactive `HH : MM` `RotaryDigitBox` readouts, a clean 12-tick circular dial, `−1` / `+1` / `+15m` steppers, and a single 5-chip preset row.
  - Screenshot 3 (`IrTriggerDefinitionSection` & `IrReceiverTelemetryCard` in `AutomationScreens.kt`, `RoomModel.kt`, `PreviewData.kt`): Seeded all 6 standard IR commands (`power`, `fan-speed-up`, `fan-speed-down`, `volume-up`, `volume-down`, `speaker-mute`), placed all IR commands in a static 2-column grid with zero horizontal or vertical scrolling, and removed the `GPIO 34` / `38 kHz DEMOD` / verbose explanation block.
  - Screenshot 4 (`Esp32NativeMusicPipelineTab` in `WledScreens.kt`): Prevented `"Mids #6 (Vocals / Snare)"` + `"FFT 120% · Gain 200%"` from wrapping onto 2–3 lines by enforcing single-line weighted layout across all 3 EQ band sliders (`Bass #0`, `Mids #6`, `Treble #13`).
  - Screenshot 5 (`WledTopActionBar` in `WledScreens.kt`): Removed the `"..."` description under `"Bedroom WLED"`, redesigned the `Light` power bar into a sleek minimalist single-line `"Power"` + `"ON"`/`"OFF"` control without verbose subtext, and arranged `Timer`, `Sync`, `Peek`, `Info`, `Config`, `PC Mode` into a non-clipping 3×2 grid.
- Added an immediate **`✕` (Close) button** (`testTag = "snackbar_close_button"`) on popup notifications (`SnackbarHost` in `MainActivity.kt`).
- Updated the **Black (`obsidian`) theme** (`Appearance.kt`) to pure deep black (`#000000` background, `#070707` surface, `#1C1C1C` line) with a warm **Champagne Gold (`#E5C158`)** accent instead of blue/cyan, and changed `LiquidToggle` inactive track color to neutral `Line`.
- Documented every app code change and the complete ESP32 connection & command specification in section 25 of `ESP32_APP_SYNC_CONTEXT.md`.

## V23 — DataStore configuration backup & restore, instantaneous zero-delay tab navigation, 12-hour / 24-hour clock format in Routines & Settings, standalone aligned Playlist creation card, and removal of IR simulation card with authentic recorded IR commands

Project delivery **V23**. Android source updated to **0.4.9 / versionCode 20**.
- Implemented DataStore configuration backup and restore (`RoomDataStore.kt`, `RoomModel.kt`, `WledModel.kt`, `ServiceScreens.kt`, `DeviceScreens.kt`) allowing users to export their complete room controller + WLED configuration to a `.json` file, clipboard/JSON editor, or local DataStore snapshot and re-import it on any device.
- Eliminated the 1.5-second bottom navigation lag (`RoomModel.kt`, `WledModel.kt`, `MainActivity.kt`, `ControlScreens.kt`, `ServiceScreens.kt`) by moving `SharedPreferences` and `DataStore` writes off the main thread onto `Dispatchers.IO`, avoiding full-state `JSONObject` re-cloning on tab switches, caching `presetEntries` on `WledUi`, and making initial `MusicScreen` playback refresh silent.
- Added 24-hour (default) and 12-hour (`AM`/`PM`) clock system support in `RotaryTimePicker` (`DesignControls.kt`), `RoutinesScreen` / `RuleDialog` (`AutomationScreens.kt`), and Settings (`ClockFormatSettingSelector` in `ServiceScreens.kt`, `DeviceScreens.kt`, and `WledTimeSettingsCard` in `WledSettingsScreens.kt`).
- Fixed Screenshot 1 (`WledPresetsAndPlaylistsTab` in `WledScreens.kt`): refactored saved playlists into a 2-row card layout so titles never wrap vertically 1 word per line, and separated **"Create or Edit Playlist"** into its own standalone `RoomCard` with aligned fields and buttons.
- Fixed Screenshot 2 (`RemotesScreen` & `RuleDialog` in `AutomationScreens.kt`, `PreviewData.kt`): removed the `IrReceiverTelemetryCard` (`"IR Receiver & Signal Test"` / `"Simulate IR Receive"`) section from `RemotesScreen`, and ensured IR command lists in `RemotesScreen` and `RuleDialog` reflect only actual recorded/saved commands in `commands` (preserving user deletions and newly learned `NEC 38 kHz` commands).
- See section 26 of `ESP32_APP_SYNC_CONTEXT.md`.

## V24 — Unique minimalistic routine card icons, universal tactile haptic feedback, foreground MediaSession service, WledWidget rounded-corner & DataStore fixes, dual HTTP/HTTPS LAN pairing, and full audit resolution

Project delivery **V24**. Android source updated to **0.5.0 / versionCode 21**.
- Assigned unique, minimalistic vector icons (`ROUTINE_ICON_CATALOG` + `assignUniqueRoutineIcons`) to each routine card across `HomeScreen` (`HomeRoutinesDashboardCard` and `HomeUniversalFavoritesSection` in `ControlScreens.kt`) and `RoutinesScreen` / `RuleDialog` (`AutomationScreens.kt`), with an interactive icon picker in the routine editor.
- Added subtle haptic feedback (`performSubtleHaptic` + `VIBRATE` permission) across all interactive buttons, toggles (`LiquidToggle`), navigation items (`LimelightNavBar`), time picker controls (`RotaryTimePicker`), volume steppers, and transport controls.
- Resolved all items in the comprehensive architecture & bug audit (`Paste_October_25__2026_-_12_43AM_8a0e49d9`):
  - Decoupled `pairingVault` from requiring a bundled `assets/room-device.config` and added manual LAN IP/hostname + token pairing and `pairing.json` import in `PairDeviceScreen` (`DeviceScreens.kt`, `RoomModel.kt`, `DeviceTransport.kt`).
  - Enabled dual HTTPS (`443`) + HTTP (`80`) LAN transport with `Authorization: Bearer <token>` and `X-API-Key: <token>` headers (`DeviceTransport.kt`) and translated all legacy companion endpoints to `/v1/state` and `/v1/command` in `RoomModel.deviceRequest()`.
  - Fixed `WledWidget.kt` rounded corner clipping (`setBackgroundResource` with `@drawable/wled_widget_background_light` and `@drawable/wled_widget_background_obsidian`), background coroutine resilience (`SupervisorJob() + Dispatchers.IO`), preset verification, and DataStore device synchronization (`RoomDataStore.readDevicesJson`).
  - Added `RoomMediaPlaybackService` (Foreground `MediaSession` + `Notification.MediaStyle`), replaced hardcoded `progress_ms = 41000` / `duration_ms = 210000` in `DeviceMusicScreen` with real-time 250ms playback extrapolation in `MusicPlayerWidget`, and wired `seek`, `shuffle`, and `repeat` end-to-end.
  - Unwrapped `"cfg"` in `WledApi.saveConfig` (`WledProtocol.kt`), normalized `lighting` and `rules` payloads (`sequence` compound actions, Spotify `action`/`argument`/`value`, and `manual` trigger fallback), enabled saving clock settings when initial `settings` is empty, and surfaced `runtime.message`/`ir_error` in `DeviceLearnDialog`.
- See section 27 of `ESP32_APP_SYNC_CONTEXT.md`.

## V25 — Spotify DataStore OAuth connection flow & basic playback control UI, full Restore Snapshot & Light Brightness 100% labels, unclipped full-width Routine RotaryTimePicker, and atomic WLED playlist saving

Project delivery **V25**. Android source updated to **0.5.1 / versionCode 22**.
- Implemented a Spotify OAuth connection flow backed by Jetpack DataStore (`RoomDataStore.kt`, `RoomModel.kt`, `SpotifyConnectActivity.kt`, `ServiceScreens.kt`) with Android Keystore AES-GCM token encryption and an embedded **Basic Playback Control UI** (active track/artist/device readout, Previous / Play-Pause / Next buttons, and Spotify volume slider).
- Fixed `"Restore Snapsh"` and `"Configuration Backup ..."` truncation in `DataStoreBackupRestoreCard` (`ServiceScreens.kt`) by giving `Save Snapshot` and `Restore Snapshot` a dedicated 2-column row (`Modifier.weight(1f)` each) and `Copy / Paste Backup JSON` a full-width row.
- Redesigned `RotaryTimePicker` (`DesignControls.kt`) into a stacked full-width vertical layout (`[12-Hour | 24-Hour]` bar, `[Hours | Minutes]` bar, wide `HOUR : MINUTE` digital boxes, and centered `136.dp` rotary dial) so neither the selection bars nor the time digits are ever half-cut inside `RuleDialog`.
- Fixed WLED playlist saving and editing (`WledModel.kt`, `WledCatalogDefaults.kt`, `WledScreens.kt`) by processing `psave` before `ps`/`pl`, preserving user-saved preset/playlist slots across asynchronous `/presets.json` refreshes (`mergePresetsPreservingUserSaves`), displaying step duration and transition (`s/step · s fade`) on `Saved Playlists` cards, adding an inline save confirmation banner, and adding bottom padding so the snackbar never overlaps `Save #ID` / `Save & Play`.
- Adjusted `HomeBrightnessAndVolumeHeroCard` (`ControlScreens.kt`) so `"Light Brightness"` and `"Music Volume"` remain 100% displayed without `".."` truncation when the readout reaches `"100%"`.
- See section 28 of `ESP32_APP_SYNC_CONTEXT.md`.

## V26 — Spotify local HTTP & flexible pairing, runtime POST_NOTIFICATIONS, standalone background media controls, and WLED widget styling

Project delivery **V26**. Android source updated to **0.5.2 / versionCode 23**.
- Updated `SpotifyConnectActivity.kt` to use `DevicePairing.parseFlexible` and accept both `https://` and `http://` controller endpoints, enabling Spotify PKCE on manual LAN IP and HTTP port 80 connections without crashing.
- Added runtime `POST_NOTIFICATIONS` permission request in `MainActivity.kt` (`RoomApp`) for Android 13, 14, and 15 (SDK 35) to prevent silent suppression of lock-screen media playback controls.
- Implemented standalone background ESP32 media command dispatch via `DeviceTransport` in `RoomMediaPlaybackService.kt` when the in-app callback is null, attached `setDeleteIntent` with `ACTION_STOP_SERVICE` to allow cleanly dismissing paused media notifications, and added `stopMediaPlayback(context)` helper.
- Updated `configuredDevice` parsing in `RoomModel.kt` to use `DevicePairing.parseFlexible(it)`, and triggered `RoomMediaPlaybackService.stopMediaPlayback()` upon logout and Spotify disconnection.
- Removed `setBackgroundColor` in `WledWidget.kt` to preserve native rounded pill button geometry on the home screen widget, and ensured immediate `WledWidget.refresh` calls in `WledModel.command()`.
- See section 29 of `ESP32_APP_SYNC_CONTEXT.md`.

## V27 — Cold launch media notification gating, unpaired Spotify PKCE linking, token UI clipping fix, and elimination of mock demo data

Project delivery **V27**. Android source updated to **0.5.3 / versionCode 24**.
- Gated `RoomMediaPlaybackService` foreground notifications on cold app launch (`isServiceRunning` tracking in companion object + `syncPlayback` active check) so launching the app when paused never spawns an unprompted "Paused" notification.
- Made controller endpoint check optional in `SpotifyConnectActivity.begin()` to allow Spotify OAuth PKCE linking and encrypted DataStore credential persistence even in demo or unpaired mode.
- Restructured `SpotifyAccountSettingsCard` (`ServiceScreens.kt`) and `MainActivity.kt`:
  - Resolved OutlinedTextField token label clipping (`"OAuth Access Token (encrypted in DataSt..."`) by simplifying labels to `"OAuth Access Token"` and `"OAuth Refresh Token"` with informative supporting text.
  - Eliminated button and song title overlap by dedicating the full width of the card to track & artist metadata, and placing a centered, well-proportioned transport bar below with subtle haptic feedback.
  - Allowed `SmallLabel` to display 2 lines with soft wrapping to prevent `"..."` truncation.
- Completely removed demo mock data across the app:
  - Cleared synthetic IR appliances (`"Ceiling fan"`, `"Desk speaker"`) and mock IR commands from `demoState()`.
  - Cleared synthetic device list from `SpotifyDeviceDropdownSelector` in `MusicPlayerWidget.kt`.
  - Removed fallback mock songs (`"Evening Signals"`, `"Warm Desk Ensemble"`) so only real Spotify playback and actual user devices appear.
- See section 30 of `ESP32_APP_SYNC_CONTEXT.md`.

## V28 — Direct Client-Side Spotify Web API Playback, Device Auto-Wake, Full Search & Playlist Browsing, and Independent Transport Control

Project delivery **V28**. Android source updated to **0.5.4 / versionCode 25**.
- Created direct Spotify Web API engine (`SpotifyApiClient.kt`) using encrypted OAuth tokens stored in Jetpack DataStore with automatic token refresh via `refresh_token`.
- Routed `play`, `pause`, `toggle`, `next`, `previous`, `seek`, `shuffle`, `repeat`, `volume`, and `transfer` commands directly to Spotify's official endpoints (`api.spotify.com/v1/me/player/...`), operating seamlessly whether an ESP32 is connected or offline.
- Integrated periodic state polling (2.5s) to synchronize live track metadata, album cover art, playback progress, and volume directly into `RoomModel` and `RoomUi`.
- Implemented smart device auto-wake fallback: if Spotify reports no active player (`NO_ACTIVE_DEVICE` / 404), the client queries `GET /me/player/devices` and automatically targets the first available device.
- See section 31 of `ESP32_APP_SYNC_CONTEXT.md`.

## V29 — Full-Featured Spotify Music Hub with Magic / Smart Shuffle, Liked Songs Library, Multi-Category Search & Live Queue

Project delivery **V29**. Android source updated to **0.5.5 / versionCode 26**.
- Redesigned `MusicScreen` into a minimalist multi-tab Spotify hub with Search, Liked Songs, Playlists, Recent, and Up Next live queue tabs.
- Added 3-way shuffle cycling: Off → Standard Shuffle → Magic Shuffle (tailored song recommendations via `/v1/recommendations?seed_tracks={trackId}`).
- Added heart/favorite button to save or remove the active track from Spotify Liked Songs (`PUT/DELETE /v1/me/tracks`).
- Extended PKCE scopes with `user-library-read` and `user-library-modify`.
- See section 32 of `ESP32_APP_SYNC_CONTEXT.md`.

## V30 — Spotify PKCE Developer Client ID Removal, Protected OAuth Validation & Main Directory Reconciliation

Project delivery **V30**. Android source updated to **0.5.6 / versionCode 27**.
- Removed hardcoded personal developer Client ID from `SpotifyPkce.kt` (`const val CLIENT_ID = ""`) and all guides to prevent unauthorized PKCE usage of the user's Spotify account.
- Enforced strict validation requiring a user-configured Client ID before opening authorization URLs or exchanging tokens.
- `SpotifyConnectActivity.kt` dynamically reads the configured Client ID from `RoomDataStore` / Intent extra (`clientIdInput`) and safely blocks unconfigured attempts.
- Added graceful null-fallback in `SpotifyApiClient.refreshAccessToken` when Client ID is blank.
- Added unit test `SpotifyPkceTest.authorizationRejectsBlankClientId()` verifying that empty/blank Client IDs are rejected.
- Fully reconciled and restored the active Android codebase in the workspace `android/` directory.
- See section 33 of `ESP32_APP_SYNC_CONTEXT.md`.

## V31 — Exhaustive Step-by-Step README Documentation & Developer Guides

Project delivery **V31**. Android source baseline remains **0.5.6 / versionCode 27**. Verified offline APK baseline remains **0.3.0 / versionCode 5**.
- Completely rewrote and expanded `README.md` into an exhaustive, production-grade guide with step-by-step instructions:
  - Complete project architecture and file directory layout.
  - Development prerequisites and system requirements (JDK 17, Android SDK API 34).
  - Step-by-step local build and test execution via Gradle wrapper (`gradlew`) and Python test suites.
  - Comprehensive Spotify Developer App setup guide covering registration, redirect URI configuration (`in.roomcontroller.app://spotify/callback`), User Management whitelisting, and in-app Client ID entry.
  - Step-by-step hardware pairing instructions for ESP32 Room Controller (certificate fingerprint pinning) and WLED (IP discovery & segment configuration).
  - Detailed feature breakdown of 180+ WLED effect engines, palettes, 2D matrix controls, live UDP streaming, vinyl turntable Spotify player, 3-way magic shuffle, liked songs library, and AudioReactive microphone DSP equalizer.
  - Comprehensive troubleshooting matrix addressing common Spotify, build, and hardware networking issues.
  - Security and privacy architecture detailing local-first design and zero hardcoded credentials.
- Updated root `README.md` with modern quick links and cross-references.
- See section 34 of `ESP32_APP_SYNC_CONTEXT.md`.





## V32 — Release Ledger & Documentation Standardization

Project delivery **V32**. Android source baseline remains **0.5.6 / versionCode 27**. Verified offline APK baseline remains **0.3.0 / versionCode 5**.
- Standardized Android release ledger filename: renamed `versions.md` → `CHANGELOG.md`.
- Standardized project delivery tracking filename: renamed `VERSION.md` → `DELIVERY_VERSION.md`.
- Updated release build automation scripts (`scripts/app_release_version.py`) to recognize `CHANGELOG.md` and `DELIVERY_VERSION.md`.
- Updated all cross-references in `README.md` and synchronization handoffs.
- See section 35 of `ESP32_APP_SYNC_CONTEXT.md`.
