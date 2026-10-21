> **ESP32 implementation update — 21 October 2026:** Release 1.1.3-controls is in [the active Arduino source](SmartRoomController_Complete_Source/SmartRoomController/SmartRoomController.ino). See [requirements coverage](SmartRoomController_Complete_Source/SmartRoomController/REQUIREMENTS_COVERAGE.md), [detailed completion checklist](SmartRoomController_Complete_Source/SmartRoomController/COMPLETION_TODO.md), and [build evidence](SmartRoomController_Complete_Source/SmartRoomController/BUILD_VERIFICATION.md). Compilation and host tests passed; physical/account/security deployment acceptance remains explicitly pending. The historical baseline below is not a record that every device has been tested.

# ESP32 Smart-Room Controller --- Complete Requirements and Design Record

> **Current requirements update — 20 October 2026:** The user subsequently confirmed
> replacing Gesto with addressable lights (~15 m, three LEDs/pixel), local-only
> Android Wi-Fi control, temporary AP provisioning/recovery, and Spotify Premium
> with supported authorization. LED density is now confirmed as 60 LEDs/metre,
> three LEDs/pixel: 20 pixels/metre, 300 pixels / 900 LEDs at exactly 15 m. Component models remain candidates; the GPIO and physical-control tables were subsequently explicitly approved as the software design basis. Electrical verification remains pending.
> See [the authoritative clarification record](esp32-firmware/docs/CONFIRMED_UPDATES.md).
> Conflicting statements in the historical baseline below are superseded by that record.

**Document status:** Requirements baseline --- not a final electrical
design\
**Last updated:** 20 October 2026\
**Purpose:** Preserve everything discussed so far, distinguish confirmed
requirements from preferences and unresolved items, and prevent
assumptions from silently becoming design decisions.

> **Important rule:** Nothing in this document authorizes final GPIO
> assignments, physical button mappings, purchases, wiring, or a final
> circuit. Those require further verification and, where applicable,
> explicit user approval.

------------------------------------------------------------------------

## 1. How to read this document

Each item is classified as one of:

-   **CONFIRMED / REQUIRED** --- explicitly stated by the user or
    directly established by the supplied material.
-   **PREFERENCE** --- desired direction, but not yet an irrevocable
    technical decision.
-   **UNRESOLVED** --- information is missing, conflicting, or must be
    checked before design/purchase.
-   **DEPENDENCY / LIMITATION** --- a feature depends on hardware,
    network, service, or protocol support.

No uncertain specification should be treated as confirmed merely because
it appears in a product listing or an earlier assistant response.

------------------------------------------------------------------------

## 2. Project overview

The user wants one integrated, desk-mounted smart-room controller based
on an ESP32, with a dedicated Android app, physical controls, infrared
(IR) learning/transmission, lighting control, microphone-based
music-reactive effects, Spotify control, schedules, and automation.

### Core goals

1.  Use an **ESP32 DevKit V1 / ESP-WROOM-32** as the main controller.
2.  Operate the system primarily from a physical panel, while also
    providing a dedicated **Android app**.
3.  Connect the Android app to the ESP32 over **Wi-Fi only**.
4.  Make app control available **at home and remotely**.
5.  Keep supported local functions working when the internet or phone is
    unavailable.
6.  Provide a compact desk-mounted enclosure with the electronics and
    wiring inside.
7.  Learn, save, organize, and transmit IR commands for a fan, speaker,
    and additional user-chosen compatible appliances.
8.  Control addressable RGB lighting, including music-reactive effects,
    once the final strip is verified.
9.  Control Spotify playback and playback devices where supported.
10. Avoid losing existing lighting functionality until the integration
    strategy is verified.

### Explicit design constraint

**Do not assign GPIO pins or final physical functions to the
encoder/four buttons without the user's explicit approval.** This
includes not presenting a tentative pinout or button map as final.

------------------------------------------------------------------------

