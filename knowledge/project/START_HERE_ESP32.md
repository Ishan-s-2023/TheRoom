**Latest project organization delivery: V8.** The app is isolated as TheRoom in `android/`, with APK and knowledge copies. ESP32 source remains 1.1.4-music, and Android remains 0.3.0/code 5. No component installation was performed. Earlier release instructions below remain historical context.

# ESP32 release 1.1.4-music — start here

**Latest project delivery: Version 7 (V7).** V7 adds app release tracking and an
append-only synchronization record; the V6 firmware source below remains unchanged
at `1.1.4-music`. The final APK is Android **0.3.0 / code 5**, release ID
**ROOM-ANDROID-0.3.0+5**. See `versions.md` for the immutable APK/source/evidence
paths and section 10 of `ESP32_APP_SYNC_CONTEXT.md` for its complete app inventory
and compatibility limits. No device was flashed or phone app installed for V7.
The V6 implementation details and earlier entry-point text below are retained as history.

**Project codebase delivery: SmartRoom Controller — Version 6 (V6).** The firmware component source is now `1.1.4-music`; no upload has been performed. See `VERSION.md` for the delivery record, `ESP32_APP_SYNC_CONTEXT.md` for the complete handoff, and `AGENTS.md` for the mandatory versioning rule on future deliveries.

[V6 public ESP32 source archive](artifacts/SmartRoomController_V6_1.1.4-music_Source.zip) — excludes private credentials. Compile/link and host regressions passed; see the build record below.

See [V6 music integration notes](SmartRoomController_Complete_Source/SmartRoomController/RELEASE_V6_1_1_4_MUSIC.md) for preserved behaviors, verification and acceptance limits.

See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for the current folder categories and archived obsolete copies.

The active Arduino source is [SmartRoomController.ino](SmartRoomController_Complete_Source/SmartRoomController/SmartRoomController.ino). Open it with its entire folder intact. The small .ino entry point calls the application implemented in the companion .cpp/.h files; it is not the earlier print-only diagnostic sketch.

1. Keep the existing DeviceSecrets.h, matching .device-private archive, room-data backup and compatible partition setting. Do not regenerate the identity or format the filesystem as a routine upgrade.
2. Use Espressif ESP32 core 3.3.1 and the exact library versions in the [README](SmartRoomController_Complete_Source/SmartRoomController/README.md). The optional tools/build.ps1 helper installs pinned libraries and checks the installed core; it compiles without uploading.
3. Review the [build record](SmartRoomController_Complete_Source/SmartRoomController/BUILD_VERIFICATION.md), then use your existing Arduino IDE USB upload workflow. Keep your existing compatible partition layout. The build target used for verification is documented; it is not a request to repartition an already configured board.
4. At 115200 baud with Newline, send `{"op":"state"}`. Confirm runtime.version is 1.1.4-music, configuration/storage/secrets are ready, and integrity_failures is zero. Follow [device acceptance](SmartRoomController_Complete_Source/SmartRoomController/DEVICE_ACCEPTANCE.md) before enabling unverified drivers.
5. Use the [detailed completion checklist](SmartRoomController_Complete_Source/SmartRoomController/COMPLETION_TODO.md) and [requirements coverage](SmartRoomController_Complete_Source/SmartRoomController/REQUIREMENTS_COVERAGE.md). Checked software items and unchecked physical/account acceptance tasks have different meanings.

The secondary source under esp32-firmware/arduino/SmartRoomController remains the previous 1.1.3-controls copy; use the canonical folder above for V6. Its separate private identity is preserved. Older extracted folders elsewhere in this workspace are not the active upload source. Source ZIPs omit private identities/signing material; an existing board must retain its own matching identity.

The separate Android project still needs the documented [direct-device API integration](SmartRoomController_Complete_Source/SmartRoomController/API.md). This change does not install new code on the board, alter Android, erase data or provision device security.
