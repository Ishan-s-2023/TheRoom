# TheRoom — SmartRoom Controller Android Application

[![Android Build](https://img.shields.io/badge/Android-API%2034-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.02.00-purple.svg)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/App%20Version-0.5.6%20(code%2027)-orange.svg)](CHANGELOG.md)
[![Delivery](https://img.shields.io/badge/Delivery%20Version-V32-blueviolet.svg)](DELIVERY_VERSION.md)
[![License](https://img.shields.io/badge/License-Proprietary%20%2F%20Private-lightgrey.svg)](#security--privacy)

**TheRoom** is a local-first, privacy-respecting Android application designed to orchestrate smart room hardware. It delivers unified control across a custom **ESP32 Smart Room Controller** and addressable LED fixtures running **WLED**, coupled with a client-side **Spotify Web API** remote featuring RFC 7636 PKCE authentication.

Built completely with modern **Jetpack Compose**, **Material 3**, and **Kotlin Coroutines**, TheRoom operates without cloud relays, subscription services, or embedded WebViews. Every control interface—from 180+ WLED light effect engines to the vinyl turntable Spotify player—is rendered natively on-device.

---

## Table of Contents

1. [Features Overview](#features-overview)
2. [Architecture & Project Layout](#architecture--project-layout)
3. [Prerequisites & System Requirements](#prerequisites--system-requirements)
4. [Step-by-Step Installation & Build Guide](#step-by-step-installation--build-guide)
5. [Step-by-Step Spotify PKCE Setup Guide](#step-by-step-spotify-pkce-setup-guide)
6. [Step-by-Step Hardware Pairing (ESP32 & WLED)](#step-by-step-hardware-pairing-esp32--wled)
7. [In-App Feature Walkthrough](#in-app-feature-walkthrough)
   - [Lighting & Ambient Effects](#lighting--ambient-effects)
   - [WLED Controller (Full Native Parity)](#wled-controller-full-native-parity)
   - [Spotify Vinyl Player & Magic Shuffle](#spotify-vinyl-player--magic-shuffle)
   - [IR Blaster & Appliance Automation](#ir-blaster--appliance-automation)
   - [AudioReactive Microphone DSP Tuning](#audioreactive-microphone-dsp-tuning)
8. [Troubleshooting & FAQ](#troubleshooting--faq)
9. [Security & Privacy Model](#security--privacy-model)
10. [Release History & Versioning](#release-history--versioning)

---

## Features Overview

### 1. Dual Hardware Control
- **ESP32 Room Controller**: Connects directly over local HTTPS with TLS certificate fingerprint pinning. Handles addressable lighting, physical relay switches, guided infrared (IR) learning/blasting for appliances (TV, AC, projector, soundbar), environmental sensors, and hardware routines.
- **WLED Ecosystem (100% Native Compose UI)**: Full-featured native client for WLED v0.14+ controllers. No embedded WebViews, iframes, or browser redirects. Features complete segment management, 180+ effect engines, 70+ color palettes, live speed/intensity sliders, preset playlists, 2D matrix controls, live UDP visualizer, and in-app network/LED settings.

### 2. Spotify Client-Side Web API Remote
- **Zero-Secret PKCE Authentication**: Employs RFC 7636 Proof Key for Code Exchange (S256). **No developer client secrets or pre-baked accounts** are stored in the app. Users configure their own Spotify Developer Client ID under Settings.
- **Dynamic OAuth Validation**: Prevents invalid auth requests by cleanly validating configuration before launching the browser.
- **Local Token Encryption**: Tokens are stored locally in Android Keystore-backed Jetpack DataStore preferences (`RoomDataStore`).
- **Interactive Vinyl Player UI**: Features an animated spinning record, realistic vinyl groove reflections, dynamic album cover art palette extraction, needle drop animation, track progress scrubbing, and volume control.
- **3-Way Magic Shuffle**: Cycles through **Off → Standard Shuffle → Magic Shuffle** (dynamically fetches tailored recommendation seeds based on active tracks).
- **Liked Songs Library Integration**: Real-time heart toggle syncs track save states directly to your personal Spotify library (`PUT/DELETE /v1/me/tracks`).
- **Active Playback Routing**: Instantly switch output playback between your phone, PC, Spotify Connect speakers, and room audio hardware.

### 3. Local AudioReactive DSP Equalizer
- Configure the ESP32’s on-device microphone processing pipeline in real time.
- Adjust frequency sensitivity bands (Bass, Mid, Treble), automatic gain control (AGC), noise squelch thresholds, and responsiveness curves for real-time music-to-light visualization.

---

## Architecture & Project Layout

```text
TheRoom/
├── app/
│   ├── build.gradle.kts                # Application dependencies, SDK levels, versioning
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml     # App permissions, activities, intent filters
│   │   │   ├── java/in/roomcontroller/app/
│   │   │   │   ├── MainActivity.kt               # Main entry point & bottom navigation
│   │   │   │   ├── ControlScreens.kt             # Room lighting, status & quick controls
│   │   │   │   ├── DeviceScreens.kt              # Hardware pairing, diagnostics & telemetry
│   │   │   │   ├── ServiceScreens.kt             # Settings, connections & Developer Client ID setup
│   │   │   │   ├── SpotifyPkce.kt                # RFC 7636 OAuth PKCE protocol implementation
│   │   │   │   ├── SpotifyConnectActivity.kt     # Deep-link callback handler & token exchange
│   │   │   │   ├── SpotifyApiClient.kt           # Spotify Web API client (playback, shuffle, library)
│   │   │   │   ├── RoomDataStore.kt              # Encrypted DataStore for credentials & settings
│   │   │   │   ├── MusicPlayerWidget.kt          # Vinyl player UI widget & interactive controls
│   │   │   │   ├── RoomMediaPlaybackService.kt   # Background media notification handler
│   │   │   │   ├── WledScreens.kt                # Native WLED segments, effects, palettes & presets
│   │   │   │   ├── WledSettingsScreens.kt        # In-app WLED WiFi, LED count & preferences
│   │   │   │   ├── WledModel.kt                  # State data models for WLED JSON API v1
│   │   │   │   ├── WledProtocol.kt               # HTTP & UDP transport engine for WLED
│   │   │   │   ├── WledConfig.kt                 # WLED preset management & configuration
│   │   │   │   ├── WledCatalogDefaults.kt        # Pre-cached catalogs for offline rendering
│   │   │   │   ├── SoundTuning.kt                # AudioReactive microphone DSP tuning UI
│   │   │   │   ├── AutomationScreens.kt          # Macro routines & timer scheduler
│   │   │   │   ├── LightingEditor.kt             # Granular color wheel & HSV palette editor
│   │   │   │   ├── DesignControls.kt             # Bespoke Compose components & luxury pickers
│   │   │   │   ├── Appearance.kt                 # Material 3 themes & color palette definitions
│   │   │   │   ├── BoundedInput.kt               # Safe numeric validation and bounds clamping
│   │   │   │   ├── DeviceTransport.kt            # Encrypted HTTPS/LAN transport to ESP32
│   │   │   │   └── RoomModel.kt                  # Domain models for room devices and states
│   │   │   └── res/                              # Drawables, layouts, XML configs, icons
│   │   └── test/java/in/roomcontroller/app/      # JVM unit tests (PKCE, WLED protocol, bounds)
├── apks/                               # Tracked binary releases, hashes & manifest
├── artifacts/                          # Release history JSON, parity matrices & inventories
├── knowledge/                          # Full architecture docs, connection guides, protocol specs
├── scripts/                            # Python build automation, verification & release checks
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Project module settings
├── gradle.properties                   # JVM memory flags and AndroidX properties
├── CHANGELOG.md                        # Android release history & internal version ledger
└── DELIVERY_VERSION.md                 # Global project delivery milestones (V1 – V32)
```

---

## Prerequisites & System Requirements

Before building or running the project, ensure your environment meets the following requirements:

| Tool | Recommended Version | Minimum Requirement | Notes |
| :--- | :--- | :--- | :--- |
| **Operating System** | Windows 10/11, macOS, Linux | Any modern 64-bit OS | Developed and tested on Windows 11 |
| **Java JDK** | **JDK 17** (Temurin / Zulu) | JDK 17 | Required for Gradle 8.2+ and AGP 8.2 |
| **Android SDK** | API 34 (Android 14) | API 26 (Android 8.0) | `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34` |
| **Android Studio** | Hedgehog / Iguana / Jellyfish | Electric Eel | Recommended for visual Compose preview |
| **Python** | Python 3.10+ | Python 3.8 | Required for running release verification scripts |
| **Physical Device / Emulator** | Android 10+ (API 29+) | Android 8.0 (API 26) | Must be connected to the same Wi-Fi LAN as ESP32 |

---

## Step-by-Step Installation & Build Guide

### Step 1: Clone the Repository
Open a terminal (PowerShell, Command Prompt, or bash) and clone the repository:
```bash
git clone https://github.com/Ishan-s-2023/TheRoom.git
cd TheRoom
```

### Step 2: Configure the Android SDK Location
Create a `local.properties` file in the root directory to define your Android SDK path (this file is ignored by git):

**On Windows:**
```properties
sdk.dir=C:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
```
*(Replace `<YourUsername>` with your Windows username).*

**On macOS / Linux:**
```properties
sdk.dir=/Users/<YourUsername>/Library/Android/sdk
# or /home/<YourUsername>/Android/Sdk
```
Alternatively, set the system environment variable `ANDROID_HOME` pointing to your SDK directory.

### Step 3: Run Automated Unit Tests
Verify that the codebase, bounds checking, WLED protocol serialization, and Spotify PKCE blank client ID guards pass all unit tests:

**Using Gradle Wrapper:**
```powershell
# Windows
.\gradlew.bat testDebugUnitTest

# macOS / Linux
./gradlew testDebugUnitTest
```

**Using the Python Release Verification Suite:**
```powershell
python scripts/test_app_release_version.py
```
*(Expected output: `Ran 9 tests in 0.2s ... OK`).*

### Step 4: Build the Debug APK
Assemble the application binary:
```powershell
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
The compiled APK will be output to:
`app/build/outputs/apk/debug/app-debug.apk`

### Step 5: Install onto an Android Device or Emulator
1. Enable **Developer Options** and **USB Debugging** on your Android smartphone.
2. Connect your phone via USB cable (or start an Android Virtual Device in Android Studio).
3. Install the generated APK:
   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Launch **Room** from your app launcher.

---

## Step-by-Step Spotify PKCE Setup Guide

To protect personal Spotify accounts, TheRoom does **not** distribute a hardcoded developer Client ID. You register your own free Spotify Developer Application in under 2 minutes.

### Step 1: Create a Spotify Developer App
1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) and log in with your Spotify account.
2. Click **Create App** in the upper right.
3. Fill in the required details:
   - **App Name**: `TheRoom Controller` (or any name you choose)
   - **App Description**: `Smart room music remote`
   - **Redirect URI**: **`in.roomcontroller.app://spotify/callback`** *(CRITICAL: Must match exactly!)*
   - **APIs Used**: Select **Web API**.
4. Check the Terms of Service box and click **Save**.

### Step 2: Authorize Your Spotify User (Development Mode)
Spotify Developer apps start in Development Mode, which requires whitelisting your account:
1. In your app dashboard, click **Settings** (top right) and select the **User Management** tab.
2. Under **Users**, enter the **Name** and the **Email Address** associated with your personal Spotify account.
3. Click **Add User**.

### Step 3: Copy Your Client ID
1. In your app dashboard under **Basic Information**, locate the **Client ID** (a 32-character hexadecimal string).
2. Click **Copy** (Note: You do **NOT** need the Client Secret).

### Step 4: Configure TheRoom App on Your Phone
1. Open **TheRoom** on your Android device.
2. Tap the bottom navigation bar and select **More** → **Settings** → **Connections**.
3. Under **Spotify Settings**, tap the **Spotify Client ID** input field.
4. Paste your 32-character Client ID.
5. Tap **Save Changes**.

### Step 5: Connect and Authorize
1. Navigate to the **Music** tab.
2. Tap **Connect Spotify**.
3. The app generates a cryptographically secure random verifier and SHA-256 challenge (`S256`), then opens your system browser to Spotify’s authorization page.
4. Tap **Agree** to authorize access.
5. The browser automatically redirects back to `in.roomcontroller.app://spotify/callback`.
6. TheRoom intercepts the callback, completes the token exchange, saves the tokens securely inside `RoomDataStore`, and displays the active vinyl playback widget.

---

## Step-by-Step Hardware Pairing (ESP32 & WLED)

### Prerequisites for Hardware Connection
- Ensure your Android phone and your ESP32 / WLED controllers are connected to the **same Wi-Fi network** (2.4 GHz subnet).
- Isolation settings (AP Isolation / Guest Network) on your router must be **disabled** so devices can communicate locally over TCP/UDP.

### Pairing with the ESP32 Room Controller
1. Open **TheRoom** and navigate to the **Devices** tab.
2. Tap **Pair Controller**.
3. Choose **Scan Network** to discover via mDNS, or select **Manual IP** and enter the ESP32’s static IP address (e.g. `192.168.1.102`).
4. The app fetches the controller’s TLS public certificate fingerprint over HTTPS.
5. Confirm the fingerprint to securely pin the certificate into Android Keystore. All subsequent communications use encrypted HTTPS directly to the ESP32.

### Connecting to WLED Lighting Controllers
1. Navigate to the **WLED** tab.
2. Tap **Add / Connect Controller**.
3. Enter your WLED controller's IP address (e.g. `192.168.1.103`) or tap **Auto-Discover**.
4. Tap **Connect**. The app queries `/json/info` and `/json/state`, instantly populating:
   - Strip length, active segments, and current power state.
   - 180+ built-in effect engines.
   - 70+ color palettes.
   - Segment boundaries, grouping, spacing, and 2D matrix setups.

---

## In-App Feature Walkthrough

### Lighting & Ambient Effects
- **Master Brightness & Power**: High-precision slider with haptic feedback.
- **HSV Color Wheel & Warm/Cool White Balance**: Full 360-degree hue picker with saturation and dedicated Kelvin white-temperature sliders.
- **Lighting Scenes**: One-tap quick presets (e.g. *Focus Work*, *Cinematic Dim*, *Nightlight*, *Deep Relaxation*).

### WLED Controller (Full Native Parity)
- **Segment Manager**: Create, delete, reverse, mirror, and adjust individual LED ranges with distinct colors and effects per segment.
- **Effect Engine**: Browse effects alphabetically or by category (Dynamic, Noise, Matrix, Audio-Reactive). Tune Effect Speed and Effect Intensity in real time.
- **Palette Selector**: Instant color transformation across classic, gradients, and custom palettes.
- **Preset Library**: Trigger saved WLED hardware presets and configure cycling playlists.
- **Live UDP Visualizer**: Stream high-framerate real-time animations directly from the phone.
- **In-App WLED Settings**: Edit controller Wi-Fi settings, LED count, output pins, max current (mA) limiters, and UI theme options (Obsidian, Midnight, Solar Amber, Cyberpunk Neon).

### Spotify Vinyl Player & Magic Shuffle
- **Vinyl Player Widget**: Realistic rotating record artwork, analog needle drop animation, interactive scrub bar, and current device badge.
- **3-Way Magic Shuffle**:
  1. *Off*: Sequential queue playback.
  2. *Shuffle*: Standard Spotify library shuffle.
  3. *Magic Shuffle*: Queries `/v1/recommendations` based on the currently playing track to automatically inject context-aware song recommendations.
- **Heart Toggle**: Save tracks to your Spotify Liked Songs library with one tap.
- **Device Switcher**: Route audio playback across any active Spotify Connect receiver.

### IR Blaster & Appliance Automation
- **Guided IR Learning**: Place existing remotes in front of the ESP32 receiver. The app records the raw pulse timing and protocol (NEC, Sony, RC5, RAW).
- **Virtual Remotes**: Custom interactive remote controls for TVs, air conditioners, fans, and audio systems.
- **Macro Routines**: Chain multiple actions (e.g., *Turn on Projector → Dim WLED to 15% → Set AC to 22°C → Start Spotify Chill Playlist*).

### AudioReactive Microphone DSP Tuning
- Real-time controls for the physical ESP32 I2S/analog microphone input.
- **Frequency Bands**: Fine-tune Bass, Midrange, and Treble sensitivities.
- **Squelch & AGC**: Set noise gate cutoffs to eliminate background room noise.

---

## Troubleshooting & FAQ

### Spotify Issues
| Problem | Cause | Solution |
| :--- | :--- | :--- |
| **"Spotify Client ID is not configured"** | No Client ID entered in settings. | Open **Settings → Connections**, enter your 32-character Client ID from the Spotify Developer Dashboard, and tap Save. |
| **"INVALID_CLIENT: Invalid redirect URI"** | Redirect URI mismatch in developer portal. | Open [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) → Settings. Ensure the Redirect URI is exactly: `in.roomcontroller.app://spotify/callback`. |
| **"User not registered in Developer Dashboard"** | App is in Development Mode and user is not whitelisted. | Go to your Spotify Developer App → **User Management**, add your Spotify login email, and save. |
| **"No active device found" error** | Spotify player daemon is dormant. | Open the official Spotify app on your phone, PC, or speaker, press Play on any song, then return to TheRoom. |
| **Refresh token failed / Blank token** | Expired session or network timeout. | Re-tap **Connect Spotify** in the Music tab to refresh authorization. |

### Build & Tooling Issues
| Problem | Cause | Solution |
| :--- | :--- | :--- |
| **"SDK location not found"** | Missing `local.properties` or `ANDROID_HOME`. | Create `local.properties` in the project root containing `sdk.dir=C:\\Users\\<YourName>\\AppData\\Local\\Android\\Sdk` (or your OS equivalent). |
| **Java version incompatibility** | Gradle running on JDK 8, 11, or 22+. | Install JDK 17 (recommended: Eclipse Temurin 17) and set `JAVA_HOME`. |
| **Gradle Daemon connection issues** | Firewall or corrupted Gradle cache. | Run `.\gradlew --stop` and retry with `.\gradlew assembleDebug --no-daemon`. |

### Hardware Connection Issues
| Problem | Cause | Solution |
| :--- | :--- | :--- |
| **ESP32 connection timeout** | Phone and ESP32 on different networks. | Ensure phone Wi-Fi is connected to the same 2.4 GHz SSID as the ESP32. Disable VPNs or private DNS. |
| **Certificate validation error** | IP changed or certificate regenerated. | In the app, navigate to **Devices** → Select Controller → **Re-pair Certificate Fingerprint**. |
| **WLED controls unresponsive** | WLED IP changed via DHCP. | Assign a static IP or DHCP reservation for WLED in your home router, then update the IP in TheRoom. |

---

## Security & Privacy Model

TheRoom is architected with a strict privacy-first philosophy:
1. **Local-First & Zero Cloud Relays**: All commands to your ESP32 and WLED hardware travel over your private local area network (LAN). No proprietary cloud servers sit between your phone and your room.
2. **Zero Hardcoded Secrets**: No developer client IDs, API keys, client secrets, or private certificates are hardcoded into the source code or binary releases.
3. **Hardware-Backed Encryption**: Sensitive authentication credentials (Spotify refresh tokens, Wi-Fi credentials, paired certificate fingerprints) are encrypted via **Android Keystore** and stored in private Jetpack DataStore containers.
4. **No Telemetry or Tracking**: The app contains zero analytics SDKs, advertising trackers, crash-reporting third-party monitors, or external network pings.

---

## Release History & Versioning

The project adheres to strict versioning and delivery standards:
- **Project Delivery Version**: **V32** (Recorded in `DELIVERY_VERSION.md`).
- **Android App Version**: **`0.5.6`** | **`versionCode: 27`** (Recorded in `app/build.gradle.kts` and `CHANGELOG.md`).
- **Verified Offline Baseline**: `0.3.0 / versionCode 5` (`ROOM-ANDROID-0.3.0+5`).

For detailed changelogs of all delivery milestones from V1 to V32, see:
- [`DELIVERY_VERSION.md`](DELIVERY_VERSION.md) — Delivery milestones, handoffs, and verification hashes.
- [`CHANGELOG.md`](CHANGELOG.md) — Chronological Android build ledger and detailed feature release notes.
- [`knowledge/docs/CONNECTION_GUIDE.md`](knowledge/docs/CONNECTION_GUIDE.md) — Detailed hardware and Spotify connection walk-throughs.
- [`artifacts/WLED_PARITY_INVENTORY.md`](artifacts/WLED_PARITY_INVENTORY.md) — Comprehensive inventory of all native WLED controls and API endpoints.

---

*TheRoom — Handcrafted local automation for modern rooms.*
