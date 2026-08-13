# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0321_text-editor-calculator-integration.md`](../S0321_text-editor-calculator-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** final audit
**Steps done:** 3 / 3
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Close documentation, catalog, and build validation for the user-visible editor calculator integration.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or existing dirty files in the touched area are understood.
- [ ] Communication policy §6 checklist is applied to feature docs.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 260 |
| `docs/FEATURES_RU.md` | Modified | ≤ 260 |
| `docs/FEATURES_UK.md` | Modified | ≤ 260 |
| `PLAN/S0321_text-editor-calculator-integration.md` | Modified | ≤ 320 |
| `PLAN/S0321_text-editor-calculator-integration/INDEX.md` | Modified | ≤ 140 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 04.1 - Update trilingual feature inventory

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 03

**Prompt for developer:**

> Update the Text Editor section in all three feature inventory files with one concise bullet describing that the text editor can launch the embedded calculator from its menu, pass selected text as input, and insert the returned result back into the document. Preserve existing formatting and platform tags. Run the communication policy §6 checklist before marking done.

**Verification:**

- `Grep` - `embedded calculator` appears in `docs/FEATURES.md` under Text Editor.
- `Grep` - `калькулятор` appears in `docs/FEATURES_RU.md` under Text Editor.
- `Grep` - `калькулятор` appears in `docs/FEATURES_UK.md` under Text Editor.
- `Manual predicate` - Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 4/4 PASS. Expected: EN `embedded calculator`, RU `калькулятор`, UK `калькулятор`, Communication Policy §6 checklist applied | actual: all three docs updated under Text Editor with symmetric short bullets; checklist passed because no error/empty-state/legal/machine-readable text was changed.

---

### Step 04.2 - Regenerate catalog and record closure

**Files:** `PLAN/S0321_text-editor-calculator-integration.md`, `PLAN/S0321_text-editor-calculator-integration/INDEX.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Update the tactical index phase counters and strategic spec tactical link if needed. Do not set the strategic status to Verified from this step.

**Verification:**

- `Command` - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `PLAN/S0321_text-editor-calculator-integration/INDEX.md` appears in the strategic spec.
- `Grep` - `Phases:` appears in `INDEX.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 3/3 PASS. Expected: catalog sync exits 0, strategic spec links tactical INDEX, INDEX contains phase counter | actual: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exited 0, tactical link present, `Phases:` present.

---

### Step 04.3 - Final build and implementation status

**Files:** `PLAN/S0321_text-editor-calculator-integration.md`, `PLAN/S0321_text-editor-calculator-integration/INDEX.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run the repository build wrapper for the standard debug target. If it passes, mark all phases done, set INDEX status to Done, update the strategic spec status to Implemented, and update the spec catalog to Implemented. If on-device verification is required by the final audit, the next skill will move the ticket to `BlockNeedUserTest`; do not add debug tags in this step unless this skill explicitly moves to that status.

**Verification:**

- `Command` - repository standard debug build exits 0.
- `Grep` - `**Status:** Implemented` appears in the strategic spec.
- `Command` - `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0321 -Format json` reports `"status":"Implemented"`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 3/3 PASS. Expected: standard debug build exits 0, strategic spec status becomes Implemented, spec catalog reports Implemented | actual: build wrapper exited 0; strategic/spec-catalog status updated in closure.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Final standard debug build exits 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persisted setting changes.
