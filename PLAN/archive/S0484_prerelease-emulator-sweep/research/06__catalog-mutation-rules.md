# Research §6.6 - FAIL-branch catalog mutation rules

**Strategic item:** §6.6
**Status:** Resolved
**Date:** 2026-06-17

## Question

On FAIL, how to dedup new `/spec-draft` tickets and how to update pending-test (`BlockNeedUserTest`) tickets.

## Findings / decisions

Mirror the established `/spec-sweep` + `/spec-test-device` + `/spec-check` discipline; the sweep never flips status by guess.

**New defects → `/spec-draft` (deduped):**

- For each distinct defect the verdict surfaces, dedup first via `scripts/spec_catalog/search.ps1` by symptom - error code / class name / subsystem keyword, and cross-check same-day created tickets. If an open ticket already covers it, reference that id; do not draft a duplicate.
- One `/spec-draft` per distinct, non-trivial, out-of-scope defect (CLAUDE.md §3.1). Capture symptom + evidence (log lines, screenshot path under `temp/`) into the draft's §0.

**Pending-test (`BlockNeedUserTest`) tickets:**

- The sweep does not auto-mark them Verified by assumption. For a ticket whose flow the sweep actually exercised, route evidence through `/spec-check <Sxxxx>` (evidence → `Verified` / `Partial` / `Broken`), exactly as `/spec-sweep` does.
- `/spec-check` owns the status flip and, on any transition leaving `BlockNeedUserTest`, performs the grep-and-delete of that spec's `Timber.d("Sxxxx:` tags in the same change (CLAUDE.md "Debug Verification Tags"). The sweep itself must never delete a tag for a ticket that stays `BlockNeedUserTest`.
- Tickets the sweep did not exercise stay untouched.

**Mutation tooling:** only via `scripts/spec_catalog/*` (`search.ps1`, `insert.ps1`/`/spec-draft`, `update.ps1`); never hand-edit `spec-catalog.jsonl`.

## Impact on plan

- Phase 05 FAIL branch: dedup via `search.ps1` → `/spec-draft` per distinct defect; delegate pending-test verification to `/spec-check`; respect tag lifecycle.
- Keeps the auto-mutation honest: drafts are deduped, status flips are evidence-driven through `/spec-check`, not guessed by the sweep.
