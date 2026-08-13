# Phase 03 — Secondary Activities Insets Fix

**Strategic spec:** [`../S0204_bugfix-toolbar-hidden-behind-statusbar.md`](../S0204_bugfix-toolbar-hidden-behind-statusbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Apply `getStatusBarHeightSafe` to the three remaining secondary activities/helpers that use bare `statusBar.top` to position their toolbar: `AddResourceFormManager`, `ResourceEditorActivity`, and `BrowseEdgeToEdgeHelper`. Each change is one-line in the existing insets listener.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`getStatusBarHeightSafe` is available in utils).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | ≤ 435 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt` | Modified | ≤ 112 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEdgeToEdgeHelper.kt` | Modified | ≤ 75 |

> All files are under 500 lines — no backups required.
> No layout XML changes in this phase.

---

## Steps

### Step 03.1 — Fix `AddResourceFormManager.applyEdgeToEdgeInsets()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt`
**Depends on:** — start of phase (Phase 01 provides utility)

**Prompt for developer:**

> In `AddResourceFormManager.applyEdgeToEdgeInsets()`, inside the `setOnApplyWindowInsetsListener` lambda:
>
> 1. Add import: `import com.sza.fastmediasorter.utils.getStatusBarHeightSafe`
> 2. Replace the toolbar padding line:
>    ```kotlin
>    // Before:
>    binding.toolbar.setPadding(
>        binding.toolbar.paddingLeft, statusBar.top,
>        binding.toolbar.paddingRight, binding.toolbar.paddingBottom
>    )
>    // After:
>    binding.toolbar.setPadding(
>        binding.toolbar.paddingLeft, insets.getStatusBarHeightSafe(activity.resources),
>        binding.toolbar.paddingRight, binding.toolbar.paddingBottom
>    )
>    ```
>
> The local `statusBar` variable (used for other insets reads) remains unchanged.
>
> Insert one debug verification tag at the entry of `applyEdgeToEdgeInsets()`:
> ```kotlin
> fun applyEdgeToEdgeInsets() {
>     Timber.d("S0204: AddResourceFormManager.applyEdgeToEdgeInsets entry")
>     // … rest unchanged
> ```
>
> Add import `import timber.log.Timber` if not already present.

**Verification:**

- `Grep` — `getStatusBarHeightSafe(activity.resources)` present in `AddResourceFormManager.kt`.
- `Grep` — `statusBar.top` has zero occurrences in `AddResourceFormManager.kt`.
- `Grep` — `Timber.d("S0204: AddResourceFormManager` matches exactly once.
- `Grep -n "Log\.d\("` — zero hits in `AddResourceFormManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS. `AddResourceFormManager` now uses `getStatusBarHeightSafe(activity.resources)`, has zero `statusBar.top` hits, exactly one `S0204` Timber tag, and zero `Log.d(` hits.

---

### Step 03.2 — Fix `ResourceEditorActivity.setupViews()` insets listener

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt`
**Depends on:** — start of phase (parallel with 03.1)

**Prompt for developer:**

> In `ResourceEditorActivity.setupViews()`, inside the `setOnApplyWindowInsetsListener` lambda on `binding.fragmentContainer`:
>
> 1. Add import: `import com.sza.fastmediasorter.utils.getStatusBarHeightSafe`
> 2. Replace the padding assignment:
>    ```kotlin
>    // Before:
>    val statusBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
>    val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
>    view.setPadding(0, statusBar.top, 0, navBar.bottom)
>    // After:
>    val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
>    view.setPadding(0, insets.getStatusBarHeightSafe(resources), 0, navBar.bottom)
>    ```
>    Remove the now-unused `statusBar` local variable line.
>
> Insert one debug verification tag at the start of the lambda body:
> ```kotlin
> ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { view, insets ->
>     Timber.d("S0204: ResourceEditorActivity insets statusBarSafe=${insets.getStatusBarHeightSafe(resources)}")
>     // … rest unchanged
> ```
>
> Add import `import timber.log.Timber` if not already present.

**Verification:**

- `Grep` — `getStatusBarHeightSafe(resources)` present in `ResourceEditorActivity.kt`.
- `Grep` — `statusBar.top` has zero occurrences in `ResourceEditorActivity.kt`.
- `Grep` — `Timber.d("S0204: ResourceEditorActivity` matches exactly once.
- `Grep -n "Log\.d\("` — zero hits in `ResourceEditorActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS. `ResourceEditorActivity` now pads from `getStatusBarHeightSafe(resources)`, the raw `statusBar.top` read is gone, the `S0204` Timber tag is present once, and `Log.d(` remains absent.

---

### Step 03.3 — Fix `BrowseEdgeToEdgeHelper.apply()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEdgeToEdgeHelper.kt`
**Depends on:** — start of phase (parallel with 03.1 and 03.2)

**Prompt for developer:**

> In `BrowseEdgeToEdgeHelper.apply()`, inside the `setOnApplyWindowInsetsListener` lambda:
>
> 1. Add import: `import com.sza.fastmediasorter.utils.getStatusBarHeightSafe`
> 2. Replace the `statusBar.top` usage in the `layoutControls` padding call:
>    ```kotlin
>    // Before:
>    val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
>    ...
>    binding.layoutControls.setPadding(
>        topBarOrigPaddingLeft,
>        topBarOrigPaddingTop + statusBar.top,
>        topBarOrigPaddingRight,
>        topBarOrigPaddingBottom
>    )
>    // After:
>    val statusBarHeight = insets.getStatusBarHeightSafe(binding.root.resources)
>    ...
>    binding.layoutControls.setPadding(
>        topBarOrigPaddingLeft,
>        topBarOrigPaddingTop + statusBarHeight,
>        topBarOrigPaddingRight,
>        topBarOrigPaddingBottom
>    )
>    ```
>
> Note: `binding.root.resources` provides `Resources` from inside the `object` scope where `activity` is not directly available. The `statusBar` local variable can be removed if it is only used for `statusBar.top`; keep it if also used for side insets (check the existing code — if `statusBar.left / statusBar.right` are also read, introduce `statusBarHeight` as an additional variable and keep `statusBar` for the side values).
>
> Insert one debug verification tag at the top of the `apply()` function body:
> ```kotlin
> fun apply(binding: ActivityBrowseBinding) {
>     Timber.d("S0204: BrowseEdgeToEdgeHelper.apply called")
>     // … rest unchanged
> ```
>
> Add import `import timber.log.Timber` if not already present.

**Verification:**

- `Grep` — `getStatusBarHeightSafe` present in `BrowseEdgeToEdgeHelper.kt`.
- `Grep` — `statusBar.top` has zero occurrences in `BrowseEdgeToEdgeHelper.kt`.
- `Grep` — `Timber.d("S0204: BrowseEdgeToEdgeHelper` matches exactly once.
- `Grep -n "Log\.d\("` — zero hits in `BrowseEdgeToEdgeHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS. `BrowseEdgeToEdgeHelper` now applies `getStatusBarHeightSafe(binding.root.resources)`, the raw `statusBar.top` usage is removed, one `S0204` Timber tag is present, and `Log.d(` remains absent.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for all three files via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

All five affected files (`ViewExtensions.kt`, `SettingsActivity.kt`, `AddResourceFormManager.kt`, `ResourceEditorActivity.kt`, `BrowseEdgeToEdgeHelper.kt`) now use the three-tier safe fallback for status bar height. Phase 04 runs final catalog sync and closes the ticket.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
