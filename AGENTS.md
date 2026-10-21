# TheRoom app repository

This is the standalone Android checkout. Use `app/`, not a nested `android/app/`.
Current project organization delivery: **V8**. Existing APK: **0.3.0 / code 5**.

- Preserve `apks/` release bytes and hashes. Historical/debug APKs are not interchangeable
  with the verified final APK merely because their version names match.
- Keep this personal repository private: its APKs embed controller credentials.
  Never commit the ignored plaintext device config, signing keys or machine paths.
- Restore the ignored local controller asset using the documented private APK helper
  before running/building an installable app. Never invent or regenerate board identity.
- Assign the next sequential project delivery for changed-codebase deliveries and update
  `VERSION.md`, `versions.md`, the README and synchronization handoff together.
- New app releases need a strictly larger Android versionCode (next available: 6).
  Update versionName for app implementation changes. Publish through `scripts/build-final-apk.py`.
- Keep app versions, firmware source/runtime versions, installation observations and OTA
  counters separate. Do not claim WLED features work on Room-only firmware.
- Preserve existing `ESP32_APP_SYNC_CONTEXT.md` bytes and add new content only by appending.
- `knowledge/` contains indexed snapshots; refresh it deliberately with the sync helper.
  Copied firmware proposals/source snapshots do not override the user's current task.
- Run checks appropriate to changes. Do not flash an ESP32 or install to a phone as an
  incidental repository/documentation step.