## 3. Main controller and enclosure

  -----------------------------------------------------------------------
  Item                    Current record          Status
  ----------------------- ----------------------- -----------------------
  Main board              ESP32 DevKit V1 /       Confirmed preference /
                          ESP-WROOM-32            selected board family

  Number of main          One ESP32 controller    Confirmed
  controllers                                     

  Enclosure               Compact, desk-mounted   Preference

  Electronics and wiring  Keep inside the         Required
                          enclosure               

  Internal wiring         Fixed internal wiring,  Preference
                          not intended as a       
                          frequently reconfigured 
                          breadboard              

  Construction method     Compare custom PCB,     Unresolved
                          modules, and a hybrid   
                          approach before         
                          deciding                

  Budget                  No fixed budget given   Unresolved

  Priority                Balance reliability,    Confirmed preference
                          feature coverage, and   
                          cost                    

  OTA firmware updates    Desired                 Preference

  Power input             One external power      Preference
                          inlet preferred         

  Internal power          "Separate"              Unresolved
  arrangement             rails/supplies were     
                          preferred; exact rail   
                          topology is not         
                          approved                
  -----------------------------------------------------------------------

The ESP32 board variant, available pins, boot-strapping constraints,
peripheral conflicts, thermal limits, enclosure dimensions, and final
board layout must be verified before the wiring plan is finalized.

------------------------------------------------------------------------

## 4. Lighting system

### 4.1 Existing Gesto strip and controller

The user previously described the existing strip as:

-   Gesto-branded strip
-   10 metres
-   12 V
-   5050 RGB
-   IP65
-   Conventional analog RGB strip (four conductors/pads were visible in
    supplied photos)
-   Existing Wi-Fi controller
-   Existing remote control
-   Existing Magic Home Pro / Magic Home app
-   The existing controller and app/remote were originally required to
    be retained.
-   The strip was described as cuttable at marked points; exact cutting
    points and connector compatibility have not been verified here.
-   Solderless four-pin connectors were previously discussed as a
    preference for reconnecting cut analog RGB strip sections.

The supplied controller photo visibly shows a generic white inline Wi-Fi
LED controller marked **"WiFi," "LED Controller," "RGB," and "DC
12--24V"**. Its exact model, chipset, firmware, API/protocol, current
rating, and internal circuitry have **not** been identified. The QR code
contents have not been decoded or verified.

### 4.2 New addressable-lighting direction

The user later shared a product screenshot for a product advertised as:

-   "Addressable RGB LED Strip 12V WS2811, 5m"
-   ₹90 per metre (as displayed in the screenshot)
-   12 V
-   60 LEDs per metre
-   Unbranded
-   The listing description calls it WS2811-based and describes
    individually addressable LEDs.
-   The listing's exact electrical and addressability details have not
    been independently verified.

The user then said they will get "the Deco lights" and "only use the
addressable lights." The exact meaning of "Deco lights" and the intended
relationship between the existing Gesto strip and the new addressable
strip need clarification.

### 4.3 Important unresolved conflict

Two prior intentions now need reconciliation:

1.  Earlier requirement: preserve the Gesto strip, its original
    controller, Magic Home app, and remote.
2.  Latest lighting direction: use only addressable lights.

Do **not** assume that the user has cancelled the existing
Gesto-retention requirement. The current record is:

-   **Addressable lighting for the ESP32-controlled effects:** preferred
    / intended.
-   **Whether the old Gesto strip/controller/app/remote must remain
    physically installed and usable:** unresolved due to the newer
    statement.
-   **Whether the addressable strip replaces the Gesto strip or is an
    additional strip:** unresolved.

### 4.4 Addressability is not yet confirmed

The user explicitly said they are not sure whether every physical LED on
the advertised strip is individually controllable and will confirm with
the dealer later.

For a typical 12 V WS2811 strip, several physical LEDs may form one
addressable pixel (often groups of three, depending on the exact strip).
A listing title or LED count does not prove the number of independently
controlled pixels.

**Do not finalize the LED purchase or power/driver design until the
dealer confirms:**

