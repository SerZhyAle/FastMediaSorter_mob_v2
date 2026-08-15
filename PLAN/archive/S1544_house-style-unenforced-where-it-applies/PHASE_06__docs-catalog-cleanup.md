# Phase 06 - Docs catalog cleanup

**Strategic spec:** [`../S1544_house-style-unenforced-where-it-applies.md`](../S1544_house-style-unenforced-where-it-applies.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Regenerate the script cheatsheet so it stops naming five deleted scripts, record the loop the new tooling belongs to, and assert that the gate inventory did not grow.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/SCRIPT_CHEATSHEET.md` | Regenerated | - |
| `docs/DEV_OPS.md` | Modified | ≤ 20 delta |

---

## Steps

### Step 06.1 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the cheatsheet with `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` - never hand-edit it, it is a render target. Confirm the five deleted fixers are gone from it and `fix-house-style.ps1` is present.

**Why:**

Strategic §11 criterion 7 requires the cheatsheet not to name a deleted script, and it was the only place any of the five was ever referenced.

**Verification:**

- `Grep` - `fix-ellipsis` and `fix-yo` return zero hits in `docs/SCRIPT_CHEATSHEET.md`.
- `Grep` - `fix-house-style.ps1` present in `docs/SCRIPT_CHEATSHEET.md`.
- `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` - exit 0. Pass `-Gate`: without it the gate reports and still exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cheatsheet regenerated via help.ps1 -Generate: 323 scripts, zero fix-ellipsis/fix-yo hits, fix-house-style present, sync gate exit 0. DEV_OPS gained a HOUSE TEXT STYLE section naming the three consumers and recording why prose carries no gate. Gate inventory 63, unchanged from baseline.

---

### Step 06.2 - Document the applied style loop

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a short subsection stating where the house text style is now applied - the translation ingest and the authored string write path - that the rule set lives in one library, and that `fix-house-style.ps1` is the manual pass for documentation prose, which carries no gate. Name the measurement that justifies the absent gate: 134 of 137 documentation files were clean without one.

**Why:**

Strategic ADR-4 leaves documentation prose deliberately unenforced, and an undocumented deliberate absence reads as an oversight to the next reader and invites a gate this ticket exists to avoid.

**Verification:**

- `Grep` - the new subsection names `house-text-style.ps1`, `locale-bulk-import.ps1` and `fix-house-style.ps1`.
- `Grep` - it states that documentation prose carries no gate and why.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cheatsheet regenerated via help.ps1 -Generate: 323 scripts, zero fix-ellipsis/fix-yo hits, fix-house-style present, sync gate exit 0. DEV_OPS gained a HOUSE TEXT STYLE section naming the three consumers and recording why prose carries no gate. Gate inventory 63, unchanged from baseline.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] **Gate inventory did not grow:** `(Get-ChildItem scripts/quality -Filter 'assert-*.ps1').Count` returns **63**, the measured count at ticket start on 2026-08-14. Strategic §11 criterion 8 and ADR-1 both turn on this, because S1340 §5 forbade growing the inventory for cosmetics and that constraint shaped the whole approach.
- [x] `post-change.ps1` gained no new step entry in this ticket's diff.
- [x] `pwsh -NoProfile -File scripts/quality/assert-hook-inventory.ps1` - exit 0.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `docs/FEATURES*.md` untouched - strategic §8 mandates no entry.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Regenerate the cheatsheet from the restored script set and revert the `docs/DEV_OPS.md` delta.
