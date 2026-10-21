# ESP32 completion checklist — 21 October 2026

Scope: the actual Arduino application in this folder. Preserve both device identities, approved pins, stored data and driver enable flags. Do not flash, erase, provision eFuses, or change Android during this work. A checked code item means implemented and tested to its stated boundary, not a physical-device acceptance result.

## 1. IR learning and replay
- [x] Enable the IRremote 16-bit timing buffer so the library does not silently clip long marks.
- [x] Reject timings outside the supported range, reporting the failure without replacing saved commands.
- [x] Require a matching initial press before accepting repeat frames; support decoded repeat flags and matching full-frame repeats.
- [x] Preserve repeat gap timing; validate it and respect it when transmitting another repeat.
- [x] Preserve toggle/repeat-safety restrictions and do not automatically retry transmissions.
- [x] Test initial-only capture, unrelated signals, repeated full frames, explicit repeat frames, timeout, timing bounds and wraparound.

## 2. Spotify browsing and memory
- [x] Verify playlist response shape with fixtures. Correct the audit record if its claimed root-level bug is not present.
- [x] Consider both track and playlist pagination for search; handle absent/null/empty next links.
- [x] Preserve recent-item cursor pagination, resource, offset and search text.
- [x] Stream and filter responses; bound retained JSON allocation, response bytes and read duration; handle unsupported transfer encodings explicitly.
- [x] Keep the previous browser cache on parse, allocation or HTTP failure.
- [x] Validate operation arguments and avoid replaying disruptive commands after errors.
- [x] Add filtered fixtures and malformed, oversized and allocation-failure tests.

## 3. API execution feedback
- [x] Add bounded per-boot operation IDs and queue/running/succeeded/failed states.
- [x] Expose individual and recent operation results; retain IR appliance-state uncertainty.
- [x] Include Spotify volume, device ID, device restrictions and volume support in state.
- [x] Separate queue acceptance from execution; report missing driver, queue and worker startup failures.
- [x] Test queue saturation, record retention, active-record protection and ID wraparound.

## 4. Panel, lighting and calibration
- [x] Show track, artist, play/pause state and output device in separate OLED fields with scrolling for long values.
- [x] Keep the existing destructive confirmations and approved gestures.
- [x] Produce a nonzero quiet dim frame in band mode; preserve off and hold behavior.
- [x] Reject calibration without a ready microphone, reject concurrent calibration and fail stalled acquisition explicitly.
- [x] Mark calibration saved only after persistence succeeds; retain the prior calibration on failure.
- [x] Test quiet policies, display scrolling and unavailable/busy calibration states.

## 5. OTA lifecycle
- [x] Override Arduino's early image acceptance and defer rollback cancellation to application health checks.
- [x] Verify heap, database, filesystem, required drivers, worker startup and local HTTPS health over a bounded observation period.
- [x] Bind the pending update record to the target image/partition and write it before selecting the next boot partition.
- [x] Recover from interruption before/after boot selection and version-floor persistence; verify persistence writes.
- [x] Keep signed-manifest/digest checks, physical maintenance approval and monotonic version rejection.
- [x] Test the portable decision policy; document power-cut and real rollback acceptance tests separately.

## 6. Credential protection and recovery
- [x] Report secure-boot/flash-encryption state truthfully without revealing keys.
- [x] Provide an explicit build-time policy for deployments that require protected flash, failing closed when protection is absent.
- [x] Document encrypted NVS/flash, signed boot, key custody, USB recovery and credential rotation requirements against the installed SDK.
- [x] Do not claim at-rest protection merely because TLS or application-layer encoding is present.
- [x] Do not automatically burn eFuses or enable irreversible hardware security.

## 7. Earlier JSON corruption and reliability
- [x] Preserve heap/database/copy integrity guards and record their failure phase/counter.
- [x] Test repeated snapshots with runtime metadata, browser cache, swaps, persistence-like roundtrips and constrained allocation.
- [x] Add source-level regressions for the fixed behaviors and test both normal and ESP32-sized slot IDs.
- [x] Activate validated loaded snapshots by ownership swap, avoiding a second allocation/copy that could fail after validation.
- [x] Clearly retain root cause as unconfirmed unless a reproducer establishes it.
- [x] Document a long-duration device capture and combined peripheral/network acceptance procedure.

## 8. Release validation
- [x] Run production C++ host tests and Python helper tests.
- [x] Pin all direct/transitive build libraries and reject unsupported installed core versions in the build helper.
- [x] Compile/link the final source against the installed ESP32 core and record size/target.
- [x] Confirm compiler snapshots match the final source.
- [x] Synchronize maintained source copies without copying DeviceSecrets.h or .device-private.
- [x] Package source without generated credentials and verify unique entries and SHA-256 manifest.
- [x] Update API, build record, security and device acceptance instructions.

