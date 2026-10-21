# Device acceptance procedure — release 1.1.3-controls

This is a verification procedure, not a record of tests already performed. Keep COMPLETION_TODO.md checked states tied to actual evidence. Run one phase at a time and record results. Do not enable unknown modules, change the partition layout, regenerate identity files, or erase storage as part of this upgrade.

## A. Upload and non-destructive startup
1. Retain the existing DeviceSecrets.h, .device-private directory and current room-data backup privately. Use the canonical sketch folder, all companion files and installed dependencies from README.md.
2. Upload using the current board/partition settings that match the existing filesystem. The verified build target uses Minimal SPIFFS with a 1,966,080-byte application slot; this is not authorization to change an existing partition layout.
3. Open Serial Monitor at 115200 with Newline and send {"op":"state"}. Require runtime.version=1.1.3-controls, storage_ready=true, configuration_ready=true, secrets_ready=true, integrity_failures=0 and the expected home connection. Drivers may correctly remain false when disabled in the stored profile.
4. If configuration_ready is false, do not format: the new loader has preserved an unreadable/unsupported room file. Restore a known valid backup using the explicit restore workflow. State and diagnostics remain available.
5. Confirm recovery credentials and the device certificate still match the private pairing archive. Use the pinned client; do not disable certificate verification.

## B. Read-only stability and network recovery
1. Run python tools/device_soak.py --host DEVICE_IP --samples 3600 --interval 1 --expected-version 1.1.3-controls --output PRIVATE_NEW_LOG.jsonl. The output file must not already exist. This sends only GET /v1/state and records selected runtime diagnostics, body size and SHA-256, not credentials or full room data.
2. Require boolean lighting.power, expected schema, no rejected snapshots, no unexpected restart and zero integrity failures. Inspect warmed-up heap trend; a minimum-heap low-water value alone does not establish a leak.
3. Perform controlled router-loss/recovery testing separately. Confirm protected AP appears, local physical functions continue, app authentication remains required and AP stops after home connection returns. Do not interpret station=true as proof of internet reachability.
4. If JSON corruption returns, retain the raw capture privately, failure phase/counter, boot ID, exact operations, SDK/library versions and serial settings. Compare an independent pinned-HTTPS capture with serial output to distinguish transport/display artifacts from an invalid live database. Do not claim a historical root cause without a reproducer.

## C. Operation results and unavailable capabilities
1. With microphone disabled, send {"op":"calibrate"}. Require ok=false and an unavailable-driver reason; it must not claim calibration started or completed.
2. With IR disabled, transmit a saved ID only if a harmless test command exists; require an unavailable-driver failure and no emission.
3. After a valid queued command, retain job_id and boot_id. Send {"op":"job","id":NUMBER} or GET /v1/jobs. Observe queued/running and a terminal result. A reboot changes boot_id; expired IDs are not reusable operation proof.
4. For IR, succeeded means a frame was transmitted. Verify appliance response separately. For Spotify, succeeded means API acceptance; verify playback state/device metadata separately. Failed disruptive operations must not automatically replay.

## D. Verified panel and microphone/lighting profile
1. Supply the verified hardware profile through the existing confirmed hardware command, then reset to initialize drivers. Confirm each enabled driver becomes ready. Do not substitute candidate module specifications for actual models.
2. Check each approved GPIO/control function, encoder direction/detents, debounce, short/long behavior and four shortcuts. Confirm important deletes and automation disabling default to Cancel.
3. On Music, test long track/artist/device values and separate Playing/Paused, track, artist and Out lines. Rotate through all menu entries despite fewer visible rows.
4. With a verified microphone, test calibration success, concurrent-calibration rejection, read interruption and a deliberately unavailable-storage failure on staging hardware. Only a persisted calibration may be reported as saved; failure must retain the previous floor.
5. Test solid, beat, bass, level and bands. During silence check dim produces the configured standby base frame, off is dark and hold retains the last physical frame. Test brightness/power transitions and the configured current estimate at safe loads.
6. Exercise simultaneous audio, LED rendering, IR and panel input. Record sample stability, frame timing, stack high-water values, heap, false triggers and driver failures under the actual installation.

