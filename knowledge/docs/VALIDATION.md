# Validation — 20 October 2026

## Android

- `assembleDebug`, `testDebugUnitTest` and `lintDebug`: **passed**.
- Three unit tests verify bounded backup/manifest input, exact-size acceptance,
  UTF-8 preservation, and stopping oversized reads before reading the full file.
- Debug APK installed and launched on an API 35 x86_64 Android emulator.
- Native navigation smoke pass covers Home, Lights, Remotes, Routines, More,
  Music, Sound and System. Screenshots are under `artifacts/`.
- A separate native workflow pass signed in through HTTP to an isolated local
  backend, learned/saved a synthetic IR command, and created a routine. The actual
  server state was read back to confirm both writes persisted.
- Visual QA identified dark system-bar icons on a dark background; explicit dark
  system-bar styling was added. Theme container colors were also made consistent.
- Lint has **0 errors and 13 warnings**. Remaining warnings concern pinned older library versions,
  intentional user-installed CA trust, development-only cleartext, and optional KTX
  style suggestions. These are not hidden with a lint baseline.
- `RoomController-debug.apk` is a development APK. Release signing, real-phone
  compatibility, actual Spotify playback and real peripheral control are not verified.

## Local backend

`python -m pytest backend/tests -q`: **25 passed**.

Verified using isolated SQLite stores and provider mocks where appropriate:

- Owner authentication, logout/revocation, Unicode usernames and login throttling.
- Separate owner/device authorization, capability checks, offline action rejection.
- Schema/range validation, appliance references and destructive confirmations.
- Job deduplication, conflicting idempotency keys, IR payload identity, single
  delivery, explicit acknowledgements and expiry without automatic replay.
- Guided IR learning, timeout/capture/save contracts and idempotent save.
- Time zones, selected weekdays, boot/missed-window skipping, repeated-minute
  suppression, DST gaps/folds and device/server scheduling ownership.
- Global automation pause while command expiry housekeeping continues.
- Event cooldowns and suppression of a startup sound transition.
- AES-256-GCM backups, wrong-passphrase rejection, atomic restore, reference
  validation and paused imported routines.
- Signed OTA manifest validation and rejection without a verified OTA capability.
- Spotify PKCE/state reuse rejection, encrypted refresh tokens, refresh-token
  preservation, volume request construction, rate-limit cooldown, and clear
  network/API access errors. The vault key must be supplied separately from the DB.
- Actual streamed request size limits, including missing Content-Length/chunked input.
- Generated local certificates pass an actual TLS handshake with strict X.509
  verification. The generator refuses to overwrite existing private keys.

A separate live HTTPS smoke test verified the server certificate, password login,
and authenticated state request against a temporary local service. It found missing
certificate key identifiers during development; the generator now includes SKI/AKI
and appropriate key usage, with regression coverage.

The suite emits one upstream Starlette TestClient/httpx deprecation warning.
It does not perform real Spotify authorization or modify a real appliance.

## Portable C++

`python esp32-firmware/tests/run_host_tests.py`: **passed** with Zig 0.13.0,
C++17, `-Wall -Wextra -Werror` for project sources.

- Supplied controller/storage/IR/time/audio/connectivity-policy assertions pass.
- New approved-panel assertions cover all four shortcuts, encoder navigation,
  selection, bounded adjustment including large input, cancellation, return home,
  empty menus, destructive confirmation and a collision-free approved GPIO profile.

Host tests use controlled adapters. They do not validate electrical outputs or
the timing/performance of actual ESP32 peripherals.

## ESP32 target build

The default development image successfully cross-compiled with ESP-IDF 5.5.0,
PlatformIO espressif32 6.12.0, Xtensa GCC 14.2.0. Output:
`artifacts/RoomController-development.bin` (207,712 bytes).

ESP-IDF rejected spaces in the original workspace path. The build helper uses an
isolated copy under `D:\CodexBuildCache\room-controller-esp32`; original sources stay
in the workspace. PlatformIO's assembly embedding issue was avoided by generating
a C++ public-CA header with CMake.

The target configuration reports a 4 MB board default versus 2 MB SDK flash setting.
This is a development-target configuration warning, **not a measurement of a connected
board**. Flash capacity and partitions must be reconciled with the actual module
before flashing. No GPIO driver, device firmware, bootloader, security eFuse or
physical circuit was flashed/configured in this session.

The separately enabled HTTPS bridge also **cross-compiled successfully** using dummy
Wi-Fi/device credentials and a local test CA. The compile-check image is 939,664 bytes;
the linker reports 939,253 flash bytes and 34,960 static RAM bytes. This does not measure
runtime heap/stack or actual board capacity. The image is for compile verification,
not deployment; it has no working peripheral capability and contains dummy credentials.

## Unverified integration requirements

Physical LED/IR/microphone/OLED/input drivers, real AP provisioning/recovery,
direct ESP-hosted app access, real on-device event dispatch, OTA installation and
rollback, board flash/NVS limits, power/brownout behavior, acoustic performance,
strip current/thermal limits, real appliance captures, and Spotify account/device
eligibility remain unverified or unimplemented as detailed in the coverage matrix.
