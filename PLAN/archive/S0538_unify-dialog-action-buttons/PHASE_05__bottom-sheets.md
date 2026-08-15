# Phase 05 - Bottom Sheets

**Strategic spec:** [`../S0538_unify-dialog-action-buttons.md`](../S0538_unify-dialog-action-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Apply the unified confirm/cancel presentation to bottom-sheet surfaces that present an action pair (owner included them in scope, 2026-06-19).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (named styles + dimens exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionRationaleBottomSheet.kt` + its layout | Modified | ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamOffloadOfferDialog.kt` (`layout/dialog_stream_offload_offer.xml`) | Modified | ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToBottomSheet.kt` + its layout | Modified | ≤ existing |
| Other action-pair bottom sheets from Step 05.1 (`IconPickerBottomSheet`, `NowPlayingBottomSheetFragment`, `WearSyncSettingsFragment` only if they carry a confirm/cancel pair) | Modified | ≤ existing |

> Selection-only sheets (single tap-to-pick, no confirm/cancel pair) are exempt - they do not present the action pair the rule governs. Step 05.1 separates action-pair sheets from selection sheets.

---

## Steps

### Step 05.1 - Identify action-pair bottom sheets

**Files:** the 7 `BottomSheetDialogFragment` files + their layouts (read-only discovery)
**Depends on:** - start of phase

**Prompt for developer:**

> Inspect each `BottomSheetDialogFragment` (`PermissionRationaleBottomSheet`, `IconPickerBottomSheet`, `StreamOffloadOfferDialog`, `NowPlayingBottomSheetFragment`, `SendToBottomSheet`, `WearSyncSettingsFragment`, and `NowPlayingViewModel`'s sheet) and its inflated layout. Classify each as (a) action-pair (has a confirm + cancel / dismiss button pair) or (b) selection-only. Only (a) is the input to Step 05.2; selection-only sheets are exempt (no separate write-up).

**Verification:**

- `Grep` over each sheet's layout for a confirm+cancel button pair identifies the in-scope set; `StreamOffloadOfferDialog` / `dialog_stream_offload_offer.xml` is confirmed action-pair and present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Discovery PASS. IN-SCOPE action-pair sheets (no layout-land twin exists for either):
  - `PermissionRationaleBottomSheet` -> `bottom_sheet_permission_rationale.xml`: confirm = `btn_perm_grant`, cancel = `btn_perm_skip`.
  - `StreamOffloadOfferDialog` -> `dialog_stream_offload_offer.xml`: confirm = `offloadDownloadButton`, cancel = `offloadCancelButton`; `offloadTryAnywayButton` is a conditional risky-override secondary (stays low-emphasis Text, not part of the confirm/cancel pair).
  - EXEMPT: `SendToBottomSheet` (`sheet_send_to.xml` = receiver list, selection-only), `IconPickerBottomSheet` (icon grid, selection-only), `NowPlayingBottomSheetFragment` + `NowPlayingViewModel` sheet (media transport controls, strategic non-goal), `WearSyncSettingsFragment` (Jetpack Compose `material3` buttons + playback IconButtons - XML named styles do not apply to Compose; out of scope).

---

### Step 05.2 - Apply unified styles to action-pair sheets

**Files:** the in-scope bottom-sheet layouts + any code that styles their buttons
**Depends on:** Step 05.1

**Prompt for developer:**

> On the confirm and cancel `MaterialButton`s of each in-scope sheet apply `Widget.FastMediaSorter.Button.DialogConfirm` (or `DialogDestructive` if the action is destructive) and `Widget.FastMediaSorter.Button.DialogCancel`, with `@dimen/dialog_action_button_gap` between them. Reuse existing string labels - introduce no new string. Keep the sheet's existing layout structure and `nextFocus*` focus order (Rule 16). No hardcoded `#hex` on buttons (Rule 19). If a layout has a `layout-land/` twin, edit both (Rule 11).

**Verification:**

- `Grep` - each in-scope sheet layout references `DialogConfirm`/`DialogDestructive` + `DialogCancel`.
- `Grep` - no new string key added.
- `.\a.ps1 fc` exits 0 (expected: PASS).

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification PASS. `bottom_sheet_permission_rationale.xml`: btn_perm_grant -> DialogConfirm, btn_perm_skip -> DialogCancel (gap = `@dimen/dialog_action_button_gap` as the vertical-stack `marginTop`). `dialog_stream_offload_offer.xml`: offloadDownloadButton -> DialogConfirm (marginBottom -> gap dimen), offloadCancelButton -> DialogCancel; offloadTryAnywayButton left as low-emphasis Text (conditional risky-override, not part of the pair). No new strings, ids preserved. `.\a.ps1 fc` BUILD SUCCESSFUL (exit 0); neuroslop gate PASS; `TODO(phase-05)` zero hits.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in any modified `.kt`.
- [ ] Dev log entry added for the bottom-sheet batch.

---

## Handoff Notes to Next Phase

(Step 05.1 records the in-scope sheet list here.) All dialog families - builder, custom layout, bottom sheet - now present the unified action pair. Phase 06 finalizes docs (Button Taxonomy) and catalog.

---

## Rollback Plan

Revert phase commit(s) - sheets return to prior styling; no data migration.
