# Phase 02 - Audit Prompt Runner

**Strategic spec:** [`../S0703_shared-state-mutation-audit.md`](../S0703_shared-state-mutation-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-26
**Completed:** 2026-06-26

---

## Objective

Persist the reusable cross-project audit prompt as a committed, runnable artifact and document the two-stage flow (harvester -> agent) so the audit can be re-run by anyone without reopening the spec.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `scripts/quality/audit-shared-state-writers.ps1` exists and runs clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/shared-state-audit-prompt.md` | New | ≤ 140 |

---

## Steps

### Step 02.1 - Commit the reusable audit prompt

**Files:** `scripts/quality/shared-state-audit-prompt.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the audit prompt document. Copy the reusable research prompt verbatim from strategic spec §5.4 into a fenced block. It must keep all five sections: ROLE, SCOPE, SURFACE A (UI objects), SURFACE B (data / state holders), GROUPING, UNSAFE-PATTERN CLASSIFIER, OUTPUT, VERIFICATION, DELIVERY. The prompt stays English and references only framework categories and roles - no project class names or paths. Above the fenced block add a one-paragraph header stating this is the agent-side stage of the S0703 audit.

**Verification:**

- `Glob` - `scripts/quality/shared-state-audit-prompt.md` exists.
- `Grep` - `SURFACE A` and `SURFACE B` both present.
- `Grep` - `UNSAFE-PATTERN CLASSIFIER` present.
- `Grep` - `/spec-draft` referenced in the DELIVERY section.

**Status:** `[x] done`

**Step Log:**

- 2026-06-26 - Verification 4/4 PASS. Files: scripts/quality/shared-state-audit-prompt.md (New). Prompt copied verbatim from strategic §5.4, all sections present. Dev log recorded.

---

### Step 02.2 - Document the two-stage run flow

**Files:** `scripts/quality/shared-state-audit-prompt.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Append a short "How to run" section to the same file: stage 1 runs `scripts/quality/audit-shared-state-writers.ps1 -Surface all -Json temp/shared-state-audit.json` to harvest ranked candidates; stage 2 hands that JSON plus the prompt block to a research agent, which adjudicates indirect writers and data-side semantics, adversarially refutes the top findings, and lists surviving non-trivial conflicts as `/spec-draft` candidates. State explicitly that the harvester is a pre-filter (mechanical, regex-level) and the agent stage is authoritative for indirect writers and concurrency reasoning.

**Verification:**

- `Grep` - `audit-shared-state-writers.ps1` referenced in the file.
- `Grep` - `temp/shared-state-audit.json` referenced.
- `Grep` - `How to run` heading present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-26 - Verification 3/3 PASS. Files: scripts/quality/shared-state-audit-prompt.md (+~20 LOC). Two-stage run flow documented (harvester -> agent). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `scripts/quality/shared-state-audit-prompt.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The audit is now fully runnable (harvester + prompt). Phase 03 makes the tool discoverable in dev docs and records the change.

---

## Rollback Plan

Revert the phase commit - a single new documentation file, no source or user-facing surface touched.
