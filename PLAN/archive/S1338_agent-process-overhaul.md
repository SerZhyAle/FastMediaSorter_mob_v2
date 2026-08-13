# Specification: S1338 - Agent process overhaul (umbrella)

**Ticket:** S1338
**Status:** Archived
**Priority:** 75
**Date:** 2026-07-31
**Tier:** 3
**Source:** `dev/AGENT_PROCESS_AUDIT_2026-07-31.md`
**Children:** S1339 (bounded rounds), S1340 (rules gate-or-compress), S1341 (model routing tiers), S1342 (canon propagation)
**Tactical plan:** `PLAN/S1338_agent-process-overhaul/INDEX.md`

---

## 0. Origin

Owner asked for an audit of the working process - agents, skills, rules - and then for a full specification to fix everything it found. The audit mined 347 main session transcripts plus 869 nested subagent transcripts (2026-06-30 .. 2026-07-31) through a 40-agent workflow with adversarial verification, and produced 143 findings.

Owner instruction on scope:

> очень важно использовать и внедрить улучшения на всех участках по всем твоим рекомендациям

So this umbrella covers every surviving recommendation. Nothing from the audit is silently dropped; items deliberately not done are listed in section 8 with the measurement that killed them.

---

## 1. The measured problem

Cost is `accumulated context x number of turns`. Nothing else is close.

- Cache reads are **72.4%** of spend; output - all prose, spec text and code - is **11.9%**.
- Every request carries **~271k cached tokens**; 305 cached input tokens ride on each output token.
- The fixed preamble floor is **~64k tokens on every request** = 23.3% of everything billed.
- **6.7% of sessions carry 50% of all cache_read.** 22 of the top 25 invoked `/spec-next`, `/spec-all` or `/spec-prerelease`.
- Compaction is manual in **159 of 161** cases, at a **median of 389,197 tokens** already accumulated.
- Quality splits cleanly by enforcement: **gated rules hold at ~99%, ungated ones at 1-8%.** `/ui-clarify` was invoked once while 33% of owner corrections were about UI placement.
- The ship-to-verify ratio is **2.23:1** - 87 tickets in `BlockNeedUserTest` against 39 `Verified`, with 154 live Timber probes in source. The audit stated 6.9:1 against the same two counts; 87/39 is 2.23, so the ratio was wrong while the counts were right. Corrected here when `scripts/spec_catalog/unverified-backlog.ps1` (phase 05) reproduced the counts and disagreed with the ratio.

The audit's own first pass was wrong by 3.13x because it summed JSONL records instead of unique `requestId`. That correction is work package A and is a prerequisite for judging every other package.

---

## 2. Owner decisions (2026-07-31)

Recorded verbatim, because they set the shape of the work.

- Autonomous loop: **"Сброс по порогу 400К"**, revised to **"хорошо, поменяй 400К на 300К"** after being shown that 400k sits at the existing manual-compaction median of 389k and would therefore change almost nothing. Implemented as a configurable threshold defaulting to **300,000 tokens**. Delegated to S1339.
- Rules: **"Гейты для дорогих, остальное сжать"** - write gates only where a defect actually reached the owner; compress the rest to one line plus a pointer. Delegated to S1340.
- Models: **"только OPUS = интеллект + Sonnet = механика. Fable и Haiku только руками"** - two automated tiers only. Delegated to S1341.
- Packaging: umbrella plus separate tickets for the three large pieces.

---

## 3. Scope

In scope for this ticket: measurement, the build and gate hot path, the closure facade, the read hook, the command surface, agent memory, the missing quality gates, and the small script-contract fixes.

Out of scope, delegated: session boundaries (S1339), the rule text and its enforcement policy (S1340), model and agent tiering (S1341).

Explicitly not in scope: any product code in `app_v2/` or `wear/`, any user-facing string, any release artefact. This ticket changes only how the work is done.

---

## 4. Work packages

### A. Measurement - promote the extractor and fix its three defects

Nothing below can be judged without a trustworthy before/after, and the current extractor is wrong three ways.

- Move `temp/scratch/mine_transcripts.py` to `scripts/metrics/mine-agent-transcripts.py` so the measurement is repeatable rather than scratch. Add `scripts/metrics/agent-cost-report.ps1` as the operator entry point, following the repo's exit-code contract (Rule 7): 0 report written, 1 error, 2 cannot verify.
- Deduplicate assistant usage by `requestId`, keeping the max per id. One API response is written as several JSONL records (thinking, text, each `tool_use`) that repeat the same `usage` object verbatim; forked sessions replay on top. Summing records inflates tokens 3.13x on the main tier and 2.28x on the nested tier, and inflates all counts 1.43x.
- Walk recursively (`os.walk`) so `<session-id>/subagents/**` is included - 869 files, 17.3% of traffic, zero requestId overlap, fully additive. Report the tiers separately as well as combined; a flat average corrupts the headline per-turn figure because subagent conversations are structurally shorter.
- Classify hard failures by `is_error` alone. The current regex over the result body scores a Kotlin file containing `catch (e: Exception)` as a failed Read, over-counting Read failures 24.7x. Keep the regex only as a separately counted soft band restricted to Bash, PowerShell, Agent and Skill payloads - that band is mandatory, because gradle runs backgrounded and a failed build returns a clean `tool_result`.
- Segment on `system` records with `subtype=compact_boundary` and report `compactMetadata.preTokens` percentiles. That series is the acceptance metric for S1339.

