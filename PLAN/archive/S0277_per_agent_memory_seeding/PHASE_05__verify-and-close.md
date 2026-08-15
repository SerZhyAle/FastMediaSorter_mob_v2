# Phase 05 - Verify and Close

**Strategic spec:** [../S0277_per_agent_memory_seeding.md](../S0277_per_agent_memory_seeding.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Confirm that all five strategic Done-signal predicates hold across the three seeded profiles, that the donor and harness config are untouched, and that the spec is ready for `/spec-check`.

---

## Prerequisites

- [ ] Phases 02, 03, 04 all `✅ Done`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| - | - | - |

> Read-only verification phase. No new files except the strategic spec's `## Last Audit` block, which is owned by `/spec-check`.

---

## Steps

### Step 05.1 - Donor & harness invariance check

**Files:** read-only diff over `.claude/agent-memory/android-rd-specialist/`, `.claude/agents/`, `.claude/settings*.json`.
**Depends on:** - start of phase

**Prompt for developer:**

> Run two `git diff --stat` invocations:
>
> ```
> git diff --stat .claude/agent-memory/android-rd-specialist/
> git diff --stat .claude/agents/ .claude/settings.json .claude/settings.local.json
> ```
>
> Both must produce empty output.

**Verification:**

- Donor diff exit code 0, output empty.
- Harness-config diff exit code 0, output empty.

**Status:** `[x]` done

---

### Step 05.2 - Per-profile completeness check

**Files:** `.claude/agent-memory/android-kotlin-developer/`, `.claude/agent-memory/android-solution-researcher/`, `.claude/agent-memory/friendly-android-doc-writer/`.
**Depends on:** Step 05.1

**Prompt for developer:**

> For each target profile, count the record files and confirm:
>
> - `android-kotlin-developer`: 25 record files + `MEMORY.md`.
> - `android-solution-researcher`: 23 record files + `MEMORY.md` (re-confirmed against actual implementation in Phase 03 - the mapping table prescribes 23 deliverable entries excluding records marked N).
> - `friendly-android-doc-writer`: 9 record files + `MEMORY.md`.
>
> Confirm each `MEMORY.md` is non-empty and lists every neighbour file by name.

**Verification:**

- `Glob` per profile returns the expected counts.
- `Grep` per profile - every `*.md` file (except `MEMORY.md`) is referenced from the local `MEMORY.md` at least once.

**Status:** `[x]` done

---

### Step 05.3 - Flip strategic status to `Implemented`

**Files:** `PLAN/S0277_per_agent_memory_seeding.md`, `PLAN/spec-catalog.jsonl` (via `update.ps1`).
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `scripts/spec_catalog/update.ps1 -Id S0277 -Status Implemented`. Update the strategic spec header `Status:` accordingly and append a Revision History entry recording the F3 completion.

**Verification:**

- `Grep` - `**Status:** Implemented` appears in `PLAN/S0277_per_agent_memory_seeding.md`.
- `update.ps1` exit code 0; journal carries `S0277` at status `Implemented`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] All steps `[x]`.
- [ ] Spec ready for `/spec-check`.

---

## Handoff Notes to Next Phase

End of pipeline; `/spec-check S0277` is the final audit gate that flips to `Verified`.

---

## Rollback Plan

If `/spec-check` finds gaps: status is set to `Partial` by the audit; surface the action items in `## Last Audit` and re-enter via `/spec-fix`.
