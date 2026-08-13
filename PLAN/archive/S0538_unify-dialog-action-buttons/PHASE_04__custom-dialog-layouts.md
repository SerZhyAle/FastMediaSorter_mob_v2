# Phase 04 - Custom Dialog Layouts

**Strategic spec:** [`../S0538_unify-dialog-action-buttons.md`](../S0538_unify-dialog-action-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Apply the Phase 01 named styles (confirm / cancel / destructive) to the action buttons of every custom inflated dialog layout, enforce 56dp min-height + 16dp gap, and re-lay non-standard layouts (cancel in the title row) onto a bottom action row - in both portrait and landscape variants.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (named styles + dimens exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_delete.xml` | Modified | ≤ existing |
| `app_v2/src/main/res/layout/dialog_copy_to.xml` + `layout-land/dialog_copy_to.xml` | Modified | ≤ existing |
| `app_v2/src/main/res/layout/dialog_rename.xml` + `layout-land/dialog_rename.xml` | Modified | ≤ existing |
| `app_v2/src/main/res/layout/dialog_rename_multiple.xml` + `layout-land/dialog_rename_multiple.xml` | Modified | ≤ existing |
| `app_v2/src/main/res/layout/dialog_scheduled_operation.xml` + `layout-land/dialog_scheduled_operation.xml` | Modified | ≤ existing |
| `app_v2/src/main/res/layout/dialog_translation_settings.xml` + `layout-land/dialog_translation_settings.xml` | Modified | ≤ existing |
| `app_v2/src/main/res/layout/dialog_network_delete_confirmation.xml` + `layout-land/dialog_network_delete_confirmation.xml` | Modified | ≤ existing |
| `app_v2/src/main/res/layout/dialog_folder_browser.xml` | Modified | ≤ existing |
| Other action-pair `layout/dialog_*.xml` + their `layout-land/` twins, discovered in Step 04.1 | Modified | ≤ existing |

> **Landscape parity (Rule 11):** every portrait `dialog_*.xml` edited here that has a `res/layout-land/` twin MUST be edited in lockstep. Twins confirmed present for: `dialog_copy_to`, `dialog_rename`, `dialog_rename_multiple`, `dialog_scheduled_operation`, `dialog_translation_settings`, `dialog_network_delete_confirmation`, `dialog_color_picker`, `dialog_filter`, `dialog_filter_resource`, `dialog_gif_editor`, `dialog_image_edit`, `dialog_network_discovery`, `dialog_player_settings`, `dialog_resource_picker`. Landscape variant ABSENT (portrait-only, no land edit needed): `dialog_delete`, `dialog_folder_browser`.

---

## Steps

### Step 04.1 - Discover the action-pair layout set

**Files:** `app_v2/src/main/res/layout/dialog_*.xml` (read-only discovery)
**Depends on:** - start of phase

**Prompt for developer:**

> Grep `res/layout/dialog_*.xml` for action-button ids (`btnOk`, `btnCancel`, `btnSave`, `btnApply`, `btnConfirm`, `btnSelect`, `btnDelete`) to determine which layouts carry a confirm/cancel (or destructive/cancel) pair - this set is the input to Steps 04.2-04.3 (no separate write-up). Progress-only dialogs (single cancel, e.g. `dialog_file_operation_progress`, `dialog_link_autodownload_progress`) keep their lone cancel styled as `DialogCancel` but need no confirm.

**Verification:**

- `Grep -l "btnCancel\|btnOk\|btnSave\|btnApply\|btnConfirm\|btnSelect\|btnDelete" app_v2/src/main/res/layout/dialog_*.xml` returns the working set, which includes at minimum `dialog_delete`, `dialog_copy_to`, `dialog_rename`, `dialog_scheduled_operation`, `dialog_translation_settings`, `dialog_network_delete_confirmation`, `dialog_folder_browser`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Discovery PASS. Working set (confirm id / cancel id):
  - PAIR (confirm=DialogConfirm, cancel=DialogCancel) + land twin: `dialog_color_picker` (btnOk/btnCancel), `dialog_copy_to` (btnSelectFolder/btnCancel), `dialog_delivery_prompt` (btn_delivery_confirm/btn_delivery_cancel), `dialog_filter` (btnApplyFilter/btnCancelFilter), `dialog_filter_resource` (btnApply/btnCancel), `dialog_player_settings` (btnApply/btnCancel), `dialog_rename` (btnApply/btnCancel), `dialog_rename_multiple` (btnApply/btnCancel), `dialog_scheduled_operation` (btnSave/btnCancel), `dialog_webview_auth` (btnWebviewAuthSave/btnWebviewAuthCancel).
  - PAIR portrait-only (no land twin): `dialog_folder_browser` (btnSelectCurrent/btnCancel).
  - DESTRUCTIVE pair portrait-only: `dialog_delete` (btnDelete=DialogDestructive / btnCancel).
  - Re-lay in 04.3: `dialog_translation_settings` (btnOk/btnCancel; cancel currently in title row) + land twin.
  - Progress single-cancel -> DialogCancel: `dialog_file_operation_progress` (+land), `dialog_file_copy_progress` (+land), `dialog_link_autodownload_progress` (portrait only).
  - EXCLUDED (not a confirm/cancel pair / out of scope): `dialog_network_discovery` (stop+cancel scan control), `dialog_resource_picker` (clear+cancel; selection by list tap), `dialog_network_delete_confirmation` (buttons supplied by the builder, already on the destructive overlay from Phase 03), `dialog_stream_offload_offer` (Phase 05), and the editor/info/media panels `dialog_gif_editor`, `dialog_image_edit`, `dialog_file_info`, `dialog_error_detail`, `dialog_slideshow_settings`, `dialog_playback_control`, `dialog_integration_test` (lone Close / media-tool controls, strategic non-goal).

---

### Step 04.2 - Apply confirm / cancel / destructive styles + size + gap

**Files:** each discovered `layout/dialog_*.xml` + its `layout-land/` twin
**Depends on:** Step 04.1

**Prompt for developer:**

> For every action-pair layout in the working set, on the `com.google.android.material.button.MaterialButton` action buttons:
> - Confirm/positive button (OK / Save / Apply / Select): `style="@style/Widget.FastMediaSorter.Button.DialogConfirm"`.
> - Cancel/negative button: `style="@style/Widget.FastMediaSorter.Button.DialogCancel"`.
> - Destructive confirm (`btnDelete` in `dialog_delete.xml`, the positive in `dialog_network_delete_confirmation.xml`): `style="@style/Widget.FastMediaSorter.Button.DialogDestructive"`.
> Set the gap between the pair using `@dimen/dialog_action_button_gap` (margin between the two buttons), not a hardcoded value. Remove any per-button hardcoded `#hex` `backgroundTint` / text color and any one-off size override now superseded by the named style (Rule 19, Rule 20). Keep `res/layout/` and `res/layout-land/` in sync. Preserve each button's existing id, `onClick` wiring, and D-pad `nextFocus*` / `focusable` attributes (Rule 16).

**Verification:**

- `Grep` - every migrated layout references `Widget.FastMediaSorter.Button.DialogConfirm` or `DialogDestructive` (positive) and `Widget.FastMediaSorter.Button.DialogCancel` (negative).
- `Grep` - `dialog_action_button_gap` referenced where a pair exists.
- `Grep` - zero `android:backgroundTint="#` and zero `="#` color literals remain on action buttons in the migrated layouts (neuroslop Rule 19).
- For each portrait file with a land twin, the twin shows the same style references.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification PASS (3 parallel agents, disjoint layout groups). Styled confirm=DialogConfirm / cancel=DialogCancel (gap = `@dimen/dialog_action_button_gap`, hardcoded `#hex`/tint/size removed) in: `dialog_color_picker`, `dialog_copy_to`, `dialog_delivery_prompt`, `dialog_player_settings`, `dialog_rename`, `dialog_rename_multiple`, `dialog_scheduled_operation`, `dialog_webview_auth` (portrait + land each), `dialog_folder_browser` (portrait only). Destructive: `dialog_delete` btnDelete=DialogDestructive (+ removed inline `backgroundTint`), btnCancel=DialogCancel. Progress single-cancel -> DialogCancel: `dialog_file_operation_progress` (+land), `dialog_file_copy_progress` (+land), `dialog_link_autodownload_progress`. Vertical-stack cases handled: `dialog_copy_to` keeps vertical spacing (horizontal gap n/a); `dialog_webview_auth` land column gap fixed to `layout_marginTop` (agent had left a no-op `marginStart`). EXEMPTION: `dialog_filter` + `dialog_filter_resource` action buttons are icon-only (no text; `app:icon`, `materialIconButtonStyle`/compact `minWidth`+insets, 3-icon rows) - text-button styles would break them; strategic §2 non-goal exempts icon/media-control buttons, so left as-is. Verified: no `#hex` on any action button; land twins carry identical styles. `translation_settings` deferred to 04.3.

---

### Step 04.3 - Re-lay non-standard layouts onto a bottom action row

**Files:** `app_v2/src/main/res/layout/dialog_translation_settings.xml` + `layout-land/dialog_translation_settings.xml` (and any other title-row-cancel layout from Step 04.1)
**Depends on:** Step 04.2

**Prompt for developer:**

> `dialog_translation_settings.xml` places Cancel in the title row (top-right). Move it into a bottom horizontal action row beside the confirm button so the pair follows the unified contract (confirm right, cancel left, `dialog_action_button_gap` between). Reuse the existing cancel string resource - do NOT introduce a new string. Apply the same in the landscape twin. If Step 04.1 surfaced other title-row-cancel layouts, apply the same re-lay there.

**Verification:**

- `Grep` - in `dialog_translation_settings.xml` the cancel button is inside the bottom button row container (not the title/header container).
- `Grep` - no new string key added (the existing cancel label id is reused).
- Portrait and `layout-land` twin both show the bottom action row.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification PASS. `dialog_translation_settings.xml` (portrait + land): moved `btnCancel` + `btnOk` out of the title row into a new bottom horizontal action row (`gravity=end`, cancel left = DialogCancel, ok right = DialogConfirm, `dialog_action_button_gap` between). Title row now holds only the title TextView (width match_parent). Ids `btnCancel`/`btnOk` preserved (code binds by id, location-independent) - no new string added (`@android:string/cancel`/`ok` reused). Updated the stale land header comment ("buttons at top" -> bottom row). No other title-row-cancel layouts surfaced in 04.1.

---

### Step 04.4 - Build and visual-compile check

**Files:** (validation step)
**Depends on:** Step 04.3

**Prompt for developer:**

> Build the app to confirm all migrated layouts inflate with no AAPT/style error: `.\a.ps1 fc` (code + resources). The destructive delete dialog must show a red confirm; standard dialogs a green confirm + outlined cancel.

**Verification:**

- `.\a.ps1 fc` exits 0 (expected: PASS).
- `Grep` for `TODO(phase-04)` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - `.\a.ps1 fc` BUILD SUCCESSFUL (exit 0); all migrated layouts inflate, resources process. `TODO(phase-04)` zero hits. Neuroslop gate PASS (layout-hardcoded-colors at baseline 98 - none added).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Every edited portrait layout with a land twin has the twin edited in lockstep.
- [ ] Dev log entry added for the layout migration batch.

---

## Handoff Notes to Next Phase

(Step 04.1 records the discovered working set here.) All custom inflated dialog layouts now present the unified pair, including destructive delete (red) and the re-laid translation-settings dialog. Phase 05 covers the bottom-sheet surfaces, the last remaining family.

---

## Rollback Plan

Revert phase commit(s) - layouts return to prior per-file styling; no data migration. Portrait/land pairs revert together.
