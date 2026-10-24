# Room 0.4.0 — Native In-App WLED & Room Integration

Project organization delivery: **V14**.
Android source version: **`0.4.0` (`versionCode 11`)**.
Verified baseline personal APK (`artifacts/RoomController-final.apk`, `0.3.0 / code 5`) remains preserved unchanged.
Target combined firmware contract: **`2.0.1-wled-room`** (`WLED 16.0.1` + `RoomBridgeUsermod` + `AudioReactive`).

## Architecture & Native Control Model

All WLED lighting, audio-reactive, preset/playlist, and controller configuration features are implemented as **100% app-owned Jetpack Compose screens** inside `in.roomcontroller.app`. All embedded WLED web portal hosts (`WledBrowser.kt`, `WledTheme.kt`, `android.webkit.WebView`, Custom Tabs, and browser redirects for controller pages) have been permanently removed.

| Surface | Implementation |
| --- | --- |
| **Native Lighting Workspace (`WledScreens.kt`)** | 5-tab Compose workspace (**Colour**, **Effects**, **Segments**, **Presets**, **Controllers**) with master power, brightness (`0..255`), transition crossfade (`tt`), nightlight (`nl` enable, duration, target brightness, mode, remaining time `rem`), live override (`lor`), primary/secondary/tertiary segment color slots (`col`), RGB/RGBW/CCT sliders, hex entry, LED color wheel & swatches |
| **Dynamic Effects & Palettes (`WledProtocol.kt`, `WledScreens.kt`)** | Live catalog loading from `/json/eff`, `/json/pal`, and `/json/fxdata`. Parses upstream effect metadata strings to render custom slider labels (`sx`, `ix`, `c1`, `c2`, `c3`), checkboxes (`o1`, `o2`, `o3`), dynamic color slot labels, palette capability flags, 1D/2D matrix indicators, and audio-reactive (`v`/`f`) filters |
| **Multi-Segment Editor (`WledScreens.kt`, `WledModel.kt`)** | Isolated segment updates (`id`, `start`, `stop`, `grp`, `spc`, `of`, `on`, `bri`, `rev`, `mi`, `frz`, `sel`, `n`) with add/delete/reset segment actions and hardware pixel ceiling validation |
| **Presets & Playlists (`WledScreens.kt`, `WledModel.kt`)** | Native `/presets.json` parsing and CRUD (`ps`, `psave` with `ib`/`sb`/`sc`, `pdel`, playlist `pl` cycling with per-step durations, transitions, repeat count, and end preset) plus Room routine linking |
| **Live LED Strip Preview (`WledProtocol.kt`, `WledModel.kt`, `WledScreens.kt`)** | Native WebSocket `/ws` live LED stream (`{"lv":true}`) decoding binary `'L'` frames (v1 2-byte header and v2 4-byte header with 1D/2D dimensions) and JSON `leds` arrays into a Compose `Canvas`, automatically sending `{"lv":false}` on hide/dispose |
| **Native Sound Tuning (`SoundTuning.kt`, `WledConfig.kt`)** | Combined WLED `AudioReactive` usermod (`/json/cfg` `um.AudioReactive`: enable, digital I2S mic type, INMP441 pins `BCLK 26`, `WS 25`, `SD 35` with reserved-GPIO conflict validation, squelch, gain, AGC, dynamics limiter/rise/fall, frequency scaling, UDP audio sync) + Room `/v1/command` `set_microphone` (`quiet_behavior`: `off`/`dim`/`hold`, `quiet_seconds`: `1..300`) + truthful `AudioReadinessState` banner (`HEALTHY_LOCAL`, `SILENT_ROOM`, `UDP_SYNC_ONLY`, `STALE_TELEMETRY`, `MIC_INIT_FAILED`, `MIC_DISABLED`, `OFFLINE`) |
| **Native Controller Settings (`WledSettingsScreens.kt`)** | 5-section native `/json/cfg` editor (**Wi-Fi & Setup AP**, **LEDs & Power Budget**, **Time & Timers**, **Sync & Interfaces**, **Pins & Backup/Restore**) using `deepMergeJson` and `stripUnchangedSecrets` so unknown firmware keys and write-only passwords (`psk`, `pskl`) are never clobbered |
| **Room HTTPS Framing & Socket Pool Repair (`DeviceTransport.kt`, `BoundedInput.kt`)** | Serializes requests per `DeviceTransport` via a Kotlin `Mutex`, sends `Connection: close`, and uses `Content-Length`-aware bounded stream reading (`readHttpBodyBytes`) so post-body TLS EOF without `close_notify` on ESP32 `esp_https_server` (`max_open_sockets = 2`) succeeds cleanly while truncated JSON deterministically fails with `EOFException` without replaying POSTs |

## Main Implementation Files

- `app/src/main/java/in/roomcontroller/app/WledProtocol.kt`
- `app/src/main/java/in/roomcontroller/app/WledConfig.kt`
- `app/src/main/java/in/roomcontroller/app/WledModel.kt`
- `app/src/main/java/in/roomcontroller/app/WledScreens.kt`
- `app/src/main/java/in/roomcontroller/app/WledSettingsScreens.kt`
- `app/src/main/java/in/roomcontroller/app/SoundTuning.kt`
- `app/src/main/java/in/roomcontroller/app/DeviceTransport.kt`
- `app/src/main/java/in/roomcontroller/app/BoundedInput.kt`
- `knowledge/docs/WLED_PARITY_MATRIX.md`
- `artifacts/WLED_PARITY_INVENTORY.md`
