# Phase 02 - Discoverability Anchor

**Strategic spec:** [`../S0816_agent-session-cost-optimization.md`](../S0816_agent-session-cost-optimization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Make the playbook a live rule, not a dead doc: add a concise cost-discipline pointer to `CLAUDE.md` §6 and mirror it in `AGENTS.md`, so every future session routes through the playbook by default.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `docs/AGENT_COST_PLAYBOOK.md` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ +12 |
| `AGENTS.md` | Modified | ≤ +12 |

> `CLAUDE.md` is a high-risk overlap file (`dirty-tree-guard.ps1` baseline). Make a minimal, additive edit only.

---

## Steps

### Step 02.1 - Add cost-discipline pointer to CLAUDE.md §6

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In `CLAUDE.md` §6 (Proactive Research & Parallelism), add one concise bullet: default to cost discipline per `docs/AGENT_COST_PLAYBOOK.md` - prefer inline over subagent for single-fact lookups, `/compact` at task boundaries and `/clear` on task switch, offload raw artifacts to `temp/`, and restrict `mobile-mcp` to exploratory UI walks. Keep it to a single bullet plus the doc link; do not restate the full playbook (that is the doc's job). Additive edit, no reordering of existing rules.

**Verification:**

- `Grep` - `AGENT_COST_PLAYBOOK.md` matches in `CLAUDE.md`.
- `Grep` - the new bullet sits within `## 6.` section bounds.

**Status:** `[ ]` not done

---

### Step 02.2 - Mirror the pointer in AGENTS.md

**Files:** `AGENTS.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mirror the same cost-discipline pointer + `docs/AGENT_COST_PLAYBOOK.md` link into `AGENTS.md` in the equivalent section, per CLAUDE.md's "sync AGENTS.md too" rule for shared-rule changes. Keep wording parallel to the CLAUDE.md bullet.

**Verification:**

- `Grep` - `AGENT_COST_PLAYBOOK.md` matches in `AGENTS.md`.

**Status:** `[ ]` not done

---

### Step 02.3 - Add playbook to research-order doc index

**Files:** `dev/PROJECT_OPERATIONS_INDEX.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a one-line entry for `docs/AGENT_COST_PLAYBOOK.md` to `dev/PROJECT_OPERATIONS_INDEX.md` in the most fitting existing list (agent/process tooling), so the playbook is reachable via the standard research-order entry point. One line, no new section unless an obviously matching one is absent.

**Verification:**

- `Grep` - `AGENT_COST_PLAYBOOK` matches in `dev/PROJECT_OPERATIONS_INDEX.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Docs-only - no build required.
- [ ] `Grep` - `AGENT_COST_PLAYBOOK.md` resolves in `CLAUDE.md`, `AGENTS.md`, `dev/PROJECT_OPERATIONS_INDEX.md`.
- [ ] Dev log entry added for each modified file.

---

## Handoff Notes to Next Phase

Playbook is now discoverable from the three canonical entry points. Phase 03 records the capability and spawns the skill-cost-tier child ticket.

---

## Rollback Plan

Revert the three additive doc edits - no code, no migration.