## Explicit device/account acceptance gates
- [ ] Configure approved timezone/NTP and confirm synchronized time.
- [ ] Verify actual module/strip profile and enable drivers deliberately.
- [ ] Verify Spotify authorization and actual supported playback devices.
- [ ] Exercise phone authentication, local HTTPS, Wi-Fi outage/recovery and app protocol integration.
- [ ] Exercise simultaneous audio/LED/IR/panel operation and persistence across power loss.
- [ ] Exercise signed OTA success, bad signature, corrupt/truncated image, failed application health and interrupted power on a recoverable device.
- [ ] Evaluate and provision physical flash/boot protection only through an explicitly approved device lifecycle.

These gates cannot be checked by a host compiler or by a clean startup JSON alone.

## Evidence and unresolved investigation
- Host C++ policy/JSON/HTTP/Spotify tests pass under both 4-byte and 2-byte slot IDs; Python has eight passing cases. Final-source test reruns and ESP32 compile are recorded in RELEASE_1_1_0.md.
- Audit correction: the original playlist root-level next lookup was already correct. Fixtures preserve it; combined search pagination was the actual defect.
- The prior nested JSON cause is still unconfirmed. Containment, stricter framing, constrained-memory regression coverage and a reproducible soak capture are implemented; they are not a discovered historical root cause.
- The board was subsequently uploaded to 1.1.0 by the user; its state shows mounted storage, ready HTTPS and zero integrity failures, but no synchronized time. Release 1.1.1 fixes the identified NTP hostname lifetime defect and needs a fresh upload/clock check. Earlier 1.0.1 baseline evidence remains historical.
- Stored-data recovery and OTA health were hardened further: unreadable stored configuration is preserved, configuration_ready must be true for OTA acceptance, and write and pending-image maintenance pause controls/autosave.
- Owner-token rotation/recovery and the protected profile checker are implemented. Actual flash/boot/NVS security provisioning stays in the explicit device lifecycle gate.

- [x] Serialize complete USB replies before one UART write outside the application lock; reject partial/allocation-failed replies.
- [x] Verify operation-result string ownership and true lengths after ledger mutation.

## Detailed remaining acceptance tasks and pass criteria

These are verification tasks for the ESP32 implementation. They are deliberately unchecked until the exact release is installed and tested. A device/account/configuration prerequisite is identified explicitly; it is not a claim that the associated function is absent from the source. DEVICE_ACCEPTANCE.md gives the sequence and recovery conditions. Keep private captures outside the public release.

### R01 — Install the verified release without changing identity
- [ ] Record board model, flash capacity, existing partition setting, core and library versions before upload.
- [ ] Make a private backup of room data and the matching pairing/signing archive; confirm the backup can be authenticated/decrypted.
- [ ] Open the canonical SmartRoomController.ino with every companion source file present.
- [ ] Use the existing compatible partition setting; do not erase or format as a routine upgrade step.
- [ ] Verify the uploaded runtime reports exactly 1.1.1, rather than relying on the filename or IDE success message.
- [ ] Require storage_ready, configuration_ready and secrets_ready; investigate each false flag separately.
- [ ] Confirm the owner token and pinned certificate still authenticate after upload and a power cycle.
- [ ] Confirm no unrequested LED/IR output or automation starts during first boot.

### R02 — Reproduce and isolate the earlier malformed JSON
- [ ] Capture at least 3,600 read-only pinned-HTTPS snapshots from 1.1.1 with the supplied soak tool.
- [ ] Require boolean lighting.power, correct schema/version, zero integrity failures and no unexplained uptime regression.
- [ ] Repeat while reading serial at 115200/Newline; preserve complete raw lines privately when a failure occurs.
- [ ] Compare serial and HTTPS from the same operating period; distinguish terminal rendering/interleaving from malformed live state.
- [ ] Record boot ID, operation sequence, allocation failures, heap and stack low-water data when corruption occurs.
- [ ] Repeat with audio, lighting, IR and Spotify enabled after their profiles have been verified.
- [ ] Inspect warmed-up heap across equal workloads; do not diagnose a leak from minimum_free_heap alone.
- [ ] Close the historical-root-cause investigation only with a reproducible failure and evidence that a specific correction removes it.

