# TheRoom

Room's standalone Android app repository. Project delivery **V8**; the existing
verified APK remains **0.3.0 / versionCode 5** (`ROOM-ANDROID-0.3.0+5`).

## Start here

- [APK catalogue](apks/README.md): copies of all six project Room APK files, with
  original paths, internal versions, hashes and the verified final release identified.
- [Knowledge index](knowledge/README.md): copied project/app/firmware documentation.
- [Complete synchronization handoff](ESP32_APP_SYNC_CONTEXT.md): app inventory and
  known app/ESP32 integration gaps; earlier content is retained.
- [Release ledger](versions.md) and [project delivery](VERSION.md).
- [WLED integration](knowledge/docs/WLED_INTEGRATION.md) and
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
configuration; do not publish this repository or redistribute these APKs publicly.
The plaintext `app/src/main/assets/room-device.config` is deliberately ignored by Git.
It remains intact in the original local checkout.

After cloning this private repository, restore the matching configuration from the
verified personal APK before running the app:

```powershell
python scripts/restore_private_config.py --apk apks/artifacts/android-releases/0.3.0-code5/RoomController-V7-android-0.3.0-code5.apk
```

The helper verifies that APK's expected checksum and writes only the ignored local
asset. It prints no credential values and refuses to replace a different existing
identity. `config/room-device.config.example` describes the fields but is not a real
controller identity. Do not regenerate ESP32 credentials to make a clone build work.

## Build and release

Install an Android SDK with API/build tools 35, set `ANDROID_HOME`, and set `JAVA_HOME`
to a compatible JDK (17 or newer). This repository uses the Gradle wrapper.

```powershell
./gradlew.bat testDebugUnitTest lintDebug
python -m unittest discover -s scripts -p test_app_release_version.py -v
```

Before generating a new released APK, advance the project delivery and Android
versionName/versionCode as documented in [versions.md](versions.md). Code 5 is already
released; the next app release needs at least code 6. Then run:

```powershell
python scripts/build-final-apk.py
```

It rejects reused app codes, freezes only Android build inputs, runs build/tests/lint,
and preserves versioned APK/source/evidence copies. Knowledge documents, APK archives
and Git internals are not copied into build inputs. Keep the signing identity consistent
for updates to an already installed app.

To refresh copied knowledge from the original combined workspace:

```powershell
python scripts/sync_workspace_knowledge.py --workspace "D:/PC DEFAULT/Room automation"
```

## Firmware compatibility

The current canonical Room ESP32 source is `1.1.4-music`; it is not the combined
WLED/Room firmware. This APK's live lighting and sound routes require WLED, while
IR/Spotify/combined routines use the retained Room `/v1` service. See the sync
handoff before claiming the complete app/board pair is functional. No ESP32 was
flashed and no APK was rebuilt as part of repository organization.
