# Phase 03 - Dead continuity layer removal

**Strategic spec:** [`../S1596_ticket-session-bootstrap.md`](../S1596_ticket-session-bootstrap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01 and 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Delete the four agent-continuity utilities with zero invocations over the measured week, together with every reference to them, keeping the live snapshot pair untouched.

---

## Prerequisites

- [x] Strategic §6 item 3 is Resolved - read `research/03__dead-continuity-layer.md`, which carries the per-script invocation counts and the reason the snapshot pair is out of scope.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/agent_continuity/start-packet.ps1` | Deleted | - |
| `scripts/agent_continuity/request-log.ps1` | Deleted | - |
| `scripts/agent_continuity/request-digest.ps1` | Deleted | - |
| `scripts/agent_continuity/dirty-tree-guard.ps1` | Deleted | - |
| `scripts/agent_continuity/README.md` | Modified | ≤ 80 changed |
| `dev/AGENT_WORKFLOW.md` | Modified | ≤ 50 changed |
| `docs/AGENT_COST_PLAYBOOK.md` | Modified | ≤ 20 changed |

---

## Steps

### Step 03.1 - Delete the four uncalled scripts

**Files:** `scripts/agent_continuity/start-packet.ps1`, `scripts/agent_continuity/request-log.ps1`, `scripts/agent_continuity/request-digest.ps1`, `scripts/agent_continuity/dirty-tree-guard.ps1`

**Depends on:** - start of phase

**Prompt for developer:**

> Delete the four files. Leave `session-snapshot.ps1` and `session-resume.ps1` in place - the writer is called 120 times a week and removing its reader would leave a live channel with no way to read it.
>
> Before deleting, confirm once more that nothing calls them: grep the repository for each file name outside `PLAN/`, `temp/` and `dev/CHANGELOG.md`.

**Why:**

Strategic ADR-4 requires the dead start layer to be disposed of inside this ticket rather than parked, because two start facades side by side is the state this ticket exists to leave, and CLAUDE.md Rule 20 makes orphaned scripts part of the same change that supersedes them.

**Verification:**

- `Glob` - none of the four paths exist.
- `Glob` - `scripts/agent_continuity/session-snapshot.ps1` and `scripts/agent_continuity/session-resume.ps1` still exist.
- `Grep` - `start-packet`, `request-log.ps1`, `request-digest`, `dirty-tree-guard` have no remaining *caller* anywhere: no `.ps1` invokes them and no driver names them as a command to run. `docs/SCRIPT_CHEATSHEET.md` still lists them until Phase 04 regenerates it, and `dev/gpt_audit.md` mentions one as a historical record; neither is a caller.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. start-packet.ps1, request-log.ps1, request-digest.ps1 and dirty-tree-guard.ps1 deleted. Re-checked before deleting: no .ps1 invokes any of them and no driver names them; every remaining hit was documentation. session-snapshot.ps1 and session-resume.ps1 kept intact - the writer runs 120 times a week and removing its reader would leave a live channel unreadable.

---

### Step 03.2 - Remove their documentation

**Files:** `scripts/agent_continuity/README.md`, `dev/AGENT_WORKFLOW.md`, `docs/AGENT_COST_PLAYBOOK.md`

**Depends on:** Step 03.1

**Prompt for developer:**

> Remove the sections describing the four deleted utilities: the "Bootstrap packet", "Request logger", "Request digest" and "Dirty-tree guard" blocks in `dev/AGENT_WORKFLOW.md`'s Agent Continuity Layer, the matching entries in `scripts/agent_continuity/README.md`, and any mention in `docs/AGENT_COST_PLAYBOOK.md`.
>
> Keep the "Resume layer" description of `session-snapshot.ps1` / `session-resume.ps1`, and add one line to `scripts/agent_continuity/README.md` recording that the four were removed as uncalled and that S1603 owns the remaining question about the snapshot pair.

**Why:**

Strategic §11 criterion 8 asks that no unused start utilities remain, and a deleted script whose documentation survives still reads as an available tool to the next agent - which is how the layer stayed indexed while dead.

**Verification:**

- `Grep` - `start-packet` returns zero hits in `dev/AGENT_WORKFLOW.md` and `docs/AGENT_COST_PLAYBOOK.md`. In `scripts/agent_continuity/README.md` the four names survive only inside the removal record - no pillar row, no usage block - because deleting the reason a thing was removed invites rebuilding it.
- `Grep` - `session-snapshot.ps1` still matches in `dev/AGENT_WORKFLOW.md`.
- `Grep` - `S1603` matches once in `scripts/agent_continuity/README.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. AGENT_WORKFLOW's Agent Continuity Layer now describes the resume pair only and points session start at session-bootstrap.ps1. AGENT_COST_PLAYBOOK's measurement loop no longer names a log nobody wrote to - it names the transcript corpus that actually produced the audit. README keeps a removal record naming all four and pointing the snapshot question at S1603.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `Grep` for the four script names finds no caller: no `.ps1` invokes them, no driver names them as a command. Remaining mentions are the README removal record, `dev/gpt_audit.md` as history, and `docs/SCRIPT_CHEATSHEET.md` until Phase 04 regenerates it.
- [x] `post-change.ps1 -Files "<the changed docs>" -ScopeToFile -Target "S1596" -ChangeType Doc` exits 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The script inventory changed, so the generated cheatsheet is now stale by construction - Phase 04 regenerates it. The snapshot pair is deliberately untouched and belongs to S1603.

---

## Rollback Plan

Revert the phase commit. Deleted scripts return with their documentation; nothing depended on them, so no consumer needs restoring.