Acceptance: re-running over the same window reports ~67,900 requests, ~14.78 G cache_read all-in, 2.71% hard failure rate and 4.18% including the soft band. Deviating from those figures means the extractor is still wrong.

### B. Statusline - report magnitude, not window fraction

`.claude/statusline.ps1` renders `used_percentage` as a bar, so 390k on a 1M window displays as `ctx ###------- 39%`. The moment of maximum cost looks like a third full. The file's own header states its purpose is to tell the operator when to compact.

- Replace the block at lines 20-32. Compute `$tok = [double]$ctx.total_input_tokens` - the field is already dereferenced at line 27, so no new data source is needed - and emit both magnitude and fraction, for example `ctx 396k (40%)`.
- Choose the warning band by `max(absolute, fraction)` so whichever is worse wins. Absolute: under 150k plain; 150-250k `[!]`; over 250k `[!!]`. Fraction: at or above 70% `[!]`; at or above 85% `[!!]`. The fraction band is what protects a 200k-window session, which exists in this corpus and would otherwise sit permanently in the plain band at 75% fill.
- Fall back to `used_percentage` alone when `total_input_tokens` is null or zero, rather than printing `0k`.
- Keep it pure arithmetic. It re-runs in a fresh `pwsh -NoProfile` process on every render, so no file reads and no transcript tailing. Keep `$ErrorActionPreference = 'SilentlyContinue'` so a schema change never blanks the line.

Saving on its own is **zero**, and must be reported as zero - the owner already compacts unaided. Its value is being the sensor S1339 acts on.

Note: the wiring lives in per-machine `.claude/settings.local.json`, so this lands on one machine unless that wiring is promoted.

### C. Build hot path

- `scripts/quality/assert-detekt.ps1` is the only gradle caller in the repo that omits `--configuration-cache`, paying a ~23 s cold configuration phase on every run. Detekt ran ~929 times in the window (519 direct plus 410 inside `post-change.ps1`). Crediting only 10 s of the 23 s gives **~2.6 h/month for a one-line change** - the highest saving-to-effort ratio in the entire audit. Do this first in this package.
- Give every gradle-backed gate a timeout ceiling of 600 s. One `post-change.ps1` run hung for **3 hours and still reported PASS**. The observed tail is 5 runs over 300 s out of 311, so 600 s never fires on a healthy run. A timeout must exit "cannot verify", never PASS.
- Add a `-Wait` mode to `scripts/utils/lock-status.ps1` and to the build lock acquisition, so callers block instead of polling. 793 lock-status polls and 48 hand-rolled `until` loops exist purely because the lock refuses rather than waits.
- Re-scope the "background every gradle target" rule. It is miscalibrated for the fast checks: the agent hand-polls with `cat` and `sleep` (~1,297 polling turns, 81 min/month of literal `sleep`) instead of letting the harness notify on completion. State a duration threshold above which backgrounding is required and below which it is forbidden.
- Verify the local build cache is actually enabled on the dev host. `gradle.properties` claims it is; the resulting warning is filtered out of the log, and every flavor switch currently recompiles from zero (~134 switches in the window).
- Migrate `app_v2` from kapt to KSP for Hilt, Room and Glide. `wear/` already did it and the plugin is on the classpath. ~35% of the measured 44 s compile chain is stub generation plus javac annotation processing, across 1,836 compile-graph runs in the window. This is the one large-effort item in the umbrella and the only one with real regression risk; it must land on its own, behind a full-flavor build proof, and may be split out if it destabilises.
- Add `-Tests` filtering guidance to the unit fast path. 163 of 344 fast-check unit runs used the full suite when a single-class filter was available.
- Enforce the once-per-ticket rule for `catalog_sync.ps1`, the largest non-gradle gate at 12.9 s across 280 runs, by making it detect an already-current index and no-op.

### D. Gate hot path

- Build one single-walk multi-matcher runner replacing the 12 gates that each walk `app_v2/src` independently. Measured: `a.ps1 fg` takes **26.5 s**, of which ~28.8 s of corpus time is duplicated walking plus 14 pwsh cold starts (~5.7 s of pure process startup). One combined pass takes it to **~5 s**, recovering **~3.35 h over 561 fg runs**.
- Fix the `-ChangedFiles` plumbing in `scripts/quality/assert-fast-gates.ps1`. It forwards the parameter to `assert-detekt.ps1` at line 102 and nowhere else. Of the 13 gates in its table at lines 51-81, **five accept `-ChangedFiles`** - `assert-flavor-flags-not-growing`, `assert-neuroslop`, `assert-public-mutable-flow`, `assert-deprecated-pm-flags`, `assert-listener-symmetry` - and **all five are invoked with no arguments at all**, so they run project-wide and go red on any other ticket's in-flight drift. Reported FAIL rate: **42%**. Pass the parameter through conditionally, exactly as the detekt branch already does, so a release or CI run without `-ChangedFiles` keeps the strict project-wide judgement.
- Retire or fold the eleven gates that have never fired in 858 closures and 561 fg runs, costing **8,563 s = 33% of all gate wall clock**. `assert-fgs-notifications` alone burns 3,909 ms per run across 646 runs for zero fires. Fold the `.kt`-scanning ones into the single pass; delete the rest. Simultaneously promote the gates with 60-75% hit rates, which currently run only 4-39 times.
- Ratchet the baselines down after every green run. They are never lowered, so full-scan mode currently ships 10 em-dashes, 5 unsafe collects and 2 `!!` for free.
- Fix the four gates that are red right now and that nothing runs. A red invisible gate trains the agent that gate output is noise - the same failure mode as the 42% fg FAIL rate.
- Add a lexical detekt preflight over changed files for ImportOrdering, MaxLineLength and MagicNumber, which are **58% of all detekt findings**. Each currently costs a ~23 s gradle round-trip to discover. `assert-detekt` rejects **50% of runs**, which is the largest single quality signal in the audit: half the code written here fails the style gate on first submission.
- Give `assert-settings-doc-sync` a delta path. It costs 35 s per run with a 171 s worst case inside an interactive closure.

