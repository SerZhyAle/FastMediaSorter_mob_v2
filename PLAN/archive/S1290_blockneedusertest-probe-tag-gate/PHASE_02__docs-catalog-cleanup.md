# Phase 02 - Docs catalog cleanup

**Strategic spec:** [`../S1290_blockneedusertest-probe-tag-gate.md`](../S1290_blockneedusertest-probe-tag-gate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Steps done:** 1 / 1
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Record the gate's second direction and its allow-list where the debug-probe rule is already documented, and regenerate the script cheatsheet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 15 delta |
| `docs/SCRIPT_CHEATSHEET.md` | Regenerated | - |

---

## Steps

### Step 02.1 - Document the second direction and regenerate the cheatsheet

**Files:** `docs/DEV_OPS.md`, `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** - start of phase

**Prompt for developer:**

> State, where the debug-probe rule is already described, that the gate now checks both directions and that an exception is an entry in `scripts/quality/blockneedusertest-probe-baseline.txt` carrying a reason - naming the one legitimate reason found by measurement, a ticket that changes tooling or documentation rather than Kotlin. Then regenerate the cheatsheet with `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` - never hand-edit it, it is a render target.

**Why:**

Strategic §2 goal 3 wants the discrepancy list usable for a batch audit, which a reader can only act on if the allow-list and its one legitimate reason are written down next to the rule they qualify.

**Verification:**

- `Grep` - `blockneedusertest-probe-baseline.txt` named in `docs/DEV_OPS.md`.
- `Grep` - the both-directions wording present.
- `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - DEV_OPS gained a DEBUG PROBE INVARIANT section naming both halves, the baseline file and the one legitimate exception reason; cheatsheet regenerated (323 scripts), sync gate exit 0.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `docs/FEATURES*.md` untouched - strategic §8 mandates no change.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the `docs/DEV_OPS.md` delta and regenerate the cheatsheet from the restored script set.