1.  Exact controller IC / protocol (for example, WS2811 or WS2812B).
2.  Strip voltage.
3.  Physical LEDs per metre.
4.  Independently addressable pixels per metre.
5.  Whether multiple physical LEDs share one pixel.
6.  Power consumption per metre and maximum current.
7.  Waterproof rating (IP rating) and construction.
8.  Number and function of wires/pads, including data direction.
9.  Recommended power injection intervals and maximum supported run
    length.
10. Whether the actual supplied product matches the listing
    photo/specification.

A dealer message was drafted asking for these details and a clear photo
of the strip/IC. The user intends to check with the dealer later.

### 4.5 Lighting features requested

The system should support, subject to final strip compatibility:

-   Manual colour control.
-   Brightness control.
-   Presets/favourites.
-   Addressable-strip animations and effects.
-   Music-reactive lighting based on the room microphone.
-   Beat/rhythm pulses.
-   Bass-responsive brightness and/or colour.
-   Overall sound-level/volume response.
-   Bass, midrange, and treble frequency-band response.
-   Configurable effect parameters.
-   Configurable lighting automation rules.
-   Time-based schedules.
-   Event-triggered lighting rules.
-   Quiet/no-music behaviour configurable by the user.
-   Automatic microphone sensitivity calibration, with manual
    sensitivity override.
-   Noise filtering/sensitivity tuning to reduce unwanted reactions.
-   Advanced lighting settings in the app.
-   Quick-access lighting favourites on the physical panel.

Ambient colour transitions and strobe were not selected as desired
effects in the earlier preference review; do not silently add them as
required features. Addressable LEDs make additional effects technically
possible, but the user should choose which effects to enable.

### 4.6 Power and safety

The final LED power supply, data interface/level shifting, common-ground
arrangement, fuse/wire sizes, connectors, and power-injection plan
depend on the confirmed strip and its measured/specification-based
current demand. They are not yet designed.

Never power a high-current LED strip from an ESP32 GPIO. The ESP32
should provide control/data signals to suitable driver circuitry, while
the strip receives an appropriately rated supply. Do not assume the
existing Gesto controller can drive an addressable strip; its visible
RGB label indicates RGB output but does not establish an addressable
data output.

------------------------------------------------------------------------

## 5. Existing lighting integration options

The user wanted the original controller investigated first and all
compatible options compared. The preferred goal was to retain the
existing app/controller/remote and add ESP32 control safely.

Options to investigate, without preselecting one:

1.  **Network/protocol integration:** determine whether the existing
    Magic Home controller can be controlled through a supported or
    reverse-engineered local protocol. Exact compatibility is unknown.
2.  **Safe hardware switching:** if retaining the analog controller is
    still required, investigate a properly engineered switching
    arrangement so the original controller and ESP32 driver are not
    electrically fighting each other.
3.  **Separate addressable strip:** keep the old Gesto installation
    independent while the ESP32 controls a new addressable strip.
4.  **Replacement:** only consider replacing the original
    strip/controller if the user explicitly agrees after trade-offs are
    presented.

No final method has been selected. Do not connect outputs from two
controllers together or connect the ESP32 directly to the 12 V strip.

------------------------------------------------------------------------

## 6. Infrared (IR) universal remote and learning

### 6.1 Intended use

Primary appliances mentioned:

-   Ceiling fan
-   Speaker

The user also wants to add any other compatible IR appliance of their
choice later.

This is an IR project, not an RF project. The user wants broad IR
compatibility, but support for every protocol/device cannot be
guaranteed.

### 6.2 Required capabilities

-   Learn/record IR signals from an existing remote.
-   Save the learned command locally on the ESP32.
-   Give appliances and commands user-defined names.
-   Organize commands by appliance.
-   Replay/transmit saved signals.
-   Allow the user to add appliances and commands through the app.
-   Provide an expandable library rather than a permanently fixed small
    list.
-   Support decoded capture when a protocol can be decoded.
-   Also retain raw/timing capture when needed for unknown or
    unsupported protocols.
-   Capture both a single press and repeated/held-button behaviour where
    the protocol and receiver permit.
-   Guided IR learning workflow on the physical panel.
-   Favourites/quick access on the panel.
-   Full command library access on the panel.
-   Diagnostics when capture or transmission fails.
-   Clear feedback and retry options.
-   Retry only safe operations; do not blindly repeat disruptive
    commands.
