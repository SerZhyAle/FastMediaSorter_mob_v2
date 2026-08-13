# Phase 03 - Scheduled Manager

**Strategic spec:** [`../S0479_settings-operations-section-decomposition.md`](../S0479_settings-operations-section-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, 05
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Extract the scheduled-operations list (adapter, dialogs, log/clear, notification + battery permission flow, automate-on-intent, expand-on-intent, flavor gate) out of `OperationsSettingsFragment` into `OperationsScheduledManager`; scheduled stays embedded in the Operations tab (owner decision).

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Backup `OperationsSettingsFragment.kt` (>500 LOC) to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsScheduledManager.kt` | New | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 880 |

> No XML touched. Preserve the `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` gate verbatim - it is a pre-existing feature flag, not a new flavor guard.

---

## Steps

### Step 03.1 - Create `OperationsScheduledManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsScheduledManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `OperationsScheduledManager` taking `binding`, `viewModel: SettingsViewModel`, `scheduledViewModel: ScheduledOperationsViewModel`, `fragment: Fragment`, `mediaCapabilities: MediaCapabilities`, `notificationsPermissionLauncher: ActivityResultLauncher<String>` (the launcher stays registered in the fragment; pass it in), and `isUpdatingFromSettings: () -> Boolean`. Move into it: `setupScheduledSection()`, `showScheduledOperationDialog()`, `confirmDeleteScheduledOp()`, `updateScheduledNotificationPermissionButton()`, `checkAndRequestScheduledPermissions()`, `checkAndOpenAutomateDialog()`, `checkAndExpandScheduledSection()`, the `scheduledAdapter` field, and the `scheduledViewModel.operations` collect. Expose `fun setup()` (adapter + listeners + `collectOnLifecycle` of operations), `fun render(settings: Settings)` (the scheduled-toggle view-sync currently in `observeData`'s `withSettingsUpdate` block: `rowEnableScheduledOps` checked state + `containerScheduledContent` / `layoutScheduledActions` visibility), `fun onResume()` (calls `updateScheduledNotificationPermissionButton`), `fun checkAndExpandFromIntent()`, and `fun onResourcesReady()` (the `checkAndOpenAutomateDialog` trigger). Preserve every `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` / `enableScheduledOperations` guard verbatim. Keep the scheduled-toggle `setOnCheckedChangeListener` and its `containerScheduledContent` / `layoutScheduledActions` visibility behaviour. Reuse `ScheduledOperationsAdapter`, `ScheduledOperationDialog`, `ScrollableTextDialog` as-is. Lifecycle-safe collection only.

**Verification:**

- `Glob` - `OperationsScheduledManager.kt` exists.
- `Grep` - `class OperationsScheduledManager` matches exactly once.
- `Grep` - `fun setup()` and `fun render(` present in the new file.
- `Grep` - `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` present in the new file.
- `Grep -n "lifecycleScope.launch {[^}]*\.collect"` - zero hits in the new file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 5/5 PASS. Files: helpers/OperationsScheduledManager.kt (New, 226 LOC). render(settings) added for toggle view-sync; flavor gate preserved verbatim.

---

### Step 03.2 - Delegate from the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `private val scheduledManager by lazy { OperationsScheduledManager(binding, viewModel, scheduledViewModel, this, mediaCapabilities, notificationsPermissionLauncher) { isUpdatingFromSettings } }`. Replace `setupScheduledSection()` + `checkAndExpandScheduledSection()` calls in `onViewCreated` with `scheduledManager.setup()` + `scheduledManager.checkAndExpandFromIntent()`. Route `onResume()` to `scheduledManager.onResume()`. In `observeData`, call `scheduledManager.render(settings)` from the `withSettingsUpdate { }` block (replacing the three scheduled-toggle view-sync lines), and replace the resources-ready `checkAndOpenAutomateDialog()` call with `scheduledManager.onResourcesReady()`. Delete the migrated members from the fragment (the seven methods listed in 03.1 plus `scheduledAdapter` and the scheduled-toggle view-sync lines). Keep `notificationsPermissionLauncher` registered in the fragment but point its callback at `scheduledManager.onResume()` (or a dedicated `refreshNotificationButton()`).

**Verification:**

- `Grep` - `scheduledManager.setup()` present in the fragment.
- `Grep` - `private fun setupScheduledSection` returns zero hits in the fragment.
- `Grep` - `private fun checkAndOpenAutomateDialog` returns zero hits in the fragment.
- `Grep` - `private fun checkAndRequestScheduledPermissions` returns zero hits in the fragment.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. Fragment delegates setup/render/onResume/checkAndExpandFromIntent/onResourcesReady; removed 7 methods + scheduledAdapter + 8 unused imports. notificationsPermissionLauncher given explicit type to break lazy/launcher cycle.

---

### Step 03.3 - Compile

**Files:** -
**Depends on:** Step 03.2

**Prompt for developer:**

> Compile the touched area. Confirm the notification permission launcher callback still routes correctly.

**Verification:**

- `/build` (or `.\a.ps1 fk`) exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - `.\a.ps1 fk` BUILD SUCCESSFUL (after explicit-type fix for recursive inference). Neuroslop gate PASS.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog regeneration deferred to Phase 06.

---

## Handoff Notes to Next Phase

Scheduled flow fully owned by `OperationsScheduledManager`. The fragment retains only the `notificationsPermissionLauncher` registration (Fragment requirement) wired to the manager.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