## E. IR capture, repeat and persistence
1. Use a harmless command and known appliance/carrier. Learn an initial press, inspect raw/decoded metadata, save and transmit once. Confirm clear overflow, unsupported-timing and timeout errors.
2. Start repeat learning for that same saved command. A single initial press must not finish repeat learning. Press then hold the same button: require a matching initial press followed by a verified decoded repeat or matching full-frame repeat. Unrelated signals and a new isolated press must not be silently saved as a repeat.
3. Inspect repeat_gap_us. Repeat emission requires repeat_safe=true, toggle=false, learned timing and a matching recently transmitted initial frame. Test the min-gap guard and the 400 ms recent-initial window. Legacy records without gaps remain stored but cannot emit repeats until re-learned.
4. Test saved commands, names, appliance references and favourites after reboot. A boot alone must not transmit old IR commands. Stateful/rolling protocols need appliance-specific verification and may remain unsupported.

## F. Spotify and time
1. Configure an approved POSIX timezone and NTP server using the complete settings object; confirm time_valid=true before Spotify TLS or schedules. Do not infer a timezone from the PC's location.
2. Authorize the actual Premium account using the supported helper. Keep token data private. Verify permission/API eligibility and actual playback devices.
3. Exercise play, pause, next, previous, Spotify volume, device transfer and play_uri. Verify reported device_id, device name, volume, restrictions and volume support. Speaker IR volume remains a separate mapping.
4. Browse >20 playlists and recent items, verify cursors, and test search where tracks have no next page but playlists do. Confirm null/deleted items do not become invalid selectable entries.
5. Interrupt internet access, reject token refresh, simulate 401/429, malformed/truncated/chunked/oversized responses and queue saturation on a test endpoint/staging harness. Confirm bounded memory, clear terminal job failure, unchanged prior browser cache and no automatic disruptive retry.

## G. Persistence, schedules and backup
1. Create named commands, presets and weekday rules; back up using pinned HTTPS and optional authenticated client encryption. Test wrong password and tamper rejection.
2. Confirm rules stay disarmed on restore and electrical configuration/credentials are not replaced by imported room data. Invalid references and malformed requests must leave the active database unchanged.
3. Test matching weekday/time, sound start/stop and IR event triggers, cooldowns, invalid clock, forward/backward correction and deliberate automation resumption. No stale schedule should blindly replay IR after boot.
4. On staging hardware, interrupt snapshot writes/renames and fill the data filesystem. Confirm fallback snapshot/recovery feedback and that a failed write is not reported committed.

## H. Signed update, rollback and protected deployment
1. Keep a recoverable USB/staging device and private signing archive. First install 1.1.0 using the normal USB workflow. Confirm the linked image uses the application rollback-defer override.
2. Test no approval, expired approval, wrong owner, malformed manifest, bad signature, wrong digest, truncated body, oversized image and non-increasing version. All must reject without selecting an unverified image.
3. Test busy IR/learning/calibration. During an accepted flash write, controls/output changes and autosave are paused. Failed writes release maintenance; a staged image remains paused until restart. A failed restart-task allocation must instruct manual reset.
4. Interrupt power before journal persistence, after journal persistence, after boot selection, during health observation and after version-floor commit. Inactive/orphan journals must not advance the floor. A conservative higher floor after rollback is acceptable; admitting an older signed version is not.
5. A pending image must keep actuator/configuration changes paused and remain pending until ten continuous healthy seconds pass, with a thirty-second deadline. Test bad heap/schema/configuration, unavailable requested driver, failed HTTPS route setup and worker startup. Require rollback; do not treat filesystem mounting alone as complete health.
6. Execute SECURITY_PROFILE.md on separately approved staging hardware: rebuilt encryption-enabled SDK, reviewed encrypted NVS-key partition, signed boot, flash/NVS protection, key custody and the chosen recovery interface. Confirm actual runtime security fields; static profile checks alone do not provision a device.
7. Test owner-token rotation, lost acknowledgement/retry, rejection of the old token and physical-USB-only reset. HTTPS must reject reset_owner_token even for an authenticated owner. Protect the private pending-token file and pairing archive with operating-system access controls.

## Completion rule
Record compiler, host, on-device and account results separately. A source checklist, successful upload or valid state response does not establish every peripheral, service, recovery path or historical corruption cause. Keep incomplete device gates unchecked.
