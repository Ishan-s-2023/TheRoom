# SmartRoom Controller — Version 31 (V31)

Current project delivery, 25 October 2026 (Asia/Calcutta). Previous delivery: V30.

- Delivery version: **V31**; previous delivery: **V30**.
- Fully updated, comprehensive, and step-by-step documentation delivery for **TheRoom** Android application:
  - Completely rewrote and expanded `android/README.md` into an exhaustive, production-grade guide covering:
    - Architecture, tech stack, and module breakdown across Kotlin Compose files.
    - Prerequisites, JDK 17 setup, and step-by-step local build commands with Gradle wrapper.
    - End-to-end Spotify Developer App registration, Redirect URI setup (`in.roomcontroller.app://spotify/callback`), User Management whitelisting, and in-app Client ID configuration.
    - Dual hardware pairing walk-through for ESP32 Room Controller (certificate pinning) and WLED (IP discovery & segment configuration).
    - Detailed feature walk-throughs: 180+ WLED effect engines, palettes, 2D matrix, live UDP streaming, vinyl turntable Spotify player, 3-way magic shuffle, liked songs library, guided IR remote learning, and AudioReactive microphone DSP equalizer.
    - Comprehensive troubleshooting matrices for Spotify OAuth, Android SDK/Gradle build issues, and LAN/Wi-Fi connectivity.
    - Security and privacy model emphasizing local-first architecture and zero hardcoded credentials.
  - Updated root `README.md` with modern quick links and cross-references to the standalone Android repository.
- Android source remains at **0.5.6 / versionCode 27** (unchanged code, updated documentation). Verified binary baseline preserved at `0.3.0 / code 5`.
- Combined ESP32 target firmware contract remains **2.0.2-wled-room**; physical COM5 hardware remains protected and untouched.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 30 (V30)

Current project delivery, 25 October 2026 (Asia/Calcutta). Previous delivery: V29.

- Delivery version: **V30**; previous delivery: **V29**.
- Android source advances to **0.5.6 / versionCode 27**:
  - **Spotify PKCE Developer Client ID Removal & Account Protection (`SpotifyPkce.kt`)**:
    - Deleted the hardcoded personal developer Client ID from `SpotifyPkce.kt` (`const val CLIENT_ID = ""`) and all documentation to prevent unauthorized PKCE usage of the user's Spotify account.
    - Updated `SpotifyPkce.authorization(verifier, state, clientId)` to require a non-blank Client ID.
  - **Dynamic Client ID Lookup in OAuth Flow (`SpotifyConnectActivity.kt`)**:
    - `SpotifyConnectActivity.begin()` dynamically checks for a user-configured Client ID from Intent extras or `RoomDataStore.settingsFlow`.
    - If unconfigured, cleanly halts the flow with the user notification: `"Spotify Client ID is not configured. Enter your Spotify Developer Client ID in Settings first."`
    - Binds the active Client ID to the authorization challenge, code exchange, encrypted DataStore persistence, and direct ESP32 credential provisioning (`op: "spotify_credentials"`).
  - **Graceful Refresh Handling (`SpotifyApiClient.kt`)**:
    - Checks `cleanClientId.ifBlank { SpotifyPkce.CLIENT_ID }` and aborts if empty, eliminating unauthenticated network spam.
  - **Settings Configuration & Connection Guide Alignment (`ServiceScreens.kt`, `ConnectionGuides.kt`)**:
    - Preserved `spotify_client_id_input` in Settings → Connections and passed `clientIdInput` explicitly to `SpotifyConnectActivity`.
    - Updated connection guides and documentation to remove the personal client ID string and direct users to configure their own developer application credentials.
  - **Test Suite Updates (`SpotifyPkceTest.kt`)**:
    - Updated PKCE authorization tests and added explicit assertion verifying that empty/blank Client IDs are rejected.
- Combined ESP32 target firmware contract remains **2.0.2-wled-room** (Version 11); physical COM5 hardware remains protected and untouched.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 29 (V29)

Current project delivery, 25 October 2026 (Asia/Calcutta).

- Delivery version: **V29**; previous delivery: **V28**.
- Android source advances to **0.5.5 / versionCode 26**:
  - **Comprehensive Spotify Music Hub (`ServiceScreens.kt`)**:
    - Redesigned `MusicScreen` into a minimalist, multi-tab Spotify hub with quick tabs:
      - 🔍 **Search**: Real-time Spotify search across tracks, playlists, albums, and artists with filter chips (`All`, `Songs`, `Playlists`, `Albums`, `Artists`), album art, track duration, and one-tap playback or queue addition.
      - ❤️ **Liked Songs**: Direct browsing and playback of saved library songs (`/v1/me/tracks`), featuring one-tap "Shuffle Liked" and individual track play. Handles library scope permission gracefully.
      - 📁 **Playlists**: Full user and followed playlists browser with high-resolution artwork and track counts.
      - 🕒 **Recent**: Browsing history of recently played tracks with timestamps and instant resume.
      - 📋 **Up Next (Queue)**: Real-time display of the live Spotify playback queue (`/v1/me/player/queue`).
  - **3-Way Shuffle & Magic Shuffle / Smart Shuffle (`MusicPlayerWidget.kt`, `SpotifyApiClient.kt`, `ServiceScreens.kt`)**:
    - Added 3-way shuffle cycling: **Off** → **Standard Shuffle** → **✨ Magic Shuffle**.
    - Magic Shuffle sets Spotify shuffle active and generates tailored song recommendations (`/v1/recommendations?seed_tracks={trackId}`) based on the currently playing track, automatically queuing smart discoveries for a seamless flow.
    - Added dedicated Shuffle Mode bar in the Music Hub and animated "Magic Shuffle Active" pill in the mini-player.
  - **Library Save / Like Button (`MusicPlayerWidget.kt`, `SpotifyApiClient.kt`)**:
    - Added heart/favorite button (`Icons.Filled.Favorite` / `Icons.Outlined.FavoriteBorder`) beside the track title with tactile haptics to instantly save or remove the active track from Spotify Liked Songs (`PUT /v1/me/tracks`, `DELETE /v1/me/tracks`).
  - **Enhanced API Client & Scopes (`SpotifyApiClient.kt`, `SpotifyPkce.kt`, `RoomModel.kt`)**:
    - Extended PKCE OAuth scopes to include `user-library-read` and `user-library-modify`.
    - Added client methods: `search(query, type, offset)`, `fetchLikedTracks(offset)`, `fetchQueue()`, `addToQueue(uri)`, `fetchRecommendations(seedTrackId, limit)`, `checkTrackSaved(trackId)`, `saveTrack(trackId)`, and `removeTrack(trackId)`.
    - Extended player commands to support `"magic_shuffle"`, `"queue"`, `"like"`, `"unlike"`, and `"play_liked"`.
- Combined ESP32 target firmware contract remains **2.0.2-wled-room** (Version 11) alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 28 (V28)

Current project delivery, 25 October 2026 (Asia/Calcutta).

- Delivery version: **V28**; previous delivery: **V27**.
- Android source advances to **0.5.4 / versionCode 25**:
  - **Direct Client-Side Spotify Web API Playback (`SpotifyApiClient.kt`, `RoomModel.kt`)**:
    - Created a direct Spotify Web API engine (`SpotifyApiClient`) using encrypted OAuth tokens stored in Jetpack DataStore with automatic PKCE token refresh via `refresh_token` and `SpotifyPkce.CLIENT_ID`.
    - Routes `play`, `pause`, `toggle`, `next`, `previous`, `seek`, `shuffle`, `repeat`, `volume`, and `transfer` commands directly to Spotify's official endpoints (`api.spotify.com/v1/me/player/...`), operating seamlessly whether an ESP32 is connected or offline.
    - Integrated real-time periodic state polling (2.5s) to synchronize live track metadata, album cover art, playback progress, and volume directly into `RoomModel` and `RoomUi`.
  - **Smart Active Device Discovery & Auto-Wake (`SpotifyApiClient.kt`)**:
    - Implemented auto-device fallback in `sendPlayerCommand`: if Spotify reports no active player (`NO_ACTIVE_DEVICE` / 404), the client queries `GET /me/player/devices` and automatically targets the first available device, waking up inactive background Spotify apps on phones, PCs, or speakers.
  - **Full Spotify Music Browsing & Playlist Streaming (`SpotifyApiClient.kt`, `ServiceScreens.kt`, `DeviceScreens.kt`)**:
    - Implemented direct `/search`, `/me/playlists`, and `/me/player/recently-played` endpoints in `SpotifyApiClient`.
    - Routed search results and playlist taps in `ServiceScreens` to trigger immediate playback via `SpotifyApiClient.sendPlayerCommand("play", uri = itemUri)`.
    - Updated `connected` check in `DeviceScreens` and `ServiceScreens` to include `ui.spotifyOAuthConnected`, ensuring the live Spotify player and controls activate immediately upon PKCE OAuth completion without requiring ESP32 pairing.