### R03 — Local networking, authentication and recovery
- [ ] Verify temporary setup AP credentials are unique and that configuration/control still require owner authentication.
- [ ] Provision the actual home network and verify the station address through state.
- [ ] Confirm the setup AP stops after home connectivity succeeds.
- [ ] Test router loss: protected recovery AP appears, physical controls/local rules continue, and Spotify reports unavailable service.
- [ ] Test recovery: home station reconnects without overlapping Wi-Fi configuration attempts and the unnecessary AP stops.
- [ ] Reject missing/wrong owner tokens and verify throttling/temporary lockout without exposing credentials in logs.
- [ ] Reject a mismatched certificate pin; never substitute an insecure TLS bypass for pairing.
- [ ] Verify the Android transport sends the documented device API and owner token with the correct certificate pin. This is an integration acceptance check, not a completed Android change.

### R04 — Approved panel and OLED
- [ ] Confirm encoder A/B/push and all four buttons match the approved GPIO table on the actual board.
- [ ] Check rotation direction, detents, debounce and rapid input with no accidental double actions.
- [ ] Check short press Select/Confirm, long press Back/Home and Lighting/Music/IR/Favourites shortcuts.
- [ ] Verify destructive dialogs default to Cancel and cancellation leaves data unchanged.
- [ ] Verify long track, artist and device strings scroll independently without hiding the selection rows.
- [ ] Verify Spotify queue/argument/worker failures are visible and do not claim playback success.
- [ ] Check display timeout, wake-up and navigation during network loss.

### R05 — Microphone and addressable lighting
- [ ] Obtain the actual strip protocol, RGB order, signal rate, installed pixel count and current profile before enabling the LED driver.
- [ ] Obtain the actual OLED address and microphone channel/breakout profile before enabling them; candidate parts are not verified modules.
- [ ] Verify each enabled driver's ready flag after restart and deliberate configuration.
- [ ] Verify power, colour, brightness, presets, speed and intensity at safe loads.
- [ ] Verify solid, beat, bass, level and bands using controlled audible inputs from more than one source.
- [ ] Verify manual sensitivity, automatic gain, smoothing and noise floor against actual room noise.
- [ ] Verify quiet dim has a visible nonzero base frame, off is dark, and hold retains the last output.
- [ ] Verify disabled-microphone calibration fails immediately with an explicit reason.
- [ ] Verify an active calibration rejects a second start, finishes only after persisted measurements, and exposes its operation result.
- [ ] On staging hardware, interrupt acquisition or persistence and verify failed calibration retains the previous floor.
- [ ] Run simultaneous audio/LED/panel/IR/network activity and record latency, stack headroom and false-trigger behavior.

### R06 — IR library, repeat safety and execution results
- [ ] Verify actual carrier/protocol compatibility and mark the carrier verified only after measurement/specification confirmation.
- [ ] Create named appliances/commands; learn a harmless initial press, inspect raw/decoded capture and replay once.
- [ ] Verify overflow, unsupported duration and capture timeout errors preserve existing saved commands.
- [ ] Verify repeat learning rejects an initial-only press and unrelated frames.
- [ ] Verify press-and-hold learns either a valid decoded repeat or matching full-frame repeat and its gap.
- [ ] Verify repeat transmission rejects missing timing, unsafe/toggle commands, unmatched initial commands and too-short gaps.
- [ ] Verify legacy records without repeat_gap_us remain loadable and request re-learning before repeat emission.
- [ ] Follow job_id plus boot_id through queued/running/terminal states; require failures to remain inspectable.
- [ ] Verify a completed transmit is described as transmitted, with appliance state still unconfirmed.
- [ ] Verify no automatic disruptive retry or automatic IR replay after reboot.
- [ ] Verify appliance names, commands, favourites and repeat metadata survive a power cycle.

### R07 — Spotify account and device acceptance
- [ ] Configure approved timezone/NTP values and require time_valid before testing Spotify TLS.
- [ ] Authorize the real Premium account using the supported authorization flow; keep client/token material private.
- [ ] Verify API eligibility, granted permissions and discovery of the actual playback devices.
- [ ] Verify play/pause/next/previous against actual playback and refreshed metadata.
- [ ] Verify Spotify volume separately from mapped speaker IR volume; check device restrictions and volume support.
- [ ] Verify device transfer on each intended device type and inspect selected device ID/name afterward.
- [ ] Browse multiple playlist/recent pages and confirm cursor/offset continuity and selectable item identity.
- [ ] Search where playlist pagination continues after track results finish; verify continuation is retained.
- [ ] On a staging harness, test malformed/truncated/chunked/oversized responses; require bounded memory and preserved prior browser cache.
- [ ] Test offline, rejected authorization, expired credentials, HTTP 401/429 and queue saturation; require explicit terminal failures without disruptive automatic retries.