-   Event-triggered automations using IR-related events where
    technically possible.
-   Time-based and event-triggered automation rules.
-   Backup and restore the IR library through the app/dashboard.
-   Keep a local copy so basic operation does not depend on the phone or
    internet.

### 6.3 Limits and verification

-   Some appliances use toggle commands; repeating a command may undo
    the intended action.
-   Some remotes send stateful, rolling, or complex signals that are not
    reliably reproducible by a simple raw replay.
-   Some IR protocols require repeat frames, timing accuracy, or
    protocol-specific handling.
-   The hardware receiver, IR LED driver/transistor, optical placement,
    current, and carrier-frequency support remain to be selected.
-   IR transmission should use a suitable driver circuit rather than
    sourcing high LED current directly from a GPIO.
-   The system should distinguish "command transmitted" from "appliance
    confirmed its state"; many IR appliances do not provide feedback.

------------------------------------------------------------------------

## 7. Spotify and music playback control

### 7.1 Desired features

The Android app and OLED should support the following where Spotify's
current APIs, account eligibility, and playback-device capabilities
permit:

-   Play / pause.
-   Next track.
-   Previous track.
-   Volume control.
-   Select playback device.
-   Display track name.
-   Display artist.
-   Display playback status.
-   Display selected device.
-   Browse saved playlists and recent items on the OLED.
-   Full music search in the Android app.
-   Choose whether a volume action controls Spotify's playback volume or
    the speaker through IR.
-   Select playback devices through both the OLED/panel and Android app.
-   Support multiple playback devices.

### 7.2 Dependencies

-   Spotify cloud/API control needs internet connectivity.
-   Authentication, Spotify account tier/eligibility, API access,
    playback device availability, and the currently supported Spotify
    integration method must be checked before implementation.
-   Not every Spotify Connect device necessarily supports every
    operation.
-   Spotify playback volume and the speaker's IR volume are separate
    controls and must not be conflated.
-   A track shown by the app/OLED is metadata from Spotify, not a
    guarantee that audio is being heard by the room microphone.

### 7.3 OLED display

The preferred OLED status fields are:

-   Track name
-   Artist
-   Playback status
-   Selected playback device

The user supplied a photo of a **0.96-inch, 128×64, blue OLED module**.
Exact controller IC, bus interface (I²C/SPI), pinout, and module voltage
compatibility remain unverified. Do not assign GPIO pins until these are
confirmed and the user approves the overall pin plan.

------------------------------------------------------------------------

## 8. Music-reactive lighting and microphone

### 8.1 Input choice

The user ultimately specified **room microphone only**. Direct audio
input is not needed and should not be added unless the user changes this
requirement.

The microphone should react to sound physically audible in the room,
regardless of whether it comes from Spotify, another app, a television,
or another source. The user does not require digital audio source
isolation or source selection.

### 8.2 Requested behaviour

-   Beat/rhythm pulses.
-   Bass-responsive brightness/colour.
-   Overall sound-level response.
-   Bass/midrange/treble band response.
-   Automatic sensitivity calibration.
-   Manual sensitivity override.
-   Sensitivity/noise filtering to reduce unwanted reactions.
-   Configurable quiet/no-music behaviour.
-   User-configurable effects and parameters.

### 8.3 Microphone placement

The user expressed a preference to put the microphone inside the
enclosure. This is a **preference, not a validated acoustic design**. A
microphone enclosed behind an unvented case may perform poorly or pick
up enclosure vibration. The case design must account for an acoustic
opening/port and placement away from electrical/motor noise if needed.

The exact microphone module has not been selected or purchased. INMP441
was discussed as a possibility in the broader project, but is **not
confirmed**.

------------------------------------------------------------------------

## 9. Physical control panel

### 9.1 Hardware requested

-   One small OLED display, described from the supplied image as
    0.96-inch 128×64 blue.
-   One rotary encoder with a push button.
-   Four separate physical buttons.
-   Compact desk-mounted enclosure.

### 9.2 Interaction preferences

