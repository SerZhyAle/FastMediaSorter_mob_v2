# Phase 02 - Documentation and closure

**Strategic spec:** [`../S1922_layout-hardcoded-dimension-literals.md`](../S1922_layout-hardcoded-dimension-literals.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

The ratchet is written down where the other layout rules are documented, and the ticket closes through the facade.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 15 added |

---

## Steps

### Step 02.1 - Document the ratchet

**Files:** `docs/DEV_OPS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a short subsection beside the other ratchet gates describing `layout-hardcoded-dimens`: what it counts, that it spans all five layout directories, that `0dp` is excluded and why, how the baseline is lowered after a migration, and that no campaign is scheduled. State the migration model in one sentence - literals convert when another ticket touches the file.

**Why:**

Strategic ADR-1 chose a model whose whole behaviour is "nothing happens on a schedule", and a gate that only ever appears as a refusal, with no written model behind it, reads as an arbitrary obstacle to whoever first trips it.

**Verification:**

- `Grep` - `layout-hardcoded-dimens` appears in `docs/DEV_OPS.md`.
- `Grep` - that subsection mentions `0dp`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1922 step 02.1

---

### Step 02.2 - Close the ticket through the facade

**Files:** the whole changed set of this ticket
**Depends on:** Step 02.1

**Prompt for developer:**

> Close with `scripts/post-change.ps1 -Files "<whole set>" -ScopeToFile -ChangeType Tooling`, naming every file this ticket changed.

**Why:**

CLAUDE.md section 12 requires the whole changed set to be named on a dirty tree, because naming one file while changing several certifies only the one named.

**Verification:**

- Run: the closure command - expected `post-change: PASS`, exit 0.
- `Grep` - `dev/CHANGELOG.md` carries a row for this ticket.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1922 step 02.2

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the documentation edit - no executable behaviour is introduced by this phase.
