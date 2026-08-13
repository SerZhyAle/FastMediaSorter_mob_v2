# Research 01 - Usage levers, inline/subagent boundary, MCP scope

**Spec:** S0816
**Date:** 2026-06-30
**Scope:** Resolve §6 open items 1-5 from in-repo evidence.

---

## §6.1 - Metric -> controllable lever mapping

Each weekly-usage metric mapped to the place it is actually controlled.

| Usage metric (last 7d) | Lever class | Where it lives | Repo-controllable? |
|------------------------|-------------|----------------|--------------------|
| 70% subagent-heavy sessions | Spawn policy (doc-rule + agent guidance) | `docs/AGENT_COST_PLAYBOOK.md` + `CLAUDE.md` + agent defs | Yes (doc/behavioural) |
| 67% usage at >150k context | Context hygiene (`/compact`, `/clear`, offload raw to `temp/`) | playbook doc-rule; `/compact` action itself is harness | Partial - rule in repo, trigger is operator/harness |
| 10% with 4+ parallel sessions | Queue sessions instead of fan-out | operator habit | No - external (operator) |
| 11% from `/spec-dev` | Skill cost tier: `model:` frontmatter + narrower invocation scope | `.claude/commands/spec-dev.md` frontmatter | Yes (mechanical) |
| 23% from MCP `mobile-mcp` | MCP hygiene: prefer `adb.ps1`/Maestro, `/compact` to flush sticky results, disable unused servers | playbook doc-rule; server disable is harness | Partial - rule in repo, disable is external |

Conclusion: 3 of 5 axes have a real in-repo lever (spawn policy, skill model tier, MCP routing rule). Two (parallel sessions, harder context-length floor) are operator/harness-side and can only be *advised*, not *gated*, from the repo.

---

## §6.2 - Inline vs subagent boundary

Evidence: `Agent` tool guidance ("For a single-fact lookup where you already know the file, symbol, or value, search directly"), `dev/AGENT_WORKFLOW.md` §8.1 (parallel subagents for *independent* lookups), and existing agent-memory notes on subagent cost.

**Heuristic (reproducible):**

- **Inline** when the answer is a single fact/edit reachable in roughly <=3 targeted tool calls and you already know (or can cheaply guess) the file/symbol/value.
- **Subagent** when ALL-or-any of:
  - answering means sweeping *many* files/dirs and you only need the conclusion, not the dumps (delegate -> keep conclusion, not file content in context);
  - the work is an *independent* branch that can run in parallel with other work;
  - it produces an *isolated artifact* (research report, audit) that need not stay raw in the main context.
- **Never** spawn a subagent for a lookup you could resolve inline - the subagent runs its own request fleet and its setup cost dominates a cheap lookup.

This is the single largest axis (70%); the boundary above is the primary deliverable.

---

## §6.3 - Cheaper heavy skills (mechanically supported?)

**YES - already wired.** `model:` frontmatter is honored by the skill/command format and is in active use:

- Commands with `model: sonnet`: `arc`, `catalog`, `log-reader`, `spec-arc`, `spec-check`, `spec-sweep`, `verify`.
- Agents: `android-solution-researcher` + `friendly-android-doc-writer` = `sonnet`; `android-kotlin-developer` + `android-rd-specialist` = `inherit`.

Levers:

- Add `model:` (cheaper tier) frontmatter to heavy skills whose work is mechanical/structured rather than deep-reasoning.
- Scope-down by invocation: `/spec-dev <id> --phase NN` / `--step NN.M` runs a slice instead of the whole plan.
- Orchestrators (`/spec-dev`, `/spec-all`) can route sub-work to cheaper subagent models per stage.

**Caution:** do NOT blanket-downgrade `/spec-dev`/`/spec-all` orchestration model - implementation correctness is reasoning-sensitive. Downgrade only clearly-mechanical stages/skills; keep the design/audit reasoning on the stronger tier. This boundary is a tactical decision, not a strategic one.

---

## §6.4 - When mobile-mcp is truly required

Evidence: `scripts/devtest/adb.ps1` (`.\a.ps1 adb <verb>`) already covers `devices/props/current/launch/stop/clear/install/shot/log/tap/text/key/prefs/shell`; Maestro harness under `scripts/devtest/maestro/` for repeatable flows; `prerelease-log-audit.ps1` for log proof. CLAUDE.md §9 already states "Prefer over raw adb for one-off work; mobile-mcp stays for agent-driven UI walks, Maestro for repeatable flows."

**Routing rule (mobile-mcp = 23%, sticky in context):**

- **Use adb.ps1** for deterministic chores: launch, screenshot, prefs read/write, log tail/grep, fixed-coordinate tap/text, install.
- **Use Maestro** for repeatable multi-step flows that will be re-run.
- **Use mobile-mcp ONLY** for exploratory, agent-driven UI walks where element discovery / dynamic coordinates are not scriptable up front.
- Because MCP tool results persist in context for the rest of the session, bound the mobile-mcp window and `/compact` immediately after the walk to flush results.

---

## §6.5 - Follow-up contour for cost optimizations

Decision: **park individual `/spec-draft` per finding** + record per-pillar child tickets under strategic §10. Do NOT create a separate heavyweight backlog file - that would contradict this spec's own thesis (avoid new heavy workflows). The existing spec-catalog *is* the backlog; `search.ps1` dedups by symptom.

---

## Measurement loop (existing infra, §5.5 of this spec)

S0268 continuity layer already provides the measurement base:

- `scripts/agent_continuity/request-log.ps1` - append one JSONL line per significant session to `dev/agent-continuity/requests.jsonl`.
- `scripts/agent_continuity/request-digest.ps1` - ranked profile over that log (+ recent `PLAN/S*.md`).

Note (out of scope, dedup-checked): `request-digest.ps1` still references retired `dev/FUNCTIONALITY.log` (S0489 -> `docs/ALL_FEATURES.jsonl`); it tolerates absence, so no functional break. Candidate for a separate `/spec-draft` if it ever surfaces noise.