- Combined ESP32 target firmware contract remains **2.0.2-wled-room** (Version 11) alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 27 (V27)

Current project delivery, 25 October 2026 (Asia/Calcutta).

- Delivery version: **V27**; previous delivery: **V26**.
- Android source advances to **0.5.3 / versionCode 24**:
  - **Cold App Launch Media Notification Gating (`RoomMediaPlaybackService.kt`)**:
    - Added `@Volatile var isServiceRunning: Boolean` tracking in `RoomMediaPlaybackService` so `syncPlayback` never triggers unprompted background/lock-screen media notifications on cold app launch when paused or when no active music is playing.
    - Cleared mock fallback track strings (`"Evening Signals"`, `"Warm Desk Ensemble"`, `"Desk speaker"`) across the service and data layer.
  - **Spotify OAuth Linking in Demo / Unpaired Mode (`SpotifyConnectActivity.kt`)**:
    - Made controller endpoint check optional in `begin()`, allowing users to link Spotify OAuth via PKCE and securely store encrypted credentials in Jetpack DataStore even before an ESP32 is paired.
  - **UI Truncation & Playback Layout Restructuring (`ServiceScreens.kt`, `MainActivity.kt`)**:
    - Fixed token form header and field clipping: simplified OutlinedTextField labels to `"OAuth Access Token"` and `"OAuth Refresh Token"` with explanatory supporting text underneath, preventing ellipsis truncation (`"..."`).
    - Redesigned the embedded **Basic Playback Control UI** into a stacked non-overlapping layout: dedicated full-width track title (`15.sp`, SemiBold) and artist with album art/music icon, paired with a centered, well-proportioned transport button bar (`44.dp` / `52.dp` / `44.dp` with `20.dp` spacing) and subtle haptic feedback.
    - Updated `SmallLabel` in `MainActivity.kt` to allow 2 lines with soft wrapping.
  - **Removal of Mock Demo Data (`RoomModel.kt`, `DeviceScreens.kt`, `MusicPlayerWidget.kt`)**:
    - Cleared demo fake IR appliances (`"Ceiling fan"`, `"Desk speaker"`) and fake IR commands from `demoState()`.
    - Removed synthetic mock device list in `SpotifyDeviceDropdownSelector` and fallback mock track titles across player views, ensuring only real user-learned commands, actual paired devices, and active Spotify playback are displayed.
- Combined ESP32 target firmware contract remains **2.0.2-wled-room** (Version 11) alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 26 (V26)

Current project delivery, 25 October 2026 (Asia/Calcutta).

- Delivery version: **V26**; previous delivery: **V25**.
- Android source advances to **0.5.2 / versionCode 23**:
  - **Spotify Connect HTTP & Flexible Pairing Support (`SpotifyConnectActivity.kt`)**:
    - Replaced strict `DevicePairing.parse()` with `DevicePairing.parseFlexible()` in `begin()` and `complete()`.
    - Allowed both `https://` and `http://` controller endpoints so manual IP pairings and HTTP port 80 connections authorize Spotify OAuth PKCE without crashing.
  - **Runtime `POST_NOTIFICATIONS` Permission on Android 13+ (`MainActivity.kt`)**:
    - Added runtime `POST_NOTIFICATIONS` permission launcher and `LaunchedEffect` check inside `RoomApp` so lock-screen and status-bar media playback notifications are never silently suppressed on Android 13, 14, and 15 (SDK 35).
  - **Standalone Background ESP32 Dispatch & Notification Dismissal (`RoomMediaPlaybackService.kt`)**:
    - Implemented a standalone background fallback in `dispatchMediaAction()` when `onMediaTransportCommand == null` (app cleared/killed), dispatching `/v1/command` directly to the ESP32 via `DeviceTransport` on `serviceScope(Dispatchers.IO)`.
    - Attached `ACTION_STOP_SERVICE` to `Notification.Builder.setDeleteIntent()` so swiping away the paused media notification cleanly terminates the service.
    - Added `RoomMediaPlaybackService.stopMediaPlayback(context)` helper in `companion object`.
  - **Flexible Configured Device Parsing & Service Teardown on Logout (`RoomModel.kt`)**:
    - Updated `configuredDevice` parsing to use `DevicePairing.parseFlexible(it)`.
    - Added `RoomMediaPlaybackService.stopMediaPlayback(getApplication())` to `logout()` and `disconnectSpotifyOAuthFromDataStore()`.
  - **WLED Widget Pill Button Styling & Immediate Refresh Trigger (`WledWidget.kt`, `WledModel.kt`)**:
    - Removed `views.setInt(R.id.wled_widget_action, "setBackgroundColor", ...)` in `WledWidget.kt` to preserve native rounded pill button geometry.
    - Added immediate `runCatching { WledWidget.refresh(getApplication()) }` right after `persist()` in `WledModel.command()` so widget updates trigger instantly.
- Combined ESP32 target firmware contract remains **2.0.2-wled-room** (Version 11) alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 25 (V25)

Current project delivery, 25 October 2026 (Asia/Calcutta).

- Delivery version: **V25**; previous delivery: **V24**.
- Android source advances to **0.5.1 / versionCode 22**:
  - **Spotify OAuth Connection Flow via Jetpack DataStore & Basic Playback Control UI (`RoomDataStore.kt`, `RoomModel.kt`, `SpotifyConnectActivity.kt`, `ServiceScreens.kt`)**:
    - Added AES-GCM Android Keystore encrypted OAuth token storage (`KEY_SPOTIFY_CONNECTED`, `KEY_SPOTIFY_ACCOUNT_NAME`, `KEY_SPOTIFY_CLIENT_ID`, `KEY_SPOTIFY_ACCESS_TOKEN_ENC`, `KEY_SPOTIFY_REFRESH_TOKEN_ENC`, `KEY_SPOTIFY_EXPIRES_AT_MS`) and `saveSpotifyOAuthTokens` / `clearSpotifyOAuthTokens` / `readSpotifyOAuthTokens` in `RoomDataStore`.
    - Updated `SpotifyConnectActivity` so OAuth PKCE token exchanges automatically persist encrypted access and refresh tokens into DataStore alongside optional ESP32 credential forwarding.
    - Updated `SpotifyAccountSettingsCard` (`ServiceScreens.kt`) with OAuth connection status, an interactive DataStore OAuth token setup panel, and an embedded **Basic Playback Control UI** (active track/artist/speaker display, Previous / Play-Pause / Next transport buttons, and Spotify volume slider).
  - **Full Display of `"Restore Snapshot"` & `"Configuration Backup"` (`ServiceScreens.kt` — Screenshot 1)**:
    - Shortened the `DataStoreBackupRestoreCard` heading to `"Configuration Backup"` (`"DATASTORE"`) and placed `Save Snapshot` and `Restore Snapshot` in a 2-column row (`Modifier.weight(1f)` each) with `Copy / Paste Backup JSON` on its own full-width row below so `"Restore Snapshot"` is never truncated to `"Restore Snapsh"`.
  - **Redesigned Routine Timer (`RotaryTimePicker` in `DesignControls.kt` — Screenshot 2)**:
    - Stacked the `[12-Hour (AM / PM) | 24-Hour (00:00–23:59)]` selector bar and the `[Hours | Minutes]` selection bar onto dedicated full-width 2-column rows so the 24-hour and Hours/Minutes selection bars are 100% displayed without clipping.
    - Placed the digital `HOUR : MINUTE` readout boxes (`RotaryDigitBox`, `height = 64.dp`, `24.sp` Monospace Bold) on their own full-width row above the `136.dp` circular rotary dial so 2-digit numbers (`04`, `51`) are never half-cut.
  - **Reliable Playlist Saving & Editing (`WledModel.kt`, `WledCatalogDefaults.kt`, `WledScreens.kt` — Screenshot 3)**:
    - Fixed `WledCatalogDefaults.applyLocalCommand` to process `psave` before `ps`/`pl` and added `mergePresetsPreservingUserSaves` + `userSavedPresets` + `presetsRevision` in `WledModel` so newly saved or edited playlists are never overwritten by stale `/presets.json` responses or corrupted by element-wise `JSONArray` merging.
    - Updated `Saved Playlists` and `Create or Edit Playlist` in `WledScreens.kt` to display step duration and transition (`110s/step · 7s fade`), show an inline save confirmation banner, support atomic `Save & Play`, and add `76.dp` bottom clearance so the snackbar never overlaps `Save #ID` / `Save & Play`.
  - **Full Display of `"Light Brightness"` at `100%` (`ControlScreens.kt`)**:
    - Adjusted the header badge (`34.dp`), spacing (`8.dp`), title (`15.sp` SemiBold), and readout (`20.sp` Bold) in `HomeBrightnessAndVolumeHeroCard` so `"Light Brightness"` is 100% displayed without `".."` truncation when brightness reaches `"100%"`.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 24 (V24)

