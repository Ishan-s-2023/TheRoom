# Approved implementation profile — 20 October 2026

The user explicitly answered **“Approve both for implementation”** to the question
covering the physical-control table and GPIO table. This supersedes older records
that say these mappings are unapproved. It does not establish electrical compatibility,
purchase approval, a completed circuit or permission to flash/provision eFuses.

| Control | Approved function |
| --- | --- |
| Encoder rotation | Navigate menus and adjust values |
| Encoder short press | Select / confirm |
| Encoder long press | Back / Home |
| Button 1 | Lighting shortcut |
| Button 2 | Spotify / music shortcut |
| Button 3 | Appliances / IR shortcut |
| Button 4 | Favourites shortcut |

| Signal | GPIO |
| --- | --- |
| OLED SDA / SCL | 21 / 22 |
| Addressable LED data | 23 |
| IR driver TX / receiver RX | 18 / 27 |
| Microphone BCLK / WS / data | 26 / 25 / 35 |
| Encoder A / B / push | 32 / 33 / 4 |
| Buttons 1 / 2 / 3 / 4 | 13 / 14 / 16 / 17 |

`esp32-firmware/main/approved_profile.hpp` contains these constants and a compile-time
collision check. `panel.cpp` implements the approved interaction policy with a
hardware-independent service adapter. Long press cancels an active edit or confirmation,
otherwise returns home. Destructive confirmation defaults to **Cancel**. There are no
unrequested hold, double-press or chord bindings.

Physical debounce, encoder direction/detents, press timing, electrical biasing,
OLED rendering and actual appliance/music service adapters remain to be connected
and verified. Profile constants alone do not initialize GPIOs. GPIO 35's input-only
constraints and the exact WROOM board/flash variant must be accounted for in wiring.

References for electrical qualification:
[ESP32 datasheet](https://www.espressif.com/sites/default/files/documentation/esp32_datasheet_en.pdf),
[ESP-IDF GPIO documentation](https://docs.espressif.com/projects/esp-idf/en/v5.5/esp32/api-reference/peripherals/gpio.html).
