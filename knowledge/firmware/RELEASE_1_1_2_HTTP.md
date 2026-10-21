# 1.1.2-http — Spotify empty-request framing

## Observed failure
The live 1.1.1-responsive controller accepted pause as operation 1, then recorded failed with detail Spotify returned HTTP 411. A separate authenticated, pinned-HTTPS GET /v1/jobs returned that intact record. Metadata GET requests continued succeeding, so a later state refresh showed an empty global Spotify error even though the pause operation had failed. The boot ID remained 1118834133; the board had not restarted.

## Fix
Arduino core 3.3.1 HTTPClient adds Content-Length automatically only when the payload has nonzero length. The firmware now explicitly adds Content-Length: 0 for empty PUT and POST requests. This covers pause/resume, next/previous and volume. Nonempty JSON bodies retain the library's normal content length; GET polling remains unchanged. The responsive worker, persistent Spotify API connection, NTP lifetime fix, authentication, TLS validation and no-automatic-disruptive-retry behavior are preserved.

See the [installed-core implementation](https://github.com/espressif/arduino-esp32/blob/3.3.1/libraries/HTTPClient/src/HTTPClient.cpp) and Spotify's [Pause Playback API](https://developer.spotify.com/documentation/web-api/reference/pause-a-users-playback).

## Validation
ESP32 compilation/link passed with exit code 0 using Arduino core 3.3.1 and esp32:esp32:esp32:PartitionScheme=min_spiffs. Program: 1,398,443 / 1,966,080 bytes (71%); global RAM: 68,464 / 327,680 bytes (20%); compiler-reported remaining RAM: 259,216 bytes before runtime allocations. All 34 frozen compiler inputs match the build snapshots. Private identities and public release manifests are verified. Actual playback control remains a post-upload acceptance check.

## Device acceptance
1. Reopen the canonical sketch and upload 1.1.2-http with the same board/partition settings and existing identity files.
2. Confirm the runtime version and synchronized time, then play music in the existing Spotify player.
3. Send {"op":"spotify","action":"pause"}; retain job_id and boot_id. Confirm the music actually pauses.
4. Send {"op":"jobs"} or {"op":"job","id":NUMBER} and inspect the terminal status. Queue acceptance is not playback success. The general spotify.error field may be cleared by a later successful metadata poll.
5. Verify play, next/previous and volume separately with deliberately requested actions. Do not blindly retry disruptive actions after an uncertain result.

## Serial-output issue
The user's serial capture still has a damaged prefix, while pinned HTTPS returns valid state/operation JSON and integrity_failures stays zero. This establishes an independent reliable capture path; it does not establish the root cause of the USB/UART/monitor corruption. This HTTP framing patch does not claim to fix that separate issue.

No board was flashed, reset or formatted automatically. Existing configuration and both private identities are retained. On-device playback acceptance requires the user upload and actual pause/resume test.
