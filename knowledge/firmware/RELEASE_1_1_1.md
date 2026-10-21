# ESP32 1.1.1 — NTP hostname ownership fix

The uploaded 1.1.0 device reported mounted storage, valid loaded configuration, ready HTTPS, station connectivity and zero integrity failures, but time_valid remained false for more than eight minutes. The configured server responded to a separate UDP NTP test from the PC; that does not by itself prove ESP32-to-server reachability.

Source inspection found a concrete lifetime defect: both boot and settings paths passed a local String's c_str() as the NTP hostname. Arduino forwards that pointer and lwIP retains it for later DNS/contact. The temporary is destroyed on return. This is a defect regardless of whether it is the sole cause of this device's failed synchronization.

## Change
- ClockConfiguration owns bounded permanent hostname/timezone buffers for the life of the application.
- Stop the previous SNTP client before changing its borrowed storage; initialize the network interface API before touching SNTP.
- Reuse the same clock setup on boot, settings, restore and each successful home-Wi-Fi connection/reconnection.
- Empty time settings stop the previous client; invalid lengths leave the previous configuration intact.
- Regression tests simulate a borrowing client, destroy/overwrite temporary input strings, and check retained names, update ordering, bounds and disabled/re-enabled configuration.
- Preserve actual timezone/NTP settings, hardware enable flags, both private identities and the partition layout. No device update or settings change is performed automatically.

## Validation
Production C++ regression tests passed with both 4-byte and 2-byte ArduinoJson slot IDs. ESP32 core 3.3.1 compilation/link passed with exit code 0 for esp32:esp32:esp32:PartitionScheme=min_spiffs. Program: 1,396,795 / 1,966,080 bytes; global RAM: 68,128 bytes; compiler-reported remaining RAM: 259,552 bytes before runtime allocations. All 34 frozen source inputs match their compiler snapshots. The strong OTA rollback-defer hook remains linked. Public source copies and ZIP manifests are verified with both private identities preserved. The connected device remains on 1.1.0 until the user uploads this patch. NTP success, Spotify authentication/API access and peripheral readiness remain separate checks.

## Source evidence
The installed Arduino 3.3.1 esp32-hal-time.c forwards server names. Espressif's [SNTP implementation](https://github.com/espressif/esp-lwip/blob/2.2.0-esp/src/apps/sntp/sntp.c#L852-L868) retains the server pointer rather than making a copy. The [ESP-IDF wrapper](https://github.com/espressif/esp-idf/blob/v5.5.1/components/lwip/apps/sntp/sntp.c) dispatches the SNTP APIs through the TCP/IP thread.

## User verification after upload
1. Open the existing canonical sketch folder and upload normally, retaining the current partition and identity files.
2. Wait 30 seconds after home Wi-Fi connects, then send {"op":"state"} at 115200/Newline.
3. Require runtime.version=1.1.1 and check time_valid=true. If false, retain the state and continue device-specific DNS/UDP diagnostics; this patch does not claim every network permits NTP.
4. Once synchronized, inspect the Spotify error and actual metadata independently. authorized=true only establishes stored refresh credentials, not successful authentication.
5. All hardware drivers are still disabled in the supplied device profile; enable them only with verified actual module parameters.
