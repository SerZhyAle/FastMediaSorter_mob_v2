**Status:** Archived

# S0601 - Settings search surfaces device-feature-gated rows as dead results (PiP, accelerometer, OCR)

## Goal

Сделать индекс поиска по настройкам device-aware: строка, скрытая в рантайме проверкой аппаратной/OS-возможности устройства, не должна возвращаться поиском как мёртвая ссылка. Это третья ось гейтинга поиска: после section-only ([SettingsSearchAvailability]) и flavor/DI/compile ([SettingsSearchCapabilityGate], S0599/S0600) остаётся device/OS-ось - строки, спрятанные `Build.VERSION.SDK_INT` / `PackageManager.hasSystemFeature(..)` / [DeviceCapabilities]. Они не покрываются инъектируемыми DI-сетами, поэтому нужен отдельный предикат, читающий device-факты в момент фильтрации.

## 0. Capture (raw)

Discovered during S0599 research. Distinct from capability/flavor gating (S0599/S0600): these rows are hidden by a runtime DEVICE-feature check, not a flavor/DI capability, so they need a device-feature signal at search-filter time rather than an injected capability set.

- `rowEnablePip` / `layoutPip` - shown only on API 31+ (`PlaybackSettingsFragment.kt:247`, `Build.VERSION.SDK_INT >= S`); indexed in settings-manifest. On pre-API-31 devices the row is gone but searchable.
- `rowFollowSystemRotationPlayer` / `layoutFollowSystemRotationPlayer` - shown only when `PackageManager.FEATURE_SENSOR_ACCELEROMETER` is present (`PlaybackSettingsFragment.kt:392`, `OperationsSettingsFragment.kt:333`); indexed via `android:id` + `SettingsToggleRow`. Dead in search on devices without an accelerometer.

## 1. Problem

`SettingsSearchAvailability` is section-only; S0599/S0600's capability gate keys off injected DI capability sets and compiled flavor capabilities, which device-feature gates are not. A separate predicate reading `Build.VERSION.SDK_INT` / `PackageManager.hasSystemFeature(..)` / `DeviceCapabilities` at filter time is required.

## 2. Evidence (confirmed device-gated indexed rows)

Full audit of the 9 scanned layouts (`SettingsSearchLayoutCatalog`) against their owning fragments. The complete set of indexed rows hidden ONLY by a device-feature signal:

