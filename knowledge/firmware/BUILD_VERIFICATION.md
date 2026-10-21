# Current release: V6 / 1.1.4-music — 21 October 2026

Compile/link passed, exit code 0, against the finalized canonical source.
Target: original ESP32, Espressif Arduino core 3.3.1,
`esp32:esp32:esp32:PartitionScheme=min_spiffs`.

- Program: **1,402,903 / 1,966,080 bytes**.
- Global RAM: **67,036 / 327,680 bytes**; compiler-reported remaining RAM:
  **260,644 bytes**, before runtime allocations.
- All **35 source/header snapshots** match after removing Arduino's generated
  line directives, including the private identity header without exporting it.
  The Arduino entry point matches after normalizing generated wrappers/prototypes.
- The compiled image contains firmware version `1.1.4-music`.
- Existing production C++ regressions passed with 4-byte and 2-byte slot IDs.
- All eight Python helper tests passed.
- New production DSP/renderer host tests passed: silence, 1 kHz tone, DC rejection,
  partial frames, eight engine effects, output bounds, zero brightness and live
  current-budget updates. Three existing unused-parameter/field warnings in the
  supplied engine are recorded in the host log; the pipeline files are unchanged.
- Prior source-manifest checks confirm unrelated implementation files are unchanged.
- Application objects were rebuilt from the finalized source snapshots. Core,
  libraries, SDK configuration and partition target retain the pinned build setup.

Logs and machine-readable evidence are under root `artifacts/esp32-V6-*`.
The public source archive is `SmartRoomController_V6_1.1.4-music_Source.zip`,
with a SHA-256 sidecar and internal per-file manifest. Credentials and generated
binaries are excluded. Existing device identity is required to build this source.

See [V6 integration and acceptance notes](RELEASE_V6_1_1_4_MUSIC.md).
No board was flashed, no Android build/install was performed and no OTA monotonic
counter was queried or changed. Physical microphone/LED/peripheral acceptance and
combined-load performance remain pending. Static RAM is not measured free heap.

---

## Historical verification before V6

# Historical release: 1.1.3-controls — 21 October 2026

Compile/link: passed, exit code 0. Original ESP32, Arduino core 3.3.1, esp32:esp32:esp32:PartitionScheme=min_spiffs. Program 1,398,547 bytes (71% of 1,966,080); global RAM 68,464 bytes (20%); compiler-reported remaining RAM 259,216 bytes before runtime allocations. All 34 frozen inputs match the build snapshots. Public ZIP manifests and private-identity exclusion checks pass.

[RELEASE_1_1_3_CONTROLS.md](RELEASE_1_1_3_CONTROLS.md) distinguishes observed live tests from post-upload acceptance. The live 1.1.2-http board synchronized after its NTP server was changed to pool.ntp.org. Its pause still hit a response parser error, including a fresh-connection test. The new binary corrects control response handling but has not been uploaded automatically; actual pause/resume remains unverified until tested on the device.

## Historical verification records

# Current release: 1.1.2-http — verified 21 October 2026

Compilation/link passed, exit code 0, original ESP32 / core 3.3.1 / esp32:esp32:esp32:PartitionScheme=min_spiffs. Program: 1,398,443 of 1,966,080 bytes (71%); global RAM: 68,464 bytes (20%); compiler-reported remaining RAM: 259,216 bytes before runtime allocations. All 34 compiler inputs match the frozen source and build snapshots.

[RELEASE_1_1_2_HTTP.md](RELEASE_1_1_2_HTTP.md) records the actual HTTP 411 failure and empty-request framing correction. Existing responsive-worker and NTP fixes are retained. Both source archives and their SHA-256 manifests are verified, and private identities are unchanged. This release has not been uploaded automatically. Playback acceptance and the separate serial-prefix investigation remain pending.

## Historical validation records

# Release 1.1.1 verification — 21 October 2026

Final compile/link: **passed, exit code 0**. Original ESP32, Arduino core 3.3.1, esp32:esp32:esp32:PartitionScheme=min_spiffs.

