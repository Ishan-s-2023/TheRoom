# Room · Android app and local controller backend

## Combined APK verification

The combined build is `artifacts/RoomController-final.apk`. Rebuild and verify it
with `python scripts/build-final-apk.py`. This freezes the current shared Android
source, runs debug assembly, JVM tests and lint, and refuses publication if the
working Android source changes during the build. The APK embeds source hashes in
`assets/final-build-manifest.json`; the matching source is
`artifacts/RoomController-final-source.zip` and results are recorded in
`artifacts/RoomController-final-verification.json`.

Older review/Spotify APKs and duplicate debug APKs were removed during cleanup.
Build scripts publish only `RoomController-final.apk`. The earlier `app/` and
`app.zip` source exports remain historical snapshots. The preview launcher uses
the final APK on a fresh start.
See [verification scope](docs/FINAL_APK.md).

## Direct ESP32 Android update (0.2.0)

The Android app now imports the firmware's private `pairing.json`, verifies its device
certificate fingerprint, authenticates HTTPS requests directly to the ESP32, provisions
home Wi-Fi, and maps room controls to `/v1/state` and `/v1/command`. Normal room control
does not need a PC or backend. See [phone setup and validation limits](docs/DIRECT_ANDROID_SETUP.md).
The companion-server instructions below describe the older development architecture.

Native Android control for the ESP32 smart-room project. The app includes home/status,
lighting, presets, IR appliance management and guided learning, Spotify, routines,
microphone tuning, diagnostics, encrypted backup/restore, and signed-update submission.

**This is a development implementation, not a verified finished hardware controller.**
The app and backend can run today, including explicit simulation. Actual LED, IR,
microphone and OLED drivers, secure AP provisioning/recovery and on-device OTA still
require implementation and hardware qualification. See [coverage](docs/IMPLEMENTATION_STATUS.md)
for the exact boundary. Unavailable hardware is rejected rather than reported as working.

The newer [confirmed decisions](esp32-firmware/docs/CONFIRMED_UPDATES.md) supersede
conflicting parts of the original requirements. Addressable lighting replaces Gesto;
app control stays local over Wi-Fi. No remote relay or public hosting is deployed.
Both control/GPIO tables were explicitly approved for implementation in this chat;
see [approved profile](docs/APPROVED_PROFILE.md). Electrical approval remains separate.

## Open the Android app

For UI review on this development computer, run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-ui-preview.ps1
```

Open **http://localhost:8767**. This streams the actual native APK from the dedicated
RoomQA emulator into your browser, with mouse/touch/keyboard controls and sample data.
It does not start the room backend or control hardware. See [UI review notes](docs/UI_REVIEW.md)
for available scenarios and remaining UI gaps. Stop with `scripts/stop-ui-preview.ps1`.
The current development computer already has the RoomQA AVD and SDK installed; these
machine-specific emulator assets are not included in the source archive.

The reviewed UI APK is `artifacts/RoomController-final.apk`. Its exact frozen
Android source is in `artifacts/RoomController-final-source.zip`, so it can be
reproduced independently of concurrent edits in this workspace. Theme selection is
under **More → Settings → Appearance**. The requested React component reference is
available at **http://localhost:8767/components** while the viewer runs; setup and
native adaptations are documented in [React components](docs/REACT_COMPONENTS.md).

Install `artifacts/RoomController-final.apk` after the build finishes. It supports
Android 8.0/API 26 and newer. Choose **Explore the interface** to review the native UI
without credentials or a server. The banner clearly marks this as an in-memory preview.
Preview learning, playback and file export use explicitly simulated data. Real encrypted
backup, real IR learning and real Spotify playback require a connected implementation.

For development builds, open `android/` in Android Studio, or use:

```powershell
python -m pip install httpx
python scripts/download-tools.py
powershell -ExecutionPolicy Bypass -File scripts/setup-android.ps1
```

The isolated tooling is stored under ignored `.tools/`. The first setup downloads
a JDK, Gradle and the Android SDK.
A Gradle wrapper is generated. Release builds enforce
HTTPS and require your own Android signing configuration before distribution.

## Run the local backend

From this directory:

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -r backend/requirements.txt
python -m backend.cli setup
```

Setup prompts privately for a password of at least 12 characters and emits one unique
device key. Save that key privately. There are no default owner credentials.

