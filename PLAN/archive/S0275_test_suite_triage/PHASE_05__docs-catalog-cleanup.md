# Phase 05 - docs-catalog-cleanup

**Strategic spec:** [`../S0275_test_suite_triage.md`](../S0275_test_suite_triage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all previous phases
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Update validation guidance, agent memory, and spec artefacts so the repo no longer relies on the pre-S0275 XML-workaround contract.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.
- [ ] The default `standard` suite outcome is known and recorded.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0275_test_suite_triage.md` | Modified | ≤ 500 |
| `PLAN/S0275_test_suite_triage/INDEX.md` | Modified | ≤ 300 |
| `CLAUDE.md` | Modified | ≤ 500 |
| `docs/DEV_OPS.md` | Modified | ≤ 500 |
| `.claude/agent-memory/android-rd-specialist/feedback_build_pre_existing_test_failures.md` | Modified | ≤ 300 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split first.

---

## Steps

### Step 05.1 - Replace the old per-class XML workaround guidance

**Files:** `CLAUDE.md`, `docs/DEV_OPS.md`, `.claude/agent-memory/android-rd-specialist/feedback_build_pre_existing_test_failures.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Remove the stale contract that treats `assembleStandardDebug` + per-class XML reads as the normal Kotlin validation path. Replace it with the post-S0275 rule: default `standard` suite exit code is authoritative; quarantine, if it exists, is a separate explicit task. If the final outcome is pure fix-all, delete the memory file instead of rewriting it.

**Verification:**

- `Grep` - `per-class XML` workaround no longer appears in the updated guidance files unless explicitly described as historical context.
- `Grep` - `testStandardDebugUnitTest` appears as the canonical Kotlin test signal in `CLAUDE.md`.
- `Grep` - `testStandardDebugQuarantineUnitTest` appears in `docs/DEV_OPS.md` if quarantine is non-empty.

**Status:** `[ ]` not done

---

### Step 05.2 - Finalize strategic and tactical artefacts with actual counts

**Files:** `PLAN/S0275_test_suite_triage.md`, `PLAN/S0275_test_suite_triage/INDEX.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Replace any stale `~26` assumptions with the actual final counts, update the phase table / progress counters, and make the tactical artefacts self-sufficient for `/spec-check`.

**Verification:**

- `Grep` - `~26` returns zero hits in both S0275 artefacts unless backed by a historical audit note.
- `Grep` - `Phases: 5 / 5 done` present in `INDEX.md` at final closure.
- `Grep` - `Status: Done` present in `INDEX.md` at final closure.

**Status:** `[ ]` not done

---

### Step 05.3 - Run final audit and closure steps

**Files:** `PLAN/S0275_test_suite_triage.md`, `PLAN/S0275_test_suite_triage/INDEX.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run the mechanical closure: dev log entries, catalog sync if needed, `/spec-check S0275`, and any trivial `/spec-fix` follow-up. The strategic spec must end in `Verified`, with `## Last Audit` filled inline.

**Verification:**

- `Grep` - `## Last Audit` contains a non-placeholder audit block in `PLAN/S0275_test_suite_triage.md`.
- `Grep` - `**Status:** Verified` present in `PLAN/S0275_test_suite_triage.md`.
- `Grep` - `Status: Done` present in `PLAN/S0275_test_suite_triage/INDEX.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Repo guidance reflects the post-S0275 validation contract.
- [ ] The memory file is removed or rewritten consistently with the final outcome.
- [ ] `dev/CHANGELOG.md` has an entry for every touched file via `add_to_dev_log.ps1` / `post-change.ps1`.
- [ ] `/spec-check S0275` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the doc-only cleanup commits and rerun `/spec-check S0275` after restoring the previous guidance.