-   Hybrid control model: physical controls plus Android app.
-   The user expects to use the physical panel primarily and not need to
    touch the phone for routine actions.
-   The OLED home screen should show a compact status header plus a
    menu.
-   Primary navigation preference: rotary encoder.
-   Quick access to IR favourites.
-   Full IR library access.
-   Guided IR-learning workflow.
-   Ask for confirmation for important/destructive actions, including:
    -   Deleting IR commands.
    -   Deleting schedules.
    -   Disabling automation.
-   Adaptive status detail.
-   Display timeout.
-   Use short press, long press, hold, double press, and two-button
    chord gestures only where they are useful. Avoid needless complexity
    while keeping the requested feature set available.

### 9.3 Explicit prohibition

**No physical button assignments, encoder action assignments,
gesture-to-action mapping, or GPIO allocations have been approved.** Do
not invent them or present them as finalized. Prepare alternatives for
user review and obtain explicit approval before finalizing.

------------------------------------------------------------------------

## 10. Android app and connectivity

### 10.1 Confirmed user choices

-   Build/use a dedicated **Android app**, not rely solely on a browser
    dashboard.
-   App-to-ESP32 connection: **Wi-Fi only**. Bluetooth is not requested.
-   App control should work **at home and remotely**.
-   App should be password-protected/authenticated.
-   The user wants OTA firmware updates.
-   Backup and restore should be supported.
-   The physical panel should remain useful when the phone is
    unavailable.
-   Keep supported features working offline wherever possible.

### 10.2 App feature areas

The app is intended to include:

1.  Smart-room home/status screen.
2.  Addressable-light control, colour/brightness, presets, effects, and
    advanced effect configuration.
3.  IR appliance and command manager: add, name, learn, save, test,
    organize, favourite, back up, restore.
4.  Spotify controls, device selection, track/artist/status,
    playlist/recent browsing, and full music search.
5.  Automation rule editor for schedules and event triggers.
6.  Microphone/music-reactive tuning.
7.  Wi-Fi and connection status.
8.  Device/system diagnostics and safe retry feedback.
9.  Settings and backup/restore.
10. OTA firmware update workflow, with appropriate safeguards.

### 10.3 Remote access security

Remote access method is **not decided**. Options should be compared
before implementation, such as:

-   VPN-based access into the home network.
-   Secure cloud relay/broker or service.
-   Another properly authenticated, encrypted remote-control
    architecture.

Do not expose a raw unauthenticated ESP32 HTTP server directly to the
public internet. Remote access requires a threat model, secure
authentication, encrypted transport, authorization, and safe update
handling. The user has not selected a remote-access provider or agreed
to a cloud dependency.

### 10.4 Local Wi-Fi and offline operation

-   The ESP32 should be able to continue supported local lighting
    effects, IR operations, and stored automations without the phone
    connected.
-   Remote app access requires the home network and external
    connectivity to be functioning.
-   Spotify cloud features require internet.
-   The user prefers fallback operation through the physical panel if
    Wi-Fi fails.
-   A fallback ESP32 access point was discussed but not selected.
-   Wi-Fi provisioning method remains undecided.
-   Local-only operation and remote operation should be designed as
    separate capability states, with clear status feedback.

### 10.5 Android implementation technology

Flutter, native Android/Kotlin, and visual app builders were discussed
as possible approaches. **No framework has been selected.** The eventual
choice should be based on required Android features, app maintenance,
security, Spotify integration, and the user's preferred development
workflow.

A browser dashboard is no longer the primary interface requirement. A
supplementary computer-access/admin interface may be considered, but has
not been approved as mandatory.

------------------------------------------------------------------------

## 11. Automation and timekeeping

### 11.1 Required automation types

The user selected **both time-based and event-triggered automation**.

Examples of potential categories, not pre-approved specific rules:

-   Lighting schedules.
-   Lighting actions based on microphone/music state.
-   IR commands triggered by user-defined events.
-   Rules involving saved device/lighting states where technically
    reliable.

The user wants configurable automation rules rather than a fixed list of
hard-coded routines.