### E. Closure facade - stop certifying unchecked work

`scripts/post-change.ps1` is the most-executed script in the repo and it currently issues green verdicts on work it never looked at.

- Add `[string[]]$Files` and scope the gates to that whole set. Today `-ScopeToFile` narrows detekt to **exactly one file** while **62% of closures span more** (mean 4.34 files), so a green PASS certifies roughly 23% of the change.
- Validate the file arguments. An unexpanded shell variable currently produces a full green PASS certifying nothing at all - 3 confirmed occurrences in 463 runs.
- Print PASS only when every gate passed. PASS is currently printed on **19% of runs that contain a gate FAIL**, and 66% of callers read only the tail of the output.
- Publish an exit-code contract, per Rule 7, in the repo's most-executed script: 0 all gates passed, 1 a gate failed, 2 could not verify. Callers must be able to distinguish "found a defect" from "did not look".
- Reorder so the gates run before the two mutating steps. 430 failed closures paid 69 min of wasted `catalog-sync` and wrote **183 duplicate changelog rows** for changes that never closed. This also makes the facade idempotent by construction and enables a free `-Plan` mode.
- Make failures self-diagnosing. The detekt gate prints the file and discards the rule and line it already parsed, costing **2.38 gradle re-runs per failure**; `ticket-log-audit` is invoked with `-Quiet`, which suppresses exactly the File:Line list needed to fix it.
- Correct `CLAUDE.md` section 12, which describes `-ScopeToFile` behaviour the script has not had since S0848/S0850. That text sits in the always-on preamble and is why the verdict is trusted.
- Retire the **29 agent-memory files totalling 101,864 bytes** that exist solely to document workarounds for this script's UX. They are the clearest evidence in the audit that a mechanical fix was substituted with institutional memory.

### F. Read discipline hook

The single largest mechanically-gateable read pattern: first touch of a file in a segment, no `offset` and no `limit`, at least 8 KB. **1,226 calls = 10.7% of Reads carrying 43.8% of all Read bytes**, and only 21.7% had a Grep or Glob in the preceding three turns.

- Add a PreToolUse hook on Read, modelled on the two existing global guards `guard-find-command.ps1` and `guard-ps1-in-bash.ps1`: block before the call, exit 2, explain the fix.
- Trigger only when the call has neither `offset` nor `limit` and the target exceeds ~200 lines.
- The escape hatch is mandatory, not optional. An explicit re-issue carrying a large `limit` must pass. Rule 8 requires reading KDoc in the affected area, `/spec-check` audits an implementation end to end, and Rule 2's 1500-LOC ceiling means a fully compliant Kotlin file legitimately reaches ~60 KB.
- The message must license Grep once then one window, not iterative probing. Break-even is 4.42 extra turns per capped read, so probing erodes the benefit.
- Document the policy in `docs/AGENT_COST_PLAYBOOK.md` under "Context hygiene", with the hook as its enforcement. Do not add it to any `.claude/commands/*.md`, where it would sit in the per-turn preamble for zero enforcement.

Net saving ~0.93% of cache_read after charging the overhead. As a prompt line instead of a hook, model it at 10-25% of that: the identical advice already ships in the Read tool schema on every turn and gets **22% compliance**. That number is the argument for the hook.

### G. Command surface

The surface costs little as text, so this package is about routing clarity and drift, not tokens. Deleting the eight never-invoked command files removes 57,919 B from disk but only ~377 tokens from the per-turn floor, because only the one-line descriptions ship until invocation.

- Delete or merge the commands that were never or barely used, except those CLAUDE.md section 3 routes and those the owner named as a preference (`/caveman` stays). Removing them shrinks a 33-way routing decision to roughly 20 non-synonymous triggers.
- Rewrite CLAUDE.md section 3 to match the surviving surface. It currently omits the second-largest cost centre and advertises dead commands. It must also list `/spec-do`, the new unbounded loop variant defined in S1339 section 4.5, beside `/spec-next` as the explicit opt-in - a command that exists but is not routed will not be found when it is wanted.
- Do not count `/spec-do` against the never-invoked commands when pruning. It is deliberately rare by design; its value is being available, not being used.
- Split the six biggest pipeline command files into a driver plus a REFERENCE file read on demand. A skill body is injected in full and permanently on invocation, so a 24 KB command costs ~6.5k tokens for the remainder of the session even when only its first page matters.
- Move literal file templates out of the prompts into `PLAN/_templates/`, the highest duplication in the surface.
- Replace the 17 paraphrases of CLAUDE.md rules inside command files with one-line pointers. The value is removing 17 drifting copies of one rule, not the bytes.
- Delete the stale `build-debug.PS1` instruction in `spec-dev.md`, which contradicts CLAUDE.md on the most expensive operation in the repo.
- Adopt the `run-fastmediasorter` / `document-registry` shape as the standard for new commands, so the split does not decay back.

