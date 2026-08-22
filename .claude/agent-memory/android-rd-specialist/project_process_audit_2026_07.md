---
name: process-audit-2026-07
description: The 2026-07-31 agent-process audit - its findings, its open owner questions, and what it forbids re-proposing
metadata:
  type: project
---

`dev/AGENT_PROCESS_AUDIT_2026-07-31.md` holds a measured audit of how the owner and the agents work together (347 main + 869 nested transcripts, 40-agent workflow, 143 findings, 26 adversarially verified).

The core economics: cost is `accumulated context x turns`. Cache reads are **72.4%** of spend, output only 11.9%; ~**271k cached tokens ride on every request**; the fixed preamble floor is ~64k = 23.3% of everything billed; and **6.7% of sessions carry 50% of all cache_read**, nearly all of them `/spec-next` / `/spec-all` loops that compact at a 389k median instead of resetting.

**Why:** the owner asked what to optimise. The answer turned out to be session boundaries and mechanical enforcement, not prose, re-reads, subagents or MCP - all four were measured and refuted as non-problems. Do not re-propose them; section 5 of the report lists each with its counter-measurement.

**How to apply:**
- Before proposing any cost optimisation, check section 5 - the idea may already be a measured dead end.
- The report's `[U]` items were never adversarially verified. Treat them as leads and re-measure before acting.
- The single strongest structural finding: **gated rules hold at ~99%, ungated ones at 1-8%**. Prefer a hook or an `assert-*` gate over a sentence in CLAUDE.md, always - the same advice already ships in the Read tool schema every turn and gets 22% compliance.
- The owner answered the blocking questions on 2026-07-31 and the work is specced as **S1338** (umbrella) with **S1339** bounded rounds, **S1340** rules gate-or-compress, **S1341** model routing, **S1342** canon propagation - all `Approved`, release package 30. His rulings: reset the loop at a **300k context threshold** (he first said 400k, then accepted that it sat at the existing 389k compaction median); **gate only what reached him, compress the rest**; **Opus = judgement, Sonnet = mechanics, Fable and Haiku by hand only**.
- An agent cannot execute `/clear` or `/compact` - they are harness built-ins. Any "reset the context" design has to be a self-halt with a resume handle, and the threshold check reads the live transcript's newest `cache_read_input_tokens`.

