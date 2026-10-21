# SmartRoom version and Android release ledger

Current synchronization/documentation delivery: **Version 7 (V7)**, recorded
21 October 2026, Asia/Calcutta. `VERSION.md` records the project delivery;
this file records the exact app releases and firmware baselines alongside them.

## Current baseline

| Version type | Recorded value | Meaning |
| --- | --- | --- |
| Project delivery | **V7** | App inventory, release tracking and append-only sync documentation; no app or firmware implementation change |
| Android versionName | **0.3.0** | Actual version embedded in the already delivered final APK |
| Android versionCode | **5** | Actual Android install/build code; not the project delivery number |
| App release ID | **ROOM-ANDROID-0.3.0+5** | Version and build together identify this APK |
| Canonical ESP32 source | **1.1.4-music** | Current V6 music implementation retained unchanged in V7 |
| Secondary ESP32 mirror | **1.1.3-controls** | Historical/secondary source, not the canonical upload folder |
| Installed ESP32 | **Not queried** | Shared handoff last records `1.1.2-http`; do not treat source as installed firmware |
| Installed phone app | **Not queried** | APK build/archival is not phone installation |
| OTA monotonic counter | **Not queried or changed** | Independent device-side counter, not Android versionCode |
| WLED/Room combined firmware | **Pending** | WLED app controls require a WLED-capable firmware build; current Room-only firmware is not that build |

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
