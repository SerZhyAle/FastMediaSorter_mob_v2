# Phase 02 — SettingsActivity Insets Fix

**Strategic spec:** [`../S0204_bugfix-toolbar-hidden-behind-statusbar.md`](../S0204_bugfix-toolbar-hidden-behind-statusbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Replace the bare `statusBar.top` read in `SettingsActivity.applyWindowInsets()` with `insets.getStatusBarHeightSafe(resources)`. The landscape height recalculation in `updateLandscapeToolbarHeight()` already reads `statusBarInsetPx` (which is stored from `applyWindowInsets`) — it becomes correct automatically once the stored value is fixed.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`getStatusBarHeightSafe` is available in utils).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 430 |

> File is 421 lines — no backup required (< 500 lines).
> No layout XML changes — landscape variant `res/layout-land/activity_settings.xml` is not modified.

---

## Steps

### Step 02.1 — Replace `statusBar.top` with safe utility in `applyWindowInsets()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** — start of phase (Phase 01 provides the utility)
**Step status:** `[x] done`

**Prompt for developer:**

> In `SettingsActivity.applyWindowInsets(insets: WindowInsetsCompat)`:
>
> 1. Add import: `import com.sza.fastmediasorter.utils.getStatusBarHeightSafe`
> 2. Replace the line that assigns `statusBarInsetPx`:
>    ```kotlin
>    // Before:
>    statusBarInsetPx = statusBar.top
>    // After:
>    statusBarInsetPx = insets.getStatusBarHeightSafe(resources)
>    ```
> 3. Replace the setPadding call that uses `statusBar.top` directly:
>    ```kotlin
>    // Before:
>    binding.toolbarContainer.setPadding(
>        binding.toolbarContainer.paddingLeft, statusBar.top,
>        binding.toolbarContainer.paddingRight, binding.toolbarContainer.paddingBottom
>    )
>    // After:
>    binding.toolbarContainer.setPadding(
>        binding.toolbarContainer.paddingLeft, statusBarInsetPx,
>        binding.toolbarContainer.paddingRight, binding.toolbarContainer.paddingBottom
>    )
>    ```
>
> No other changes. `updateLandscapeToolbarHeight()` already reads `statusBarInsetPx` — it is correct once the field is set via the safe utility.
>
> **Note:** The local `statusBar` variable in `applyWindowInsets` may still be used for nav bar insets (`navBar = insets.getInsets(...)`) — keep it. Only the `statusBar.top` usage is replaced.

**Verification:**

- `Grep` — `getStatusBarHeightSafe` matches at least once in `SettingsActivity.kt`.
- `Grep` — `statusBar.top` has zero occurrences in `SettingsActivity.kt` (all uses replaced).
- `Grep` — `safeStatusBarInsetPx = insets.getStatusBarHeightSafe(resources)` present in `SettingsActivity.kt`.
- `Grep -n "Log\.d\("` — zero hits in `SettingsActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS. `applyWindowInsets()` now reads `getStatusBarHeightSafe(resources)`, `statusBar.top` no longer appears in `SettingsActivity.kt`, and `Log.d(` remains absent. Narrow compile: `.\gradlew.bat :app_v2:compileNoLegalDebugKotlin` → `BUILD SUCCESSFUL`.

---

### Step 02.2 — Insert debug verification tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags" — spec S0204 will enter `BlockNeedUserTest` after all phases are implemented. Insert one `Timber.d` tag at the entry point of `applyWindowInsets()` to confirm it fires on the car head unit during on-device testing:
>
> ```kotlin
> private fun applyWindowInsets(insets: WindowInsetsCompat) {
>     Timber.d("S0204: SettingsActivity.applyWindowInsets statusBarSafe=${insets.getStatusBarHeightSafe(resources)}")
>     // … rest of function unchanged
> ```
>
> One tag only — at the entry of the function, not on every modified line.

**Verification:**

- `Grep` — `Timber.d("S0204: SettingsActivity.applyWindowInsets` matches exactly once in `SettingsActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. One `Timber.d("S0204: SettingsActivity.applyWindowInsets` tag is present for `BlockNeedUserTest` logcat probing.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `SettingsActivity.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`SettingsActivity` now stores the correct status bar height (with OEM fallback) in `statusBarInsetPx`. Both portrait padding and landscape height recalculation use this corrected value.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