### H. Agent memory

- Enforce a byte budget on `MEMORY.md` with a new `scripts/quality/assert-memory-budget.ps1`. It is 18,839 B billed on every one of the corpus's turns, and two manual compactions were both undone within a week at a measured regrowth of 1.1 KB/day. Target 9,000 B, with 6,000 B as the stretch.
- Prune the **58 of 230 pointers never opened** across 347 sessions, and the **40% of memory bytes never read once**. Only 20% of sessions perform any recall read.
- Add an expiry mechanism keyed to ticket liveness. **55% of memory bytes are anchored to tickets that no longer exist.**
- Delete the ~20% of memory bytes that restate rules already in the always-loaded preamble. That is double and triple billing of the same instruction.
- Merge the 11 files / 36 KB covering detekt into one. They cross-link into a 2-3 file read cascade on every detekt incident.
- Move `project_launcher_roadmap_greenlit.md` out of memory. It is a shadow release queue living in the wrong storage class, and two transcripts record acting on its stale status.
- Add a staleness check flagging memory files whose named paths no longer exist. Keep it cheap - measured dead-path staleness is low (6 paths), so this guards trust rather than recovering bytes.
- Record the rule that memory must not restate CLAUDE.md, and that the corpus is currently **written 2.3x more often than it is consulted**.

Quality note: memory once wrote a false architectural claim into strategic spec S1233, costing a spec correction plus a compile run to disprove it. The budget is a cost measure; the expiry and the no-restatement rule are correctness measures.

### I. Missing quality gates

These are the gates the owner's own corrections justify, and they are the highest-value part of the umbrella.

- `scripts/quality/assert-window-insets.ps1` for Rule 17. The rule has no gate and the same defect reached the owner **twice**. Runtime is a grep.
- Wire `/ui-clarify` plus a screenshot step into the pipelines that actually build UI. It was invoked **once** while **33% of owner corrections were UI placement**. A rule invoked once in a month is not a rule.
- Require before/after repro evidence on bugfix tickets. "No completion claim without proof" has no gate where it matters most; 39 of 232 active tickets are bugfixes.
- Gate the autonomous loop on the unverified backlog so it stops chaining when `BlockNeedUserTest` exceeds a ceiling. The ratio is **6.9:1** today and 38% of the catalog is shipped-but-unproven. Coordinate with S1339, which owns the loop.
- Scope the document-registry mandate to the pipelines where it is real and trigger it from `post-change.ps1`. It is stated in five always-on places and obeyed at ~0.6-3% of its own cadence, which teaches that mandates are optional. Coordinate with S1340, which owns the rule text.

### J. Script contract fixes

Small, independent, each removing a recurring failure class.

- `dev/CATALOG/scripts/query.ps1` line 33 - give `-Module` a default of `app_v2`. It is mandatory today and only two modules exist. ~85 recorded failures.
- `scripts/document_registry/query.ps1` - "no matches" currently exits 1, and the mandatory loop hits that outcome 39% of the time, so a normal result reads as a failure.
- Give the four sibling spec CLIs one spelling for the same argument and a `-Help` on each. They spell it four ways and none supports `-Help`. ~90 failures.
- Upgrade `scripts/quality/assert-exit-contract.ps1` to require a message with every non-zero exit. 480 failures returned only `Exit code 1` with no reason.
- Correct CLAUDE.md section 7, which teaches PowerShell batching syntax without saying it is PowerShell-tool-only. 137 interop failures.
- Align the Rule 24 text with what `guard-find-command.ps1` actually enforces (the hook requires `-maxdepth`, the rule bans `find` outright) - 134 blocks with zero decay over a month - and make `guard-ps1-in-bash.ps1` heredoc- and quote-aware. It blocked a legitimate Python string literal during this very audit.
- Make status-query scripts return 0 with a status field instead of failing the tool call to report normal state (lock free, device offline).
- Make the spec-file mutators not rewrite a file under an open edit; 44 stale-file Edit failures trace to this.
- Fix `CLAUDE.md:77`, which authorises behaviour the owner banned and Rule 23 blocks mechanically.
- Decide the fate of `AGENTS.md` and `.github/copilot-instructions.md`. Claude Code never loads them, they have drifted, and one contradicts CLAUDE.md. Either sync them mechanically or delete them; leaving a contradicting unsynced copy is the worst of the three.

---

## 5. Sequencing

1. Package A (measurement) and the single line in package C (`--configuration-cache`). Both are trivial; A gates the judgement of everything else.
2. Package D's `-ChangedFiles` plumbing and package E's `[string[]]$Files` plus honest PASS. These stop the two mechanisms that currently produce false verdicts.
3. Package B (statusline), then S1339 - the sensor ships before the thing it senses.
4. Package F (read hook), package D's single-pass runner, package I's insets gate.
5. S1340 and S1341.
6. Packages G, H, J.
7. Package C's KSP migration last, alone, behind a full-flavor proof.

---

## 6. Measurement plan

Re-run package A's extractor two weeks after the first four steps land, and compare against these recorded baselines.