Current project delivery, 25 October 2026 (Asia/Calcutta).

- Delivery version: **V24**; previous delivery: **V23**.
- Android source advances to **0.5.0 / versionCode 21**:
  - **Unique, Minimalistic Icons for Each Routine Card (`AutomationScreens.kt`, `ControlScreens.kt`)**:
    - Added a 16-icon minimalistic vector icon catalog (`ROUTINE_ICON_CATALOG`: Sun, Moon, Equalizer, Bolt, Spa, Coffee, Tv, Air,AutoAwesome, Nightlight,Timer, MusicNote,SelfImprovement,Sensors,Bedtime,Weekend), deterministic collision-free assignment (`assignUniqueRoutineIcons`), and an interactive **Routine Icon** selector inside `RuleDialog` (`AutomationScreens.kt`).
    - Added `HomeRoutinesDashboardCard` on the main `HomeScreen` (`ControlScreens.kt`) and updated `HomeUniversalFavoritesSection` and `RoutinesScreen` so every routine card displays a unique, visually distinct minimalistic icon badge with one-tap execution.
  - **Subtle Tactile Haptic Feedback Across All Button Interactions (`DesignControls.kt`, `MainActivity.kt`, `AutomationScreens.kt`, `ControlScreens.kt`, `MusicPlayerWidget.kt`, `DeviceScreens.kt`, `AndroidManifest.xml`)**:
    - Declared `<uses-permission android:name="android.permission.VIBRATE"/>` and added `performSubtleHaptic(view: View, heavy: Boolean = false)` in `DesignControls.kt`.
    - Wired subtle haptic feedback into `Modifier.livelyClick`, `Modifier.livelyPressScale`, `ActionButton`, `LiquidToggle`, `LimelightNavBar`, `Choices`, `ConfirmDialog`, `RotaryTimePicker`, `RotaryDigitBox`, `SpeakerIrVolumeControl`, `SpotifyLocalNetworkControlCard`, `MusicPlayerWidget` transport controls, routine cards/templates, and snackbar dismiss buttons.
  - **Full Resolution of All Audit Report Findings (`Paste_October_25__2026_-_12_43AM_8a0e49d9`)**:
    - **1.1 (`room-device.config` Decoupling)**: Removed `.takeIf { configuredDevice != null && it == configuredDevice }` when reading `pairingVault` in `RoomModel.kt`, added manual LAN IP/hostname + token pairing (`pairManualDevice`) and `pairing.json` import (`importPairingJson`) in `PairDeviceScreen` (`DeviceScreens.kt`), and made `useBuiltInDevice()` fall back gracefully without throwing `IllegalStateException`.
    - **1.2 (HTTPS 443 + HTTP 80 Dual Transport)**: Updated `DeviceTransport.kt` to support both leaf-pinned HTTPS (`443`) and LAN HTTP (`80`) with automatic fallback and both `Authorization: Bearer <token>` and `X-API-Key: <token>` headers while keeping `DevicePairing.parse()` strict for unit test verification.
    - **1.3 (Legacy Companion Endpoint Translation)**: Translated `/spotify/player`, `/spotify/devices`, `/spotify/playback`, `/spotify/search`, `/spotify/playlists`, `/spotify/recent`, `/learning`, `/learning/{id}`, `/learning/{id}/save`, `/settings`, `/diagnostics`, and `/jobs/{id}` into native `/v1/state` and `/v1/command` calls inside `RoomModel.deviceRequest()`.
    - **2.1–2.4 (`WledWidget.kt` Home Screen Widget Fixes)**: Preserved 24dp rounded corners and border using `setBackgroundResource` (`@drawable/wled_widget_background`, `@drawable/wled_widget_background_light`, `@drawable/wled_widget_background_obsidian`) instead of `setBackgroundColor`, switched widget coroutine execution to `SupervisorJob() + Dispatchers.IO`, fixed false-positive preset check failures (`ps == preset || pl == preset || on`), and synchronized device lookup with `RoomDataStore.readDevicesJson(context)`.
    - **3.1–3.4 (`RoomMediaPlaybackService.kt`, `MusicPlayerWidget.kt`, `DeviceScreens.kt`, `RoomModel.kt`, `PreviewData.kt`)**: Added `RoomMediaPlaybackService` (Foreground `MediaSession` + `Notification.MediaStyle` for lock-screen and background playback control), replaced hardcoded `progress_ms = 41000` / `duration_ms = 210000` in `DeviceMusicScreen` with live state and 250ms real-time playback extrapolation in `MusicPlayerWidget`, and wired `seek`, `shuffle`, and `repeat` end-to-end.
    - **4.1–4.4 (Lighting & Native ESP32 Music Pipeline)**: Fixed `WledApi.saveConfig` (`WledProtocol.kt`) to unwrap any `"cfg"` wrapper before posting to `/json/cfg`, handled combined V10 firmware `"Managed by WLED settings"` responses on `op: "microphone"` / `"calibrate"`, normalized `op: "lighting"` payloads (`power`, `color`, `brightness`, `effect`, `speed`, `intensity`), and prevented dual-transmission slider storms in `ControlScreens.kt` by deferring `model.coalescedLighting` to `onValueChangeFinished` when WLED is online.
    - **5.1–5.4 & 6.1 (Automations, Clock & IR Learn)**: Sanitized `rules` payloads in `RoomModel.kt` (`manual` trigger fallback, Spotify `action`/`argument`/`value` fields, and `type: "sequence"` with `actions` array for multi-action routines), enabled saving clock/timezone in `DeviceSystemScreen` even when `settings` is initially empty (defaulting to `"IST-5:30"`), and surfaced `runtime.message`/`ir_error` in `DeviceLearnDialog` with clean UTF-8 strings.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 23 (V23)

Current project delivery, 25 October 2026 (Asia/Calcutta).

