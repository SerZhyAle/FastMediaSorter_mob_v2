# Phase 01 - Fix dead routing mechanism

**Strategic spec:** [`../S1341_agent-model-routing-tiers.md`](../S1341_agent-model-routing-tiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none directly (Phase 04 verifies its outcome)
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Remove the inert `model: sonnet` frontmatter key from all 14 command files (confirmed: command frontmatter never routed - 115/115 invocations kept the session model) and record the fact in `docs/AGENT_COST_PLAYBOOK.md` so the mistake is not repeated.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none - foundation phase)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/verify.md` | Modified | -1 line |
| `.claude/commands/spec-arc.md` | Modified | -1 line |
| `.claude/commands/spec-check.md` | Modified | -1 line |
| `.claude/commands/spec-sweep.md` | Modified | -1 line |
| `.claude/commands/quick.md` | Modified | -1 line |
| `.claude/commands/ns.md` | Modified | -1 line |
| `.claude/commands/newlog.md` | Modified | -1 line |
| `.claude/commands/log-reader.md` | Modified | -1 line |
| `.claude/commands/doc-update.md` | Modified | -1 line |
| `.claude/commands/arc.md` | Modified | -1 line |
| `.claude/commands/caveman-commit.md` | Modified | -1 line |
| `.claude/commands/git.md` | Modified | -1 line |
| `.claude/commands/caveman.md` | Modified | -1 line |
| `.claude/commands/catalog.md` | Modified | -1 line |
| `docs/AGENT_COST_PLAYBOOK.md` | Modified | +3-5 lines |

---

## Steps

### Step 01.1 - Remove `model: sonnet` from all 14 command files

**Files:** the 14 command files listed in "Files Touched" above
**Depends on:** - start of phase

**Prompt for developer:**

> Each of the 14 files has a YAML frontmatter block with a line `model: sonnet`. Delete exactly that line from each file's frontmatter; leave every other frontmatter key (`description`, etc.) and the rest of the file untouched. Confirmed via `Grep "^model: sonnet" .claude/commands/*.md` before this step - exactly these 14 files match, no others.

**Verification:**

- `Grep "^model: sonnet" .claude/commands/*.md` returns zero matches (down from 14).
- `Glob .claude/commands/*.md` count unchanged (14 files edited, none deleted/renamed).
- Spot-check one file (`.claude/commands/quick.md`): `Grep "^description:" .claude/commands/quick.md` still matches (frontmatter otherwise intact).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. `Grep "^model: sonnet" .claude/commands/*.md` -> 0 (was 14). `.claude/commands/*.md` count 34, unchanged. `quick.md` description line intact. Files: 14 command files (each -1 line). Dev log recorded via post-change.ps1.

---

### Step 01.2 - Record "command frontmatter does not route" in AGENT_COST_PLAYBOOK.md

**Files:** `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a short note to `docs/AGENT_COST_PLAYBOOK.md` stating: command-file (`.claude/commands/*.md`) frontmatter `model:` key has no routing effect in this harness - only agent-definition (`.claude/agents/*.md`) frontmatter and the Workflow tool's `opts.model` field route. Cite the evidence: 115 of 115 measured command invocations kept the session model regardless of the command's frontmatter. Place it near any existing model/routing/cost guidance in the file, or as a new short subsection if none exists.

**Verification:**

- `Grep "command.*frontmatter.*does not route\|frontmatter.*has no routing effect" docs/AGENT_COST_PLAYBOOK.md -i` matches.
- `Grep "115" docs/AGENT_COST_PLAYBOOK.md` matches (evidence cited).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 2/2 PASS. Added as a new paragraph in "Spawn policy - inline vs subagent" section (line 39), next to the pre-existing `enable_mcp_tools`/`define_subagent` paragraph (a second, independent instance of that same suspect claim - noted, not re-scoped here; already parked as S1348 during Phase 02 planning). Files: docs/AGENT_COST_PLAYBOOK.md (+1 paragraph). Dev log recorded via post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - skip. No Kotlin/build-graph file touched; Validation Ladder type is Doc.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (2 batched entries, one per step).
- [x] If public API changed: skip - no app source touched.
- [x] Phase-boundary audit run - not applicable in the Kotlin-audit sense (no `.kt`/layout/Room/DI/lifecycle change).

---

## Handoff Notes to Next Phase

Phase 02 is independent of this phase's edits (different files: agent definitions, not command files) - no ordering dependency, both must finish before Phase 04's final verification.

---

## Rollback Plan

Revert phase commit(s) - text-only change to 14 command files plus one doc, no data migration or user-facing surface changed.