- Median pre-compaction `compactMetadata.preTokens`: **389,197**.
- p90 session request count: **308.6**.
- All-in cache_read per calendar day.
- Hard tool-failure rate: **2.71%**; with the soft band **4.18%**.
- `a.ps1 fg` wall clock: **26.5 s**; fg FAIL rate: **42%**.
- Detekt FAIL rate on `assert-detekt` runs: **50%**.
- `MEMORY.md` size: **18,839 B**.
- Ship-to-verify ratio: **2.23:1** (87 `BlockNeedUserTest` against 39 `Verified`), measured by `scripts/spec_catalog/unverified-backlog.ps1`. The audit's 6.9:1 was an arithmetic error over the same counts.

Honour `docs/AGENT_COST_PLAYBOOK.md` line 84: land the change, then measure. No percentage in this specification is a promise; every one is an estimate carrying the audit's own arithmetic.

### 6.1 Baseline re-measured on the corrected extractor, 2026-07-31

Package A landed and the figures below were produced by `scripts/metrics/agent-cost-report.ps1` over 2026-06-30..2026-07-31. Artifact: `temp/S1338/baseline-2026-07-31.json`.

| Metric | Audit figure | Re-measured | Verdict |
| --- | --- | --- | --- |
| Unique requests | ~67,900 | 64,698 | -4.7%, corpus drift |
| All-in cache_read | ~14.78 G | 14.26 G | -3.5%, corpus drift |
| Hard tool-failure rate | 2.71% | **2.71%** | exact |
| With the soft band | 4.18% | 5.81% | +39%, definitional |
| Median pre-compaction `preTokens` | 389,197 | **389,197** | exact |
| Compactions, manual share | 159 of 161 | 157 of 160 | consistent |
| Nested tier share of cache_read | 17.3% | **17.3%** | exact |
| Read-failure over-count from the regex | 24.7x | 25.1x | consistent |

Three figures reproduce exactly, which is the evidence that the corrected extractor is trustworthy. Two notes on the ones that do not:

- **Corpus drift, not extractor error.** The transcript corpus now holds 323 main and 830 nested sessions against the audit's 347 and 869. An unfiltered run returns the same counts, so sessions have been pruned since the audit. The request and cache_read shortfalls track that reduction and are not a defect.
- **The soft band is wider than the audit's.** This implementation admits Bash, PowerShell, Agent, Task and Skill, per section 4 package A. The audit's band was evidently narrower. The band was left as specified rather than tuned until it matched 4.18% - fitting a measurement to its expected answer is the failure this package exists to correct.

Package A's own acceptance criterion should therefore be read as met: the deviations are explained by the corpus and by a definition, not by arithmetic.

### 6.2 Re-measurement, 2026-08-01

Artifact: `temp/S1338/remeasure-2026-08-01.json`, window 2026-06-30..2026-08-01 against the 6.1 baseline's 2026-06-30..2026-07-31.

**Read this table for what it is not.** Section 6 asks for the re-run **two weeks after** the first four packages land. They landed on 2026-07-31 and 2026-08-01, so this window contains essentially no post-change behaviour - it is the same corpus plus one day, and that one day is this ticket's own work. Every cost figure below is therefore a *carry-forward*, not an effect. The tool-level figures on the right of the table are different: they measure an artefact directly and are true now.

| Metric | Baseline (6.1) | 2026-08-01 | Reading |
| --- | --- | --- | --- |
| Unique requests | 64,698 | 65,882 | +1,184 = this ticket's own day |
| All-in cache_read | 14.26 G | 14.47 G | same, +1 day |
| Hard tool-failure rate | 2.71% | 2.72% | unchanged |
| With the soft band | 5.81% | 5.82% | unchanged |
| Median pre-compaction `preTokens` | 389,197 | **389,197** | unchanged, and expected to be: S1339 owns the threshold and has not landed |
| p90 pre-compaction `preTokens` | - | 648,995 | first record |
| Compactions | 160 | 160 | unchanged |
| `a.ps1 fg` wall clock | 26.5 s | **18.9 s** | -28.7%, measured now, PASS |
| Fast-gate battery verdict | 42% FAIL rate | PASS, 11/11 green | measured now |
| `MEMORY.md` size | 18,839 B | **16,595 B** | -11.9%, phase 08's ratchet |
| Ship-to-verify ratio | 2.23:1 (87/39) | **2.23:1 (87/39)** | unchanged; S1339 owns the loop gate |

Three figures in section 6 cannot be measured yet and are not guessed:

- **Detekt FAIL rate** (was 50%). Needs a corpus of post-change `assert-detekt` runs, and phase 04's lexical preflight has been live for one day. The full-project gate is red today at 649 weighted issues, none of them this ticket's - S1338 changed no `.kt` file.
- **The failed-`Edit` counter-metric** (baseline 249). This is section 7's test of whether the phase 03 read hook pushed the agent into editing against partial context. It needs the same two-week window; one day of data would be noise.
- **KSP compile saving.** Package C's last item is blocked, not skipped - see the audit below.

### 6.3 Re-measurement, 2026-08-02

Artifact: `temp/S1338/transcript_metrics.json`, window 2026-06-30..2026-08-02.
`scripts/metrics/agent-cost-report.ps1`: expected exit 0 | actual **0**.

