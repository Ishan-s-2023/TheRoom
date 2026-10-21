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
