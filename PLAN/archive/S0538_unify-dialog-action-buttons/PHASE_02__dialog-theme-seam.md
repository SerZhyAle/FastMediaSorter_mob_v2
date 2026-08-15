# Phase 02 - Compact Hook + Dialog Theme Seam

**Strategic spec:** [`../S0538_unify-dialog-action-buttons.md`](../S0538_unify-dialog-action-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Make the action-button rule live app-wide: apply the compact-dialog-buttons theme overlay once in `BaseActivity` (so every dialog follows the global "Compact elements" toggle), and wire a `materialAlertDialogTheme` overlay so every `MaterialAlertDialogBuilder` dialog inherits the confirm/cancel styles without per-call edits.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (styles, attrs, dimens, compact overlay exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLayoutModePrefs.kt` | Modified | ≤ 12 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified | ≤ 6 added |
| `app_v2/src/main/res/values/themes.xml` | Modified | ≤ 25 added |

> `PlayerLayoutModePrefs` is the project's synchronous mirror of `useCompactElements` (DataStore is async) and already exposes `applyControlsThemeOverlay(activity)`; this phase adds a sibling overlay-applier. `BaseActivity` already references `ui.*` helpers, so calling it introduces no new layering issue (research 03).

---

## Steps

### Step 02.1 - Add a compact-dialog-buttons overlay applier

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLayoutModePrefs.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Next to `applyControlsThemeOverlay(activity)`, add `fun applyCompactDialogButtonsOverlay(activity: Activity)` that applies the compact overlay only when compact is on: `if (isCompact(activity)) activity.theme.applyStyle(R.style.ThemeOverlay_FastMediaSorter_CompactDialogButtons, true)`. Keep it synchronous (reads the existing prefs mirror); no DataStore.

**Verification:**

- `Grep` - `fun applyCompactDialogButtonsOverlay` matches once in `PlayerLayoutModePrefs.kt`.
- `Grep` - `ThemeOverlay_FastMediaSorter_CompactDialogButtons` referenced there.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. `PlayerLayoutModePrefs.kt` +applyCompactDialogButtonsOverlay. Dev log recorded.

---

### Step 02.2 - Apply the overlay in BaseActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `BaseActivity.onCreate`, before `setContentView(binding.root)`, call `PlayerLayoutModePrefs.applyCompactDialogButtonsOverlay(this)` so the activity theme carries the compact dialog-button sizes when compact mode is on. This is one app-wide hook (every dialog-hosting activity extends `BaseActivity`). Do not change any other onCreate behavior.

**Verification:**

- `Grep` - `applyCompactDialogButtonsOverlay(this)` matches once in `BaseActivity.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `BaseActivity.kt` (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. `BaseActivity.kt` +import +applyCompactDialogButtonsOverlay(this) before setContentView. Dev log recorded.

---

### Step 02.3 - Define the MaterialAlertDialog theme overlay

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `ThemeOverlay.FastMediaSorter.MaterialAlertDialog` (parent `ThemeOverlay.Material3.MaterialAlertDialog`) mapping the button-bar slots to the Phase 01 styles:
> - `buttonBarPositiveButtonStyle` = `@style/Widget.FastMediaSorter.Button.DialogConfirm`
> - `buttonBarNegativeButtonStyle` = `@style/Widget.FastMediaSorter.Button.DialogCancel`
> - `buttonBarNeutralButtonStyle` = `@style/Widget.FastMediaSorter.Button.DialogCancel`
> Destructive (red) builder dialogs override the positive slot per-call in Phase 03 - the seam default stays green confirm.

**Verification:**

- `Grep` - `name="ThemeOverlay.FastMediaSorter.MaterialAlertDialog"` matches once.
- `Grep` - `buttonBarPositiveButtonStyle` + `DialogConfirm` co-occur; `buttonBarNegativeButtonStyle` + `DialogCancel` co-occur.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. `themes.xml` +ThemeOverlay.FastMediaSorter.MaterialAlertDialog seam. Dev log recorded.

---

### Step 02.4 - Attach the seam to the app theme

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `<item name="materialAlertDialogTheme">@style/ThemeOverlay.FastMediaSorter.MaterialAlertDialog</item>` to `Theme.FastMediaSorter.App`. Do not set a global `alertDialogTheme` (the seam is Material-only by design; bare builders migrate in Phase 03).

**Verification:**

- `Grep` - `materialAlertDialogTheme` present in `Theme.FastMediaSorter.App` with the overlay value.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 1/1 PASS. `themes.xml` materialAlertDialogTheme attached to Theme.FastMediaSorter.App. Dev log recorded.

---

### Step 02.5 - Build and verify compile

**Files:** (validation step)
**Depends on:** Step 02.4

**Prompt for developer:**

> Build to confirm the Kotlin hook compiles and the theme attrs resolve: `.\a.ps1 fc` (code + resources).

**Verification:**

- `.\a.ps1 fc` exits 0 (expected: PASS).
- `Grep` for `TODO(phase-02)` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - `.\a.ps1 fc` BUILD SUCCESSFUL (exit 0). Kotlin compiles, theme attrs resolve.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (`.\a.ps1 fc`).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `PlayerLayoutModePrefs.kt` + `BaseActivity.kt` + `themes.xml`.

---

## Handoff Notes to Next Phase

All `MaterialAlertDialogBuilder` dialogs inherit green confirm + outlined cancel, sized from `?attr/dialogActionButtonMinHeight`; in compact mode `BaseActivity` swaps that attr to the 50% value, so builder dialogs auto-shrink. Phase 03 migrates the remaining bare `AlertDialog.Builder(` constructors into this seam.

---

## Rollback Plan

Revert phase commit(s) - removing the overlay + seam reverts dialogs to the prior Material default; no data or layout change. The `BaseActivity` call is a single additive line.