| Metric | §6.1 baseline (07-31) | §6.2 (08-01) | 2026-08-02 | Reading |
| --- | --- | --- | --- | --- |
| Sessions | 1,153 | - | 885 | corpus pruned again, see below |
| Unique requests | 64,698 | 65,882 | 64,061 | fell on a longer window - pruning, not usage |
| All-in cache_read | 14.26 G | 14.47 G | 14.82 G | +1 day |
| Hard tool-failure rate | 2.71% | 2.72% | 2.78% | flat |
| With the soft band | 5.81% | 5.82% | 5.94% | flat |
| Compactions | 160 | 160 | 156 | pruning |
| Median pre-compaction `preTokens` | 389,197 | 389,197 | **391,161** | flat, and expected to be |
| p90 pre-compaction `preTokens` | - | 648,995 | 648,995 | unchanged |
| Failed `Edit` calls | 249 (baseline) | not measured | **212** of 15,682 | §7 counter-metric, first reading |
| Ship-to-verify ratio | 2.23:1 (87/39) | 2.23:1 (87/39) | **1.54:1 (88/57)** | measured now, real movement |

**The same carry-forward caveat as §6.2 applies, one day further on**, and two entries need naming
rather than glossing.

- **The corpus shrank while the window grew.** 885 sessions against 1,153 two days earlier, on a window
  one day longer. Transcripts are being pruned faster than they accrue, which §6.1 already identified as
  drift rather than extractor error. The consequence is a measurement constraint worth writing down:
  **absolute totals are not comparable across runs of this report - only rates and percentiles are.**
- **The compaction median moved 389,197 -> 391,161, which is no movement.** S1339 owns that threshold
  and has been live for one day; a p50 computed over five weeks cannot show it. This is the metric the
  section 6 two-week window exists to move, and it is the reason this table is not evidence of a saving.

**The section 7 counter-metric reads well, with a caveat.** Failed `Edit` calls are **212** against the
recorded baseline of **249**, so the phase 03 read hook has not pushed the agent into editing against
partial context - the risk §7 named. Take it as reassurance, not proof: the baseline was drawn over
substantially the same corpus, so part of the difference is the same pruning that moved every other
absolute count down. The honest statement is that the failure mode did not appear, not that the hook
improved editing.

**The ship-to-verify ratio moved, and this one is not a carry-forward.** It is a catalog state, not a
transcript statistic, so it reads true today: **2.23:1 -> 1.54:1**. The numerator barely moved (87 -> 88
`BlockNeedUserTest`); the denominator did the work, **39 -> 57 `Verified`**. Phase 05's
`unverified-backlog.ps1` is what makes it observable at all, and it is the gate S1339 stops the
autonomous loop on - so the loop now has more headroom than it did when the backlog measure shipped.

Still not measurable, and still not guessed: the detekt FAIL rate against its 50% baseline needs a
corpus of post-change `assert-detekt` runs, and the phase 04 preflight has been live for two days.

### 6.4 The KSP saving, measured 2026-08-03

The one figure §6.2 and §6.3 both had to leave blank, because package C's last item was blocked
rather than skipped. S1317 landed, the tree compiles, and phase 10 ran. Artifacts:
`temp/S1338/compile-baseline.json` (kapt, 2026-08-02) and `temp/S1338/compile-after-ksp.json`
(KSP, 2026-08-03), both produced by the same script shape - same warmup, same `--rerun` isolation,
three rounds, median not mean.

| Task, standard debug | kapt (10.1) | KSP (10.6) | Delta |
| --- | ---: | ---: | ---: |
| Stub generation | 52.28 s | - | removed entirely |
| Annotation processing | 21.41 s | 12.05 s | |
| **Annotation chain** | **73.69 s** | **12.05 s** | **-83.6%** |
| Kotlin compile | 69.72 s | 49.56 s | -28.9% |
| **Full chain** | **143.41 s** | **61.55 s** | **-57.1%** |
| Annotation share of chain | 51.4% | 19.6% | |

Three things this table is not, each named rather than left for a reader to assume.

- **Only the annotation-chain row is cleanly attributable.** Stub generation is precisely what KSP
  removes, and it is the larger half of the old chain. The 20 s off Kotlin compilation is confounded
  twice: the arms ran a day apart with different daemon and file-system-watch state, and dropping
  kapt also removes generated stubs from that task's own inputs.
- **This is a full rebuild, not the everyday incremental one.** `--rerun` forces a complete run of
  each task. Strategic §4 package C's "~35% of the measured 44 s compile chain" describes the
  incremental chain, so neither its percentage nor its 44 s transfers to this table. What transfers
  is the direction, and it holds.
- **The migration is not free.** `ksp.incremental=false` had to be set - KSP's incremental mode fails
  on this host with `this and base files have different roots`. The everyday incremental cost is
  therefore unmeasured and could be worse than the table implies. It is recorded in
  `dev/TECH_REQUIREMENTS.md` in place of the retired `kapt.incremental.apt` row rather than buried.

---

## 7. Risks

- The KSP migration is the only item that can break the build. It ships alone, last, with a full-flavor proof.
- Retiring never-fired gates trades latency for coverage. A gate that never fired may be preventing a defect class by deterrence rather than detection; fold rather than delete where the scan is cheap inside the single pass.
- The read hook can make a legitimate whole-file read impossible if the escape hatch is implemented as anything weaker than an unconditional pass on explicit `limit`.
- Partial reads raise the risk of editing against incomplete context - an `old_string` unique in the window but not in the file. Watch the failed-Edit rate (baseline: 249 real Edit failures) as the counter-metric for package F.
- Pruning memory can delete a trap that cost real turns to discover. Prune by "never opened in 347 sessions" and by dead ticket anchor, never by age alone.

---

## 8. Deliberately not done

Each of these was proposed by an audit agent and killed under adversarial verification. Recorded so they are not re-litigated.