- Delivery version: **V23**; previous delivery: **V22**.
- Android source advances to **0.4.9 / versionCode 20**:
  - **Configuration Backup & Restore via Jetpack DataStore (`RoomDataStore.kt`, `RoomModel.kt`, `WledModel.kt`, `ServiceScreens.kt`, `DeviceScreens.kt`)**:
    - Implemented complete cross-device configuration export and restore in `RoomDataStore` (`exportConfigurationBackup`, `importConfigurationBackup`, `readLatestLocalBackupJson`) capturing the full Room controller state (`settings`, `lighting`, `microphone`, `appliances`, `commands`, `rules`, `presets`, `scenes`, `plugs`, `wake_alarms`), WLED controller state (`snapshot`, `presets`, `config`, `targetHost`, `activeTab`, `pcMode`, `colorSlot`, `effectFilter`, `configSection`), UI preferences (`themeId`, `use24HourFormat`, `homePresetCategory`, `presetCategories`), and `favorites` inside a versioned `roomcontroller.datastore.backup` (`v1`) JSON envelope.
    - Added `DataStoreBackupRestoreCard` in Settings (`SystemScreen` and `DeviceSystemScreen`) supporting **Export `.json`** (Android Storage Access Framework `CreateDocument`), **Import `.json`** (`GetContent`), **Copy JSON / Paste or Edit Backup JSON** dialog, and 1-tap **Save Local Snapshot** / **Restore Snapshot** stored directly in DataStore.
  - **Instantaneous Zero-Delay Navigation (`RoomModel.kt`, `WledModel.kt`, `MainActivity.kt`, `ControlScreens.kt`, `ServiceScreens.kt`)**:
    - Eliminated the 1.5s tab switch delay by moving `savePersistedState` (`SharedPreferences.apply()` + background `JSONObject` cloning) and `syncDataStore` onto `Dispatchers.IO` coroutines without mutating `_ui.value` with a cloned `JSONObject` on every tab switch.
    - Cached parsed WLED presets (`presetEntries`) directly on `WledUi`, debounced `WledModel.persist()` asynchronously on `Dispatchers.IO`, keyed `HomePresetCategorySection` `remember` blocks on `roomPresetsJson` instead of `ui.state.toString()`, and made initial `MusicScreen` playback refresh non-blocking.
  - **12-Hour (`AM`/`PM`) & 24-Hour Clock System (`DesignControls.kt`, `AutomationScreens.kt`, `ServiceScreens.kt`, `DeviceScreens.kt`, `WledSettingsScreens.kt`, `RoomDataStore.kt`)**:
    - Added `use24HourFormat` (`true` = 24-hour default, `false` = 12-hour `AM`/`PM`) persisted in DataStore and synced with `settings.use_24_hour_format` on the controller.
    - Added `ClockFormatSettingSelector` in Settings (`SystemScreen`, `DeviceSystemScreen`, and synced in `WledTimeSettingsCard`) and an inline `24H` / `12H` toggle in `RotaryTimePicker` (`RuleDialog`), updating the `HH : MM` readout, `AM`/`PM` selector pills, 12-hour vs 24-hour clock dial face, quick time preset chips, and routine schedule summaries (`formatClockTime`).
  - **Standalone & Aligned Playlist Creation Card (`WledScreens.kt` — Screenshot 1)**:
    - Refactored saved playlist rows in `WledPresetsAndPlaylistsTab` into a clean 2-row card layout (title + step sequence on top; `Play Playlist`, `★`, `✎`, `🗑` action bar on bottom) so playlist names never wrap into a 1-word-per-line vertical column.
    - Extracted **"Create or Edit Playlist"** into its own standalone `RoomCard` with aligned rows, non-wrapping field labels (`ID (1–250)` and `Playlist name`), and equal-width single-line `Save #ID` and `Save & Play` buttons.
  - **Removed IR Simulation Section & Enforced Authentic Recorded IR Commands (`AutomationScreens.kt`, `PreviewData.kt` — Screenshot 2)**:
    - Removed `IrReceiverTelemetryCard` (`"IR Receiver & Signal Test"` / `"Simulate IR Receive & Test Reaction"`) from `RemotesScreen` (`AutomationScreens.kt`) since each learned IR command row already has its own inline `Send` (`▶`) button.
    - Updated `buildCompleteIrOptionList` (`AutomationScreens.kt`) and `PreviewSession` (`PreviewData.kt`) so IR command lists in `RemotesScreen` and `RuleDialog` display only actual recorded/saved commands from `ui.state.items("commands")` (preserving deletions and newly learned `NEC 38 kHz` commands without re-seeding fake fallback commands).
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 22 (V22)

Current project delivery, 24 October 2026 (Asia/Calcutta).

- Delivery version: **V22**; previous delivery: **V21**.
- Android source advances to **0.4.8 / versionCode 19**:
  - **Instantaneous Zero-Lag Bottom Navigation (`DesignControls.kt`, `MainActivity.kt`, `RoomModel.kt`)**:
    - Decoupled bottom navigation page state from asynchronous DataStore writes (`LaunchedEffect(page)`), retained per-page `ScrollState` instances across tabs (`Home`, `Music`, `Light`, `Remote`, `More`), and tuned `LimelightNavBar` to a critically damped spring (`dampingRatio = 0.90f, stiffness = 1500f`) so tab switching is immediate with zero delay.
  - **All 5 Screenshot Fixes (`WledScreens.kt`, `DesignControls.kt`, `AutomationScreens.kt`, `RoomModel.kt`, `PreviewData.kt`)**:
    - Screenshot 1 (`WledSegmentsTab` in `WledScreens.kt`): Shortened truncated `"HOW LOGICAL STEPS ARE DETERMINED ON THIS ..."` to **`LOGICAL STEP MODE`**, arranged Zone Boundary Presets (3×2), Step-Determination Modes (2-column + full-width), and Segment Flags (`Reverse`, `Mirror`, `Freeze`, `Selected` in 2×2) into equal-width grids without uneven trailing gaps, fixed `"75 logical steps"` wrapping into 3 lines (`75 / logical / steps`) into a single-line `"75 steps"` pill, and placed `Start LED`, `Stop LED`, `Grouping`, and `Spacing` in a 2×2 grid so labels are never cut off.
    - Screenshot 2 (`RotaryTimePicker` in `DesignControls.kt` & `RuleDialog` in `AutomationScreens.kt`): Fixed `"Minutes"` wrapping into 3 lines (`Mi / nu / tes`), removed the duplicate outer preset row (`07:00`, `09:00`, `18:30`, `22:00`, `23:30`), aligned `Every day / Weekdays / Weekends` (3 equal columns) and `Mon..Sun` (7 equal columns), and redesigned `RotaryTimePicker` into a minimalist, luxury hardware time setter featuring interactive `HH : MM` `RotaryDigitBox` readouts, a clean 12-tick circular dial, `−1` / `+1` / `+15m` steppers, and a single 5-chip preset row.
    - Screenshot 3 (`IrTriggerDefinitionSection` & `IrReceiverTelemetryCard` in `AutomationScreens.kt`, `RoomModel.kt`, `PreviewData.kt`): Seeded all 6 standard IR commands (`power`, `fan-speed-up`, `fan-speed-down`, `volume-up`, `volume-down`, `speaker-mute`), rendered all IR commands in a static 2-column grid with zero horizontal or vertical scrolling required to select a command, and removed the `GPIO 34` / `38 kHz DEMOD` / verbose explanation block.
    - Screenshot 4 (`Esp32NativeMusicPipelineTab` in `WledScreens.kt`): Fixed `"Mids #6 (Vocals / Snare)"` + `"FFT 120% · Gain 200%"` wrapping onto 2–3 lines by enforcing single-line weighted layout (`maxLines = 1, softWrap = false`) across `Bass #0`, `Mids #6`, and `Treble #13`.
    - Screenshot 5 (`WledTopActionBar` in `WledScreens.kt`): Removed the `"..."` description under `"Bedroom WLED"`, redesigned the `Light` power button into a sleek minimalist single-line `"Power"` + `"ON"`/`"OFF"` bar without verbose subtext, and arranged the 6 utility buttons (`Timer`, `Sync`, `Peek`, `Info`, `Config`, `PC Mode`) into a non-clipping 3×2 grid.
  - **Dismissible Popup Notifications (`MainActivity.kt`)**:
    - Added a custom `SnackbarHost` with an immediate **`✕` (Close / Cross) button** (`Icons.Outlined.Close`, `testTag = "snackbar_close_button"`) on every popup notification so pressing `✕` hides it immediately (`data.dismiss()`).
  - **Deep Black + Champagne Gold Theme (`Appearance.kt`, `DesignControls.kt`)**:
    - Updated the **Black (`obsidian`)** theme to pure deep black (`#000000` background, `#070707` card surface, `#1C1C1C` hairline border, `#9E9E9E` muted text, `#FFFFFF` primary text) paired with a warm, luxury **Champagne Gold (`#E5C158`)** accent instead of blue/cyan, and changed `LiquidToggle`'s inactive track from bluish `#2E3346` to neutral `Line`.
  - **Complete ESP32 Connection & Command Specification (`ESP32_APP_SYNC_CONTEXT.md` Section 25)**:
    - Recorded all V22 app code changes and documented the complete HTTP + UDP transport, authentication headers, and every JSON command/endpoint contract between the Android app and the ESP32 controller.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 21 (V21)

Current project delivery, 23 October 2026 (Asia/Calcutta).

