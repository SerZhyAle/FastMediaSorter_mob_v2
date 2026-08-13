# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0269_post_change_ritual_unification.md`](../S0269_post_change_ritual_unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Synchronize the spec artifacts with the implemented dispatcher decisions and run the final static closure sweep before `/spec-check`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6.3 is Resolved.
- [x] Strategic §6.4 is Resolved.
- [x] Strategic §8 confirms the ticket is internal-only.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0269_post_change_ritual_unification.md` | Modified | ≤ 340 |
| `PLAN/S0269_post_change_ritual_unification/INDEX.md` | Modified | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 04.1 - Keep the strategic spec aligned with the implemented dispatcher decisions

**Files:** `PLAN/S0269_post_change_ritual_unification.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Re-read the strategic spec after prompt adoption and adjust it only where the implementation proved a concrete dispatcher choice. Keep the ticket internal-only, preserve the fixed six-value `ChangeType` contract, and keep the tactical link current.

**Verification:**

- `Grep` - `## 6. Research items - resolved` appears in `PLAN/S0269_post_change_ritual_unification.md`.
- `Grep` - `**Tactical plan:** ` appears in `PLAN/S0269_post_change_ritual_unification.md`.
- `Grep` - `docs/FEATURES*.md` returns zero hits in the change summary for this phase.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: PLAN/S0269_post_change_ritual_unification.md. Tactical link, resolved research section, and implemented status aligned. Dev log recorded.

---

### Step 04.2 - Run the final static closure sweep across active S0269 surfaces

**Files:** `PLAN/S0269_post_change_ritual_unification/INDEX.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Update the tactical index notes and counters after the final prompt changes, then run a static sweep across the active files touched by S0269 to confirm there is no remaining instruction to commit gitignored catalog indexes or to use raw `scan.ps1` + `render.ps1` as the normal closure path.

**Verification:**

- `Grep` - `Commit updated `dev/CATALOG/<module>.jsonl` + `<module>.md`` returns zero hits across `CLAUDE.md`, `.github/copilot-instructions.md`, `.github/agents/android-rd-specialist.agent.md`, `.claude/agents/android-rd-specialist.md`, `.github/prompts/quick.prompt.md`, `.claude/commands/quick.md`, `.github/prompts/spec-dev.prompt.md`, and `.claude/commands/spec-dev.md`.
- `Grep` - `scan.ps1` + `render.ps1` as a routine closure instruction returns zero hits across `.github/prompts/quick.prompt.md`, `.claude/commands/quick.md`, `.github/prompts/spec-dev.prompt.md`, and `.claude/commands/spec-dev.md`.
- `Grep` - `Status:` remains present in `PLAN/S0269_post_change_ritual_unification/INDEX.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: PLAN/S0269_post_change_ritual_unification/INDEX.md. Final static closure sweep passed on active S0269 surfaces. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Strategic and tactical S0269 files reflect the same dispatcher contract and closure surface.
- [x] Active prompt/rule files touched by this ticket no longer contain the stale catalog-commit guidance or raw scan/render closure path.
- [x] Dev log entry added for every file in "Files Touched" via `\.\scripts\add_to_dev_log.ps1`.
- [x] `/spec-check S0269` is the only remaining closure action.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.