- `rowEnablePip` - `PlaybackSettingsFragment.applyConfig` sets `layoutPip.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (line 247). Single indexed row inside `layoutPip`.
- `rowFollowSystemRotation` - `OperationsSettingsFragment` sets `layoutFollowSystemRotation.isVisible = hasAccelerometer` (`hasSystemFeature(FEATURE_SENSOR_ACCELEROMETER)`, lines 333-334).
- `rowFollowSystemRotationPlayer` - `PlaybackSettingsFragment` sets `layoutFollowSystemRotationPlayer.isVisible = hasAccelerometer` (lines 61, 392).
- `rowCameraOcrTranslationEnabled` / `rowCameraOcrOnly` - `OperationsSettingsFragment.applyFlavorRestrictions` hides them when `hasOcrAndTranslation = capabilityAvailability.isTranslationAvailable() && DeviceCapabilities.isOcrSupported(context)` is false (lines 526-529). The translation half is the flavor/compile axis already owned by `SettingsSearchCapabilityGate` (S0600); the `isOcrSupported` half is the device axis owned here. Both must pass, and `SettingsSearchRegistry` ANDs the two gates.

Deliberately excluded (not a pure device-feature axis):

- `rowEnableOcr` - on a device where `DeviceCapabilities` reports OCR unsupported, `OtherMediaSettingsFragment.applyDeviceCapabilityRestrictions` keeps the row VISIBLE (disabled, with an explanatory `tvOcrSummary`); only the compile axis (`!isTranslationAvailable()`) hides it. So it is not a dead device-feature result - the device axis must not gate it. The non-indexed `layoutOcrFontSize` / `layoutOcrFontFamily` wrappers it hides are not in the search manifest.
- `btnNotificationPermission` (`PlaybackSettingsFragment`) / `btnScheduledNotificationPermission` (`OperationsScheduledManager`) - gated by `SDK_INT >= TIRAMISU`, but their visibility is dominated by runtime permission state (`!isNotificationPermissionGranted()`), a toggle's checked state, and `BuildConfig` (persistent-audio / scheduled-ops). A device-feature SDK gate alone would not make them non-dead in the common case (modern device, permission granted, toggle off). They are transient action buttons whose dead-ness is a runtime-state problem, out of S0601's device-feature scope. Parked separately.

Architecture notes:

- `SettingsSearchIndex.key` = the row's `android:id` resource-entry name, so a per-row key table is the natural keying (mirrors `SettingsSearchRegistry.isCapabilityAvailable`). Each device-gated container wraps a single indexed row, so the container-ancestry approach S0599 needed (multiple rows per gated card) buys nothing here - a key table is simpler and equally correct.
- `SettingsManifestExportTest` serializes a curated field subset, not the gate output, so adding the gate leaves `docs/settings/settings-manifest.json` byte-identical (doc-sync gate stays green).

## 3. Resolution

A device-feature gate parallel to the capability gate, keyed by the row's search key, reading only pure device/OS facts (no `BuildConfig`, Rule 15). ANDed into the registry filter after the capability gate.

### Phase 01 - Device-feature gate

- `SettingsSearchDeviceFeatureGate` (`ui/settings/search/`, `@Singleton @Inject constructor(@ApplicationContext Context)`).
- Three device signals: `supportsPictureInPicture = SDK_INT >= S`; `hasAccelerometer = packageManager.hasSystemFeature(FEATURE_SENSOR_ACCELEROMETER)` (lazy); `supportsOcr = DeviceCapabilities.isOcrSupported(context)` (lazy).
- `fun isAvailable(key: String): Boolean` maps each gated key to its signal (`else -> true`).
- The key->signal mapping is extracted to a `@VisibleForTesting internal fun decide(key, pip, accel, ocr)` so it is unit-testable without a `Context`; the device-fact acquisition stays thin glue.

### Phase 02 - Registry wiring

- Inject `SettingsSearchDeviceFeatureGate` into `SettingsSearchRegistry`.
- Extend the lazy `entries` filter: `availability.isAvailable(it) && capabilityGate.isAvailable(it) && deviceFeatureGate.isAvailable(it.key) && isCapabilityAvailable(it.key)`.
- Filter stays lazy (no eager `collect()` on construction).

### Phase 03 - Unit test + build

- `SettingsSearchDeviceFeatureGateTest` (`src/test`) exercises `decide(..)`: PiP suppressed/kept by SDK signal; both rotation rows by accelerometer; both camera-OCR rows by `supportsOcr`; unknown key always kept.
- Verification: `.\a.ps1 fk` exits 0; the new test class + `SettingsSearchCapabilityGateTest` pass (`--tests` filter, BUILD SUCCESSFUL).

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0599 (container/capability gate, introduced the search-gating architecture and named this device axis), S0600 (per-row flavor/compile gate, owns the translation half of the camera-OCR rows), S0602 (default-player non-functional rows).
- **Flavor scope:** the gate reads pure device/OS facts, so suppression activates per-device, not per-flavor; no per-flavor source-set code added.
- **User-facing strings:** none; no new string, no layout change, no setting added/moved/renamed - settings manifest and `SETTINGS_REFERENCE*.md` unaffected (Rule 22 not triggered).
- **Scope decision:** device-feature axis only (PiP API-31, accelerometer rotation, OCR device support). Notification-permission buttons (SDK + runtime-permission + toggle + BuildConfig confounds) and the toggle-state nesting of `rowCameraOcrOnly` are runtime-state problems split out.

## 6. Open items / future hardening

- The in-app search now suppresses these rows per device, but the static `docs/settings/settings-manifest.json` (standard-flavor scan on a capable build host) still lists them. Making the manifest generator device-aware is out of scope (the manifest has no single target device).
- Notification-permission buttons and other runtime-state-gated rows (permission/toggle dependent) remain potential dead results; a runtime-state search axis is a separate, larger change. Parked as a draft.

## 10. Related / discovered

- S0599 - container/capability search gate (architecture origin).
- S0600 - per-row flavor/compile search gate.
- S0602 - default-player rows visible but non-functional.
