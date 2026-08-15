# Phase 02 — dev-docs-update

**Strategic spec:** [`../S0179_git-branching-model.md`](../S0179_git-branching-model.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Update `dev/PROJECT_OPERATIONS_INDEX.md` and `dev/AGENT_WORKFLOW.md` to reflect the branching model — agents referencing these docs will know about the branch rules without reading CLAUDE.md in full.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/PROJECT_OPERATIONS_INDEX.md` | Modified | ≤ 20 new lines |
| `dev/AGENT_WORKFLOW.md` | Modified | ≤ 10 new lines |

---

## Steps

### Step 02.1 — Add branching topology to PROJECT_OPERATIONS_INDEX

**Files:** `dev/PROJECT_OPERATIONS_INDEX.md`
**Depends on:** — start of phase (Phase 01 done)

**Prompt for developer:**

> Read `dev/PROJECT_OPERATIONS_INDEX.md`. Locate `## 1) Workspace Topology`. After the existing bullet list in that section, append:
>
> ```markdown
> - Branch model: `main` = release-stable only; development in `DEBUG-v001`, `DEBUG-v002`, … (see `CLAUDE.md § Git Branching Model`).
> ```
>
> Then locate `## 5) Fast Commands`. After the last entry in that section, add:
>
> ```markdown
> - Show current branch: `git branch --show-current`
> - Create next DEBUG branch: `git checkout main && git pull && git checkout -b DEBUG-v00N`
> - Merge DEBUG to main: `git checkout main && git merge --no-ff DEBUG-v00N`
> ```

**Verification:**

- `Grep` — `Branch model:` appears in `dev/PROJECT_OPERATIONS_INDEX.md`.
- `Grep` — `DEBUG-v00N` appears in `dev/PROJECT_OPERATIONS_INDEX.md`.
- `Grep` — `## 1) Workspace Topology` still present (section not overwritten).
- `Grep` — `## 5) Fast Commands` still present.

**Status:** `[ ]` not done

---

### Step 02.2 — Add branch check to AGENT_WORKFLOW task-definition step

**Files:** `dev/AGENT_WORKFLOW.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Read `dev/AGENT_WORKFLOW.md`. Locate `### 8.0 TASK DEFINITION`. After the `- **Gate**:` line in that section, insert:
>
> ```markdown
> - **Branch check**: Run `git branch --show-current`. Confirm the session is on the expected branch before any file edit. If on `main`, proceed only for hotfixes or pre-branch tooling setup; all other development belongs on a `DEBUG-v00N` branch.
> ```

**Verification:**

- `Grep` — `Branch check` appears in `dev/AGENT_WORKFLOW.md`.
- `Grep` — `DEBUG-v00N` appears in `dev/AGENT_WORKFLOW.md`.
- `Grep` — `### 8.0 TASK DEFINITION` still present.

**Status:** `[ ]` not done

---

### Step 02.3 — Add branching context to AGENT_WORKFLOW planning step

**Files:** `dev/AGENT_WORKFLOW.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `dev/AGENT_WORKFLOW.md`, locate `### 8.3 PLANNING PHASE`. After the `- **Output**:` line in that section, append:
>
> ```markdown
> - **Branch note**: If this task includes work not intended for the upcoming release, identify those steps explicitly and flag them for the "future" DEBUG branch.
> ```

**Verification:**

- `Grep` — `Branch note` appears in `dev/AGENT_WORKFLOW.md`.
- `Grep` — `### 8.3 PLANNING PHASE` still present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` — `DEBUG-v00N` appears in both `dev/PROJECT_OPERATIONS_INDEX.md` and `dev/AGENT_WORKFLOW.md`.
- [ ] Dev log entries added for both modified files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 02 completes doc-layer awareness. Phase 05 (git-branch-init) depends on this phase being done — agent instructions must be in place before the first branch is created.

---

## Rollback Plan

Revert edits to both files — no data migration, no user-facing surface changed.
