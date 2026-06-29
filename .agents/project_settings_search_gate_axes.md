---
name: settings-search-gate-axes
description: Settings-search dead-result suppression is split across 3 independent gates by axis; route new dead-result findings to the right one
type: project
---

Settings-search hides "dead result" rows (indexed in static XML but runtime-hidden) via THREE independent gates, all ANDed in `SettingsSearchRegistry.entries`:

- `SettingsSearchAvailability` - section axis (media sections vs per-flavor `@SupportedMediaSection`).
- `SettingsSearchCapabilityGate` - flavor/DI/compile axis: container-ancestry for empty multibound DI sets (S0599) + per-row keys for typed/compiled capabilities like `isTranslationAvailable`, `supportsMicRecording`, `supportsCloud` (S0600).
- `SettingsSearchDeviceFeatureGate` - device/OS axis: pure `Build.VERSION.SDK_INT` / `PackageManager.hasSystemFeature` / `DeviceCapabilities.isOcrSupported` (S0601). Keyed by row key; `@VisibleForTesting decide()` pure fn.

**Why:** a single row can span two axes - the camera-OCR rows (`rowCameraOcrTranslationEnabled`/`rowCameraOcrOnly`) are gated by `isTranslationAvailable()` (CapabilityGate) AND `isOcrSupported` (DeviceFeatureGate); both must pass. Runtime-state-gated rows (notification-permission buttons: permission grant + toggle state) fit NONE of the three and are parked as S0604.

**How to apply:** when a new "search returns a hidden row" finding appears, classify the dominant gate the fragment uses (section / DI-set+compile / device-OS / runtime-state) and add to the matching gate or S0604; ticket family is S0597-S0604. Registry's own `isCapabilityAvailable()` still owns default-player keys (S0602).
