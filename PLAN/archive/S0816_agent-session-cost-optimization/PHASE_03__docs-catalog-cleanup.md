# Phase 03 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0816_agent-session-cost-optimization.md`](../S0816_agent-session-cost-optimization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Close out the docs-only change set: dev-log every modified file and spawn the per-pillar follow-up child ticket for skill-cost-tier `model:` assignment.

---

## Prerequisites

- [ ] Phase 01 + Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified (via script only) | n/a |
| `PLAN/Sxxxx_skill-cost-tier-model-assignment.md` | New (child ticket via `insert.ps1`) | n/a |

> No `.kt` changed - `catalog_sync.ps1` / `scan.ps1` skipped. No shipped capability - `ALL_FEATURES` skipped (internal dev tooling).

---

## Steps

### Step 03.1 - Dev-log every modified file

**Files:** `dev/CHANGELOG.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Append one dev-log entry per logical change via `.\scripts\add_to_dev_log.ps1` (never hand-edit `dev/CHANGELOG.md`): the new playbook doc, and the discoverability anchor edits (`CLAUDE.md` + `AGENTS.md` + `dev/PROJECT_OPERATIONS_INDEX.md`) as one batched logical entry. Use target `spec-dev`.

**Verification:**

- `Grep` - `AGENT_COST_PLAYBOOK` matches in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

### Step 03.2 - Spawn skill-cost-tier child ticket

**Files:** `PLAN/Sxxxx_skill-cost-tier-model-assignment.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Allocate an id via `scripts/spec_catalog/next-id.ps1`, then `insert.ps1` a Draft strategic spec `PLAN/Sxxxx_skill-cost-tier-model-assignment.md` for the deferred mechanical change: assign cheaper `model:` frontmatter to clearly-mechanical leaf skills (candidates: `doc-update`, `git`, `ns`, `quick`, `skill-fix`, `caveman-commit`), explicitly excluding reasoning-sensitive orchestrators (`/spec-dev`, `/spec-all`, `/spec-tech`). Reference S0816 §10 as parent. Keep it a skeleton - this step only parks it, does not develop it.

**Verification:**

- `Glob` - `PLAN/S*_skill-cost-tier-model-assignment.md` exists.
- `select.ps1` - new id resolves with `status: Draft`.

**Status:** `[ ]` not done

---

### Step 03.3 - Record child ticket under strategic §10

**Files:** `PLAN/S0816_agent-session-cost-optimization.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add the newly-created child ticket id to strategic §10 (Связи с другими спеками) as the first concrete per-pillar follow-up (skill-cost tiers). One bullet, no prose restatement.

**Verification:**

- `Grep` - the child ticket id matches in `PLAN/S0816_agent-session-cost-optimization.md` §10.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] Child ticket exists and is recorded under strategic §10.
- [ ] `/spec-check S0816` can run.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, `/spec-check S0816` audits the doc deliverables against §11 criteria and flips strategic status to `Verified`.

---

## Rollback Plan

Revert dev-log entries and archive the child ticket via `/spec-arc` - no code touched.
