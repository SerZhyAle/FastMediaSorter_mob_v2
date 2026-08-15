# Phase 05 - Gestures Manager

**Strategic spec:** [`../S0479_settings-operations-section-decomposition.md`](../S0479_settings-operations-section-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Extract the screen-gesture overlay subgroup (overlay enable + permission dialog, screenshot destination, three gesture-action pickers, clipboard toggle, accessibility-settings entry) out of `OperationsSettingsFragment` into `OperationsGesturesManager`. Capability stays runtime-gated by the injected `Set<ScreenGestureOverlayController>` (empty except noLegal) - the manager lives in `src/main`, no flavor source set.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Backup `OperationsSettingsFragment.kt` (>500 LOC) to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsGesturesManager.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 700 |

> No XML touched. The whole gestures card hides when the controller set is empty; preserve that gate.

---

## Steps

### Step 05.1 - Create `OperationsGesturesManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsGesturesManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `OperationsGesturesManager` taking `binding`, `viewModel: SettingsViewModel`, `fragment: Fragment`, `screenGestureControllers: Set<ScreenGestureOverlayController>`, `gestureActionPickerManager: ScreenshotGestureActionPickerManager`, `overlayPermissionLauncher: ActivityResultLauncher<Intent>` (stays registered in the fragment), `isUpdatingFromSettings: () -> Boolean`, and the shared `pickDestination` / `refreshLabel` callbacks. Move into it `setupSystemAppsSection()` and `showGesturePermissionDialog()`, plus a `fun render(settings: Settings)` for the gesture rows from `observeData` (overlay toggle, clipboard toggle, the three `tvScreenshotGestureAction*Value` labels via `gestureActionPickerManager.labelFor`, screenshot destination label). Preserve the empty-controller-set hide gate, the `ActivityNotFoundException` fallback to the educational dialog (S0449 ADR-1), and the dismiss-revert behaviour. Expose `fun setup()` and `fun render(settings)`. The overlay-permission granted-callback (currently in the fragment's `overlayPermissionLauncher`) should call back into the manager to enable the overlay and persist - expose `fun onOverlayPermissionResult()` for that.

**Verification:**

- `Glob` - `OperationsGesturesManager.kt` exists.
- `Grep` - `class OperationsGesturesManager` matches exactly once.
- `Grep` - `fun setup` and `fun render` and `fun onOverlayPermissionResult` present in the new file.
- `Grep` - `ActivityNotFoundException` present in the new file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. Files: helpers/OperationsGesturesManager.kt (New, 178 LOC). setup()/render(settings)/onOverlayPermissionResult(); empty-controller-set gate + S0449 ADR-1 fallback preserved.

---

### Step 05.2 - Delegate from the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add `private val gesturesManager by lazy { OperationsGesturesManager(binding, viewModel, this, screenGestureControllers, gestureActionPickerManager, overlayPermissionLauncher, { isUpdatingFromSettings }, ::showDestinationPicker, ::refreshDestinationLabel) }`. Replace `setupSystemAppsSection()` with `gesturesManager.setup()`, and call `gesturesManager.render(settings)` from the `withSettingsUpdate { }` block. Point the `overlayPermissionLauncher` callback at `gesturesManager.onOverlayPermissionResult()`. Delete `setupSystemAppsSection()` and `showGesturePermissionDialog()` and the gesture-row block from `observeData`. The `gestureActionPickerManager` field stays in the fragment (passed into the manager) or moves wholesale into it - either is acceptable as long as no duplication remains.

**Verification:**

- `Grep` - `gesturesManager.setup()` present in the fragment.
- `Grep` - `gesturesManager.render(` present in the fragment.
- `Grep` - `private fun setupSystemAppsSection` returns zero hits in the fragment.
- `Grep` - `private fun showGesturePermissionDialog` returns zero hits in the fragment.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. Fragment delegates gesturesManager.setup()/render()/onOverlayPermissionResult(); overlayPermissionLauncher given explicit type to break lazy/launcher cycle; removed 2 methods + unused ActivityNotFoundException/Timber imports.

---

### Step 05.3 - Compile

**Files:** -
**Depends on:** Step 05.2

**Prompt for developer:**

> Compile the touched area for both `standard` and `noLegal` (the gestures capability is live only on noLegal). Use `.\a.ps1 fk` for standard and confirm noLegal compiles in Phase 06's build matrix.

**Verification:**

- `/build` (or `.\a.ps1 fk`) exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - `.\a.ps1 fk` BUILD SUCCESSFUL (after clearing corrupted Kotlin incremental cache from accumulated daemons - not a code error). Neuroslop gate PASS. noLegal build deferred to Phase 06.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog regeneration deferred to Phase 06.

---

## Handoff Notes to Next Phase

All five subgroups extracted. The fragment now holds: lifecycle, `setupViews` row wiring for copy/move/safety/recipients/behaviour/other-features/system-apps non-capture rows, the shared `showDestinationPicker` / `refreshDestinationLabel`, `applyFlavorRestrictions`, the settings `withSettingsUpdate` block delegating to managers, and the three permission launchers. Phase 06 verifies the < 800 LOC target and the noLegal build.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
