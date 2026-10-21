# 1.1.3-controls — control-response handling

## Live findings
The uploaded 1.1.2-http controller was on home Wi-Fi but had no synchronized time. Reapplying its existing time.google.com configuration did not synchronize it. Changing only ntp_server to pool.ntp.org produced time_valid=true on the actual ESP32; that server is now retained in its saved settings. PC reachability of Google did not establish ESP32 reachability.

With time valid, pause operation 3 reached the API but failed with Spotify trailing JSON data. An intentionally invalid read-only recent-history cursor produced HTTP 400 and closed the connection; a subsequent fresh-connection pause operation 9 still hit the same parser error. Therefore connection reuse alone is not an established root cause. The old firmware parses every non-204 successful response as JSON, including control responses that are not metadata resources. Refreshed state still reported playing=true; physical pause has not been confirmed.

## Changes
- Parse JSON only for GET resources that return metadata or browser data. A successful control response is evaluated by HTTP status rather than requiring a metadata JSON object.
- Use a fresh, non-reusable connection for each control request and close it afterward, preventing unused response data from leaking into a later poll. Metadata GET polling retains connection reuse.
- Retain explicit Content-Length: 0 for empty PUT/POST requests, the responsive worker/queue expiry behavior, NTP hostname ownership, TLS verification and operation-result reporting.
- HTTP 2xx means API acceptance, not confirmation of physical playback. Actual device state and the user's observation remain the acceptance criteria. No disruptive request is automatically retried.

## Build validation
ESP32 compilation/link passed, exit code 0: core 3.3.1, esp32:esp32:esp32:PartitionScheme=min_spiffs. Program: 1,398,547 / 1,966,080 bytes (71%); global RAM: 68,464 bytes (20%); compiler-reported remaining RAM: 259,216 bytes before runtime allocations. All 34 frozen compiler inputs match their build snapshots. Both public source copies and ZIP manifests are verified with private identities unchanged. Live control acceptance on this release still requires upload; the old-firmware test results above are not validation of the new binary.

## After upload
1. Reopen the canonical sketch and upload with the existing identity and compatible partition settings.
2. Require runtime.version=1.1.3-controls and time_valid=true. The saved server should be pool.ntp.org.
3. With music playing, send {"op":"spotify","action":"pause"}. Retain the new job_id and boot_id; old IDs belong to the preceding boot.
4. After five seconds, send {"op":"jobs"}; verify a terminal result and whether the music actually stops. Use state to check the refreshed spotify.playing value afterward.
5. Test resume only when deliberately requested. If the API accepts a command but playback does not change, retain both the job result and fresh playback metadata rather than treating the acknowledgement as proof.

No board was flashed automatically. Account credentials, timezone, driver flags and both source-folder identities are preserved. Hardware/phone integration and the historical serial-prefix investigation remain separate acceptance gates.