**Retrospective run 2026-08-05 (5 days post-change, adversarially verified - 4 of 5 findings refuted or corrected):**
- Cost effect is **null within noise**. Per-turn context is flat *within each tier* (main 282.4k -> 288.3k, nested 116.4k -> 112.6k); the aggregate rise is a tier-mix shift - subagent delegation halved, 35.9% -> 17.8% of turns. At PRE's tier mix POST is +1%. Every "-30% for bounded work" style split is a selection artifact: the same procedure on the untouched PRE window yields -24.6% with nothing changed. Always demand a placebo split on PRE before believing a POST split.
- What did move, verifiably: `/compact` 79 -> 1 and `/clear` 4 -> 20, compactions 5.3/day -> ~0, longest block 1473 -> 358 turns, assert-* gates 34 -> 44.
- The always-on preamble floor moved -2.46% (85,822 -> 83,707 B) = ~0.23% of the bill - two orders of magnitude below the corpus's own daily variance, so undetectable by construction. ~40k of the ~64k floor is harness-owned; any repo-side cut is capped at ~37% of it.
- **The owner's two goals are decoupled and this is the headline.** Output (all prose, spec text, every written artifact) is 11.9% of the bill, so culling artifacts is a speed/sanity lever, not a token lever. Do not sell one as the other.
- **RETRACTED same day: "46.5% of specs are never re-read" and "42% of tactical plans are never opened" are both instrument artifacts.** Counting consumption by the **Read tool alone is invalid** in this repo. Read.file_path lands inside a tactical dir 740 times against Edit.file_path 2378 - and `/spec-dev` *executes* a step by Edit-ing its `[ ]` -> `[x]` checkbox, so the act of consumption is an Edit, not a Read. Shell reads (`head`, `sed`, `Get-Content`, `Select-String`) carry no `file_path` field at all and are invisible to a tool-name scan. Corrected never-consumed rate for tactical plans: **3 of 78 = 3.8%** (9-11.5% under the strictest non-Edit variants), not 42.1%. Zero of them are `BlockNeedUserTest`. Second, independent error: 17 of the 126 "tactical directories" hold no plan at all - crash logs, screenshots, an owner voice note - so the denominator was contaminated too. Only 110 of 460 tickets (23.9%) ever get a tactical plan: rare-and-used, not common-and-ignored, and 43% of used plans are read by a *different session* than wrote them (up to 9 days later), so the artifact is a real cross-context channel.
- **How to apply:** before claiming any artifact is unread, enumerate every consumption channel first (Read, Edit, Write, Grep, shell content verbs, subagent prompts) and report the sensitivity across them. A tool-name scan alone is off by an order of magnitude here. The owner's instinct that his parked tickets are fine was correct and my contrary recommendation was withdrawn.
- Still standing (these do not depend on the broken instrument): the tactical template is byte-for-byte unchanged (INDEX.md + 5 PHASE files); `/quick` has **never been invoked once** in the whole corpus and `/skill-fix` ~0 - the cheap tiers are dead letters, so "pick the smallest tier" is an ungated rule behaving exactly like every other ungated rule.
- Device-test backlog as of 2026-08-05: **97 `BlockNeedUserTest`, 2 Verified, ship-to-verify 48.5:1** (the 2026-07-31 audit recorded 87 and 6.9:1). 165 `Timber.d("Sxxxx:` probe lines from 87 tickets are live in source - R8 `-assumenosideeffects` strips `Timber.d`/`v` from release bytecode, so nothing reaches users; the cost is source clutter and debug logcat noise only. Do not overstate it as shipped-code risk.
- The `.claude/reference/` split is only a win where the companion goes unread - true for `/spec-next` alone (~-16% injected); the other five companions are read at >=1x per invocation, i.e. strictly worse than the single file they replaced.
- S1341 routing: agent frontmatter is fixed, but product days reverted to ~97% Opus - the Sonnet share lives entirely in the two meta days.
- **Do not re-measure before ~2026-08-15.** Five days cannot clear the PRE window's own variance; POST sits inside PRE's contiguous 5-day range on every headline metric (p41..p91).

**Second pass 2026-08-12 - action patterns, not cost.** Owner asked what repeated manual work can be
automated. Measured over 2026-08-05..11 (954 files, 497 sessions, 31,776 requests) and written up in
`dev/AGENT_PROCESS_AUDIT_2026-08-12.md`; six findings parked as **S1594-S1599** (release package 31).
Headline numbers worth not re-deriving: warm-up to first edit median **28 turns**; `post-change.ps1`
824 runs / **215 failures (26%)**, recovery median 8 turns; `detekt-gate` 458 runs / 113 fails /
**11.2 h = 75% of all gate wall time** while `detekt-preflight` checks only 3 rules and misses the
LargeClass/LongMethod that actually fail it; `exit 127` 181x (**python3 91** - the machine has `python`
only, PS cmdlets piped in the Bash tool ~89, node 22); `guard-uncapped-read` 381 blocks of which
**31.8% retry with limit>=1500**, i.e. read the whole file anyway; `document_registry/query`
**56.7% empty** (was 39% in July, worse because work moved into launcher); Grep 4,264 calls with
12.8% zero-hit and 1,963 consecutive Grep->Grep pairs. Total mechanically removable ~2,700 turns/week,
~10% of traffic. **How to apply:** these are measured, not guesses - cite them instead of re-mining,
and when working any of S1594-S1599 read that report first.

**S1595 is Verified (2026-08-12).** F3 is closed, and the fix is NOT the one the audit proposed.
Extending the lexical preflight to more rules was measured and refuted: its three rules fully cover
only **13.9%** of attributable gate failures, nine hand-listed rules reach 48.1%, `ComplexMethod`
(named in the capture) caused **zero** failures while the two biggest misses were `ReturnCount` 22
and `ArgumentListWrapping` 15, and the size rules cannot be reproduced lexically at all - flagged
and unflagged classes overlap by a 240-line band under every metric. Instead `detekt-preflight.ps1`
now runs the REAL analyser scoped to the changed files via `detekt-scoped.ps1` (detekt CLI
`--input`, no gradle, no `BUILD.LOCK`, 2.1 s) and is **FATAL**, so a finding aborts the closure
before the ~87 s gate starts. Whole-module vs scoped measured finding-for-finding identical (14/14,
zero unique either way). `--auto-correct` was rejected: the only switch is the shared `formatting`
flag, which would arm ~5,591 findings, 46% of the baseline. Debt-ticket priorities were NOT
rewritten - touch frequency is 41/17/1/1, not the "all four weekly" the audit assumed, and the
ruling is the owner's (§6.3 still Open). Parked from its research: **S1600** - `assert-detekt`
prints an empty findings list then FAILs, 37 times, 14 under current code.

