# Phase 03 - Catalog Guidance

**Strategic spec:** [`../S0270_workspace_noise_and_lookup_strategy.md`](../S0270_workspace_noise_and_lookup_strategy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Align `dev/CATALOG/README.md` with the new lookup split while preserving script ownership and JSONL write discipline.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6.3 research item is Resolved.
- [x] Strategic §6.5 research item is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/README.md` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Document semantic queries vs narrow exact-match reads

**Files:** `dev/CATALOG/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Rewrite the `When to use the catalogue` section so it explicitly distinguishes semantic queries handled by `query.ps1` from narrow exact-match lookups that may read `app_v2.jsonl` or `wear.jsonl` directly. Keep the wording short and operational; do not restate the entire strategic rationale.

**Verification:**

- `Grep` - `semantic` or `semantics` appears in `dev/CATALOG/README.md`.
- `Grep` - `exact-match` appears in `dev/CATALOG/README.md`.
- `Grep` - `query.ps1` appears in the updated usage guidance.
- `Grep` - `.jsonl` appears in the updated usage guidance.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: dev/CATALOG/README.md. Dev log recorded.

---

### Step 03.2 - Preserve script-only write ownership

**Files:** `dev/CATALOG/README.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add or refine a compact note that direct `.jsonl` reads are allowed for narrow lookup, but writes remain script-only through `scan.ps1`, `set.ps1`, or `remove.ps1`. Ensure the rule cannot be read as permission to hand-edit catalogue records.

**Verification:**

- `Grep` - `read` and `.jsonl` appear in the same paragraph or bullet.
- `Grep` - `write` or `writes` appears in the same paragraph or bullet.
- `Grep` - `scan.ps1` appears in the write-ownership guidance.
- `Grep` - `set.ps1` appears in the write-ownership guidance.
- `Grep` - `remove.ps1` appears in the write-ownership guidance.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 5/5 PASS. Files: dev/CATALOG/README.md. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `dev/CATALOG/README.md` still documents all five catalogue scripts.
- [x] `Grep` for `query.ps1`, `scan.ps1`, `set.ps1`, and `remove.ps1` in `dev/CATALOG/README.md` returns at least one hit each.
- [x] Dev log entry added for `dev/CATALOG/README.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The catalogue README now mirrors the repo-wide lookup rule and keeps manual-write ambiguity closed.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
