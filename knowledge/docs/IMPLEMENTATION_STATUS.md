# Requirements coverage and remaining work

This matrix covers the original baseline and the newer confirmed decisions.
“Implemented” below describes software behavior in the named layer. It does not
claim the corresponding appliance or circuit has been verified.

| Requirement | Android / backend implementation | ESP32 / external dependency |
| --- | --- | --- |
| Dedicated Wi-Fi Android app | Native Kotlin/Compose, Android 8+, nine feature screens | Real phone/network acceptance pending |
| Home/status and connection feedback | Last saved lighting, controller heartbeat, simulation and stale banners | Actual capability telemetry requires configured bridge |
| Local-only architecture | Local HTTPS companion service; no cloud relay deployment | Direct ESP-hosted app API remains pending; companion PC currently needed |
| Authentication | Password hashing, 12-hour revocable sessions, login throttling; Android Keystore token encryption | Per-device key; embedded HTTPS app server/pairing pending |
| Secure transport | Release HTTPS enforcement, private CA generator, certificate validation | Optional HTTPS bridge; no insecure fallback; device security not provisioned |
| Initial/recovery access points | Not implemented in the app/backend | Supplied lifecycle policy tests pass; real protected AP, credential exchange and recovery UI pending |
| Colour and brightness | Palette/custom HEX, brightness, power, validation, explicit apply | Physical strip driver/protocol/current limit pending |
| Presets / favorites | Persistent saved presets, home quick actions, deletion confirmation | Snapshot retains presets; local panel service adapter pending |
| Reactive effects | Solid/beat/bass/level/bands choices, speed/intensity controls | Real renderer, temporal beat tracking, DSP integration and strip verification pending |
| Microphone only | Automatic/manual sensitivity, noise floor, smoothing, quiet policy/timeout, calibration request | Actual I²S capture/calibration/quiet processing pending; supplied DFT is a reference |
| Frequency telemetry | Bass/mid/treble display from device telemetry | Real live band production pending |
| IR appliance library | Add, rename, group, favorites, delete, explicit send, decoded/raw/repeat fields | Cached library synchronization exists; transmitter/receiver drivers pending |
| Guided IR learning | Start/listen/capture/timeout/retry/save; backend capture callback | Real capture/decoder and carrier qualification pending; simulator supplies synthetic data |
| Repeated/held IR | Repeat timings stored and backed up | Verified per-protocol hold/replay still pending; no blind repeated toggles |
| IR state reporting | Transmitted versus confirmed distinction; job status and diagnostics | Actual appliances give no state feedback unless supported separately |
| Physical panel mapping | Explicitly approved; portable menu/shortcut/edit/confirmation policy implemented and tested | GPIO input driver, debounce, OLED rendering and action adapters pending |
| OLED music/status | Core view abstraction and backend music data available | OLED driver, clipping/scrolling, timeout behavior and panel music binding pending |
| Spotify authorization | PKCE, expiring state, encrypted token storage, refresh | User client ID/redirect/API access and actual authorization required |
| Playback and devices | Play/pause, next/previous, volume, device transfer, metadata | Real eligible devices/account testing pending; errors shown, no automatic disruptive retries |
| Playlists/recent/search | Native screens, search/page offsets and recent cursors, load-more action | No Spotify account connected here; real API access pending |
| Spotify vs speaker volume | Explicit preference; speaker path uses selected speaker IR command | IR volume not inferred from Spotify percentage |
| Daily/weekly schedules | Selected weekdays, 24-hour time, explicit IANA zone, edit/pause/delete | Final recurrence preferences not finalized; one-time/calendar recurrence pending |
| DST/time validity | Skip nonexistent spring times; first fall occurrence once; invalid time blocks scheduling | NTP needs configured source; no RTC assumed |
| Event rules | Sound start/stop and IR events, cooldown and pause | Actual device event producers and on-device event dispatch/cooldown adapter pending |
| Offline rules | Server evaluates simulator rules; physical device claims schedule ownership to avoid duplicate dispatch | Nine-date cached schedule core; no infinite offline recurrence; events not yet wired |
| Restoration | Persistent backend settings/library; lighting NVS adapter; no IR on startup | Actual lighting restoration depends on driver; reboot rules remain disarmed until fresh sync |
| Persistence | SQLite transactions/WAL and versioned device JSON snapshots | Flash capacity/NVS partitions/storage-full hardware tests pending |
| Backup/restore | Passphrase-encrypted export/import, schema/reference validation, atomic replacement, imported rules paused | Device re-sync after restore; physical panel backup UI not required |
| Reliability | Command expiry, at-most-once delivery, idempotency conflict checks, no blind retry; stale UI | Brownout/flash wear/timing/network failure testing on hardware pending |
| Diagnostics | Request outcome history, device heap/RSSI/firmware/time, explicit unsupported capabilities | Actual receiver/transmitter/audio diagnostics pending |
| OTA | Signed-manifest import/confirmation; Ed25519 validation; capability gating | Download/hash verification, partition selection, boot validation/rollback and flashing not implemented; OTA stays disabled |
| Gesto retention | Superseded by explicit replacement decision | No Gesto integration, removal or wiring performed |
| Addressability/power | No false per-LED claims; approved pins kept separate from verified electrical design | Exact strip/voltage/pixel count/current/injection/level shifting/fusing still required |
| Enclosure/PCB/components | Existing reference preserved; no fabricated final electrical design | Exact module selection, dimensions, thermal/power/circuit qualification pending |
| Exclusions | No Bluetooth, RF, direct audio input, strobe or public remote service | No extra mappings or automatic purchases |

Hardware-independent software does not resolve missing component specifications.
The remaining items above must be completed before this can be called a finished
standalone smart-room controller. An app screen or an API route is not evidence
that an unconnected peripheral works.