### 11.2 Time schedules

-   Scheduling is required.
-   Weekly-versus-daily schedule details have not been explicitly
    finalized.
-   Time zone and daylight-saving handling need to be defined; the
    user's current context is India, but the firmware should not
    hard-code a time zone without approval.
-   Timekeeping preference was Wi-Fi time/NTP; a physical RTC was not
    selected.
-   Behaviour after a long outage, when time may be invalid, must be
    designed explicitly.
-   The system should not issue old IR commands automatically after
    power restoration.

### 11.3 Power restoration

The user's preference is:

-   Restore saved settings and the last lighting mode/state where
    feasible.
-   Do **not** blindly replay old IR commands on power-up.
-   Automation resumption after a reboot should be deliberate and safe.

------------------------------------------------------------------------

## 12. Power system and electrical design

### 12.1 Current preferences

-   One external power input is preferred.
-   The internal power arrangement was described as "separate," but the
    precise meaning and rail topology remain unconfirmed.
-   Compact enclosure and fixed internal wiring.
-   Balance reliability, feature coverage, and cost.
-   The existing Gesto controller label indicates a 12--24 V DC input
    range, while the user's original Gesto strip was described as 12 V.
    This does not prove the controller's exact current capacity or the
    power requirement of a future addressable strip.

### 12.2 Still to determine

-   Exact addressable strip and its maximum current.
-   Whether the existing Gesto strip/controller stays in the system.
-   Power supply output voltage/current/wattage and safety
    certifications.
-   Whether multiple DC rails are needed for the ESP32, OLED,
    microphone, addressable strip, and other circuitry.
-   Whether any power converters are needed.
-   Fusing, wire gauge, connector ratings, reverse-polarity protection,
    transient protection, strain relief, heat management, and grounding.
-   LED data-line level compatibility and buffering/level shifting if
    needed.
-   Physical separation of noisy/high-current LED and audio/microphone
    circuits.
-   Power-on and brownout behaviour.
-   Enclosure ventilation and thermal limits.

No final supply or wiring design has been approved. Do not select a
supply based only on nominal voltage or a product listing without
calculating current demand and checking all connected loads.

------------------------------------------------------------------------

## 13. Hardware/components: confirmed versus not confirmed

  -----------------------------------------------------------------------
  Component / area                    Current state
  ----------------------------------- -----------------------------------
  ESP32 DevKit V1 / ESP-WROOM-32      Selected board family

  Existing Gesto 10 m 12 V 5050 IP65  Previously confirmed as existing
  analog RGB strip                    hardware; whether retained after
                                      addressable-strip decision is
                                      unresolved

  Existing white Wi-Fi RGB controller Photos supplied; generic label
                                      visible; exact model/protocol
                                      unknown

  Magic Home Pro / Magic Home app     User identified this as the
                                      existing app

  Existing remote control             User said it is part of the
                                      existing lighting setup and should
                                      be retained unless that requirement
                                      is changed

  New addressable LED strip           Intended, but exact
                                      model/specification and purchase
                                      unconfirmed

  12 V WS2811 listing at ₹90/m, 60    Seen in screenshot; seller specs
  LEDs/m                              unverified; independent pixels/m
                                      unknown

  0.96-inch 128×64 blue OLED          Photo supplied;
                                      controller/interface/pinout
                                      unverified

  Rotary encoder with push button     Required hardware; exact model
                                      unselected

  Four separate physical buttons      Required hardware; no assignments
                                      approved

  Microphone module                   Room microphone required; module
                                      unselected; INMP441 was only a
                                      possible candidate

  IR receiver                         Required for learning; exact part
                                      unselected

  IR emitter/driver                   Required for transmission; exact
                                      part/circuit unselected

  Power supply/converters/protection  Not finalized

  PCB approach                        Compare custom PCB, modules,
                                      hybrid; undecided

  Enclosure dimensions/material       Compact desk box requested;
                                      dimensions/material undecided
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 14. Reliability, feedback, diagnostics, and data

### Required or preferred behaviour

-   Clear success/failure feedback for IR capture/transmission and app
    commands.