- Delivery version: **V21**; previous delivery: **V20**.
- Android source advances to **0.4.7 / versionCode 18**:
  - **Mute Locks Volume Slider, High-Contrast Black Theme (`obsidian`) & Light Theme Volume Rocker Fix (`Appearance.kt`, `DesignControls.kt`, `RoomModel.kt`, `ControlScreens.kt`, `DeviceScreens.kt`, `ServiceScreens.kt`)**:
    - Pressing the `Mute` button in `SpeakerIrVolumeControl` toggles `speaker_muted` and locks/disables the speaker volume slider (`Slider05`) and `IR Vol −` / `IR Vol +` buttons until unmuted.
    - Added the **Black (`obsidian`)** theme (`#000000` pure black background, `#0B0D12` deep obsidian panel, `#00F0FF` electric cyan accent, `#FFFFFF` high-contrast text) alongside `Midnight`, `Aurora`, `Ember`, and `Pearl`.
    - Fixed `Slider05` in Light (`Pearl`) theme so active/inactive equalizer bars, wave arcs, and tooltip pills use theme-aware high-contrast colors instead of white-on-light.
  - **All 8 Screenshot Fixes (`WledScreens.kt`, `WledSettingsScreens.kt`, `ControlScreens.kt`)**:
    - Screenshot 1: Removed the extra `Core 0 · INMP441 I²S DSP` and `Core 1 · WS2812B Engine` info rows and turned the 3 static `Bass #0`, `Mids #6`, `Treble #13` boxes into **interactive, controllable Bass, Mid, and Treble sliders (`0–200%`)** paired with live FFT level readouts (`WledScreens.kt`).
    - Screenshot 2: Fixed the `100%` preset button under `Master Brightness` so `%` is never cut off, shortened `LIVE LED STRIP` to `LIVE STRIP`, redesigned `ESP32 Music` vs `WLED FX` into a zero-gap segmented switcher, and placed the sub-tabs in a single-row `horizontalScroll` (`WledScreens.kt`).
    - Screenshot 3: Changed truncated `"Apply to ES..."` button to **`Apply ESP32`** (`WledScreens.kt`).
    - Screenshot 4: Fixed truncated `"Quick Pre..."` heading, removed the `"Page X/Y"` pagination button, put category chips in a single-row `horizontalScroll`, and made the preset list smoothly scrollable (`verticalScroll` up to `340.dp`) (`ControlScreens.kt`).
    - Screenshot 5: Removed `"PRIMARY CONTROL · 01"` and `"PRIMARY CONTROL · 02"` labels, added **Device / Speaker Volume (`SpeakerIrVolumeControl`)** directly inside the Home hero card below `Music Volume`, and fixed the `#HEX` badge in `Color Studio` (`ControlScreens.kt`).
    - Screenshot 6: Enabled saving custom playlists (`Save Playlist #N` and `Save & Play` always active with sensible defaults) and added an **`Edit`** button on each saved playlist row to load it back into the editor (`WledScreens.kt`).
    - Screenshot 7: Shortened `"Save Security"` to **`Save Lock`** so it never truncates (`WledSettingsScreens.kt`).
    - Screenshot 8: Fixed `"Show all 170"` wrapping vertically 1 character per line and made effect filter chips a single-row `horizontalScroll` (`WledScreens.kt`).
  - **Illuminated Reactor Power Button (`WledScreens.kt`) & `LimelightNav` Bar (`/components/ui/limelight-nav.tsx`, `/components/ui/demo.tsx`, `DesignControls.kt`, `MainActivity.kt`)**:
    - Redesigned the `Light` section power button into an illuminated full-width reactor power bar with glowing radial icon, status subtitle, and `ON`/`OFF` pill badge.
    - Integrated `/components/ui/limelight-nav.tsx` and `/components/ui/demo.tsx` and implemented `LimelightNavBar` in Jetpack Compose with sliding glowing limelight bar and conical spotlight trapeze beam.
  - **Rotary Time Picker & IR Receive Signal Definition / Reaction UI (`DesignControls.kt`, `AutomationScreens.kt`)**:
    - Replaced the text field for schedule time in `RuleDialog` with `RotaryTimePicker` featuring scrollable `HH` and `MM` drum wheels and a circular rotary jog dial.
    - Added `IrReceiverTelemetryCard` in `RemotesScreen` and `IrTriggerDefinitionSection` in `RuleDialog` defining the incoming IR signal (`NEC / RC5 / Sony · 38 kHz` on GPIO 34, hex frame, pulse count & mark/space `µs` timings), selecting which learned IR command triggers a routine, and testing/simulating the controller's reaction.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 20 (V20)

Current project delivery, 22 October 2026 (Asia/Calcutta).

- Delivery version: **V20**; previous delivery: **V19**.
- Android source advances to **0.4.6 / versionCode 17**:
  - **Slider Touch Protection & Glitch-Free Home Brightness (`DesignControls.kt`, `WledModel.kt`, `RoomModel.kt`, `MainActivity.kt`)**:
    - Touching within `28.dp` of a slider thumb attaches directly to the current value (`attachRelative = true`) and tracks relative finger delta (`dx`) so touching the thumb never jumps the value.
    - Extended external state lockout to `750ms` post-release across `LedSlider`, `Slider05`, `BrightnessControl`, `VolumeControl`, and `SettingSlider`.
    - Fixed the DataStore reactive feedback loop in `WledModel` and `RoomModel` and gave the top loading indicator in `MainActivity.kt` a fixed `3.dp` height container so Home screen brightness adjustments never glitch or jump the layout.
  - **Favorites in Home Quick Presets, Category Filtering, Preset Limit (4) & Warning Dialog (`ControlScreens.kt`, `RoomModel.kt`, `WledScreens.kt`)**:
    - Favorited items (WLED Presets, Room Scenes, Effects, Sound Modes, Palettes, Colors) appear immediately at the top of `HomePresetCategorySection` ("Quick Presets") and under their category chips (`Favorites`, `Ambient`, `Vibrant FX`, `Music & Sound`, `Focus & Work`, `Playlists`, `Room Scenes`).
    - Capped displayed preset buttons to `MAX_HOME_PRESETS = 4` (with a `Page X/Y` selector when a category has more than 4 items).
    - Trying to add more than `4` favorites shows an `AlertDialog` (`"Favorite Limit Reached (4/4)"`) and Snackbar warning the user to remove a favorite first.
  - **Shortened Headers, Mode Boxes, Nightlight Timer Buttons & Config Tab Names (`ControlScreens.kt`, `WledScreens.kt`, `WledSettingsScreens.kt`)**:
    - Screenshot 1: Replaced truncated `"Color Corr... #FFEA0D"` with `"Color Studio"` + `#HEX` badge on the header row (`ControlScreens.kt`).
    - Screenshot 2: Shortened `WledStripPreviewCard` header to `"LIVE LED STRIP · ${snap.ledCount} LEDS · ${snap.fps} FPS"`, removed the verbose explanation under `"Music Response"`, and shortened the mode boxes to `"ESP32 Music"` (`"Inbuilt DSP"`) and `"WLED FX"` (`"Strip FX"`) (`WledScreens.kt`).
    - Screenshot 3: Gave Nightlight `"Start Timer"` / `"Update Timer"` and `"Stop Timer"` equal `Modifier.weight(1f)` and single-line text so `"Stop Timer"` never wraps (`WledScreens.kt`).
    - Screenshot 4: Shortened config tab labels to `"Wi-Fi"`, `"LEDs"`, `"2D"`, `"UI"`, `"UDP"`, `"Time"`, `"Audio"`, `"Security"` (`WledSettingsScreens.kt`).
  - **Unified 300-LED Live Strip Preview Without Stuck Pink Half (`WledCatalogDefaults.kt`, `WledModel.kt`, `ControlScreens.kt`)**:
    - Screenshot 5: Unified default segment color/effect across the 300-LED strip in `WledCatalogDefaults.kt`, added `sanitizeSnapshotSegments` in `WledModel.kt` to repair legacy multi-segment snapshots where segment 1 (`150..300`) was stuck on pink `[255, 0, 128]`, and updated all segments simultaneously when adjusting colors from Home.
  - **Single Music Controller, Active Spotify Device Dropdown, Synced Album Art, Instant Volume & Speaker/Device IR Volume Control (`MusicPlayerWidget.kt`, `ServiceScreens.kt`, `DeviceScreens.kt`, `RoomModel.kt`, `PreviewData.kt`)**:
    - Screenshots 6 & 7: Removed the duplicate `SpotifyLocalNetworkControlCard` from `MusicScreen` and `DeviceMusicScreen`.
    - Added `SpotifyDeviceDropdownSelector` inside the Music Player card (`MusicPlayerWidget.kt`) so tapping `"Desk speaker"` opens a dropdown of all Spotify-linked devices to switch playback target, and removed the separate `"Play it here"` card.
    - Synced song cover artwork (`AsyncImage`) with the active track across all views and preview tracks.
    - Removed `"Set volume"` and `"Refresh"` buttons; made Spotify Volume update instantaneously on drag (`model.coalescedVolume`) and added `SpeakerIrVolumeControl` directly below to control physical speaker/device volume via IR (`model.coalescedSpeakerIrVolume` + `IR Vol −`, `Mute`, `IR Vol +`).
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 19 (V19)

