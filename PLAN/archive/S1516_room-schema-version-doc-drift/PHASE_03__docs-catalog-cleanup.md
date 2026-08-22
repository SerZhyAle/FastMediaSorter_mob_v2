# Phase 03 - Docs catalog cleanup

**Strategic spec:** [`../S1516_room-schema-version-doc-drift.md`](../S1516_room-schema-version-doc-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none - final phase
**Steps done:** 1 / 1
**Started:** -
**Completed:** 2026-08-14

---

## Objective

Document the expanded Room pin contract so future contributors include every current reference.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/README.md` | Modified | ≤ 30 |

---

## Steps

### Step 03.1 - Document the current-reference Room pin contract

**Files:** `scripts/doc-drift/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the drift-checker documentation to distinguish the two current Room schema references from historical snapshots and state that both are required pin targets.

**Why:**

The manifest is extensible, so contributors need an explicit rule that prevents historical baselines from being treated as live pins.

**Verification:**

- `Grep` - `docs/DEV_OPS.md` occurs in `scripts/doc-drift/README.md`.
- `Grep` - `historical snapshot` occurs in `scripts/doc-drift/README.md`.
- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin room-schema-version` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - README distinguishes the two required current Room references from historical snapshots.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits in the files listed under Files Touched.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no runtime behavior or persistent data changes.