-   Retry only operations that are safe to retry.
-   Do not blindly repeat state-toggle or disruptive IR commands.
-   Local persistence for IR commands, configuration, preferences, and
    supported automation.
-   Backup/restore through the Android app (or another approved
    management interface).
-   Explain which features are unavailable when Wi-Fi/internet is down.
-   Keep physical controls useful when the phone is offline or
    unavailable.
-   Restore saved settings/last lighting mode where feasible after power
    returns.
-   Do not replay old IR commands on power restoration.
-   Diagnostics should distinguish a locally transmitted command from
    confirmed appliance state if the appliance provides no feedback.
-   Authentication and remote access must be designed securely.

Storage format, backup encryption, backup destination, data retention,
and conflict handling between local and backup copies remain unresolved.

------------------------------------------------------------------------

## 15. Priorities and non-goals

### Current priorities

1.  Avoid assumptions.
2.  Preserve existing functionality unless the user explicitly changes
    that requirement.
3.  Keep the system compact and desk-mounted.
4.  Use the physical panel as the primary routine interface, with an
    Android app as a full companion interface.
5.  Balance reliability, features, and cost.
6.  Make local features resilient to internet loss.
7.  Support user-defined IR appliances/commands and configurable
    automations.
8.  Use room microphone input for music-reactive effects.
9.  Provide remote app access through a secure design.
10. Do not finalize GPIOs or physical control mappings without explicit
    approval.

### Explicit exclusions or limits

-   No Bluetooth app connection was selected; the chosen
    phone-to-controller connection is Wi-Fi only.
-   No direct audio input is required; room microphone only.
-   No RF-control scope was requested for the universal remote; the
    stated scope is IR.
-   Do not assume all IR protocols/appliances will be supported.
-   Do not assume every physical LED in a listed WS2811 strip is an
    independent pixel.
-   Do not assume Spotify features will work without internet or without
    supported account/API/device access.
-   Do not add ambient transitions or strobe as required features
    without user approval.
-   Do not assume the Gesto controller supports a particular local API
    or can drive addressable LEDs.
-   Do not assign GPIOs or physical control functions without explicit
    approval.

------------------------------------------------------------------------

## 16. Open questions and blockers

These are the outstanding clarifications and checks. They are listed so
the project can progress without pretending unknown details are settled.

### A. Lighting --- highest-priority ambiguity

1.  **Does the existing Gesto strip/controller/remote/Magic Home app
    need to remain installed and usable, or does "only use the
    addressable lights" mean replacing the Gesto strip for this
    project?** This is a genuine conflict between earlier and newer
    requirements.
2.  Is the addressable strip replacing the Gesto strip or being added as
    a separate strip?
3.  What exact addressable strip does the dealer supply?
4.  Does the dealer confirm exact IC, voltage, pixels per metre, power
    per metre, IP rating, wire/pad functions, and data direction?
5.  What does "Deco lights" refer to exactly (a particular product/brand
    or simply decorative/addressable lights)?

### B. Existing controller integration

6.  Can the exact controller model or QR contents be identified?
7.  Is local-network control of the Magic Home controller supported by
    this specific unit?
8.  If not, does the user want a separate addressable strip while
    retaining the Gesto system, or should alternatives be compared?

### C. Android app and remote access

9.  Which app development technology should be used: Flutter,
    Kotlin/native Android, or another approach?
10. Which remote access approach is preferred after comparing VPN versus
    secure relay?
11. Is a supplementary browser/desktop interface wanted, or should the
    Android app be the only software interface?
12. How should the app be authenticated, and should remote control
    support one phone or multiple authorized users?
13. What Wi-Fi provisioning/fallback process should be used if home
    Wi-Fi credentials change or the router is unavailable?

### D. Spotify

14. What Spotify account tier and playback devices will be used?
15. Which device types need to be supported, and are they normally on
    the same home network?
16. Is the user willing to use the required Spotify authorization/API
    flow and any associated service limitations?

### E. Time and automation

17. Should schedules support one-time, daily, selected weekdays, weekly,
    or recurring calendar rules?
