# SmartRoom Controller — Version 6 (V6), firmware 1.1.4-music

Delivered 21 October 2026 (Asia/Calcutta). Canonical source:
`SmartRoomController_Complete_Source/SmartRoomController/`.

## Integration

`Hardware.cpp` now initializes and uses the supplied `MusicAudioDSP` and
`MusicLightingEngine`. I2S stays on core 0 at 16 kHz / 512 samples; the application
renders 300 pixels every 25 ms on core 1. GPIOs remain LED 23, microphone BCLK 26,
WS 25, DIN 35. The supplied pipeline files are unchanged.

The manual's four substitutions were adapted to preserve existing behavior:

- Initialize audio independently of LED enablement, so microphone telemetry and
  calibration also work without a strip.
- Retain calibration acquisition, persistence, failure rollback and automatic/manual
  noise-floor handling. Keep the configured smoothing for rendered RMS/bands and telemetry.
- Compare the previous sound state against the quiet-timeout result, preserving
  sound_started/sound_stopped rules instead of overwriting the previous state first.
- Process FFT outside the application mutex; publish complete features under it.
  Latch beat/bar/drop/flux until one render consumes them.
- Preserve quiet hold, off and dim policies. Dim uses the selected solid color
  and the existing 6% factor, followed by the new engine's gamma correction.
- Refresh current-budget settings on every frame. These remain software estimates.
- Verify RGB layout with compile-time assertions. Render into real `MusicPixel`
  objects and copy channels, avoiding C++ aliasing violations from the proposed cast.

RoomApp retains its internal beat flag. Existing API telemetry retains rms,
sound_active and three bands mapped from DSP indices 0, 6 and 13. The API, presets and panel still support the original
solid/bands/beat/bass/level effects. The engine also contains comet/sparkle/wash;
this release does not expand validation or Android controls to expose them.

Wi-Fi, Spotify, IR, OLED, JSON/NVS persistence and OTA implementation files are
unchanged. The version string in `Config.h` is now `1.1.4-music`.

## Verification and acceptance

See `BUILD_VERIFICATION.md` and root `artifacts/esp32-V6-*.log` for final build
and regression results. New deterministic host tests exercise production DSP
silence, a 1 kHz tone, DC rejection, partial frames, all eight engine renderers,
output bounds, zero brightness and live current caps. Existing production policy,
request, database, HTTP, Spotify and Python helper suites remain relevant.

The reference manual's CPU timings, free-heap estimates and “100% compatible”
claims have not been measured on hardware for this release. The supplied engine
currently emits a fixed 120 BPM reference; it does not estimate actual tempo.
Its configured band edges extend to the 8 kHz Nyquist limit. Host renderer tests
do not establish physical light quality, microphone calibration or combined load
performance. Verify calibration save/failure, quiet policies, one sound event per
transition, one flash per beat, current limits and existing peripherals on the board.

No board was flashed and no app was built or installed by this release. Last
recorded board firmware remains `1.1.2-http`; current live state was not queried.
Android stays `0.3.0` / versionCode `5`. The OTA monotonic counter is stored in
the device's NVS, was not queried or changed, and is independent of V6 and the
firmware string. Private identity and partition layout are retained.

The secondary `esp32-firmware/arduino/SmartRoomController` copy remains the
previous `1.1.3-controls` source; it is not the V6 upload source. Historical source
snapshots and archives remain unchanged. Any new V6 public source archive excludes
`DeviceSecrets.h`, `.device-private` and generated build files; it requires the
existing board's own matching identity for compilation.
