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

**Subagent MCP isolation:** a subagent is defined by a `.claude/agents/*.md` file and spawned with the `Agent` tool's `subagent_type`; there is no `define_subagent` tool, so the allowlist can only be written in that file. Always supply an explicit `tools:` frontmatter allowlist that omits MCP tool names, unless the subagent specifically needs to execute exploratory UI walks on the device (highly rare). This prevents spawning duplicate Node/MCP server processes. (S1348: `enable_mcp_tools` is not a real Claude Code option - do not use it; two fictional API names in one line is how that error kept propagating.)

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

## Always-loaded context budget

`CLAUDE.md`, `AGENTS.md` and the active agent definition are injected into **every** request. Their
bytes are not billed to the session that needed them - they are billed to the whole corpus, forever.
Enforcement is `scripts/quality/assert-always-loaded-budget.ps1`, in the fast-gate batch and in
`post-change.ps1`, with one ceiling per file in `always-loaded-budget-baseline.txt` beside it. The
ceiling drops on a run that passed (`-UpdateBaseline`) and the gate refuses to raise one.

Measured 2026-09-04 (S2513, 78850 deduplicated requests over 770 sessions): the fixed preamble is
**75702 tokens per request and 37.8% of all billed `cache_read`**, against 23.3% five weeks earlier.
The share grew from both ends - the preamble gained 18% while the average request shrank 27%, because
the earlier accumulation tickets worked and what they left behind is the floor. Inside the floor the
growth sits in one file: `CLAUDE.md` reached 67251 B, a 2.35x gain in 35 days at ~770 B/day, which is
24.6% of the floor and 9.3% of the whole bill.

Why a ratchet and not a rule or a cleanup: both were tried. The rule form is ungated, and the
2026-07-31 audit measured ungated rules holding at 1-8% against ~99% for gated ones. The cleanup form
ran three times - S1340 compressed `CLAUDE.md` on 2026-08-01, and `MEMORY.md` was compacted twice -
and all three were undone, within a month and within a week respectively. Regrowth stopped only where
a ratchet judged the file.

**What the ceiling is for, and what it is not for.** It budgets the weight of the RATIONALE, never
the number of rules. 25.9% of `CLAUDE.md` - 17398 B across 86 sentences - is incident narrative:
dates, measured values, ticket ids. That text is needed when a rule is broken and when it is about to
be changed, and in no other request. So it moves into the refusal text of the gate that enforces the
rule, which is read exactly at the moment of a violation and never otherwise. A rule whose
enforcement is not mechanical keeps its rationale where it is - there the rationale IS the mechanism.
Deleting a rule to fit a ceiling is forbidden outright (S2517 ADR-4): it silently returns the exact
cost the gates exist to avoid.

The upper bound on what any repo-side change can recover is **14.0% of the bill** - 28.1k of the
75.7k-token floor is repo- or owner-authored, the rest is the harness's own system prompt and tool
schemas. A proposal promising more than that has an arithmetic error. And this is a bill lever only:
context correlates with turn latency at +0.065 against +0.681 for output volume (2026-08-28), so
nothing here is a speed improvement and it must not be sold as one.

---

### Path-scoped rules are the lever the budget gate points at (S2521)

Claude Code loads `.claude/rules/*.md` lazily when the file carries `paths:` frontmatter: the rule enters context the first time Claude reads a file matching one of its globs, and not before (`code.claude.com/docs/en/memory`, "Path-specific rules"). `@path` imports do NOT do this - an imported file is expanded at launch and costs exactly what inline text costs - and a rules file without `paths:` is loaded at launch too. Block-level HTML comments in `CLAUDE.md` are stripped before injection, so a maintainer note in a comment costs nothing; the budget gate measures the injected bytes, not the file.

Measured 2026-09-04 on the first split: `CLAUDE.md` went from 65 383 B to the size the baseline now records, with every numbered statement and every rule number kept in place. Four detail files exist - `spec-catalog.md` (`PLAN/**`), `android-source.md` (`app_v2/**`, `wear/**`), `agent-chat.md` (the chat and identity scripts), `command-authoring.md` (`.claude/commands/**` and its neighbours). The rule for a new one: the statement stays in `CLAUDE.md` with its number, the mechanism and the incident go to the detail file, and `paths` names the files a session must have read before the detail can matter. A runtime without lazy loading reads all of them at start (`AGENTS.md` section 1), which is why the detail files carry no statement of their own - a runtime that misses them misses only the reasons.

## Agent-memory hygiene

Three rules, written portably because they hold in any project with a persistent agent memory.
Enforcement for the size consequence is `scripts/quality/assert-memory-budget.ps1`, in the fast-gate
batch, ratcheting the index size down and never up.

- **Memory must not restate CLAUDE.md or any always-loaded text.** A rule in the preamble is billed
  on every turn already; a memory file repeating it is billed a second time and drifts from the
  original independently.
- **A memory does NOT expire with the ticket that taught it - only its author can say when it
  expires.** This rule used to read the other way, and the gate used to report every file whose
  every `Sxxxx` was Archived or absent. S2308 measured that check on this corpus (2026-09-01): it
  fired on 269 of the 342 ticket-anchored files and 3 of them were genuinely dead, about 1%
  precision, because a memory is written precisely so its lesson outlives the incident - ticket
  liveness measures the age of an anchor, not the decay of a claim. Narrowing did not rescue it
  (`type: project` plus unreachability reached 7%, a vanished `temp/` path 11%). The check was
  removed and replaced by one that reads the author's own declaration - "delete this memory when
  ..", "this snapshot decays" - which measured 100%. So: when a memory records a moment rather than
  a durable fact, write its expiry condition into the file, because nothing else can infer it.
- **A memory that will not be read is not worth writing.** The corpus is written about 2.3x more
  often than it is consulted, and only ~20% of sessions perform any recall read at all. Write for
  the future session that will hit the same trap, not to record that the work happened.

Two facts that shape how the rules are applied here, measured rather than assumed
(`temp/S1338/memory-usage.json`, `scripts/metrics/mine-memory-usage.py`):

- **Only the index is billed per turn.** `MEMORY.md` is injected into every turn; the other files
  are read on demand - ~220 of them when this was written, 408 on 2026-09-01. Deleting a detail file
  therefore saves nothing that is billed and loses the trap it records. Shrink the index; keep the
  traps.
- **"Never read" is rarer than it looks.** 17.4% of bytes were never read by any session, not 40% -
  the 40% band is "read in at most one session", which is usually just the session that wrote it.

Quality note, not cost: memory once wrote a false architectural claim into strategic spec S1233,
costing a spec correction plus a compile run to disprove it. The budget is a cost measure; the
expiry rule and the no-restatement rule are correctness measures.

---

## MCP hygiene

`mobile-mcp` results stay sticky in context for the rest of the session, so every walk has a lasting cost. Route by cheapest sufficient proof:

- **`adb.ps1`** (`scripts/devtest/adb.ps1`, `.\a.ps1 adb <verb>`) for deterministic chores: launch, screenshot, prefs read/write, log tail/grep, tapping by resource-id (`tap-id`) or by label (`tap-label`), text, install. This is the default for one-off device work. Aim a tap by id first: a label is translated, so a label-aimed call passes only on the locale it was written on (S1879), and a remembered coordinate goes stale the moment the list scrolls (S1847).
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
