# Phase 04 - Docs/catalog cleanup

**Strategic spec:** [`../S1341_agent-model-routing-tiers.md`](../S1341_agent-model-routing-tiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Verify the strategic §7 acceptance list end to end, run the document-registry closing calls (`repository-rules` and `developer-operations` records are both touched), and log the change.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

None new - this phase verifies and logs prior phases' edits.

---

## Steps

### Step 04.1 - Verify strategic §7 acceptance list

**Files:** none (verification only)
**Depends on:** Phase 01, 02, 03 complete

**Prompt for developer:**

> Confirm each strategic §7 acceptance bullet: no `model:` key remains in any `.claude/commands/*.md`; every agent in `.claude/agents/` carries an explicit `model:` (never `inherit`). The "re-measure with S1338 package A" and "rework rate" bullets are forward-looking metrics, not checkable now - list them as Manual in the final report, not as failed predicates.

**Verification:**

- `Grep "^model:" .claude/commands/*.md` returns zero matches (no command carries a `model:` key at all, not even a different value).
- `Grep "^model: inherit" .claude/agents/*.md` returns zero matches across all files in the directory.
- `Glob .claude/agents/*.md` count is 6 (4 pre-existing + `android-device-operator.md` + `repo-mechanic.md`).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `Grep "^model:" .claude/commands/*.md` -> 0. `Grep "^model: inherit" .claude/agents/*.md` -> 0. Agent file count -> 6. Manual (forward-looking, not checkable now): re-measure spend per model with S1338 package A after two weeks; watch the subagent retry rate (baseline 1%) as the counter-metric.

---

### Step 04.2 - Document-registry closing calls and dev log

**Files:** none (script execution only)
**Depends on:** Step 04.1

**Prompt for developer:**

> `.claude/commands/*.md` and `.claude/agents/*.md` fall under the `repository-rules` registered document; `docs/AGENT_COST_PLAYBOOK.md` falls under `developer-operations`. Run `scripts/document_registry/validate.ps1` then `scripts/document_registry/generate.ps1 -Check`. Then run the batched dev-log entry for the whole ticket.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- `.\scripts\add_to_dev_log.ps1` invoked at least once for this ticket's batched entry.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `validate.ps1` -> "Document registry PASS: 24 record(s)", exit 0. `generate.ps1 -Check` -> "Generated document views are current.", exit 0. Batched dev-log recorded. Ready for `/spec-check S1341`.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - skip. No Kotlin/build-graph file touched.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1` for the whole ticket (batched).
- [ ] If public API changed: skip.
- [ ] Phase-boundary audit run - not applicable; final `/spec-check` is the closing audit.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. Next step is `/spec-check S1341`.

---

## Rollback Plan

No content changes in this phase beyond verification and logging.
