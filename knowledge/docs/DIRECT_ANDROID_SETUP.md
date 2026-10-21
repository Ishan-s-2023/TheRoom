# Direct Android / ESP32 control

Build version 0.2.0 connects to the Arduino SmartRoom firmware's HTTPS API directly.
Normal room control needs the ESP32 and phone on Wi-Fi; no companion computer or backend is required.

1. Install `artifacts/RoomController-final.apk` (Android 8 or newer).
2. Transfer your ESP32's private `.device-private/pairing.json` to your phone securely.
   Do not use another controller's file. Never share this file or include it in source archives.
3. Join the ESP32 setup Wi-Fi using the SSID and password supplied with that file.
   Stay connected if Android warns that this network has no internet.
4. Open Room and import pairing.json. In Settings, select **Connect through setup Wi-Fi**.
5. Enter the home 2.4 GHz Wi-Fi name/password and select **Save home Wi-Fi to ESP32**.
6. The setup AP closes when the ESP32 joins home Wi-Fi. Switch the phone to home Wi-Fi.
7. Enter `https://` followed by the ESP32's IP from its display or your router's device list,
   then select **Reconnect on home Wi-Fi**. A hostname also works if your network resolves it;
   this firmware does not advertise mDNS, so use the actual IP when it does not resolve.
   The verified address is saved for subsequent launches. If DHCP changes it, reconnect from Settings.

Pairing credentials are encrypted with Android Keystore. Home Wi-Fi credentials are sent only
to the ESP32 and are not persisted by the app. Each request includes the device bearer token.
The exact leaf certificate SHA-256 fingerprint and validity period are checked before sending
HTTP credentials. This pinned identity also works at DHCP IPs absent from the certificate SAN.
Redirects are disabled. Device requests explicitly use the connected Wi-Fi network, including
the setup AP without internet; see [Android network routing](https://android-developers.googleblog.com/2016/07/connecting-your-app-to-wi-fi-device.html).

Lighting, presets, microphone settings/calibration, IR library/capture/transmission, routines,
automation arming, Wi-Fi provisioning and Spotify commands map to `/v1/command`; status polls
`/v1/state`. Microphone percentages are converted to firmware fractional values. All routines
use the controller POSIX timezone. Configure timezone and NTP in Settings before scheduling.

Spotify uses **Connect Spotify** on the Music screen for browser authorization with PKCE.
The configured Spotify developer app must allow the phone callback
`in.roomcontroller.app://spotify/callback`. It requires internet, a valid device clock,
and eligible API playback access. Spotify and IR acceptance means queued work;
read refreshed device status for execution errors. Physical IR transmission does not confirm
appliance state. The app never automatically retries commands.

Peripheral availability depends on flashed firmware and verified hardware configuration.
The interface preview remains simulated. Legacy companion-server backup/update screens are
not exposed for direct-device sessions; use firmware maintenance tools for those operations.

Validation: Android debug assembly, JVM tests (pairing validation, certificate pin mismatch,
state conversion and bounded input), lint, and an emulator UI check. Physical phone/ESP32
pairing and hardware output require an on-device test and are not claimed by these checks.