- Program: **1,396,795 / 1,966,080 bytes**.
- Global RAM: **68,128 / 327,680 bytes**; compiler-reported remaining RAM **259,552 bytes** before runtime allocations.
- All **34 frozen compiler inputs** match the build snapshots; generated Arduino line directives/prototypes/wrapper whitespace are the only normalized material.
- The strong verifyRollbackLater OTA hook remains linked.
- Production C++ regressions pass under both slot-ID sizes, including the new NTP hostname lifetime, configuration bounds and stop-before-update tests.
- The eight Python helper cases passed in the preceding 1.1.0 verification; those helpers are unchanged in this patch.
- Both public source copies and source ZIP manifests were verified; private identities and credentials remain excluded from ZIPs.

See [RELEASE_1_1_1.md](RELEASE_1_1_1.md) for the time-sync defect and [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md) for post-upload checks. The connected board is still on 1.1.0 until the user uploads this patch; a successful compile is not on-device clock or Spotify acceptance.

---

## Historical 1.1.0 verification

# Release 1.1.0 verification — 21 October 2026

The final Arduino CLI compilation and link passed with exit code 0. This record applies to the actual multi-file application in this folder, including the final OTA maintenance, Spotify feedback and loaded-snapshot ownership changes.

| Check | Observed result |
| --- | --- |
| Target | Original ESP32 / ESP32 Dev Module |
| Core | Espressif Arduino 3.3.1 |
| FQBN | esp32:esp32:esp32:PartitionScheme=min_spiffs |
| Application slot | 1,966,080 bytes, dual-slot OTA layout |
| Program | 1,396,599 bytes (71%) |
| Global RAM | 67,928 bytes (20%) |
| Compiler-reported remaining RAM | 259,752 bytes before runtime allocations |
| Frozen source/build snapshot comparison | All 32 compiler inputs matched |
| OTA early-acceptance override | ELF symbol has strong T binding for verifyRollbackLater |
| Production C++ host regressions | Passed with both 4-byte and 2-byte ArduinoJson slot IDs |
| Python helper tests | Eight passed with warnings treated as errors, with a narrowly scoped CA-bundle parser deprecation exception |
| Python/PowerShell syntax and document checks | Passed |
| Public source release integrity | Unique ZIP entries, SHA-256 manifest and private-material exclusion verified |

## What the tests establish

The host C++ suite compiles production database validation, request parsing, Spotify response projection/cache handling and the portable runtime policies, with -Wall -Wextra -Werror. It covers IR timing/repeat gates, pulse encoding, quiet-lighting/calibration policies, OTA decisions/journal matching, operation retention/string ownership, strict requests, chunked/truncated/oversized HTTP bodies, filtered Spotify pagination and allocation failures, and repeated constrained-memory snapshots. Two-byte slot IDs exercise ESP32-sized identifiers; this is not complete ESP32 ABI emulation.

Python tests cover unique identities and signed manifests, exact certificate pinning and pin rejection, authenticated backup/tamper rejection, protected-profile checks, owner-token rotation with lost-ack recovery, soak validation, and identity-preserving Spotify CA refresh. Historical zero serial numbers in trusted CA bundles receive a narrowly scoped parser-warning exception; ESP32 TLS verification remains enabled.

The build uses the exact dependencies listed in README.md. The build helper now checks core 3.3.1 and pins direct and supporting libraries. All companion application sources compiled and linked. Generated private identities were retained locally and excluded from public ZIPs. The separate maintained identity was preserved rather than replaced by the active device identity.

## Boundaries and pending acceptance

The new source was not uploaded during this task. The observed 120-request pinned-HTTPS baseline belongs to the existing 1.0.1-diagnostics device, not this release. The historical nested-JSON root cause remains unconfirmed; containment and regression coverage do not prove that cause.

Real 1.1.0 peripheral operation, Spotify account/device behavior, Android transport integration, long-duration combined workloads, storage power-cut recovery and signed OTA/rollback still require DEVICE_ACCEPTANCE.md. Unknown modules remain disabled pending their actual electrical/profile verification. A compiled current-limiting estimate is not electrical power-system verification.

The installed SDK lacks secure boot, flash encryption and encrypted NVS. The static protected-profile checker correctly rejects it. SECURITY_PROFILE.md records the explicit protected deployment gate; no eFuses or irreversible security settings were changed. The verified partition target does not authorize repartitioning an existing configured board.

Read RELEASE_1_1_0.md for the change mapping and COMPLETION_TODO.md for every remaining acceptance task. A clean compile is not a guarantee of zero possible defects.
