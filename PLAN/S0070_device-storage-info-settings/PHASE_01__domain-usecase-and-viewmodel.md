# Phase 01 — domain-usecase-and-viewmodel

**Strategic spec:** [`../S0070_device-storage-info-settings.md`](../S0070_device-storage-info-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Introduce `GetDeviceStorageUseCase` with StatFs-based disk query (async, error-safe); add `DeviceStorageState` sealed class; wire ViewModel with `refreshDeviceStorage()` method and `deviceStorage: StateFlow<DeviceStorageState>`.

---

## Prerequisites

- [ ] Pre-Implementation Blocker (§ Pre-Implementation Blockers in INDEX) is resolved or explicitly overridden.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/` exists.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` exists and uses StateFlow.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/DeviceStorageState.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDeviceStorageUseCase.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 600 |

---

## Steps

### Step 01.1 — Create `DeviceStorageState` sealed class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/DeviceStorageState.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a sealed class `DeviceStorageState` (in `domain/model/`) with two cases:
> - `Success(availableGb: Double)` — available storage in gigabytes, formatted to 1 decimal place.
> - `Error(message: String)` — error state with user-facing message (e.g., "Unavailable").
> No public API beyond these two cases. Timber logging happens in the UseCase, not here.

**Verification:**

- `Glob` — file exists at `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/DeviceStorageState.kt`.
- `Grep` — `sealed class DeviceStorageState` declaration found.
- `Grep` — `data class Success` and `data class Error` both present.
- `Grep` — no `Log.d` or `Log.e` in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: domain/model/DeviceStorageState.kt (6 LOC). Dev log recorded.

---

### Step 01.2 — Create `GetDeviceStorageUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDeviceStorageUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `GetDeviceStorageUseCase` (in `domain/usecase/`), a class with a single method `invoke(): DeviceStorageState`.
> 
> Implementation:
> - Use Android's `StatFs` to query the internal storage (root "/").
> - Calculate available bytes as `StatFs.availableBlocks * StatFs.blockSize`.
> - Convert to GB: `availableBytes / (1024.0 * 1024.0 * 1024.0)`, format to 1 decimal place.
> - Wrap in try-catch: catch any exception (SecurityException, IOException, etc.) and return `DeviceStorageState.Error("Unavailable")`.
> - Log errors via Timber (e.g., `Timber.w(e, "Failed to query storage")`).
> - No @Inject, no viewModelScope — this is a pure domain function. Caller (ViewModel) handles async dispatch.

**Verification:**

- `Glob` — file exists at `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDeviceStorageUseCase.kt`.
- `Grep` — `class GetDeviceStorageUseCase` declaration found.
- `Grep` — `invoke(): DeviceStorageState` method signature present.
- `Grep` — `StatFs` imported and used.
- `Grep` — `Timber.w` or `Timber.e` for error logging present.
- `Grep` — no direct Fragment or Activity references.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 6/6 PASS. Files: domain/usecase/GetDeviceStorageUseCase.kt (19 LOC). Dev log recorded.

---

### Step 01.3 — Add `deviceStorage: StateFlow<DeviceStorageState>` to SettingsViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `SettingsViewModel`:
> 
> 1. Add a private `_deviceStorage` MutableStateFlow initialized to `DeviceStorageState.Error("Loading...")`.
> 2. Public property: `val deviceStorage: StateFlow<DeviceStorageState> = _deviceStorage.asStateFlow()`.
> 3. Inject `GetDeviceStorageUseCase` via Hilt `@Inject constructor(...)`.
> 4. In the existing initialization (constructor or an init block), launch a coroutine on `viewModelScope` to call `GetDeviceStorageUseCase().invoke()` and update `_deviceStorage`.
> 5. Do NOT await or block — use `launch { ... }` and let the StateFlow emit the result.

**Verification:**

- `Grep` — `private val _deviceStorage: MutableStateFlow<DeviceStorageState>` present in SettingsViewModel.
- `Grep` — `val deviceStorage: StateFlow<DeviceStorageState>` public property present.
- `Grep` — `@Inject constructor(...)` includes `GetDeviceStorageUseCase`.
- `Grep` — `launch { ... }` or `launchIn(viewModelScope)` pattern present for the usecase call.
- `Grep` — `_deviceStorage.value = ...` update statement found.
- `Grep` — no blocking calls (no `.runBlocking`, no synchronous waits).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 6/6 PASS. Files: SettingsViewModel.kt (+11 LOC). Note: added `@Inject constructor()` to GetDeviceStorageUseCase to enable Hilt injection. Dev log recorded.

---

### Step 01.4 — Add `refreshDeviceStorage()` method to SettingsViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `SettingsViewModel`, add a public method `fun refreshDeviceStorage()` that:
> - Launches a coroutine on `viewModelScope`.
> - Calls `GetDeviceStorageUseCase().invoke()` and updates `_deviceStorage` with the result.
> - This mirrors the initialization logic from Step 01.3 so that button clicks (later, in Fragment) trigger a refresh.

**Verification:**

- `Grep` — `fun refreshDeviceStorage()` method signature present in SettingsViewModel.
- `Grep` — `launch { ... }` or `launchIn(viewModelScope)` pattern inside the method.
- `Grep` — `_deviceStorage.value = ...` update statement found.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: SettingsViewModel.kt (+6 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles: BUILD SUCCESSFUL 2026-05-03.
- [x] `Grep -n "TODO(phase-01)"` returns zero hits.
- [x] Dev log entries added:
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/model/DeviceStorageState.kt" "feature" "Add DeviceStorageState sealed class"`
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDeviceStorageUseCase.kt" "feature" "Add GetDeviceStorageUseCase for disk query"`
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt" "feature" "Add deviceStorage StateFlow and refresh method"`

---

## Handoff Notes to Next Phase

**Invariants established:**
- `GetDeviceStorageUseCase` is a pure domain function returning `DeviceStorageState` synchronously.
- `SettingsViewModel.deviceStorage` is a `StateFlow<DeviceStorageState>` that subscribers can observe.
- `SettingsViewModel.refreshDeviceStorage()` triggers an async re-query.
- All errors are wrapped safely — no exceptions escape to the caller.

**Next phase (Phase 02):**
- Integrate Fragment to observe `viewModel.deviceStorage` and render values into UI elements (not yet created).
- On button click, call `viewModel.refreshDeviceStorage()`.

---

## Rollback Plan

Revert commits that introduced `DeviceStorageState.kt`, `GetDeviceStorageUseCase.kt`, and changes to `SettingsViewModel.kt`. No data migration or user-facing surface changed.
