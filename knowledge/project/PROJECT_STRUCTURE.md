# Project organization — V6

Project root: `D:\PC DEFAULT\Room automation`.

V6 integrates music DSP/lighting in the canonical ESP32 folder, with firmware source version 1.1.4-music. See VERSION.md and the canonical RELEASE_V6_1_1_4_MUSIC.md. The secondary firmware mirror remains at 1.1.3-controls. No device installation was performed.

Historical V5 organized the workspace without changing firmware/app implementation or deleting files. The six obsolete ESP32 items listed below were moved, with every file's SHA-256 verified. Active development paths and private identities remain in place. The historical V5 ESP32 snapshot is `1.1.3-controls`; the captured Android source version is `0.3.0` / versionCode 5. These component versions and installation status are independent of project delivery versions.

## Active development: use these

| Location | Purpose |
| --- | --- |
| `SmartRoomController_Complete_Source/SmartRoomController/` | Canonical ESP32 source. Open SmartRoomController.ino with all companion files intact. |
| `android/` | Current native Android app and direct ESP32 integration. |
| `scripts/` | Development, app build, preview, packaging and QA helpers. |

Never replace the canonical controller's DeviceSecrets.h or .device-private identity with those from another folder. Keep private files out of public exports.

## Project rules and synchronization

| Location | Purpose |
| --- | --- |
| `AGENTS.md` | Mandatory development/delivery versioning rule. |
| `VERSION.md` | Current delivery label and version history. |
| `START_HERE_ESP32.md` | Arduino entry point and build/upload guidance. |
| `ESP32_APP_SYNC_CONTEXT.md` | Full requirements/API/app integration handoff and earlier source snapshot. Embedded source remains the captured snapshot, not automatically refreshed working code. |
| `ESP32_Smart_Room_Controller_Requirements.md` | Original requirements; later confirmed choices supersede conflicting details. |
| `docs/` | Approved mappings, direct connection guides, app/API documentation and evidence. Some documents describe older releases. |
| `APP_FEATURES.md`, `README.md`, `esp32 diagram.png` | Supporting descriptions and hardware reference; actual electrical qualification remains separate. |

## Build and verification support: keep available

- `.arduino-deps/`: readable Arduino dependency copies.
- `.tools/`: downloaded toolchains/utilities.
- `artifacts/`: APKs, source snapshots, logs and verification reports. Evidence applies to its recorded source/build, not every later edit.
- `.gitignore`: exclusions for private/generated content.

## Conditional or recovery material: retained in place

| Location | Why retained |
| --- | --- |
| `esp32-firmware/` | Maintained public mirror, tests, bridge source, builds and a separate private identity. Not the canonical upload folder. |
| `firmware/` | Alternative PlatformIO implementation with unique source. |
| `artifacts/spotify-latency-fix/`, `artifacts/spotify-tls-fix/` | Historical fixes/recovery evidence; some content includes credentials. |
| `backend/`, `data/room.db`, `.certs/` | Optional companion service, database and certificates; not needed for normal direct phone-to-ESP32 control. Database/certificates may be sensitive. |
| `test_playback.py` | Diagnostic utility. |
| `app/` | Remaining historical project export. Do not develop against this copy. Only its three obsolete ESP32 subfolders were relocated. |

## App appearance/reference material

`components/`, `component-lab/`, `lib/`, `styles/`, `component-lab.html`, `components.json`, `package.json`, `package-lock.json`, `tsconfig.json`, `vite.config.ts` support the React component preview. Root `node_modules/` is its reinstallable dependency directory. These are separate from the native app and ESP32 runtime.

## Music integration references

`MUSIC_REACTIVE_SYSTEM_COMPLETE_GUIDE.md`, `MUSIC_TO_LIGHT_PIPELINE.md`, `MUSIC_TO_LIGHT_INTEGRATION_MANUAL.md` and `CODEX_INTEGRATION_PROMPT.md` are music-pipeline references/proposals. V6 integrates the supplied pipeline using the user-authorized pasted prompt and integration manual, with compatibility corrections documented in the canonical V6 release notes. Other proposals and unmeasured performance claims are not automatically implemented or verified.

## Obsolete ESP32 copies: archived, not deleted

Archive root: `archive/obsolete-esp32-V5/`. Original relative paths are preserved beneath it:

1. `SmartRoomController_Arduino/`
2. `app/SmartRoomController_Arduino/`
3. `app/SmartRoomController_Complete_Source/`
4. `app/esp32-firmware/`
5. `ESP32_Firmware_Handover/`
6. `artifacts/esp32-1.0.1-before-audit-changes.zip`

`MOVE_MANIFEST.json` in that archive records the original/destination paths, byte lengths and SHA-256 of each moved file. All were checked after moving. For reversal, move an archived item back to its original relative location only after confirming the destination is absent; never overwrite active source or credentials.

Historical documents may still mention original locations. Resolve those obsolete paths through the archive. Older export scripts may reference the old folders and should be reviewed before reuse; no export or source archive was regenerated by this organization task.

## Generated caches

`.pytest_cache/`, `__pycache__/`, `.pio/`, Gradle caches and build directories are generally regenerable, but builds may contain private generated headers or useful recovery binaries. They were retained. Preserve required evidence and private recovery material before any later cleanup.
