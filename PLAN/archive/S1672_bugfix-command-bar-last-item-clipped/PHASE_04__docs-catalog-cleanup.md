# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1672_bugfix-command-bar-last-item-clipped.md`](../S1672_bugfix-command-bar-last-item-clipped.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-08-15

---

## Objective

Close the ticket mechanically: regenerate the class catalog for the two new classes and record the delivered fix in the capability inventory.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |

---

## Steps

### Step 04.1 - Regenerate the class catalog and set roles

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket, then set `role` and `status` for `MainCommandBarPlanner` and `MainCommandOverflowMenuManager` with `dev/CATALOG/scripts/set.ps1`. Neither class is flavor-specific, so no `-NoFlavors` hint applies.

**Why:**

CLAUDE.md's post-change contract requires the catalog to carry a role and status for every new class, and the catalog is the first lookup any later ticket makes for this screen.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*MainCommandBarPlanner*"` returns one record with a non-empty `role`.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*MainCommandOverflowMenuManager*"` returns one record with a non-empty `role`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Catalog synced by close-and-log (scan 2857 records + render); role and status=new set for MainCommandBarPlanner and MainCommandOverflowMenuManager. ALL_FEATURES record added by close-and-log -FuncOp CHANGE: main-screen.command-bar-overflows-into-the-three-dots-menu, flavors standard,noLegal,lite,photos,legacy,vr read off the source set (src/main, no BuildConfig gate). validate.ps1 exit 0, 720 records; grep S1672 in ALL_FEATURES = 1 hit.

---

### Step 04.2 - Record the capability fix

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one `FIX` record via `scripts/all_features/add.ps1` describing the shipped behaviour in English: the main-screen command bar now drops labels before it drops commands, hides only as many commands as the width shortfall requires, and offers each hidden command in the three-dots menu. Flavors come from the gate output, not from memory - the change lives in `src/main` and reaches all six.

**Why:**

CLAUDE.md's feature-inventory rule makes `docs/ALL_FEATURES.jsonl` the developer inventory of shipped capability, and a user-visible fix with no record there is invisible to the next release's showcase diff.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0; record `expected: 0 | actual: <code>`.
- `Grep` - `S1672` present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Catalog synced by close-and-log (scan 2857 records + render); role and status=new set for MainCommandBarPlanner and MainCommandOverflowMenuManager. ALL_FEATURES record added by close-and-log -FuncOp CHANGE: main-screen.command-bar-overflows-into-the-three-dots-menu, flavors standard,noLegal,lite,photos,legacy,vr read off the source set (src/main, no BuildConfig gate). validate.ps1 exit 0, 720 records; grep S1672 in ALL_FEATURES = 1 hit.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the ticket via `.\scripts\add_to_dev_log.ps1` (one entry per logical change, not per file).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit - regenerated indexes and one inventory row, no source and no user-facing surface changed.