18. What should happen to scheduled events if the ESP32 has been offline
    and time has not yet synchronized?
19. Should automation resume automatically after reboot, beyond
    restoring saved lighting settings? IR commands must not be blindly
    replayed.

### F. Physical and electrical design

20. What are the exact OLED module controller/interface and voltage
    requirements?
21. Which microphone module should be selected after comparing acoustic
    quality, noise, size, and cost?
22. What IR receiver range and emitter range are needed for the room
    layout?
23. What enclosure dimensions and approximate maximum weight are
    acceptable?
24. Does "separate" power mean separate regulated rails for different
    loads, separate supplies inside the box, or another arrangement?
25. Is a custom PCB acceptable after a tested prototype, or should the
    project remain module-based?
26. What is the budget range, if/when the user wants to set one?

Not all of these questions need to be answered at once. The
lighting-retention conflict and actual addressable-strip specification
are the most immediate blockers to finalizing the lighting hardware.

------------------------------------------------------------------------

## 17. Suggested implementation sequence (not yet approved as a schedule)

This sequence is a proposal for organizing work, not a commitment to
particular parts or dates.

1.  **Resolve requirements conflicts:** especially existing Gesto
    retention versus addressable-only lighting.
2.  **Verify hardware identity/specifications:** dealer confirmation for
    the addressable strip; identify OLED and existing controller where
    possible.
3.  **Compare architecture options:** PCB/modules/hybrid; local/remote
    app architecture; power distribution; lighting integration.
4.  **Create a component list with alternatives:** include
    specifications, rationale, compatibility, estimated cost, and items
    that still require seller confirmation.
5.  **Propose physical-control alternatives:** show options for
    encoder/four buttons and OLED navigation, but do not finalize
    mappings until the user explicitly approves.
6.  **Prepare a proposed pin/resource map:** only after exact modules
    and electrical needs are known, and only for review; do not call it
    final without approval.
7.  **Prototype subsystems separately:** ESP32 communication, OLED,
    buttons/encoder, IR learning/transmit, microphone analysis,
    addressable lighting, Spotify integration.
8.  **Integrate local persistence and automation.**
9.  **Implement the Android app and secure remote access.**
10. **Test failure cases:** Wi-Fi outage, internet outage, power
    cycling, invalid time, missing Spotify device, IR failure, LED power
    interruption, and backup restoration.
11. **Finalize enclosure, PCB, wiring, and build instructions only after
    verification and approval.**

------------------------------------------------------------------------

## 18. Explicit approval log

The following are not approved merely by appearing in this document:

-   Final GPIO assignments.
-   Final physical button mapping.
-   Final encoder/gesture mapping.
-   Exact LED strip model.
-   Exact LED power supply and power distribution.
-   Exact Magic Home integration method.
-   Android framework.
-   Remote access provider/architecture.
-   Exact microphone/IR/OLED component models.
-   Final PCB layout and enclosure dimensions.
-   Final purchase list.

Any future proposal should label choices as **proposal**, **confirmed**,
or **pending** and update this document when the user explicitly
decides.

------------------------------------------------------------------------

## 19. Summary of the current baseline

The project is an ESP32 DevKit V1 / ESP-WROOM-32 smart-room controller
with a dedicated Wi-Fi-only Android app available locally and remotely,
a desk-mounted physical panel (0.96-inch 128×64 blue OLED, rotary
encoder, four buttons), IR learning/transmission and expandable
appliance commands, Spotify controls, room-microphone music-reactive
lighting, user-configurable schedules and event-triggered automation,
local persistence and backup/restore, OTA preference, and offline
operation wherever technically feasible.

The major unresolved lighting issue is that the user originally required
retention of the existing Gesto analog RGB strip/controller/app/remote,
but later said they will use only addressable lights. That must be
clarified rather than silently choosing one. The advertised 12 V WS2811
strip has not been verified with the dealer, and its independent pixel
density and power demand are unknown.

**No GPIO allocation or physical button mapping has been approved. No
final circuit, bill of materials, or wiring plan should be represented
as complete until the listed dependencies are resolved.**
