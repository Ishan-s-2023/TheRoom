# Device log follow-up — 20 October 2026

Revision: `1.0.1-diagnostics`.

The former network loop called WiFi.begin every 15 seconds while association could still be running. It now tracks one attempt for up to 30 seconds, cancels it through esp_wifi_disconnect, and waits 15 seconds before another attempt. Saving new credentials schedules that same controlled cancellation. SDK automatic reconnect is disabled so it does not compete with this policy. Recovery AP behavior is retained.

The recursively nested lighting.power response is not reproduced on the host and its root cause remains unconfirmed. State responses now check heap integrity, validate the live database and copied response, and check response allocation failure. A failed check returns an error instead of reporting ok. These checks are diagnostic containment, not proof that a memory-corruption cause is fixed. Existing stored configuration is not silently erased or replaced.

Runtime fields now include storage_ready, ap_ip, wifi_status, wifi_connecting, minimum_free_heap, and task_stack_high_water (native FreeRTOS units). The existing ip field remains the station address; use ap_ip when connected through the recovery AP.

Validation: production host tests passed, including 10,000 JSON copy/swap/serialization cycles on each of normal and 16-bit slot-ID configurations; retry tests cover attempt timeout, backoff and clock rollover. Three Python helper tests passed. ESP32 compilation passed with Arduino core 3.3.1, target esp32:esp32:esp32:PartitionScheme=min_spiffs: 1,368,335 bytes of program storage (69% of 1,966,080), 62,936 bytes of global RAM (19%). Physical-board verification is pending. This compile target is a validation target; retain the board's current partition selection during diagnosis.

Next device check: upload this revision, open Serial Monitor at 115200 baud with Newline, and send {"op":"state"}. Confirm runtime.version is 1.0.1-diagnostics and provide the complete response plus any errors. Keep the current partition selection and do not format or erase storage during diagnosis. Never share passwords, DeviceSecrets.h, or .device-private files.