Current project delivery, 22 October 2026 (Asia/Calcutta).

- Delivery version: **V19**; previous delivery: **V18**.
- Android source advances to **0.4.5 / versionCode 16**:
  - **Moved Spotify Account Linking to Settings (`ServiceScreens.kt`, `DeviceScreens.kt`, `MainActivity.kt`)**: Shifted the Spotify OAuth / PKCE account connection card out of `MusicScreen` and `DeviceMusicScreen` into Settings (`SystemScreen` and `DeviceSystemScreen` via `SpotifyAccountSettingsCard`), leaving a clean `"Open Settings to link Spotify"` button on the Music screen when unlinked.
  - **Master "Automations active" Toggle Controls All Routines Simultaneously (`AutomationScreens.kt`, `RoomModel.kt`)**: Wired the `Automations active` toggle in `RoutinesScreen` to `model.setAllRoutinesEnabled(nextArmed)` so turning it ON or OFF enables or disables every routine (`rules`) and the master automation flag together, persisting to DataStore and syncing with the controller.
  - **Theme-Colored `LiquidToggle` Without Stray White Dot (`DesignControls.kt`)**: Updated `LiquidToggle` to use the active theme accent color (`Lime` / `LocalRoomPalette.current.accent`) instead of hardcoded blue (`#275EFE`), removed the stray white `dropCircle` dot above the white thumb circle, added a subtle contrast outline, and locked `Modifier.requiredSize(52.dp, 32.dp)` so toggle switches never deform.
  - **Redesigned Config Page & Fixed All 6 Screenshot Layout / Truncation / Deformed Button Issues (`WledSettingsScreens.kt`, `WledScreens.kt`, `ControlScreens.kt`, `SoundTuning.kt`, `MainActivity.kt`)**:
    - Redesigned `WledControllerSettingsSection` (`WledSettingsScreens.kt`) with a clean 2x4 category selector grid (`Overview`, `Wi-Fi & AP`, `LEDs & Power`, `Sync & Live`, `Buttons & Backup`, `Audio DSP`, `Security & OTA`, `Time & Macros`), non-truncating single-line headers, `SettingToggleRow` across all toggles (fixing squished/invisible UDP broadcast and OTA lock toggles), and equal-weight single-line buttons.
    - Fixed `WledInfoDiagnosticsCard` (`WledScreens.kt`) header wrapping, `0.42f`/`0.58f` key/value column alignment, and equal-weight `"Refresh Info"` / `"Reboot WLED"` buttons.
    - Fixed `Esp32NativeMusicPipelineTab` (`WledScreens.kt`) top card (`Core 0 · INMP441 I²S DSP` / `Core 1 · WS2812B Engine` on single lines), compact 2–3 line `FFT REAL-TIME VIEW · RMS` + 3-band meters, single-line contracted mode rows that expand cleanly when selected, and equal-weight non-wrapping bottom buttons (`"Save Mic & EQ"` / `"5s Calibrate"` and `"Apply to ESP32"` / `"Save Preset"`).
    - Shortened verbose `SectionLabel` titles and trailing badges across `ControlScreens.kt`, `SoundTuning.kt`, `WledScreens.kt`, and `WledSettingsScreens.kt` to eliminate `"..."` truncations.
    - Completed full `/v1/command` and `/actions` parity in `RoomModel.kt` and `PreviewData.kt` so every button and workflow operates end-to-end.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 18 (V18)

Current project delivery, 22 October 2026 (Asia/Calcutta).

- Delivery version: **V18**; previous delivery: **V17**.
- Android source advances to **0.4.4 / versionCode 15**:
  - **Spotify Local Network API Control Component in Compose (`DesignControls.kt`, `ControlScreens.kt`, `DeviceScreens.kt`, `ServiceScreens.kt`)**: Added `SpotifyLocalNetworkControlCard` (`spotify_local_network_card`) interfacing with the controller's local network Spotify API (`/v1/command` `op: "spotify"` for `play`, `pause`, `previous`, `next`, `volume`, `refresh` and `/spotify/playback`) with track/artist/device status, transport controls, `LiquidToggle` power switch, and `Slider05` volume wave control.
  - **`LiquidToggle` & `Slider05` Compose Components (`DesignControls.kt`)**: Ported the `liquid-toggle.tsx` gooey 2-circle morph + `dropCircle` (`52x32` viewBox, `default`/`success`/`warning`/`danger`/`lime` variants) to `LiquidToggle` across all toggle switches in the app, and ported `slider-05.tsx` (44-bar equalizer wave slider with cosine finger dip, active sine wave arc, and floating spring tooltip pill) to `Slider05` for Music Volume on Home and Music screens.
  - **Fixed UI Layout & Sizing Regressions (`ControlScreens.kt`, `AutomationScreens.kt`, `WledScreens.kt`)**:
    - Fixed `Vol 100%` wrapping onto 3 lines in `HomeBrightnessAndVolumeHeroCard` by giving all 4 quick volume chips (`Vol 25%`, `Vol 50%`, `Vol 75%`, `Vol 100%`) and 4 brightness chips (`15%`, `40%`, `70%`, `100%`) equal `Modifier.weight(1f)` and single-line `maxLines = 1, softWrap = false`.
    - Fixed routine cards in `RoutinesScreen` (`AutomationScreens.kt`) changing height when toggled off due to `"DISABLED"` wrapping 1 character per line vertically next to long routine names (`"Goodnight & Sleep"`).
    - Fixed `Esp32MusicPipelineQuickBanner` (`WledScreens.kt`) changing height when switching between `Inbuilt ESP32 Music` and `WLED FX Engine` by enforcing single-line ellipsized subtitles and equal-weight `52.dp`-high mode buttons.
    - Fixed the `Delete` button squishing vertically in `WledSegmentsTab` (`WledScreens.kt`) and added interactive **Zone Boundary Presets** (`Full Strip`, `Zone 1 · Left/Desk`, `Zone 2 · Ceiling`, `Zone 3 · Right`, `Left Half`, `Right Half`) and **Step-Determination Mode Selectors** (`Linear 1 px/step`, `2-LED Block Steps`, `4-LED Block Steps`, `Staggered Dotted Steps`, `Wide Spaced Steps`, `Center-Out Mirrored Steps`, `Reversed Stop→Start Steps`) with live step count and formula readout.
  - **Hardware-Accurate WS2812B LED Strip Representation (`WledScreens.kt`)**: Upgraded `WledAnimatedStripCanvas` to render a recessed dark PCB channel with discrete SMD5050 pixel housings, crisp inner emitter diodes, specular center highlights, segment boundary markers, and true unlit state when strip power is OFF.
  - **Disabled Child Controls When Section Toggles Are OFF & Full 3-Band EQ (Bass/Mid/Treble) in Settings (`ControlScreens.kt`, `WledScreens.kt`, `WledSettingsScreens.kt`, `SoundTuning.kt`, `RoomModel.kt`, `DeviceScreens.kt`, `ServiceScreens.kt`)**: Disabled and dimmed child controls whenever a section's toggle is OFF (Brightness, Music Volume, Color Correcting, Segment power, ESP32 Native Music power, AudioReactive DSP, and AGC manual sensitivity), preserved `bass_gain`, `mid_gain`, and `treble_gain` across `RoomModel` and `SoundTuning`, and added `RoomThreeBandEqSettingsCard` to Settings (`DeviceSystemScreen`, `SystemScreen`) and `Esp32NativeMusicPipelineTab`.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 17 (V17)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V17**; previous delivery: **V16**.