**S1594 is Verified (2026-08-12)** - the F4+F5 half is shipped, so ~562 of those turns/week are gone:
`~/bin/python3` shims the 91 `python3` failures, the new global guard
`guard-bash-unavailable-command.ps1` refuses PowerShell cmdlets / `node`-`npm`-`npx` / `& {` before
the call (CLAUDE.md Rule 28), and `guard-uncapped-read.ps1` now **rewrites instead of blocking** -
it injects `limit: 800` plus a truncation notice and exempts `.claude/commands|skills|templates|
reference|agents`. S1596-S1599 remain Draft in release package 31.

**S1599 is BlockNeedUserTest (2026-08-12), and its capture's numbers are RETRACTED.** F7's
"typical zero-hit patterns" list was produced by a counter incremented on *every* Grep and
printed under the zero-hit heading - so it is the week's most frequent patterns, not the
missing ones, and the two-kinds split drawn from it is unsupported. Corrected: **651 / 4,297 =
15.2%** empty, not 544 / 12.8%. The real shape is **scope, not naming**: 93.9% of empty results
carried an explicit `path` and an unscoped Grep missed **zero** times; 66.1% were abandoned with
no follow-up at all; 6.9% are absence checks where zero is the right answer. All three
directions in the capture are refuted or capped - multipattern is already the dominant shape
(75.7% of *failing* calls are alternations), doc-structure navigation is 2.6%, a catalog member
index tops out at ~1.8%. Shipped instead: `.claude/hooks/observe-empty-grep.ps1`, a `PostToolUse`
hook that re-runs an empty path-scoped Grep at the repo root and attaches the count - **silent
when the widened run also misses**, so a false positive is impossible by construction. **How to
apply:** the abandoned-66% is a *correctness* problem, not a token one - an empty result reads
as "does not exist" and nobody re-asks; do not sell fixes for it as savings. And before quoting
any audit number, read the branch of the mining script that produced it.

Measurement method and its traps: [[transcript-cost-mining]]. Harness capabilities a hook can and
cannot use: [[claude-code-hook-capabilities]].

**Third pass 2026-08-20 - where the >300k spend actually sits.** Owner asked "am I burning tokens
for nothing". Measured 2026-08-11..20 (1,190 files, 38,522 deduped requests, both tiers):
8.96 G cache_read, 27.9 M output, **98.7% of billed tokens are cache_read and 0.3% are output** -
so nothing written (spec text, code, prose) is a cost lever, only the number of turns and the
context each one carries. Main-tier average context **277,892 tokens per request**; nested 93,051.
**The finding: 34.9% of main-tier requests run above 300k and carry 58.0% of the tier's cache_read.**
Attributed by each session's dominant slash command: `/spec-next` 2 sessions, 0.050 G, **0.000 G
above 300k - the Stage 5b threshold stop works perfectly**; `/spec-do` 17 sessions, 2.091 G, of
which **1.636 G (78%) above 300k**, peak context 999,154 on a single request. `/spec-do` documents
this by design ("the sanctioned way to decline the threshold trade", its own rule 5 says a
`--threshold` argument is read by nothing). Counterfactual: capping every main-tier request at
300k = -20.3% of that tier's cache_read; at 200k = -37.2%. Output for the same window bought
2,062 dev-log rows, 371 distinct tickets touched, 85 `-> Verified` audits.
**How to apply:** the loop's endlessness, not its verbosity, is the whole overspend - do not
propose trimming prose, artifacts or re-reads to fix it (section 5 already refuted those). The
open design question is a self-halt with auto-resume for `/spec-do`, since an agent cannot run
`/clear` itself. Hard tool-failure rate for the window: 1,640 / 44,520 tool_result blocks = 3.7%.
