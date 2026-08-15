# Phase 05 - Dead code removal

**Strategic spec:** [`../S0384_unify-scrollable-text-dialogs.md`](../S0384_unify-scrollable-text-dialogs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 2

---

## Objective

Remove the orphaned `dialog_log_view` layout and all auto-strings orphaned by this spec, in all three locales, after re-confirming zero references.

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| `res/layout/dialog_log_view.xml` + `layout-land/dialog_log_view.xml` | Deleted | 0 references |
| `res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` | Modified | remove orphaned auto-strings |

---

## Steps

### Step 05.1 - Re-confirm zero references, then delete dead layout + orphan strings

**Prompt for developer:**

> Re-run reference checks immediately before deleting (Strict Rule mitigation): `dialog_log_view`, `tvLogText`, `dialog_log_view_tvLogText_`, and any `dialog_scrollable_text_*` / `dialog_scheduled_log_*` auto-string keys. Delete `dialog_log_view.xml` (+land). From all three `strings.xml` remove the orphaned auto-string keys: `dialog_log_view_tvLogText_breakStrategy`, `dialog_log_view_tvLogText_lineSpacingMultiplier`, plus any `dialog_scheduled_log_*` keys orphaned by Phase 04 (confirm zero references first). Do not remove strings still referenced by live code/layouts (e.g. `scheduled_ops_log_title`, `scheduled_ops_log_clear`, `scheduled_ops_log_empty` remain in use via the unified call).

**Verification:**

- `Glob` - `dialog_log_view.xml` absent (both orientations).
- `Grep` - `dialog_log_view_tvLogText_` returns zero hits across all `strings*.xml`.
- `Grep` - removed keys have zero remaining references in `app_v2/src`.

**Status:** `[ ]` not done

---

### Step 05.2 - Build + locale parity

**Prompt for developer:** Build `standard debug`. Confirm EN/RU/UK string parity for the touched files.

**Verification:**

- `.\a.ps1 dq` - BUILD SUCCESSFUL.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "dialog_"` - exit 0 (no orphaned/missing parity introduced).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] All steps `[x]`; `standard debug` green; locale parity holds.

---

## Handoff Notes to Next Phase

All duplicate and orphaned dialog artefacts removed. Only `ScrollableTextDialog` + `dialog_error_detail` remain for free-text dialogs.
