# ESP32 requirements coverage — release 1.1.1

Read the historical requirements together with esp32-firmware/docs/CONFIRMED_UPDATES.md and the later approved GPIO/control mapping. The addressable replacement, local-only Wi-Fi and approved controls supersede the earlier Gesto/remote-access/unassigned-control statements. This table concerns ESP32 source; it is not a claim that the separate Android app already speaks this API.

| Requirement | Implementation | Verification boundary / remaining gate |
| --- | --- | --- |
| Original ESP32, approved GPIOs and controls | hardware_profile.hpp, Config.h, Panel.cpp | ESP32 build; actual board/wiring acceptance pending |
| Encoder navigation/edit/select/back and four shortcuts | Panel.cpp | Debounce/gesture implementation; physical encoder/button test pending |
| SSD1306 menus, favourites, learning and confirmations | Panel.cpp | Compiled; actual OLED/interface/address and visual acceptance pending |
| Track, artist, status and output device | Panel.cpp, Spotify.cpp | Separate scrolling fields; real metadata/OLED test pending |
| Addressable replacement, approximately 300 pixels at exactly 15 m | Config.h, Hardware.cpp, DatabaseValidation.cpp | Approved calculation; installed count, protocol, RGB order and current must be verified before enabling |
| Colour, brightness, presets and adjustable effects | Hardware.cpp, RoomApp.cpp | Validated settings and driver code; strip acceptance pending |
| Beat, bass, overall level and frequency bands | Hardware.cpp | I2S/FFT/render code; actual acoustic response pending |
| Room microphone, automatic/manual sensitivity and noise filtering | Hardware.cpp, Diagnostics.cpp | Disabled/busy/stalled calibration and persistence failure handled; actual module/acoustics pending |
| Quiet dim/off/hold | RuntimePolicies.h, Hardware.cpp | Portable policies tested; physical output acceptance pending |
| Named appliances, expandable command library and favourites | RoomApp.cpp, DatabaseValidation.cpp, Panel.cpp | Validation/persistence host tests; storage/heap limits are explicit |
| IR decoded metadata plus raw capture/transmit | Hardware.cpp, PulseEncoding.h | Timing/pulse tests and ESP32 build; appliance/carrier/driver acceptance pending |
| Press/hold repeats and disruptive-command safety | RuntimePolicies.h, Hardware.cpp | Initial-match/gap/timeout/safety regressions; real remote repeat acceptance pending |
| IR feedback without claiming appliance confirmation | JobData.h, Diagnostics.cpp, Hardware.cpp | Bounded result ledger tests; physical response still separate |
| Spotify playback, volume, device selection/transfer | Spotify.cpp | API client compiled; Premium/API permissions/device support pending |
| Saved playlists, recent items and search | SpotifyData.cpp, Spotify.cpp, Panel.cpp | Filtered fixtures, pagination and transactional cache tests; real API/account acceptance pending |
| Supported Spotify authorization and refresh | tools/spotify_authorize.py, Spotify.cpp | Helper/client implementation; real authorization and permission test pending |
| Spotify volume separate from IR speaker volume | Panel.cpp, settings volume_target and IR command mappings | Both command paths present; actual target mapping and device tests pending |
| Local Wi-Fi provisioning and protected router-loss AP | Network.cpp | Real Wi-Fi baseline exists only for 1.0.1; 1.1.0 recovery acceptance pending |
| Local authenticated/encrypted app API | Network.cpp, API.md | Pinned-client Python tests; Android transport integration acceptance pending |
| Unique setup/owner credentials and failed-login limiting | tools/prepare_device.py, Network.cpp | Identity/pin tests; real failed-login acceptance pending |
| Local operation without phone/internet | Hardware.cpp, Panel.cpp, Network.cpp | Source paths remain local; simultaneous outage/peripheral test pending; Spotify still needs internet |
| Local persistence and backup/restore | RoomApp.cpp, DatabaseValidation.cpp, tools/backup_crypto.py | Schema, roundtrip, wrong-password/tamper tests; power-cut/full-filesystem tests pending |
| Daily/weekday schedules and event rules | Hardware.cpp, DatabaseValidation.cpp | Validation and clock/cooldown/no-catch-up logic; on-device clock/event acceptance pending |
| Signed OTA, integrity checks, deliberate approval and recovery | Network.cpp, OtaHealth.cpp, RuntimePolicies.h | Signature/policy tests and ESP32 build; staging-device rollback/power-cut matrix pending |
| Stored-secret protection and deliberate recovery | SECURITY_PROFILE.md, OtaHealth.cpp, owner-token commands | Development SDK reports unprotected storage; protected SDK/device provisioning is an explicit open deployment gate |
| Clear diagnostics and bounded asynchronous work | Diagnostics.cpp, JobData.h, RequestValidation.cpp, tools/device_soak.py | Ledger, string ownership, request and constrained-memory regressions; 1.1.0 soak pending |

Non-selected strobe/ambient effects, remote cloud/VPN control and obsolete Gesto integration are not added. Calendar/one-off scheduling policy, arbitrary long/stateful/rolling IR compatibility and a complete Unicode OLED font are not silently promised. The requested broad IR and Spotify capabilities remain conditional on the actual appliance/API/device, as the requirements themselves state.

COMPLETION_TODO.md contains the detailed implementation checklist and individual acceptance tasks. DEVICE_ACCEPTANCE.md specifies their execution order. RELEASE_1_1_0.md records observed results separately from pending work.
