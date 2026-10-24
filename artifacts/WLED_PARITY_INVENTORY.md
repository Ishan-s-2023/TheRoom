# TheRoom WLED + Room Combined Firmware Control Parity Inventory

See canonical copy in `knowledge/docs/WLED_PARITY_MATRIX.md`.
- **Project Delivery**: V14 (Android `0.4.0` / `versionCode 11`)
- **Combined Firmware Target**: `2.0.1-wled-room` (WLED `16.0.1` + `AudioReactive` + `RoomBridgeUsermod`)
- **Portal Removal**: `WledBrowser.kt`, `WledTheme.kt`, and `androidx.webkit` removed; 100% of supported lighting, segment, preset, playlist, live LED preview, AudioReactive, Room quiet behavior, Wi-Fi, LED output, timer/NTP, UDP/realtime sync, button/encoder, and backup/restore controls are implemented in native Jetpack Compose screens.
