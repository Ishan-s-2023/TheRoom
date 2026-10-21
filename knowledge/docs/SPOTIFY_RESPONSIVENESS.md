# Spotify responsiveness update

Android 0.2.2 follows a Spotify command by its operation ID and boot ID. It shows
progress immediately, polls small job records at 250ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“500 ms intervals, and stops
when Spotify succeeds or fails. Queue acknowledgement is never shown as completed
playback. A controller reboot or timeout produces an unknown-result message; the
app never resends the original action automatically.

The app reuses its pinned transport for the same controller, allowing Android to
reuse its TLS session/connection infrastructure. Normal direct-device state
refreshes use a 1.5-second interval instead of four seconds. Pairing is still built
into the APK; installation over the old app preserves its stored session.

The performance firmware is based on the current 1.1.1 source snapshot in
artifacts/spotify-latency-fix/source-snapshot.json. It preserves the existing clock,
security, hardware and operation-ledger behavior. Only Spotify.cpp and the displayed
version change. User commands drain before background playback polling. Successful
playback controls schedule an immediate playback refresh and update the confirmed
play/pause/volume fields. The Spotify API connection is retained where supported,
with certificate checks enabled. It is closed before token refresh or after an
error to avoid keeping an invalid/incomplete response stream.

Spotify 401/403/404/429 and connection errors get specific user-facing explanations
in place of the generic action-not-retried suffix. API acceptance still does not
prove physical speaker output. Internet and Spotify service latency remain part
of the control path.

Phone-to-ESP32 ping before this update averaged 15.799 ms with no packet loss.
That measures local connectivity, not Spotify execution time. Use the live
verification report in artifacts/spotify-latency-fix for measured command timings.

The canonical Complete Source firmware now contains the exact compiled changes.
The isolated performance sketch, original two changed files, and review patch are
also retained in the artifact folder. Uploading an older source replaces the fixes.

Live testing also exposed the ESP32 secure client's default 120-second TLS
handshake timeout. Both Spotify authorization and API clients now bound that
handshake to five seconds. HTTP connect/read deadlines remain explicit. A weak
connection can fail, but one TLS attempt no longer holds the music queue for two
minutes. No control action is automatically retried.

Queued requests older than eight seconds expire before execution, with a failed
operation record, rather than producing a delayed control after connectivity
recovers. Compile-time checks cover the deadline boundary and millis rollover.
A request already running is observed to completion instead of being resubmitted.

## Final verification boundary

The final firmware revision is installed on the physical ESP32 and flash data
verification passed. The final APK passed 18 tests and lint with no errors.
Installation on the phone did not finish because the USB phone disconnected.
The latest live read-only command returned its clock-prerequisite failure in
about 280 ms; this is not a successful Spotify playback latency measurement.
Google NTP and a direct-IP Cloudflare NTP test did not synchronize the board on
the current connection. The previous NTP setting was restored. RSSI was -88 dBm.
A working network/clock and installation of the APK are required for final
phone-to-playback acceptance. No physical audio playback was changed in testing.
