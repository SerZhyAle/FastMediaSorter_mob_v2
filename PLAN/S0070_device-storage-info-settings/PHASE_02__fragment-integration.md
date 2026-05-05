# Phase 02 — fragment-integration

**Strategic spec:** [`../S0070_device-storage-info-settings.md`](../S0070_device-storage-info-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Bind `GeneralSettingsFragment` to observe `SettingsViewModel.deviceStorage` StateFlow; update TextView elements (one for label, one for value) on state changes; wire button click to `viewModel.refreshDeviceStorage()`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (UseCase, ViewModel, StateFlow all present).
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` exists.
- [ ] Fragment has a binding reference to `fragment_settings_general.xml` (DataBinding or view binding already in use).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 600 |

---

## Steps

### Step 02.1 — Add TextViews for storage info to Fragment binding scope

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `GeneralSettingsFragment`, add references to two TextViews (they will be created in layout Phase 03):
> - `binding.textDeviceStorageLabel` — small label text.
> - `binding.textDeviceStorageValue` — the actual available storage value.
> - `binding.btnDeviceStorageRefresh` — the refresh button (ImageButton).
> 
> No functional code yet — just ensure the Fragment is aware of these views. (If using manual findViewById, add them to onViewCreated; if using dataBinding, they auto-resolve once layout includes them.)

**Verification:**

- `Grep` — `textDeviceStorageLabel`, `textDeviceStorageValue`, `btnDeviceStorageRefresh` are referenced in the Fragment (either via binding or findViewById).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS. Files: GeneralSettingsFragment.kt (+import). Dev log recorded.

---

### Step 02.2 — Observe `deviceStorage` StateFlow and update UI

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `onViewCreated` (after binding is set), subscribe to `viewModel.deviceStorage` using `collectOnLifecycle`:
> - `viewModel.deviceStorage.collectOnLifecycle(viewLifecycleOwner) { state -> ... }`.
> - On `DeviceStorageState.Success(availableGb)`, format and display it: e.g., `binding.textDeviceStorageValue.text = String.format("%.1f Гб", availableGb)`.
> - On `DeviceStorageState.Error(message)`, display the error message in the same TextView.
> - Use `collectOnLifecycle` (already in project utilities) for proper lifecycle binding.

**Verification:**

- `Grep` — `collectOnLifecycle(viewLifecycleOwner)` pattern found in onViewCreated.
- `Grep` — `viewModel.deviceStorage` observation code present.
- `Grep` — `is DeviceStorageState.Success` or `when (state)` pattern present.
- `Grep` — `textDeviceStorageValue.text = ` assignment found.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS. collectOnLifecycle + when-branch for Success/Error. Dev log recorded.

---

### Step 02.3 — Wire refresh button click to `viewModel.refreshDeviceStorage()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `onViewCreated`, set a click listener on `binding.btnDeviceStorageRefresh`:
> - On click, call `viewModel.refreshDeviceStorage()`.
> - That's it — the ViewModel will update `deviceStorage` StateFlow, which triggers the observer from Step 02.2.

**Verification:**

- `Grep` — `btnDeviceStorageRefresh.setOnClickListener` or similar pattern found.
- `Grep` — `viewModel.refreshDeviceStorage()` invoked inside the click handler.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS. btnDeviceStorageRefresh.setOnClickListener → refreshDeviceStorage(). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles: run `/build`.
- [ ] Fragment correctly observes StateFlow and updates TextViews on state change (testable with a temporary log message).
- [ ] Button click triggers `refreshDeviceStorage()` without crashing.
- [ ] `Grep -n "TODO(phase-02)"` returns zero hits.
- [ ] Dev log entries added:
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt" "feature" "Integrate device storage StateFlow and refresh button"`

---

## Handoff Notes to Next Phase

**Invariants established:**
- Fragment subscribes to `viewModel.deviceStorage` and renders state changes to TextViews.
- Button click wires to `viewModel.refreshDeviceStorage()`.
- UI is **not yet visible** — Phase 03 creates the layout elements.

**Next phase (Phase 03):**
- Add layout elements (`textDeviceStorageLabel`, `textDeviceStorageValue`, `btnDeviceStorageRefresh`) to `fragment_settings_general.xml`.
- Position them above the first CardView (Interface).
- Set sizes (8sp text, 18dp button).
- Use nul vertical padding.

---

## Rollback Plan

Revert changes to `GeneralSettingsFragment.kt`. The bindings added in Step 02.1 become dangling references until Phase 03 creates the layout elements — so Phase 03 is a hard dependency.