- Trimming the command surface for token savings. Real saving is 0.137% of the per-turn floor, not the 38x implied by the byte count.
- Optimising prose or output verbosity. Output is 11.9% of the bill, and ~90% of it is thinking tokens that are billed but never persisted, so char-based trimming targets 9% of a 12% bucket. Russian chat is cheap and is a stated owner preference.
- A UserPromptSubmit context-pricing hook. Timing-blind by construction: it fires when the owner types, while the tax accrues inside autonomous blocks. Measured silent in 33 of 59 `/spec-all` blocks, and it cannot act because `/clear` is a harness built-in rather than a tool.
- Suppressing within-segment re-reads. The eliminable pool is 378 calls = 0.084% of cache_read, and the worst-looking case is correct behaviour: micro-windows before an Edit while the file drifts under it. Suppressing it raises the failed-Edit rate.
- Treating tool failures as a cost bucket. Real rate 4.18%, worth ~3.8% of cache_read. Individual fixes in package J are justified on quality, not cost.
- Cutting or expanding subagent usage on cost grounds. The apparent per-turn discount is a length-mix artifact; like-for-like it is 10-21%, and subagents burn 1,667 cache_read per output token against main's 260. The existing spawn policy stands.
- Routing bulk reading to a subagent as a cost trick, and building a guard against whole-file reads of very large files - measurement shows they are not happening, and such a guard would protect 0.0005% of cache_read.
- Decomposing `app_v2/build.gradle.kts` on read-cost grounds. The causal story does not hold and the change is a regression.
- Arguing context hygiene on quality grounds. No quality degradation with context size was measured here. Arguing it on anything but cost attaches the real fix to a false rationale.

Keep, because it pays for itself: the `assert-*` gate mechanism as a class, `enable_mcp_tools: false` on subagents, `run_in_background` for gradle with `temp/BUILD.LOCK`, `mobile-mcp` restricted to exploratory walks, the Sxxxx lifecycle itself, **phase-boundary audits** (median rework after an in-flight correction is 30 turns against 4-7x that for a whole-ticket redo), and the pipeline's 1% subagent retry rate.

---

## 9. Portability constraint

Owner instruction, 2026-07-31:

> После внедрения мне нужно распространить инновации через канон на мои прочие проекты

That turns portability into a design constraint on this ticket, not a later concern. S1342 owns the propagation; this section owns building things that can be lifted.

- Package A's extractor must not depend on anything Android. It reads Claude Code transcripts, which every project has. Write it stack-agnostic from the start rather than porting it later.
- Package B's statusline is already stack-agnostic. Keep it arithmetic-only so it stays that way.
- Package F's read hook is harness-level, not project-level. Author it as a global hook alongside the two that already exist, so other projects inherit it by configuration rather than by copy.
- Package E's closure fixes are project-specific in their implementation but universal in their invariants - a verdict covers the whole change, PASS means every gate passed, a script distinguishes "found a defect" from "could not verify". Write those invariants down as invariants, not as comments inside one script.
- Package D's single-pass runner is a pattern, not a portable artefact. Do not attempt to generalise it; record the pattern and leave the implementation local.
- Package H's memory rules are entirely portable and should be written as rules rather than as one-off pruning.

The rule of thumb, which is also the audit's own central lesson: prefer a skill loaded on demand over a rule read on every turn, and prefer one method that travels over ten copies of a script.

## 10. Acceptance

The umbrella is `Implemented` when every package above is either landed or has an explicit recorded decision not to land it, and `Verified` when the section 6 re-measurement has run and its numbers are written back into this file.

Child tickets close on their own terms.

---

## Last Audit

**2026-08-01 - nine of ten packages landed, one blocked by another ticket.**

