# Agent Cost Playbook

Single operational playbook for keeping agent-session cost low without losing execution quality. The source signal is a weekly usage summary of the agent shell (subagent share, context length, parallel sessions, heavy skills, MCP servers) - not Android app profiling. Strategic rationale: `PLAN/S0816_agent-session-cost-optimization.md`. This doc is the *policy*; mechanical changes (e.g. per-skill model tiers) ship as their own child tickets.

---

## Metric -> lever

Each weekly-usage axis mapped to the place it is actually controlled. Only repo-controllable axes can be gated; the rest are advised.

| Usage axis | Lever class | Where it lives | Repo-controllable |
|------------|-------------|----------------|-------------------|
| Subagent-heavy sessions (largest axis) | Spawn policy | this doc + `CLAUDE.md` + agent defs | Yes (behavioural) |
| Usage at >150k context | Context hygiene (`/compact`, `/clear`, `temp/` offload) | this doc; `/compact` action is harness | Advise, not gate |
| 4+ parallel sessions | Queue instead of fan-out | operator habit | Advise, not gate (external) |
| `/spec-dev` weight | Skill cost tier (`model:` frontmatter, narrower scope) | command frontmatter | Yes (mechanical) |
| `mobile-mcp` weight | MCP hygiene (`adb.ps1`/Maestro first, `/compact` to flush) | this doc; server disable is harness | Advise, not gate |

Three of five axes have a real in-repo lever (spawn policy, skill model tier, MCP routing). Two (parallel sessions, the >150k context floor) are operator/harness-side - advise only.

---

## Spawn policy - inline vs subagent

The largest cost axis. Default to inline; spawning is the exception, not the reflex.

**Inline** when the answer is a single fact or edit reachable in roughly **<=3 targeted tool calls** and you already know (or can cheaply guess) the file, symbol, or value. Catalog/grep lookups, one-file reads, a known edit - all inline.

**Subagent** when ANY of:

- answering means sweeping *many* files or directories and you only need the conclusion, not the dumps (delegate so the file content never enters your context);
- the work is an *independent* branch that runs in parallel with other work;
- it produces an *isolated artifact* (research report, audit) that need not stay raw in the main context.

**Hard rule:** never spawn a subagent for a lookup you could resolve inline. A subagent runs its own request fleet; its setup cost dominates a cheap lookup. When you do spawn, the agent's final message is the result you keep - relay the conclusion, not the file dumps.

---

## Context hygiene

Long sessions are more expensive even when cached. Keep context lean:

- `/compact` mid-task at natural boundaries (a phase done, a sub-investigation closed) - not in the middle of a reasoning chain.
- `/clear` when switching to an unrelated task - do not carry a finished task's context into a new one.
- Offload raw artifacts - build output, logcat dumps, large file contents - to `temp/` and reference the path, instead of holding the raw text in chat. The working tree and `temp/` are the durable store; the chat is not.
- Close stale session branches rather than letting them accumulate.

---

## MCP hygiene

`mobile-mcp` results stay sticky in context for the rest of the session, so every walk has a lasting cost. Route by cheapest sufficient proof:

- **`adb.ps1`** (`scripts/devtest/adb.ps1`, `.\a.ps1 adb <verb>`) for deterministic chores: launch, screenshot, prefs read/write, log tail/grep, fixed-coordinate tap/text, install. This is the default for one-off device work.
- **Maestro** (`scripts/devtest/maestro/`) for repeatable multi-step flows that will be re-run.
- **`mobile-mcp` ONLY** for exploratory, agent-driven UI walks where element discovery or dynamic coordinates cannot be scripted up front.
- Because MCP results are sticky, bound the mobile-mcp window and `/compact` immediately after the walk to flush the accumulated tool results.

---

## Skill cost tiers

`model:` frontmatter is already honored and in active use, so cheaper tiers are a mechanical lever, not a wish:

- Commands already on `sonnet`: `arc`, `catalog`, `log-reader`, `spec-arc`, `spec-check`, `spec-sweep`, `verify`.
- Agents: `android-solution-researcher` + `friendly-android-doc-writer` = `sonnet`; `android-kotlin-developer` + `android-rd-specialist` = `inherit`.

Levers:

- Add a cheaper `model:` tier to skills whose work is mechanical/structured rather than deep-reasoning.
- Scope-down by invocation: `/spec-dev <id> --phase NN` or `--step NN.M` runs a slice instead of the whole plan.
- Orchestrators can route mechanical sub-stages to a cheaper subagent model.

**Caution:** do NOT blanket-downgrade reasoning-sensitive orchestrators - `/spec-dev`, `/spec-all`, `/spec-tech`. Implementation and design correctness are reasoning-sensitive; downgrade only clearly-mechanical leaf skills.

Applied (S0825) - mechanical leaf skills on `sonnet`: `caveman-commit`, `caveman`, `quick`, `ns`, `git`, `doc-update`. Held on the strong model by design: `skill-fix` (bug diagnosis), `caveman-review` (review = bug-catching), all orchestrators and release routes.

---

## Measurement loop

Do not promise exact token savings before a reproducible before/after exists (strategic §2 non-goal). The S0268 continuity layer is the measurement base:

- `scripts/agent_continuity/request-log.ps1` - appends one JSONL line per significant session to `dev/agent-continuity/requests.jsonl`.
- `scripts/agent_continuity/request-digest.ps1` - prints a ranked profile over that log plus recent `PLAN/S*.md`.
- The external weekly usage summary is the outer before/after check that validates whether the rules above actually moved the axes.

---

## Follow-up

Per-pillar improvements are parked as individual `/spec-draft` child tickets - not a separate heavyweight backlog file (that would contradict this playbook's own thesis). The spec-catalog is the backlog; `scripts/spec_catalog/search.ps1` dedups by symptom.

- Skill-cost-tier `model:` assignment for clearly-mechanical leaf skills - done in S0825 (Verified).
