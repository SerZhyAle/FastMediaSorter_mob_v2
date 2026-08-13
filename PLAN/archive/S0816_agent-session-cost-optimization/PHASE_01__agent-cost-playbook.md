# Phase 01 - Agent Cost Playbook

**Strategic spec:** [`../S0816_agent-session-cost-optimization.md`](../S0816_agent-session-cost-optimization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Create `docs/AGENT_COST_PLAYBOOK.md` - one English developer doc that converts the weekly usage signal into concrete routing rules across all five strategic pillars (spawn policy, context hygiene, skill cost tiers, MCP hygiene, measurement loop).

---

## Prerequisites

- [ ] Strategic §6 research items Resolved (see INDEX Pre-Implementation Blockers).
- [ ] Read [`research/01__usage-levers-and-boundaries.md`](research/01__usage-levers-and-boundaries.md) in full.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/AGENT_COST_PLAYBOOK.md` | New | ≤ 220 |

> No layout, no flavor, no Kotlin. English dev doc per repo doc conventions (`..` not `...`, plain hyphen, no em-dash).

---

## Steps

### Step 01.1 - Scaffold playbook with header + metric-to-lever table

**Files:** `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `docs/AGENT_COST_PLAYBOOK.md`. Open with a one-paragraph purpose statement: this doc is the single operational playbook for keeping agent-session cost low without losing execution quality; the source signal is a weekly usage summary of the agent shell, not Android app profiling. Add a `## Metric -> lever` table mapping each weekly-usage axis (subagent-heavy sessions, >150k context, 4+ parallel sessions, `/spec-dev` weight, `mobile-mcp` weight) to its lever class and whether it is repo-controllable, reproducing the table from research artifact §6.1. Mark the two operator/harness-only axes explicitly as "advise, not gate".

**Verification:**

- `Glob` - `docs/AGENT_COST_PLAYBOOK.md` exists.
- `Grep` - `## Metric -> lever` matches once.
- `Grep` - `mobile-mcp` present in the table region.

**Status:** `[ ]` not done

---

### Step 01.2 - Write Spawn policy section (inline vs subagent boundary)

**Files:** `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `## Spawn policy - inline vs subagent`. State the reproducible heuristic from research §6.2: inline when the answer is a single fact/edit reachable in roughly <=3 targeted tool calls with known file/symbol/value; subagent when sweeping many files for only the conclusion, an independent parallel branch, or an isolated artifact (research/audit). Add the hard rule: never spawn a subagent for a lookup resolvable inline. This is the largest axis (70%) - keep it first and unambiguous.

**Verification:**

- `Grep` - `## Spawn policy` matches once.
- `Grep` - `<=3` or `3 targeted` present (boundary stated numerically).
- `Grep` - `never spawn` (case-insensitive) present.

**Status:** `[ ]` not done

---

### Step 01.3 - Write Context hygiene + MCP hygiene sections

**Files:** `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `## Context hygiene`: rules for `/compact` mid-task at natural boundaries, `/clear` when switching to an unrelated task, and offloading raw artifacts (logs, build output, large dumps) to `temp/` instead of holding them in chat. Then add `## MCP hygiene`: the mobile-mcp routing rule from research §6.4 - `adb.ps1` for deterministic chores, Maestro for repeatable flows, mobile-mcp ONLY for exploratory agent-driven UI walks where elements/coordinates are not scriptable; because MCP results stay sticky in context, bound the window and `/compact` immediately after the walk. Reference `scripts/devtest/adb.ps1` and `.\a.ps1 adb <verb>` as the cheap alternative.

**Verification:**

- `Grep` - `## Context hygiene` matches once.
- `Grep` - `## MCP hygiene` matches once.
- `Grep` - `adb.ps1` present.
- `Grep` - `/compact` present at least twice (context + MCP sections).

**Status:** `[ ]` not done

---

### Step 01.4 - Write Skill cost tiers section

**Files:** `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `## Skill cost tiers`. Record that `model:` frontmatter is already honored and in active use (list the current `sonnet` commands/agents from research §6.3). State the levers: cheaper `model:` tier for mechanical skills, scope-down via `--phase`/`--step`, orchestrators routing sub-work to cheaper subagent models. State the caution verbatim in intent: do NOT blanket-downgrade reasoning-sensitive orchestrators (`/spec-dev`, `/spec-all`); downgrade only clearly-mechanical stages. Note that applying `model:` assignments to leaf skills is tracked as a separate child ticket (per-skill judgement), so this playbook is the policy and the child ticket is the mechanical change.

**Verification:**

- `Grep` - `## Skill cost tiers` matches once.
- `Grep` - `model:` present.
- `Grep` - `/spec-dev` and `/spec-all` both present in the caution.

**Status:** `[ ]` not done

---

### Step 01.5 - Write Measurement loop section + child-ticket pointers

**Files:** `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add `## Measurement loop`: the S0268 continuity layer is the measurement base - `scripts/agent_continuity/request-log.ps1` appends per-session JSONL, `scripts/agent_continuity/request-digest.ps1` prints a ranked profile; the external weekly usage summary is the before/after check. Do not promise exact token savings before a reproducible before/after exists (strategic §2 non-goal). Close with `## Follow-up`: per-pillar improvements are parked as individual `/spec-draft` child tickets (not a separate heavyweight backlog file); name the first one - skill-cost-tier `model:` assignment.

**Verification:**

- `Grep` - `## Measurement loop` matches once.
- `Grep` - `request-digest.ps1` present.
- `Grep` - `## Follow-up` matches once.
- File line count `<= 220` (`(Get-Content docs/AGENT_COST_PLAYBOOK.md).Count`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Docs-only - no build required.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] All five pillar sections present (`Spawn policy`, `Context hygiene`, `Skill cost tiers`, `MCP hygiene`, `Measurement loop`).
- [ ] Dev log entry added for `docs/AGENT_COST_PLAYBOOK.md`.

---

## Handoff Notes to Next Phase

Playbook doc exists at `docs/AGENT_COST_PLAYBOOK.md`. Phase 02 wires discoverability so future sessions actually read it.

---

## Rollback Plan

Delete `docs/AGENT_COST_PLAYBOOK.md` - no code, no migration, no user-facing surface.
