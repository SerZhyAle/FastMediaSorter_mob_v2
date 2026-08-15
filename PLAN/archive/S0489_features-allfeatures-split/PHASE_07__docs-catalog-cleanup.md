# Phase 07 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0489_features-allfeatures-split.md`](../S0489_features-allfeatures-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Finalize the change: ensure the docs map references the new inventory, the FEATURES showcase note is consistent across EN/RU/UK, and dev changelog covers every modified file.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/PROJECT_OPERATIONS_INDEX.md` | Modified | ≤ 15 |
| `docs/FEATURES.md`, `_RU.md`, `_UK.md` | Modified (note only) | ≤ 6 |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |

---

## Steps

### Step 07.1 - Reference the inventory in project index/docs map

**Files:** `dev/PROJECT_OPERATIONS_INDEX.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `docs/ALL_FEATURES.jsonl` (developer feature inventory, EN-only, source of truth) and the `scripts/all_features/` tooling to the project operations index / feature-to-path map, so future work routes through it. Note that `docs/FEATURES*` is the curated showcase and `dev/FUNCTIONALITY.log` is retired.

**Verification:**

- `Grep` - `ALL_FEATURES.jsonl` referenced in `dev/PROJECT_OPERATIONS_INDEX.md`.
- `Grep` - `dev/FUNCTIONALITY.log` marked retired (or removed from active routing) in the index.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Added ALL_FEATURES inventory + tooling to Research Routing in PROJECT_OPERATIONS_INDEX.md; noted FUNCTIONALITY.log retired and FEATURES as showcase.

---

### Step 07.2 - FEATURES trilingual showcase note consistency

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Confirm the strategic §8 FEATURES change is reflected: each FEATURES file states it is the curated showcase pointing to `docs/ALL_FEATURES.jsonl`. (Content pruning happened in Phase 04; this step only verifies/repairs the trilingual note parity, no further pruning.)

**Verification:**

- `Grep` - `ALL_FEATURES.jsonl` present in all three FEATURES files.
- `Grep -c "^## "` equal across the three files (section parity intact).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. ALL_FEATURES.jsonl pointer present in all three FEATURES files; section parity 18/18/18. Pruning was done in Phase 04; this step confirmed the trilingual note.

---

### Step 07.3 - Dev changelog closure

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Step 07.2

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry for every file created/modified across Phases 01-07 (schema, data files, `scripts/all_features/*`, the gate, post-change wiring, skills, CLAUDE.md, FEATURES trio, project index). Use `.\scripts\add_to_dev_log.ps1` for any missing entries. No `.kt` changed → catalog regeneration is not required.

**Verification:**

- `Grep` - `ALL_FEATURES` appears in `dev/CHANGELOG.md`.
- `Grep` - `scripts/all_features` appears in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. dev/CHANGELOG.md carries ALL_FEATURES (23 entries) and scripts/all_features (4) lines from per-step post-change runs. No .kt changed -> catalog regen not required.

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] `dev/CHANGELOG.md` covers every modified file.
- [ ] `dev/CATALOG` regeneration not required (no `.kt` change) - confirmed.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0489`.

---

## Rollback Plan

Revert the cleanup commit (docs/index notes only). No functional surface affected.
