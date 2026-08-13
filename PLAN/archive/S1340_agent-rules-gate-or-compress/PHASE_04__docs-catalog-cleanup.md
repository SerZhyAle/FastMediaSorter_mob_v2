# Phase 04 - Docs/catalog cleanup

**Strategic spec:** [`../S1340_agent-rules-gate-or-compress.md`](../S1340_agent-rules-gate-or-compress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Close the ticket: confirm the aggregate byte-count reduction strategic §5 requires, run the document-registry closing calls (CLAUDE.md is a registered document under the `repository-rules` record), and log the change.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

None new - this phase verifies and logs prior phases' edits; no further content edits.

---

## Steps

### Step 04.1 - Verify byte-count reduction and acceptance bullets

**Files:** none (verification only)
**Depends on:** Phase 01, 02, 03 complete

**Prompt for developer:**

> Measure `CLAUDE.md`'s current byte count (`(Get-Item CLAUDE.md).Length` via PowerShell) and confirm it is below the 32,657 B baseline recorded in strategic §5 (measured 2026-08-01, tactical-planning time). Confirm each strategic §5 acceptance bullet against the actual edits: byte count fell with every removed line traceable to a gate/hook/document (spot-check 3-5 removed lines against Phase 01/02's verification predicates); Rule 17's gate is unchanged (not touched by this ticket, already satisfied via S1338); no rule listed in strategic §4 (Rule 12, Rule 10.1) was compressed; zero new `assert-*` scripts were added (`Glob scripts/quality/assert-*.ps1` count unchanged from the pre-ticket count, unless Phase 01.4 added a docstring-only edit to an existing script, which does not count as a new script).

**Verification:**

- PowerShell `(Get-Item CLAUDE.md).Length` returns a value less than 32657.
- `ls scripts/quality/assert-*.ps1 | wc -l` count is unchanged from the pre-S1340 count - no new file added by this ticket (the exact baseline figure carried from tactical-planning research turned out to be an approximation, corrected below; what matters is the delta is zero, not the absolute number).
- `Grep "No root writes" CLAUDE.md` and `Grep "no completion claim without fresh evidence" CLAUDE.md -i` both still match (Rule 10.1 / Rule 12 untouched, per §4 constraint).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `(Get-Item CLAUDE.md).Length` -> 26,111 B (baseline 32,657 B, -6,546 B / -20.0%) - well under threshold, and now below even the strategic spec's original stale 28,559 B figure. `AGENTS.md` 10,571 -> 10,223 B, `.github/copilot-instructions.md` 8,137 -> 8,002 B (both fell too, from Phase 03's dedup work). `ls scripts/quality/assert-*.ps1 | wc -l` -> 40 (self-correction: the "39" figure carried from tactical-planning research was an off-by-one approximation, not verified with `wc -l` at the time; the actual invariant this predicate protects - zero NEW files added by this ticket - holds regardless, since this ticket touched zero files under `scripts/quality/`). `Grep "No root writes"` -> line 129, `Grep "no completion claim..."` -> line 135, both untouched. Acceptance bullets spot-checked against Phase 01-03 Step Logs: every removed line traces to a named gate (`assert-neuroslop.ps1`, `agent-lock.ps1`), a named document (`docs/CODE_AUDIT_PROTOCOL.md` Layers, `docs/DEV_OPS.md` new subsections), or a named script (`spec-next-preflight.ps1` etc). Rule 17's gate (`assert-window-insets.ps1`) untouched, confirmed pre-existing from S1338. No rule in strategic §4 (Rule 12, Rule 10.1) was compressed. Zero new `assert-*` scripts.

---

### Step 04.2 - Document-registry closing calls and dev log

**Files:** none (script execution only)
**Depends on:** Step 04.1

**Prompt for developer:**

> `CLAUDE.md` is a registered document (`repository-rules` record in `docs/DOCUMENT_REGISTRY.jsonl`, confirmed during this ticket's task-start document-registry query). Per CLAUDE.md's own mandate (now compressed by Phase 02.2, but still in force): run `scripts/document_registry/validate.ps1` then `scripts/document_registry/generate.ps1 -Check` to confirm no generated artifact (`docs/DOCS_MAP.md`, `sitemap.xml`) drifted from the registry. Then run the batched dev-log entry for the whole ticket and flip the strategic spec toward `/spec-check`.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0 (no drift).
- `.\scripts\add_to_dev_log.ps1` invoked at least once for this ticket's batched entry (per CLAUDE.md journaling-granularity rule - one entry per logical change, not per file).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `validate.ps1` -> "Document registry PASS: 24 record(s)", exit 0. `generate.ps1 -Check` -> "Generated document views are current.", exit 0. Batched dev-log entry recorded summarizing all 4 phases. Ready for `/spec-check S1340`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - skip. No Kotlin/build-graph file touched.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1` for the whole ticket (batched, per journaling-granularity rule).
- [x] If public API changed: skip - no app source touched.
- [x] Phase-boundary audit run - not applicable; this is a docs-only ticket, final `/spec-check` is the closing audit.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. Next step is `/spec-check S1340`.

---

## Rollback Plan

No content changes in this phase beyond verification and logging - nothing to roll back here specifically; rolling back the ticket means reverting Phase 01-03's commits.
