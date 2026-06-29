---
name: welcome-process-consolidation
description: Owner welcomes cutting/merging workflow ceremony and authorizes editing CLAUDE.md, agent defs, and skill files to do it
type: feedback
---

Owner actively wants the spec/dev workflow leaner and treats the agent's own governing surface - `CLAUDE.md`, `.claude/agents/*.md`, `.claude/commands/*.md` - as editable, not just project scripts.

**Why:** On 2026-06-18 the owner asked whether the spec pipeline had too much "paperwork/water work" and, on a 6-point consolidation proposal, said "fix everything." That pass rewrote `CLAUDE.md §12`, both impl agent defs, `/spec-next`, `/spec-all`, added `scripts/spec_catalog/spec-next-preflight.ps1`, and added `-ListAreas` to `all_features/add.ps1`. The motivation was token cost, failure surface, and duplicate re-reads across skills - not wall-clock.

**How to apply:** When you spot redundant ceremony (duplicate per-file journaling, re-reads of the same spec/catalog across chained skills, exploratory queries a script flag could kill, hand-rolled steps a facade already covers), propose a concrete consolidation and, on approval, edit the rule/skill/agent-def directly. Keep read-only vs mutation boundaries intact (e.g. a preflight stays read-only; the skill owns the writes) and validate every touched script to exit 0. Distinct non-trivial findings still get parked via `/spec-draft` rather than folded in (cf. [[search-duplicates-by-symptom]]).
