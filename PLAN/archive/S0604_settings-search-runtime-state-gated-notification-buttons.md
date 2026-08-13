**Status:** Archived

# S0604 - Settings search surfaces runtime-state-gated notification-permission buttons as dead results

## 0. Capture (raw)

Discovered during S0601 (device-feature search gate) implementation and confirmed by an independent audit of the 9 scanned settings layouts.

Two indexed action buttons appear in settings search but are hidden the vast majority of the time by RUNTIME state (not a device feature, not a flavor/DI capability), so they are dead/misleading search results:

- `btnNotificationPermission` (`PlaybackSettingsFragment.kt:536-539`) - visible only when `SDK_INT >= TIRAMISU && !isNotificationPermissionGranted() && rowEnablePersistentAudioPlayback.isChecked` (and `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK`). On a modern device with the permission already granted or the background-audio toggle off (the common case) the button is GONE, yet searching "notification permission" returns it.
- `btnScheduledNotificationPermission` (`OperationsScheduledManager.kt:192-199`) - hidden on `SDK_INT < TIRAMISU`; on API 33+ hidden when the POST_NOTIFICATIONS permission is already granted. Same dead-result pattern; also under the `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` section gate.

These differ from S0599 (DI-capability axis), S0600 (flavor/compile axis) and S0601 (device-feature axis): the dominant gate is mutable runtime state (a permission grant, a toggle). The existing three search gates cannot express it, and a device-feature SDK gate alone would not fix the common case (modern device, permission granted -> button still hidden).

## 1. Problem

Settings search has no runtime-state axis. Transient permission-prompt action buttons are indexed like persistent settings rows, so they surface as search results that lead to a screen where the button is hidden by current permission/toggle state.

## 2. Scope to investigate

- Decide the right fix: a runtime-state search axis (read live permission/toggle state at filter time) vs simply de-indexing transient permission-prompt action buttons from the search source (they are not navigable settings).
- Confirm whether any other indexed entries are transient action buttons gated purely by mutable runtime state.
- Whichever fix: keep the search index lazy and avoid coupling the registry to live permission callbacks.

## 3. Resolution

De-index the two transient permission-prompt buttons at the source rather than add a runtime-state search axis. A live-state gate would have to read the permission grant + toggle at filter time, coupling `SettingsSearchRegistry` to permission callbacks and breaking the lazy/cacheable index (§2 forbids this) - and it still would not make the common case (modern device, permission already granted) useful. These buttons are not navigable settings: they are one-shot prompts that vanish once the permission is granted, so they do not belong in a settings index at all.

### Decision: de-index (not a runtime-state axis)

- `LayoutSettingsSearchSource` skips `btnNotificationPermission` and `btnScheduledNotificationPermission` via a `TRANSIENT_ACTION_BUTTON_IDS` allow-list, mirroring the existing id-based handling. Excluding at the source (not in a gate) removes them from both the in-app index and the generated manifest, which share the same scan.
- The always-visible `btnPermissionsManagement` (General section, no runtime gating - only a click listener) stays indexed; it is a genuine navigable entry, not a transient prompt.
- Audit confirms these are the only two indexed action buttons gated purely by mutable runtime state (the third permission-related button, `btnPermissionsManagement`, is persistent).

### Confirmed runtime gates (why they are dead results)

- `btnNotificationPermission` (`PlaybackSettingsFragment.updateNotificationPermissionButtonVisibility`): visible only on API 33+ AND notification permission not granted AND the background-audio toggle checked (AND `ENABLE_PERSISTENT_AUDIO_PLAYBACK`). GONE in the common case.
- `btnScheduledNotificationPermission` (`OperationsScheduledManager.updateScheduledNotificationPermissionButton`): hidden below API 33 and whenever POST_NOTIFICATIONS is already granted.

### Collateral

- Removed the two now-orphaned keys from `docs/settings/settings-annotations.json`; regenerated `settings-manifest.json` (both keys dropped) and re-rendered `SETTINGS_REFERENCE*.md`.
- Updated the `SettingsSearchDeviceFeatureGate` KDoc note: these buttons are now de-indexed at the source, not silently out of the device axis.

### Verification

- `.\a.ps1 fk` exits 0.
- New permanent guard `SettingsManifestExportTest.transient permission-prompt buttons are not indexed` asserts both keys are absent from the live scan (passes).
- `settings-doc-sync` gate green: catalog complete, manifest fresh, annotations covered (189 keys, 0 orphans), reference up to date, HOW_TO recipes in sync.