- Android source advances to **0.4.3 / versionCode 14**:
  - **Bottom Navigation Bar Scale & Color Animations (`MainActivity.kt`)**: Added spring scale (`animateFloatAsState`, `iconScale` `1.0f..1.15f`, `labelScale` `1.0f..1.05f`, plus `livelyPressScale`) and smooth color transitions (`animateColorAsState`, `tween(240)`) to the bottom navigation bar items (`Home`, `Music`, `Light`, `Remote`, `More`) when switching between sections.
  - **Zero-Lag, Jitter-Free Sliders (`DesignControls.kt`, `WledModel.kt`, `RoomModel.kt`, `MainActivity.kt`)**: Re-engineered `LedSlider`, `Slider05`, `BrightnessControl`, `VolumeControl`, `SettingSlider`, and `LedWheelColorPicker` to use local drag state (`dragFraction`), unified zero-slop `awaitPointerEventScope` touch tracking, `IntOffset` placement-phase thumb positioning, 24 ms outbound throttling during active drags, a 220 ms post-release settle window so network poll responses never snap the thumb backward, and debounced DataStore/SharedPreferences persistence inside `WledModel.coalescedCommand` and `RoomModel.coalescedLighting` / `coalescedMicrophone` / `coalescedVolume`.
  - **Focused, Uncluttered Home Screen Redesign (`ControlScreens.kt`)**: Redesigned `HomeScreen` around a clear 4-tier hierarchy: (1) **Brightness & Music Volume Hero Command Deck** (`HomeBrightnessAndVolumeHeroCard`) at the very top with smooth brightness/volume sliders, power toggle, and now-playing transport; (2) **Color Correcting Studio** (`HomeColorCorrectingCard`) directly below with Chromatic Hue (`0°–360°`), Warm↔Cool White Balance (`2700K–6500K`), Color Saturation (`0–100%`), and 6 calibrated studio swatches; (3) **Favorites Within Reach** strictly capped at `MAX_HOME_FAVORITES = 4`; and (4) **Presets by Category** strictly capped at `MAX_HOME_PRESETS = 4`.
  - **Inbuilt Native ESP32 Music Response Pipeline in Lights (`WledScreens.kt`)**: Added the `ESP32 Music` pipeline selector banner and dedicated `Esp32NativeMusicPipelineTab` in the `Light` section to control the inbuilt custom ESP32 firmware pipeline (`SmartRoomController` `1.1.4-music`: Core 0 `MusicAudioDSP` INMP441 I²S at `16 kHz / 512 samples` with 16-band FFT & beat/flux latching + Core 1 `MusicLightingEngine` rendering `300 px @ 25 ms`), distinct from WLED's AudioReactive effects, with live 3-band telemetry (`DSP #0 Bass`, `DSP #6 Mids`, `DSP #13 Treble`), native mode selection (`beat`, `bass`, `bands`, `level`, `solid`), pulse decay (`speed`) and response (`intensity`) sliders, and INMP441 AGC/sensitivity/noise-floor/smoothing/quiet-policy controls.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside canonical **1.1.4-music** native music pipeline support; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 16 (V16)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V16**; previous delivery: **V15**.
- Android source advances to **0.4.2 / versionCode 13**:
  - **Jetpack DataStore Local Storage Layer (`RoomDataStore.kt`)**: Integrated `androidx.datastore:datastore-preferences` (`Context.roomDataStore` / `RoomDataStore`) into both `WledModel` and `RoomModel` to persist and observe the selected `Light` control tab (`activeTab`: `Colors`/`Effects`/`Palettes`/`Segments`/`Presets`/`Config`), `pcMode`, `colorSlot`, `effectFilter`, `configSection`, top-level `selectedPage`, `homePresetCategory`, custom preset category assignments (`presetCategories`), user-defined WLED state (`savedSnapshot`, `savedPresets`, `savedConfig`), Room controller state (`persistedRoomStateJson`), and all favorite collections across app restarts.
  - **Preset Category Selector on the Home Screen (`ControlScreens.kt` & `WledScreens.kt`)**: Added `HomePresetCategorySection` to `HomeScreen` featuring interactive category filter chips (`All`, `Favorites`, `Ambient`, `Vibrant & FX`, `Music & Sound`, `Focus & Work`, `Playlists`, `Room Scenes`), smart automatic category inference (`PresetCategories`), DataStore-persisted custom category tagging per preset, one-tap preset/scene activation, and category selection during WLED preset creation.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside legacy **1.1.4-music** compatibility; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 15 (V15)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V15**; previous delivery: **V14**.
- Android source advances to **0.4.1 / versionCode 12**:
  - **Bottom Navigation Bar Reordering**: Placed `Light` in the center of the bottom navigation bar (`Home`, `Music`, `Light`, `Remote`, `More`) in `MainActivity.kt`.
  - **Full UI Selection & State Persistence Across Restarts and Tab Switches**: Persisted active top-level screen (`ui_active_screen`), Light workspace sub-tab (`subTab`), color slot (`colorSlot`), effect category filter (`effectFilter`), selected segment (`selectedSegmentId`), WLED `/json/cfg` settings section (`configSection`), IR remote category filter (`ui_remote_cat`), and More section (`ui_more_section`) in `SharedPreferences` across `MainActivity.kt`, `WledModel.kt`, `WledScreens.kt`, and `WledSettingsScreens.kt`.
  - **ESP32 Preset & State Persistence on Save/Apply**: Added immediate preset activation (`setPreset`) when saving a preset with `saveBounds = true` ("OK" / save current state) and persisted optimistic snapshot overrides in `SharedPreferences` (`last_snapshot_<id>`) in `WledModel.kt` so saved presets and lighting states survive app restarts and sync cleanly to the ESP32.
  - **Lively LED Strip Peek & True Hardware Color Rendering**: Implemented pure-Kotlin gamma-boosted LED strip preview rendering (`luminousLedColor`, `hsvToComposeColor`, `computeStripPreviewPixels` in `WledCatalogDefaults.kt`) and vivid gradient tracks (`ChannelLedSlider`, Kelvin CCT `LedSlider`) so LED strip previews, palette bars, and sliders never appear dark or muddy.
  - **Compact Segment Target Selector**: Replaced the oversized card list in `SegmentTargetSelectorCard` (`WledScreens.kt`) with a compact, dense horizontal pill bar without wasted vertical space.
  - **Universal Favorites Pinning to Home With Instant Jump**: Extended `RoomModel.toggleFavorite` (`RoomModels.kt`, `ControlScreens.kt`, `WledScreens.kt`) so any WLED Preset, Playlist, Effect, Palette, Colour Swatch, Room Scene, or IR Command can be starred as a Favorite and pinned to the Home screen's "Favorites Within Reach" section, with automatic navigation (`onFavoriteAdded`) to the Home screen upon starring.
  - **Lively Spring Button Animations**: Added tactile spring scale animations (`Modifier.livelyClick` and `Modifier.livelyPressScale` in `DesignControls.kt`) across navigation items, buttons, chips, swatches, and cards.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** alongside legacy **1.1.4-music** compatibility; no firmware flash or OTA counter change performed.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 14 (V14)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V14**; previous delivery: **V13**.
