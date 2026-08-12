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

**Subagent MCP isolation:** when defining a subagent via `define_subagent`, always set `enable_mcp_tools` to `false` unless the subagent specifically needs to execute exploratory UI walks on the device (highly rare). This prevents spawning duplicate Node/MCP server processes.

**Command frontmatter does not route (S1341, 2026-08-01).** A `model:` key in a `.claude/commands/*.md` file's frontmatter has no routing effect in this harness - only `.claude/agents/*.md` frontmatter and the Workflow tool's `opts.model` field actually select a model. Evidence: 115 of 115 measured command invocations kept the session model regardless of the command's own frontmatter. 14 command files carried a dead `model: sonnet` line for this reason until S1341 removed it - do not add one back expecting it to route anything.

---

## Context hygiene

Long sessions are more expensive even when cached. Keep context lean:

- `/compact` mid-task at natural boundaries (a phase done, a sub-investigation closed) - not in the middle of a reasoning chain.
- `/clear` when switching to an unrelated task - do not carry a finished task's context into a new one.
- Offload raw artifacts - build output, logcat dumps, large file contents - to `temp/` and reference the path, instead of holding the raw text in chat. The working tree and `temp/` are the durable store; the chat is not.
- Close stale session branches rather than letting them accumulate.

### Reading a large file

First touch of a file goes: one `Grep` or `Glob` to locate the region, then **one** `Read` carrying an explicit `limit` (and `offset`) wide enough to cover it. One narrowing step, then one window - iterative probing costs more turns than the whole-file read it replaced.

Measured on the 2026-06-30..2026-07-31 corpus: uncapped first reads of files over 8 KB were 10.7% of all `Read` calls and carried **43.8% of every byte read**, and only 21.7% of them had a `Grep` or `Glob` in the preceding three turns.

Enforcement is `guard-uncapped-read.ps1`, a `PreToolUse` hook on `Read` in the per-machine Claude home (`~/.claude/hooks/`, wired in `~/.claude/settings.json`). It **rewrites** such a read rather than refusing it: a `Read` that has neither `offset` nor `limit`, against a file longer than 200 lines, gets `limit: 800` injected into its input and proceeds. When the file is longer than that window the hook also returns an `additionalContext` line naming the file's real length and how many lines are not shown, so a partial read is never silent. Everything else is passed through untouched - including any read that names an explicit `limit`, however large. That escape hatch is unconditional on purpose: reviewing the KDoc of an affected area (Rule 8), auditing an implementation end to end (`/spec-check`) and opening a compliant 1500-LOC Kotlin file are all legitimate whole-file reads.

Agent-infrastructure directories are exempt outright - `.claude/commands/`, `.claude/skills/`, `.claude/templates/`, `.claude/reference/`, `.claude/agents/`. A command driver or a skill is meaningless to read partially, since a skipped fragment is a skipped step of the procedure.

The hook refused rather than rewrote until S1594 (2026-08-12). It fired 381 times in the week of 2026-08-05, and **31.8% of those blocks were answered by re-reading the same file with `limit >= 1500`** - the whole thing anyway. The turn was spent and no context was saved. A `PreToolUse` hook can return `updatedInput` and modify the tool input rather than only allow/deny/ask, so the window is now injected instead of demanded: the saving survives and the 381 turns disappear. The window of 800 comes from what the model actually asked for after a block - 22.4% re-read with under 300 lines and 45.5% with 300-799. Contract tests: `.claude/hooks/global-hook-tests/Run-GuardUncappedRead-Tests.ps1`.

The same advice already ships in the `Read` tool schema on every turn and gets 22% compliance. That gap - not the byte count - is why this one is a hook and not a line of prose in a command file, where it would sit in the per-turn preamble enforcing nothing.

Counter-metric: partial reads raise the risk of an `old_string` that is unique in the window but not in the file, so watch the failed-`Edit` rate (baseline 249 real failures) alongside the read volume.

---

## Agent-memory hygiene

Three rules, written portably because they hold in any project with a persistent agent memory.
Enforcement for the size consequence is `scripts/quality/assert-memory-budget.ps1`, in the fast-gate
batch, ratcheting the index size down and never up.

- **Memory must not restate CLAUDE.md or any always-loaded text.** A rule in the preamble is billed
  on every turn already; a memory file repeating it is billed a second time and drifts from the
  original independently.
- **A memory anchored to a ticket expires with that ticket.** 52% of memory bytes here reference
  tickets that are Archived or gone from the catalog. The gate reports those files; a memory whose
  ticket is dead and which no session has re-read is a deletion candidate.
- **A memory that will not be read is not worth writing.** The corpus is written about 2.3x more
  often than it is consulted, and only ~20% of sessions perform any recall read at all. Write for
  the future session that will hit the same trap, not to record that the work happened.

Two facts that shape how the rules are applied here, measured rather than assumed
(`temp/S1338/memory-usage.json`, `scripts/metrics/mine-memory-usage.py`):

- **Only the index is billed per turn.** `MEMORY.md` is injected into every turn; the other ~220
  files are read on demand. Deleting a detail file therefore saves nothing that is billed and loses
  the trap it records. Shrink the index; keep the traps.
- **"Never read" is rarer than it looks.** 17.4% of bytes were never read by any session, not 40% -
  the 40% band is "read in at most one session", which is usually just the session that wrote it.

Quality note, not cost: memory once wrote a false architectural claim into strategic spec S1233,
costing a spec correction plus a compile run to disprove it. The budget is a cost measure; the
expiry rule and the no-restatement rule are correctness measures.

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

Do not promise exact token savings before a reproducible before/after exists (strategic §2 non-goal). The measurement base is the session transcript corpus under `~/.claude/projects/`, mined directly:

- Tool calls, their arguments and their failures are all in the transcripts, deduplicated by `requestId`. That is what the 2026-08-12 process audit measured, and what produced S1594-S1599.
- An execution that goes through a script leaves its arguments in the transcript, which is why `scripts/spec_catalog/plan-tick.ps1` takes an explicit step list: the invocation is itself the record of which steps ran.
- The external weekly usage summary is the outer before/after check that validates whether the rules above actually moved the axes.

The S0268 request log and its digest used to be named here as the measurement base. They were invoked zero times in the audited week and were removed by S1596; a measurement base nobody writes to measures nothing.

---

## Follow-up

Per-pillar improvements are parked as individual `/spec-draft` child tickets - not a separate heavyweight backlog file (that would contradict this playbook's own thesis). The spec-catalog is the backlog; `scripts/spec_catalog/search.ps1` dedups by symptom.

- Skill-cost-tier `model:` assignment for clearly-mechanical leaf skills - done in S0825 (Verified).
