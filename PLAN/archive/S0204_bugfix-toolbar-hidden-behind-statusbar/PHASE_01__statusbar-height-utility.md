# Phase 01 — Status Bar Height Safety Utility

**Strategic spec:** [`../S0204_bugfix-toolbar-hidden-behind-statusbar.md`](../S0204_bugfix-toolbar-hidden-behind-statusbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Add `WindowInsetsCompat.getStatusBarHeightSafe(resources)` extension to the shared utils package. This is the only source of truth for status bar height used by all subsequent phases — it replaces bare `statusBars().top` calls that return 0 on OEM Android 8.x car head units.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(none — first phase)*
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/ViewExtensions.kt` | Modified | ≤ 55 |

---

## Steps

### Step 01.1 — Add `getStatusBarHeightSafe` extension to ViewExtensions.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/utils/ViewExtensions.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following Kotlin extension function to `ViewExtensions.kt` (after the existing imports, before the first function body). The function returns the status bar height in pixels with a three-tier fallback to work around OEM Android 8.x builds that report `statusBars().top == 0`:
>
> ```kotlin
> import android.content.res.Resources
> import androidx.core.view.WindowInsetsCompat
>
> /**
>  * Returns the status bar height in pixels.
>  *
>  * Three-tier fallback for OEM Android 8.x (API 26/27) devices where
>  * WindowInsetsCompat.Type.statusBars() may report 0 despite a visible status bar:
>  * 1. Modern typed API (correct on API 30+ and well-behaved OEMs).
>  * 2. Deprecated systemWindowInsetTop (broader OEM compatibility on API 20–29).
>  * 3. System resource "status_bar_height" (always available, OEM-independent).
>  */
> @Suppress("DEPRECATION")
> fun WindowInsetsCompat.getStatusBarHeightSafe(resources: Resources): Int {
>     val fromType = getInsets(WindowInsetsCompat.Type.statusBars()).top
>     if (fromType > 0) return fromType
>     val fromSystemWindow = systemWindowInsetTop
>     if (fromSystemWindow > 0) return fromSystemWindow
>     val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
>     return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
> }
> ```
>
> Do not remove or modify any existing content in the file.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/utils/ViewExtensions.kt` exists.
- `Grep` — `fun WindowInsetsCompat.getStatusBarHeightSafe` matches exactly once in that file.
- `Grep` — `getIdentifier("status_bar_height"` present in that file (confirms fallback tier 3 is written).
- `Grep -n "Log\.d\("` — zero hits in `ViewExtensions.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: utils/ViewExtensions.kt (+22 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `ViewExtensions.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`WindowInsetsCompat.getStatusBarHeightSafe(resources)` is now importable from `com.sza.fastmediasorter.utils`. Phases 02 and 03 can run in parallel once this phase is ✅ Done.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