For localhost development/simulation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Simulator -Port 8443
```

For LAN HTTPS, generate a private CA and a certificate covering your computer's
actual static LAN address or DNS name. The following address is only an example:

```powershell
python scripts/create-local-certs.py --host 192.168.1.50 --host room.local
powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Simulator -ListenAddress 0.0.0.0
```

Install `.certs/room-ca.cer` as a trusted CA on your phone, connect to the same Wi-Fi,
then sign in to `https://192.168.1.50:8443` with your owner credentials. Ensure your LAN
firewall permits the chosen port. The certificate must cover the exact address used.
Keep `.certs/*.key` and `data/` private and restrict their filesystem permissions.
The certificate helper never overwrites existing keys. Remove or relocate the initial
localhost-only development certificates before generating your LAN certificate.

Omit `-Simulator` to use a real provisioned controller. Until verified peripheral
drivers advertise capabilities, hardware actions return a clear unavailable error.
Run exactly **one** backend worker; device status and the scheduler have a single owner.

This implementation uses a **local companion host** for the Python service. The app
does not yet connect directly to an ESP32 HTTPS server. Migrating the API onto the
ESP32 and completing protected AP provisioning/recovery remain tracked requirements.
The companion-host architecture is a development choice, not a finalized hardware requirement.

## ESP32 integration

The supplied C++ core is retained. `backend_bridge.cpp` adds an optional local HTTPS
client, device telemetry, single-delivery command acknowledgements, NVS snapshot storage,
IR-library synchronization and device-owned cached schedule occurrences.

The bridge is off by default. To configure in an ESP-IDF shell:

1. Copy the public `.certs/ca.pem` to `esp32-firmware/main/backend_ca.pem`.
2. Open `idf.py menuconfig`, enable **Room development bridge**, and supply the local
   HTTPS origin, unique device key, Wi-Fi credentials and NTP server.
3. Build with `idf.py build`, or use `python -m platformio run -d esp32-firmware`.

On Windows paths containing spaces, use `scripts/build-firmware.ps1` instead. It
copies the build inputs to an isolated cache without spaces. The `-BridgeCheck`
option uses dummy credentials only to validate compilation; it is not device provisioning.
The default helper explicitly disables the bridge, including after a previous
compile check. Install PlatformIO first with `python -m pip install platformio==6.2.0`.

No firmware is automatically flashed. Credentials in SDK configuration/firmware
artifacts are sensitive; flash encryption and secure boot are not activated.
No peripheral is initialized just because its GPIO has been approved.

The bridge caches up to 60 KB of JSON subject to available NVS capacity; storage
failure preserves the previous snapshot and is reported in device logs. It caches
nine local calendar dates of schedule occurrences. It does not promise unlimited
offline recurrence or unlimited ESP32 storage. After reboot, automation starts disarmed
until fresh authenticated synchronization; missed events are skipped.

## Spotify

Create your own eligible Spotify developer application. Set these environment
variables before starting the backend; never place tokens or a client secret in the APK:

```powershell
$env:ROOM_SPOTIFY_CLIENT_ID = 'your-client-id'
$env:ROOM_SPOTIFY_REDIRECT_URI = 'https://your-local-host:8443/spotify/callback'
$env:ROOM_VAULT_KEY = 'your-private-persistent-random-secret-of-at-least-32-characters'
```

Register that **exact** redirect URI with Spotify. The browser must be able to reach
and trust the local HTTPS endpoint. Authorization uses PKCE and state validation;
refresh tokens are encrypted in the server store. Preserve the vault key in a private
server secret store across restarts; it is deliberately not stored with the database
or included in backups. Use the Android Music screen to
connect, then return and refresh. Real account/device access has not been tested here.

Implementation references: [Spotify PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow),
[redirect requirements](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri),
[2026 API changes](https://developer.spotify.com/documentation/web-api/references/changes/february-2026),
[July quota changes](https://developer.spotify.com/documentation/web-api/references/changes/july-2026).

## Verification and recovery

```powershell
python -m pytest backend/tests -q
python esp32-firmware/tests/run_host_tests.py
android\gradlew.bat -p android assembleDebug lintDebug
powershell -ExecutionPolicy Bypass -File scripts/build-firmware.ps1
```

The C++ tests need Zig as described in the firmware README. The backend tests use
temporary databases and controlled simulation. See [validation](docs/VALIDATION.md)
for results and limits. Source API documentation is available at `/docs` on your local server.

Local-console recovery commands:

```powershell
python -m backend.cli reset-password
python -m backend.cli rotate-device-key
```

Password reset revokes all phone sessions. Device-key rotation requires updating the
ESP32 credential. Backups exclude credentials/tokens and are AES-256-GCM encrypted
with a passphrase-derived key. Restore validates all references transactionally and
pauses imported routines; it never sends IR commands.