- Android source advances to **0.4.0 / versionCode 11**:
  - Complete native in-app WLED + Room integration replacing all dependence on WLED's embedded web portal (`WledBrowser.kt`, `WledTheme.kt`, and `androidx.webkit` deleted).
  - Exhaustive control parity matrix recorded in `knowledge/docs/WLED_PARITY_MATRIX.md` and `artifacts/WLED_PARITY_INVENTORY.md`.
  - Native lighting workspace (`WledScreens.kt`, `WledProtocol.kt`, `WledConfig.kt`, `WledModel.kt`): master power, brightness (coalesced 120 ms slider + `/json/si` readback), crossfade transitions, nightlight (`nl` timer/mode/target), live override (`lor`), WebSocket live LED strip preview (`{"lv":true}` binary `'L'` + JSON frames), Primary/Secondary/Tertiary segment color slots, RGBW/CCT/Hex entry, dynamic `/json/eff`, `/json/pal`, and `/json/fxdata` parameter sliders (`sx`,`ix`,`c1`,`c2`,`c3`) and options (`o1`,`o2`,`o3`), isolated multi-segment geometry/grouping/spacing/offset/reverse/mirror/freeze editor, and native WLED presets & playlists CRUD (`/presets.json`, `ps`, `psave`, `pdel`, `pl`) with Room routine linking.
  - Native audio-response & microphone screen (`SoundTuning.kt`): WLED `AudioReactive` configuration (`/json/cfg` `um.AudioReactive`: enable, digital I2S mic type, INMP441 pins `26/25/35` with `RoomHardwarePins` conflict validation, squelch, gain, AGC, dynamics limiter/rise/fall, frequency scaling, UDP audio sync), Room quiet-room behavior (`off`/`dim`/`hold`, timeout `1..300s`), sound-reactive effect launcher, and explicit `AudioReadinessState` telemetry classification.
  - Native controller settings (`WledSettingsScreens.kt`, `DeviceScreens.kt`): Wi-Fi station + write-only password & setup AP (`4.3.2.1` / `192.168.4.1`), LED strip hardware & software current budget (`maxpwr`, `ledma`, GPIO 23 guard, color order, boot defaults, gamma), shared NTP/POSIX timezone & WLED timers, UDP/E1.31/MQTT sync, physical button/GPIO map, native `presets.json`/`cfg.json` export & validated import, and controller reboot.
  - Room HTTPS transport repair (`DeviceTransport.kt`, `BoundedInput.kt`): serialized per-controller HTTPS requests, `Connection: close` header to prevent ESP32 2-socket pool exhaustion, and `Content-Length`-bounded stream reading to handle post-body TLS closure without false failures while deterministically rejecting truncated payloads.
- Combined ESP32 target firmware contract remains **2.0.1-wled-room** (WLED `16.0.1` + `RoomBridgeUsermod` + `AudioReactive`) alongside legacy **1.1.4-music** compatibility; no firmware flash or OTA counter change performed in this step.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 13 (V13)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V13**; previous delivery: **V12**.
- Android source advances to **0.3.5 / versionCode 10**:
  - Automations pause state visual and interaction disabling: when automations are paused, all routine cards and quick-start templates are visibly grayed out (`alpha(0.45f)`), click interactions (toggle, "Run now", "Edit", "Delete", template selection) are completely disabled, and an informative pause banner and "PAUSED" badges are clearly presented.
  - Settings screen cleanup: removed the redundant multi-method connection instructions (`ConnectionGuides`) from the lower section of Settings across direct device and service configurations, keeping Settings focused on essential Wi-Fi credentials, clock/timezone preferences, backup/restore, firmware updates, and diagnostics.
- Canonical ESP32 source remains **1.1.4-music**; no firmware modification, flash or OTA counter change.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 12 (V12)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V12**; previous delivery: **V11**.
- Android source advances to **0.3.4 / versionCode 9**: redesigns the Routines menu and creation modal into a fully functional automation system. Features include:
  - Instant "Run now" execution for any routine right from the routine card and live "Test action now" testing within the routine editor.
  - Quick-start one-tap routine templates carousel (Morning Wakeup, Evening Wind-Down, Music Bass Sync, Bedtime All Off).
  - Rich lighting action customization (power toggle, visual color swatches, brightness slider 1-100%, dynamic effects like solid/bass/beat/level/bands, speed and intensity controls, or saved presets).
  - Infrared appliance command integration and Spotify music playback control.
  - Comprehensive triggers: Schedule with quick time chips and weekday presets, sound started (music detected), sound stopped (room quiet), IR remote signal received, and one-tap manual rituals.
  - Automation master status banner with Arm / Pause toggle and timezone indicator.
- Canonical ESP32 source remains **1.1.4-music**; no firmware modification or flash.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 11 (V11)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V11**; previous delivery: **V10**.
- Android source advances to **0.3.3 / versionCode 8**: implements proper Spotify album artwork
  and track photo rendering across all player and browsing surfaces using Coil (`AsyncImage`),
  with robust multi-path JSON extraction (`image_url`, `artwork_url`, `artwork`, nested `album.images`),
  tap-to-expand artwork display in `MusicPlayerWidget` with smooth vinyl canvas fallback, track
  photos in `DeviceMusicScreen`, item thumbnails in browse/search results, and realistic preview fixtures.
- Canonical ESP32 source remains **1.1.4-music**; no firmware modification or flash.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 10 (V10)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V10**; previous delivery: **V9**.
- Android source advances to **0.3.2 / versionCode 7**: implements the LED color wheel control
  (`LedWheelColorPicker`) matching the hardware reference design — full radial Hue-Saturation disc
  with pure white center and vibrant perimeter (no muddy black on the color wheel), dedicated
  brightness and saturation gradient sliders, two rows of quick color swatches (with off and rainbow "R"),
  and circular `Fx` (reactive effects) and `Bg` (solid color) mode buttons.
- Canonical ESP32 source remains **1.1.4-music**; no firmware modification or flash.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 9 (V9)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V9**; previous delivery: **V8**.
- Android source advances to **0.3.1 / versionCode 6**: handles missing `assets/room-device.config`
  gracefully without inventing board identity, automatically launches the in-memory UI preview when
  no local device configuration or session is present, and adds the AI Studio emulator debug signing config.
- Canonical ESP32 source remains **1.1.4-music**; no firmware modification, flash or OTA counter change.
- Earlier verified personal APK **0.3.0 / code 5** (`917a4bdba23b4f7c9e2b7042abb365e52204fbe8752b0b5d9a0407d2d2d076c4`) remains preserved.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 8 (V8)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- V8 organizes the existing Android folder as the standalone **TheRoom** Git repository.
- Room APK copies and main knowledge documents are collected in `android/apks/` and
  `android/knowledge/`, with original paths, hashes and release identities recorded.
- Android remains **0.3.0 / code 5**; no app implementation was changed or rebuilt.
- Canonical ESP32 source remains **1.1.4-music**; no firmware modification or flash.
- Raw device configuration, machine paths and caches stay untracked. Personal APKs
  still embed controller credentials and belong only in the verified private repository.
- Release helpers now support either the original workspace or the standalone app root.
- Remote: `https://github.com/Ishan-s-2023/TheRoom.git`; target branch `main`.

Earlier deliveries are retained below.

# SmartRoom Controller — Version 7 (V7)

Current project delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V7**; previous delivery: **V6**.
- Scope: append the verified final-app inventory and compatibility record to the shared
  handoff, establish the Android release ledger, preserve versioned APK/source/evidence
  copies, and guard future builds against reused app version codes.
- Android remains **0.3.0**, versionCode **5**, release ID **ROOM-ANDROID-0.3.0+5**.
  This is the existing delivered APK, not a newly compiled or installed app.
- Canonical ESP32 source remains **1.1.4-music**; firmware implementation is unchanged.
- No device flash, app installation or OTA counter query/change was performed.
- WLED app lighting/sound and the current Room-only firmware are separate paths;
  complete WLED/Room firmware integration remains outstanding.
- See `versions.md` for release numbering and exact immutable artifacts, and section 10
  appended to `ESP32_APP_SYNC_CONTEXT.md` for the comprehensive app record.

The historical V6 delivery record is retained below.

# SmartRoom Controller — Version 6 (V6)

Current project codebase delivery, 21 October 2026 (Asia/Calcutta).

- Delivery version: **V6** (previous delivery: V5).
- ESP32 source version: **1.1.4-music** (previous: 1.1.3-controls).
- Verification: ESP32 compile/link and host regressions passed; see canonical BUILD_VERIFICATION.md.
- Canonical source: SmartRoomController_Complete_Source/SmartRoomController/.
- Android source: **0.3.0**, versionCode **5**, unchanged.
- OTA monotonic version: device NVS value, not queried or changed.
- Installation: no board flash or Android installation performed. Last recorded board: 1.1.2-http.

V6 integrates the supplied Music-to-Light DSP and renderer while preserving calibration,
quiet policies, telemetry and sound events. See START_HERE_ESP32.md, the V6 addendum
in ESP32_APP_SYNC_CONTEXT.md, and RELEASE_V6_1_1_4_MUSIC.md in the canonical source.
BUILD_VERIFICATION.md records build/test status. The secondary firmware mirror remains
at 1.1.3-controls; use the canonical source above for V6.

V5 organized obsolete copies under archive/obsolete-esp32-V5 without implementation
changes. V4 named the earlier codebase and synchronization handoff. Those historical
snapshots are retained. Newly generated V6 archives carry V6 in their names.

The mandatory sequential delivery version rule is in AGENTS.md.
