# Phase 03 - Builder Migration

**Strategic spec:** [`../S0538_unify-dialog-action-buttons.md`](../S0538_unify-dialog-action-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Migrate the remaining bare `AlertDialog.Builder(` constructor call-sites (which do not inherit the Phase 02 Material seam) to `MaterialAlertDialogBuilder`, and apply the red destructive style to builder-based delete confirmations.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (the Material seam is live).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| Discovered bare-builder call-sites (grep in Step 03.1) | Modified | per file ≤ existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/helpers/ResetConfirmationDialog.kt` | Modified | ≤ 40 |

> Discovery is grep-driven, not a hand-list: `MaterialAlertDialogBuilder` returns `androidx.appcompat.app.AlertDialog`, so an import of that type is NOT proof of a bare builder. Only `new`-style `AlertDialog.Builder(` / `android.app.AlertDialog.Builder(` constructors are in scope. `core/ui/DialogAccessibilityHelper.kt` references `AlertDialog` as a type for focus routing - it is not a builder call-site and is out of scope.

---

## Steps

### Step 03.1 - Discover and migrate bare builder constructors

**Files:** call-sites surfaced by grep (e.g. `ui/keybinding/helpers/ResetConfirmationDialog.kt`)
**Depends on:** - start of phase

**Prompt for developer:**

> Find every bare builder constructor in `app_v2/src/main`: grep for `AlertDialog.Builder(` and `android.app.AlertDialog.Builder(`, excluding `MaterialAlertDialogBuilder(`. For each that shows a positive/negative action pair, replace the bare `AlertDialog.Builder(context)` with `MaterialAlertDialogBuilder(context)` so it inherits the Phase 02 seam. Keep the same `setPositiveButton` / `setNegativeButton` calls and titles. Do not restyle buttons in code (`getButton(...).setTextSize(...)`) - the seam handles presentation; in-code restyling fights `DialogAccessibilityHelper` post-show wiring (research 02). Timber only if logging is touched.

**Verification:**

- `Grep -n "AlertDialog\.Builder(" app_v2/src/main` returns zero hits for action-pair dialogs (only non-builder type references remain).
- `Grep` - `MaterialAlertDialogBuilder` now present in each migrated file.
- `.\a.ps1 fk` exits 0 (Kotlin compiles).

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 3/3 PASS. Grep-driven discovery found far more bare builders than the research estimate (~5): 58 files carried `AlertDialog.Builder(`. Migrated every confirm/cancel PAIR call-site (both `setPositiveButton` + `setNegativeButton`) to `MaterialAlertDialogBuilder` via 5 parallel agents over disjoint file groups: 55 call-sites across ~40 files. Skipped (left bare, correctly) the non-pairs: single-button OK dialogs, selection-list (`setItems`/`setSingleChoiceItems` + cancel, no positive), and custom `setView` dialogs whose buttons live in the layout (handled in Phase 04). 33 bare `AlertDialog.Builder(` remain across 25 files - all verified non-pair. No framework→appcompat type breakage (Rule 5 caution applied; 0 manual cases). `.\a.ps1 fk` BUILD SUCCESSFUL (exit 0). Backups of >500 LOC files in `temp/S0538_phase03_backup/`. Dev log deferred to phase end (batch).

---

### Step 03.2 - Apply destructive style to builder-based delete confirmations

**Files:** builder call-sites that confirm a destructive action (surfaced by grep)
**Depends on:** Step 03.1

**Prompt for developer:**

> For builder dialogs whose positive action is destructive (delete / remove / clear), override the positive-button slot to the red destructive style instead of the seam's green confirm. Use the per-dialog theme overload `MaterialAlertDialogBuilder(context, R.style.…DialogDestructive overlay)` or set the positive button style for that dialog only - whichever the codebase already supports without post-show button hacks. Confirm vs destructive must stay distinguishable by more than color (label text states the destructive verb).

**Verification:**

- `Grep` - destructive builder dialogs reference `DialogDestructive` (via a per-dialog overlay) and not the default green path.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. Added `ThemeOverlay.FastMediaSorter.MaterialAlertDialog.Destructive` (red filled positive + outlined cancel, self-contained sibling of the base seam) to `themes.xml`. Applied it via the per-dialog `MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)` overload to 25 destructive builder dialogs across 18 files (delete/remove/clear/reset of user data, files, connections, cache, scheduled ops, keybindings, settings sections, extensions, accounts) - including pre-existing Material delete dialogs (DuplicatesFragment, StandaloneFileOperationsHandler, ResourceOpsMenuManager, PlayerDrawingSaveHelper, ExtensionsManagerFragment, AuthSessionsListFragment), not just the freshly-migrated ones. Removed two now-conflicting post-show recolor hacks (`getButton(BUTTON_POSITIVE).setTextColor(colorError)` in `BrowseDialogHelper.showNetworkDeleteConfirmation` and `ResourceOpsMenuManager.showDeleteBySizeConfirm`) - the red-filled button already carries white text, so the old red-on-red recolor would have made the label unreadable (Rule 20/21). Left 3 reversible cases green (sign-out, disable-remote-source toggle, exit-with-pending-queue). `.\a.ps1 fk` BUILD SUCCESSFUL (exit 0); neuroslop gate PASS (all dimensions at baseline).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk` minimum; `.\a.ps1 d` if packaging proof wanted).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in every modified file (Timber only).
- [ ] Dev log entry added for the migration batch.

---

## Handoff Notes to Next Phase

All builder dialogs (Material-native and migrated) now present the unified pair. Remaining surfaces are the custom inflated layouts (Phase 04) and bottom sheets (Phase 05), which do not go through the builder seam.

---

## Rollback Plan

Revert phase commit(s) - migrated dialogs return to bare builder styling; no data migration. Each call-site is independently revertable.
