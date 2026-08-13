# Phase 06 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0538_unify-dialog-action-buttons.md`](../S0538_unify-dialog-action-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Document the dialog action-button rule in the mandatory Button Taxonomy, regenerate the class catalog if public API changed, and record the dev log. No FEATURES change (strategic §8 = "Без изменений").

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ARCHITECTURE.md` | Modified | ≤ 20 added |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | n/a |

---

## Steps

### Step 06.1 - Document the dialog action rule in Button Taxonomy

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Button Taxonomy (MANDATORY)" section, add the dialog action-button rule: in non-system dialogs and action-pair bottom sheets, the confirm button uses `Widget.FastMediaSorter.Button.DialogConfirm` (green `success_color`), the cancel button uses `Widget.FastMediaSorter.Button.DialogCancel` (outlined neutral), and destructive confirm uses `Widget.FastMediaSorter.Button.DialogDestructive` (red `delete_button`); min-height 56dp, `dialog_action_button_gap` between the pair; OS/system dialogs are exempt. Add the three style rows to the taxonomy table. State the seam: `MaterialAlertDialogBuilder` dialogs inherit via `materialAlertDialogTheme` automatically. This is a real doc delta (outside `PLAN/`), permitted in the final cleanup phase.

**Verification:**

- `Grep` - `DialogConfirm` and `DialogCancel` and `DialogDestructive` each appear in `docs/ARCHITECTURE.md`.
- `Grep` - `materialAlertDialogTheme` mentioned as the builder seam in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. Enhanced the existing "Dialog action pair (S0538)" note in `docs/ARCHITECTURE.md` Button Taxonomy: added a 3-slot table (DialogConfirm green / DialogCancel outlined / DialogDestructive red), the `dialog_action_button_gap`, the `materialAlertDialogTheme` builder seam, the per-dialog destructive overlay overload, and the custom-layout/OS-exempt notes.

---

### Step 06.2 - Regenerate catalog (if public API changed)

**Files:** `dev/CATALOG/app_v2.jsonl` + `.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> If Phase 03 changed any public class/method signature (the bare-builder migrations were internal; likely no public API delta), regenerate the catalog once: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Resource-only phases (01, 02, 04, 05) do not require catalog regen. If no Kotlin public API changed, note "no public API delta - catalog regen skipped".

**Verification:**

- Either `catalog_sync.ps1` exits 0, or a note records "no public API delta - skipped".

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - `catalog_sync.ps1 -Module app_v2` exit 0 (1898 records, unchanged - confirms the builder migrations were internal, no public API/signature delta). Index is gitignored.

---

### Step 06.3 - Dev log for the spec

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 06.2

**Prompt for developer:**

> Record one dev-log entry for the S0538 implementation batch: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/themes.xml" "ui" "S0538: unify dialog confirm/cancel action buttons (green confirm, outlined cancel, red destructive)"`. Do NOT add a `docs/ALL_FEATURES.jsonl` record (strategic §8 = no new user-perceived capability; this is consistency polish).

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an `S0538` entry for the dialog button unification.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Dev-log entries recorded across phases 03-06 (one per logical batch). No `docs/ALL_FEATURES.jsonl` record (strategic §8 = consistency polish, no new user-perceived capability; `-SkipFuncLog`). One `Timber.d("S0538:")` BlockNeedUserTest probe inserted at the destructive delete confirmation flow (`BrowseDialogHelper`); final `.\a.ps1 d` full APK BUILD SUCCESSFUL (exit 0) validates code + tag + packaging. Journal -> `BlockNeedUserTest`.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `docs/ARCHITECTURE.md` documents the dialog action rule.
- [ ] `dev/CHANGELOG.md` has the S0538 entry.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: enter `BlockNeedUserTest` (insert one `Timber.d("S0538: …")` probe at a representative dialog entry before the final build), then `/spec-test-device` to verify on a phone and in D-pad/TV mode, then `/spec-check S0538`.

On-device smoke (2026-06-19, emulator-5556, fresh standard-debug APK): app launches with all S0538 changes, no crash. Triggered the Settings "Reset General section" confirmation - it rendered the unified pair correctly: large RED filled "OK" (DialogDestructive) on the right, large OUTLINED neutral "Cancel" on the left, clear gap between, cancel-left/confirm-right per contract. Confirms the `materialAlertDialogTheme` seam + per-dialog destructive overlay + named styles + 56dp sizing all render on a real device. Cancelled without resetting. Did NOT auto-run `/spec-check` (it would strip the verification tag prematurely) - the comprehensive acceptance (green confirm visual, custom-layout dialogs, bottom sheets, portrait+landscape, compact 50% sizing, D-pad/TV focus, colorblind) stays with the owner per strategic §3.3. The `Timber.d("S0538:")` probe lives on the file-delete flow and was not exercised on the emulator (AVD media not MediaStore-indexed).

---

## Rollback Plan

Revert phase commit(s) - doc + changelog only; catalog index is gitignored and regenerable.