| Package | Verdict |
| --- | --- |
| A - measurement | **Landed** (phase 01). Extractor promoted to `scripts/metrics/`, all four defects fixed. Three baseline figures reproduce the audit exactly, which is the evidence it is trustworthy - see §6.1. |
| B - statusline | **Landed** (phase 01). Reports magnitude and fraction, banded by whichever is worse. Its saving is zero and is reported as zero; its value is being the sensor S1339 acts on. Wiring lives in per-machine `settings.local.json`, so it is one machine unless that is promoted. |
| C - `--configuration-cache` | **Landed** (step 01.9), and the spec's arithmetic corrected: measured 23.4 s -> 18.8 s is **4.6 s** per warm run, so **~1.19 h/month**, not the ~2.6 h the spec claimed off a cold-daemon assumption. Still the best ratio in the umbrella for a one-line change. |
| C - timeouts, lock `-Wait`, backgrounding rule, build cache, `-Tests`, catalog_sync | **Landed** (phase 06). Every gradle-backed gate runs under a 600 s ceiling and a timeout is exit 2 CANNOT-VERIFY, never a green verdict on a gate that never ran. The build-cache item closes as a **recorded decision, not a fix**: gradle itself prints that no cache is configured, and the comparison needs a tree that builds. `catalog_sync` no-ops when the index is current - confirmed live today, both modules returned "up to date". |
| C - KSP migration | **Landed** (phase 10, 2026-08-03), and it waited for the right reason. S1317 landed, the tree compiled, and only then did the migration run - so the before/after exists rather than being asserted. All three processors are on KSP, `app_v2/build.gradle.kts` holds zero `kapt` tokens, and the annotation chain measures **73.69 s -> 12.05 s** on a full rebuild, the full chain 143.41 s -> 61.55 s (§6.4). Proof: six flavors compile green, a minified `assembleNoLegalRelease` completes with no R8 warning naming generated DI, Room or Glide code, the exported Room schema is byte-identical to the committed one, and the full unit suite passes. Two costs are recorded, not buried: `ksp.incremental=false` (KSP's incremental mode fails on this host), and the everyday incremental chain is therefore unmeasured. |
| D - gate hot path | **Landed** (phases 02.7 and 04). Twelve lexical rules judged over one walk, `a.ps1 fg` **26.5 s -> 18.9 s** measured today, six baselines ratcheted, `-ChangedFiles` forwarded to all five gates that accept it. The detekt preflight ships measured rather than asserted: ImportOrdering 100/100, MaxLineLength 90.5/100, MagicNumber 80.6/46.3 precision/recall. |
| E - closure facade | **Landed** (phase 02). `-Files` scopes every gate to the whole set, an unexpanded argument is exit 2, PASS prints only when nothing failed, gates run before the mutating steps. Two P1 defects were found by running it rather than by reading it. |
| F - read hook | **Landed** (phase 03). `guard-uncapped-read.ps1` blocks an uncapped Read over 200 lines with an unconditional escape hatch on an explicit `limit`; it fired on this very session's first Read and the hatch worked. Policy documented in `docs/AGENT_COST_PLAYBOOK.md` and in no command file. |
| G - command surface | **Landed with two named residues** (phase 07). Six drivers plus `.claude/reference/`, four skeletons in `.claude/templates/`, 69 rule paraphrases replaced by pointers, CLAUDE.md section 3 completed with the seven commands it omitted. **Residue 1:** the alias merges (`/arc` vs `/spec-arc`, `/quick` vs `/skill-fix` vs `/ns`) are owner-facing and wait on an owner decision; only `/ns` survives every filter as a deletion candidate. **Residue 2:** the `/spec-do` bullet waits on S1339 landing the command file. The audit's "delete the eight never-invoked commands" did **not** survive verification - 16 of the 17 never-typed commands are reachable another way, and `/verify` alone is referenced by 11 command files. |
| H - agent memory | **Landed** (phase 08), with its own premise corrected: only `MEMORY.md` is billed per turn, so the prune took the 14-file intersection of never-read and dead-ticket rather than the ~130-file union. Index **18,839 -> 16,595 B** with every surviving pointer intact. The 9,000 B target ships as a ratchet, not a red gate - closing the rest is an owner decision. |
| I - missing quality gates | **Landed** (phase 05). Rule 17 has a gate, baselined at 28 pre-existing sites and proven to fail on a planted host. `/ui-clarify` and the bugfix repro requirement are refusal conditions on the two transitions that ship UI and close a bugfix, not prose. `unverified-backlog.ps1` publishes the backlog S1339 stops on - and disproved the audit's 6.9:1 ratio, which is arithmetically **2.23:1** over the same counts. |
| J - script contracts | **Landed** (phase 09), except one item delegated. `AGENTS.md` and `.github/copilot-instructions.md` are **delegated to S1340 §3.4** - sync them mechanically or delete them, but do not leave a contradicting unsynced copy. Recorded here so neither ticket re-litigates it. |

**Scope boundaries this plan resolved**, so the child tickets do not fight over them:

- Landed here: the CLAUDE.md corrections that must ship with the script or hook they describe - section 12's `-ScopeToFile` text, line 77's parallel-build contradiction, section 7's PowerShell-tool scope, Rule 24's disagreement with its own hook, and section 3's contents.
- Belongs to S1340: the *compression* of the rules, the rule text for the four new gates, and the fate of `AGENTS.md` / `.github/copilot-instructions.md`.
- Consumed by S1339: `unverified-backlog.ps1`'s exit codes and `-Json` shape are a contract.
- Consumed by S1342: the extractor, the statusline, the read hook, the closure invariants and the memory rules.

**2026-08-03 - the last package closed.** The paragraph that used to sit here said the umbrella could not be `Implemented` because package C's KSP item was blocked on S1317, and that a blocker is not a recorded decision. S1317 landed, phase 10 ran end to end, and phase 11's step 11.3 re-ran: `a.ps1 fk` exit 0, the eleven non-gradle gates exit 0, exit-contract and memory-budget exit 0. Every one of the ten packages now carries a landed verdict or a recorded decision, so §10's first condition is met and the umbrella is `Implemented`.

**One thing this session added beyond the plan, because the phase-boundary audit found it.** Three ProGuard keep rules named a Glide facade (`GlideApp`, `GlideRequest`, `GlideRequests`) that KSP does not generate - and that kapt had emitted one package lower, so the rules matched nothing even before the migration. R8 never complains about a keep rule that matches nothing, which is how a wrong rule survives in a shipped config. Removed, and the minified release re-proved afterwards rather than reasoned about.

**What `Verified` still waits on, and it is not this ticket's work.** §10 makes the umbrella `Verified` only when the §6 re-measurement has run and its numbers are in this file. §6.4 now holds the KSP figures, but §6's own instruction is to re-run two weeks after the first four packages land - that window closes around 2026-08-14. Three figures stay unmeasurable until then: the detekt FAIL rate against its 50% baseline, the failed-`Edit` counter-metric from §7, and any cost figure that is currently a carry-forward rather than an effect.

**Probe tags:** this ticket changes no product code and never entered `BlockNeedUserTest`. `Timber.d("S1338:` in any `.kt`: expected 0.