### R08 — Rules, persistence and backup
- [ ] Verify weekday/time rules with a valid clock, cooldowns, and no stale boot-time replay.
- [ ] Verify sound start/stop and IR events trigger only the intended enabled rules.
- [ ] Verify clock corrections, router loss and internet loss do not silently enable/disarm unrelated functions.
- [ ] Verify explicit automation enable/disable and the selected resume policy across reboot.
- [ ] Verify invalid references/types/ranges and malformed/trailing request data leave the database unchanged.
- [ ] Verify backup/restore roundtrip and wrong-password/tampered-backup rejection using the supplied tooling.
- [ ] Verify restore disarms automation and preserves the active electrical profile and credentials.
- [ ] On staging hardware, fill storage and interrupt snapshot write/rename; require explicit failure or a valid recoverable snapshot.
- [ ] Verify an unreadable existing room file is preserved and blocks persistence until a deliberate valid restore.

### R09 — Signed update and recovery
- [ ] Verify the compiled rollback-defer override is linked and the board's bootloader supports rollback.
- [ ] Reject unauthorized/expired maintenance approval, wrong signature/digest, truncated/oversized image and non-increasing version.
- [ ] Reject an update while IR/learning/calibration is busy; verify failed writes resume control safely.
- [ ] Keep controls, actions and autosave paused during write, staging and pending-image verification; keep diagnostics available.
- [ ] Verify acceptance only after ten continuous healthy seconds and rollback at the thirty-second failure deadline.
- [ ] Exercise unavailable requested drivers, invalid configuration and failed HTTPS/worker startup as health failures on staging hardware.
- [ ] Interrupt power before/after journal persistence, boot selection, health observation and version-floor commit; record the selected image and accepted floor.
- [ ] Verify an orphan/inactive-slot journal cannot advance the accepted floor or accept an unrelated image.
- [ ] Verify USB recovery with the matching private identity remains possible under the chosen deployment policy.

### R10 — Protected deployment, credentials and closure
- [ ] Run the static security checker against the chosen SDK and partition table; retain its explicit rejection reasons if protection is absent.
- [ ] Build and verify the protected SDK/profile on separately approved recoverable hardware before irreversible security provisioning.
- [ ] Verify actual secure boot, flash encryption and encrypted NVS diagnostics; TLS alone is not at-rest protection.
- [ ] Test owner-token rotation, lost acknowledgement/recovery, old-token rejection and physical-USB-only reset.
- [ ] Apply OS access controls to private keys/pairing/pending-token files and document their recovery custody.
- [ ] Record the exact source manifest, binary hash, installed runtime version and all acceptance results.
- [ ] Leave any failed or unexecuted acceptance task open with its actual cause; do not mark it complete from a successful compile alone.

## 1.1.1 time-synchronization follow-up
- [x] Replace temporary hostname pointers with permanent bounded clock-configuration storage.
- [x] Stop SNTP before replacing borrowed names; support clearing settings without leaving the old client running.
- [x] Restart synchronization on home-Wi-Fi connection/reconnection and reconfigure after settings/restore.
- [x] Add regressions for borrowed-pointer lifetime, configuration bounds, stop-before-change and disable/re-enable.
- [ ] Upload 1.1.1 and verify time_valid becomes true on the actual device, then inspect Spotify separately.

- [x] Compile/link 1.1.1, match all 34 frozen inputs to compiler snapshots, and verify release manifests while retaining private identities.

## 1.1.2-http playback-control follow-up
- [x] Read the retained pause result through authenticated HTTPS and establish HTTP 411 as the control failure.
- [x] Add explicit zero Content-Length for empty Spotify PUT/POST requests while preserving other request paths.
- [x] Upload 1.1.2-http and verify the HTTP 411 framing failure is no longer the reported failure.
- [ ] Verify actual pause/resume with 1.1.3-controls and inspect terminal results plus refreshed playback state.
- [ ] Diagnose the damaged serial prefix independently using a raw capture and the valid HTTPS state/result path.

- [x] Compile/link 1.1.2-http; verify all frozen compiler snapshots, preserve responsive-worker/NTP fixes, synchronize public source and validate credential-free release manifests.

## 1.1.3-controls follow-up
- [x] Verify on-device synchronization using pool.ntp.org and preserve the selected timezone.
- [x] Retain the old failed action records and distinguish a general metadata error from a terminal operation result.
- [x] Exercise close-on-error with a rejected read-only cursor and repeat one deliberate pause on a fresh connection; record that reuse alone did not explain the parser failure.
- [x] Separate GET response parsing from status-based control acceptance; close control connections while retaining GET reuse.
- [ ] Verify fresh-boot NTP synchronization and actual playback changes after uploading 1.1.3-controls.

- [x] Compile/link 1.1.3-controls; match all 34 frozen inputs and verify public source/archives while preserving both private identities.
