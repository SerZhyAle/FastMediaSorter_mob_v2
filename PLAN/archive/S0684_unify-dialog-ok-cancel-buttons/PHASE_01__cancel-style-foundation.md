# Phase 01 - Cancel Style Foundation

**Strategic spec:** [`../S0684_unify-dialog-ok-cancel-buttons.md`](../S0684_unify-dialog-ok-cancel-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Restyle the single shared `DialogCancel` style (soft-pink tonal, shorter) and widen `DialogConfirm`/`DialogDestructive` so that, with zero per-layout edits, every dialog (builder + custom) shows a green wide-tall confirm and a pink narrower-shorter cancel. Saturated red stays exclusive to destructive confirm.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6 research items are Resolved (they are - see INDEX Pre-Implementation Blockers).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/colors.xml` | Modified | +2 lines |
| `app_v2/src/main/res/values-night/colors.xml` | Modified | +2 lines |
| `app_v2/src/main/res/values/dimens.xml` | Modified | +2 lines |
| `app_v2/src/main/res/values/themes.xml` | Modified | ~ +4 lines |

> No `layout/*.xml` or `layout-land/*.xml` is touched in this phase: width/height/colour are driven entirely by the shared styles, so the change propagates to all custom layouts and builder dialogs at once (research 03). Landscape parity is therefore automatic - no land twin edits.

---

## Steps

### Step 01.1 - Add soft-pink cancel colors (day + night)

**Files:** `app_v2/src/main/res/values/colors.xml`, `app_v2/src/main/res/values-night/colors.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `values/colors.xml` (near the existing `confirm_button_bg`/`confirm_button_on` at lines ~173-174) add:
> `<color name="cancel_button_bg">#FFF4D9DE</color>` and `<color name="cancel_button_on">#FF5A1F2A</color>`, with a one-line comment noting it is the soft-pink tonal cancel, distinct from the saturated destructive red.
> In `values-night/colors.xml` (near `confirm_button_bg`/`confirm_button_on` at lines ~49-50) add the night pair: `cancel_button_bg` = `#FF5C3A43`, `cancel_button_on` = `#FFFFDCE4`.
> Exact hex values come from research 01 (contrast-verified: day 9.51:1, night 7.76:1; far from delete red). Do not invent other values.

**Verification:**

- `Grep` - `cancel_button_bg` and `cancel_button_on` each appear once in `values/colors.xml` and once in `values-night/colors.xml`.
- `Grep` - day `cancel_button_bg` = `#FFF4D9DE`, night = `#FF5C3A43`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - Verification 2/2 PASS. Added `cancel_button_bg`/`cancel_button_on` to `values/colors.xml` (#FFF4D9DE/#FF5A1F2A) and `values-night/colors.xml` (#FF5C3A43/#FFFFDCE4).

---

### Step 01.2 - Add cancel-height and confirm-width dimens

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Next to the existing dialog action dimens (`dialog_action_button_min_height`, `dialog_action_button_gap` at lines ~325-328) add:
> `<dimen name="dialog_cancel_button_min_height">48dp</dimen>` (shorter than the 56dp confirm; equals the touch-target floor - never lower) and
> `<dimen name="dialog_confirm_button_min_width">120dp</dimen>` (wide "under-finger" confirm; the cancel stays content-narrow).
> One-line comment tying both to S0684.

**Verification:**

- `Grep` - `dialog_cancel_button_min_height` = `48dp` and `dialog_confirm_button_min_width` = `120dp` each present once in `dimens.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - Verification 1/1 PASS. Added `dialog_cancel_button_min_height` (48dp) + `dialog_confirm_button_min_width` (120dp) to `values/dimens.xml`.

---

### Step 01.3 - Restyle DialogCancel; widen DialogConfirm + DialogDestructive

**Files:** `app_v2/src/main/res/values/themes.xml`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> In `values/themes.xml`:
> 1. Change `Widget.FastMediaSorter.Button.DialogCancel` (lines ~324-328): set `parent="Widget.FastMediaSorter.Button.Tonal"`; replace the outlined body with `backgroundTint=@color/cancel_button_bg`, `android:textColor=@color/cancel_button_on`, `android:minHeight=@dimen/dialog_cancel_button_min_height`. Remove the `strokeColor`/`colorOnSurface` outlined items (no longer an outlined button).
> 2. In `Widget.FastMediaSorter.Button.DialogConfirm` (lines ~311-316) add `<item name="android:minWidth">@dimen/dialog_confirm_button_min_width</item>`.
> 3. In `Widget.FastMediaSorter.Button.DialogDestructive` (lines ~318-322) add the same `android:minWidth` item.
> 4. Update the S0538 dialog-pair comment (lines ~307-308) to: confirm = green filled (wide), cancel = soft-pink error-container tonal (shorter + narrower), destructive = red filled. Keep colours via `@color/`/`?attr/` only - never a `#hex` literal in the style body (Rule 19).

**Verification:**

- `Grep` - `Widget.FastMediaSorter.Button.DialogCancel` style block contains `@color/cancel_button_bg`, `@color/cancel_button_on`, `@dimen/dialog_cancel_button_min_height`, and `parent="Widget.FastMediaSorter.Button.Tonal"`; contains no `strokeColor`.
- `Grep` - both `DialogConfirm` and `DialogDestructive` style blocks reference `@dimen/dialog_confirm_button_min_width`.
- `Grep` - zero new `="#` colour literals added inside any style body in `themes.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - Verification 3/3 PASS. `DialogCancel` parent -> Tonal, `cancel_button_bg`/`cancel_button_on`/`dialog_cancel_button_min_height`, stroke removed. `DialogConfirm` + `DialogDestructive` gained `dialog_confirm_button_min_width`. Comment updated. No `#hex` literals added.

---

### Step 01.4 - Build and resource-inflation check

**Files:** (validation step)
**Depends on:** Step 01.3

**Prompt for developer:**

> Build to confirm the styles resolve and all dialogs inflate with no AAPT/style error: `.\a.ps1 fc` (code + resources). The neuroslop layout-hardcoded-colour baseline must not rise (no `#hex` added to layouts; colours live in `colors.xml`).

**Verification:**

- `.\a.ps1 fc` exits 0 (record `expected: 0 | actual: <n>`).
- `Grep` for `TODO(phase-01)` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - `.\a.ps1 fc` BUILD SUCCESSFUL (expected: 0 | actual: 0). `TODO(phase-01)` zero hits. Neuroslop layout-hardcoded-colors gate PASS (no `#hex` added to layouts).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` exits 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the colors/dimens/themes batch via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The visual change is complete and single-sourced in `values/themes.xml` + `colors.xml` + `dimens.xml`. Builder dialogs (via `materialAlertDialogTheme`) and all custom layouts (direct style reference) now render a green wide-tall confirm and a pink shorter-narrower cancel; destructive confirm stays red. Phase 02 codifies this in docs + a mechanical gate so new dialogs cannot drift.

Note for the BlockNeedUserTest transition (handled by `/spec-dev`): this is a resource-only change with no altered Kotlin flow. If a debug probe is required for device traceability, place a single `Timber.d("S0684: <flow>")` at one representative custom-dialog show site (e.g. the rename or delete dialog manager) - not across layouts.

---

## Rollback Plan

Revert the phase commit(s) - `DialogCancel` returns to neutral outlined, confirm loses its min-width, the four new resources drop out. No data migration, no user-facing surface beyond button styling.
