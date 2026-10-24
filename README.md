# TheRoom

Room's standalone Android app repository. Project delivery **V29**; Android source is **0.5.5 / versionCode 26**, while the existing verified personal APK baseline remains **0.3.0 / versionCode 5** (`ROOM-ANDROID-0.3.0+5`).

## Start here

- [APK catalogue](apks/README.md): copies of all six project Room APK files, with
  original paths, internal versions, hashes and the verified final release identified.
- [Knowledge index](knowledge/README.md): copied project/app/firmware documentation.
- [Complete synchronization handoff](ESP32_APP_SYNC_CONTEXT.md): app inventory and
  known app/ESP32 integration gaps; earlier content is retained.
- [Release ledger](versions.md) and [project delivery](VERSION.md).
- [WLED control parity matrix](knowledge/docs/WLED_PARITY_MATRIX.md),
  [WLED integration](knowledge/docs/WLED_INTEGRATION.md), and
  [ESP32 migration plan](knowledge/artifacts/ESP32-WLED-MIGRATION.md).

The verified final APK is
[`apks/artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5.apk`](apks/artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5.apk).
Its SHA-256 is `917a4bdba23b4f7c9e2b7042abb365e52204fbe8752b0b5d9a0407d2d2d076c4`.
V8 organizes the repository; it does not rebuild or renumber that APK.

## Repository layout

| Location | Contents |
| --- | --- |
| `app/` | Native Kotlin/Compose app, resources and Android tests |
| `gradle/`, Gradle files | Standalone Android build configuration and wrapper |
| `apks/` | Personal APK copies and a SHA-256/internal-version catalogue |
| `knowledge/` | Indexed snapshots of main project, app and firmware documents |
| `scripts/` | Release version checks, frozen build, tests, credential restoration and knowledge refresh |
| `artifacts/` | Release history and verification metadata; future app release outputs |
| `config/` | Non-secret private-configuration example |

The ESP32 application source, toolchain downloads, build caches and optional Python
companion backend are outside this standalone app repository. Firmware context remains
available in the copied documents. A copied knowledge file is a dated snapshot; use
the recorded canonical source and version when changing firmware.

## Private controller configuration

This repository is private. The personal APKs contain the original controller access
credentials for this room. Do not publish, push publicly or invent device certificates.
Before building or testing with a device connection, restore the ignored local asset:

```bash
python scripts/restore_private_config.py --apk apks/artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5.apk
```

When no device credentials are present (such as in fresh checkouts or UI preview), the
app automatically defaults to the in-memory UI preview without throwing errors.

## Release verification and versioning

New app releases must use a strictly larger Android `versionCode` and update
versionName/versionCode as documented in [versions.md](versions.md). Code 5 is already
verified and preserved. Verify app releases using:

```bash
python scripts/build-final-apk.py
```
