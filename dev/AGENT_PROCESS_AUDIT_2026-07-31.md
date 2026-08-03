# Agent process audit - cost, speed, quality

**Date:** 2026-07-31
**Corpus:** 2026-06-30 .. 2026-07-31, 347 main session transcripts + 869 nested subagent transcripts (1.7 GB)
**Method:** transcript mining (python), 13 parallel dimension audits, 26 adversarial verifications, 1 synthesis pass. 40 agents, 4.29 M subagent tokens, 63 min wall clock.
**Raw notes:** `temp/scratch/audit/*.md` (13 area reports), `temp/scratch/audit/_TITLES.md` (all 143 findings), `temp/scratch/audit/_SYNTHESIS.md`, `temp/scratch/audit/_REF_SHORT.md` (refutations).

Confidence is marked on every item:
- **[V]** survived adversarial verification
- **[D]** diagnosis reproduced, impact estimate disputed or unproven
- **[U]** measured but never adversarially verified
- **[X]** refuted - listed only where it prevents a wrong decision

Only 26 of the 143 findings could be put through adversarial verification, and 20 of those were knocked down - usually on the impact estimate rather than the diagnosis. Treat every **[U]** as a lead, not a result. Load-bearing claims were additionally hand-checked against the working tree on 2026-07-31; one agent claim was found false and is corrected in place (B3).

---

## 0. Baseline correction - read this before any number below

The first pass of this audit was wrong by 3x, and catching that is itself a finding about how this project measures.

One API response is written to the transcript as several JSONL records (thinking block, text block, each `tool_use`), and every record repeats the same `usage` object verbatim. Forked sessions replay their own history on top. Summing records instead of unique `requestId` triple-counts everything.

Independently re-derived (my own scan, not the agents'):

- main tier: 141,985 records -> **45,026 unique requests**, 38.274 G raw -> **12.213 G cache_read** (inflation **3.13x**)
- nested subagent tier: 56,276 records -> **22,879 unique requests**, 5.852 G raw -> **2.572 G** (inflation 2.28x)
- **all-in: 67,905 requests, 14.784 G cache_read** - not 38.3 G

Second defect: the original miner classified a tool call as failed if the *result body* matched `error:|Exception|FAILED`. Reading a Kotlin file containing `catch (e: Exception)` scored as a failure. Real rate: **1,940 hard failures (2.71%)**, plus 1,045 soft (gate FAIL, `BUILD FAILED`, non-zero exit that returns cleanly because gradle runs backgrounded) = **4.18%**, not 9.3%. Read failures were over-counted 24.7x (1,903 -> 77).

Third defect: the miner walked only the session root, missing 869 subagent transcripts - 17.3% of all traffic, with zero requestId overlap, so it is additive.

**What survives unchanged:** ~**271 k cache_read tokens per request** (numerator and denominator deflate together), and every figure expressed as a *percentage of cache_read*. The ranking of levers does not move; if anything the top lever strengthens - cache_read's share of spend rises from 68.8% to **72.4%**.

Corrected month at Opus list rates: **~$30.6 k equivalent** (cache_read 72.4% / cache_create 15.4% / output 11.9% / fresh input 0.3%). This is notional under a subscription; its use is ranking levers, not budgeting.

**Action A0 [V]:** fix `temp/scratch/mine_transcripts.py` (dedup by `requestId` keeping max; `os.walk` instead of `os.listdir`; `is_error` alone for hard failures with the regex kept as a separate soft band gated to Bash/PowerShell/Agent/Skill; segment on `compact_boundary`). Zero saving, but it is the prerequisite for measuring whether anything below worked. Effort: trivial.

---

## 1. Where the money actually goes

Cost here is `accumulated context x number of turns`. Nothing else is close.

- **203 -> 305 cached input tokens are carried per 1 output token** (worse after dedup).
- Output - my prose, spec text, code - is **11.9%** of spend. Russian chat is ~5.7% of accumulated context and is a stated preference; it is not worth touching.
- The **fixed preamble floor is ~64 k tokens on every single request** = **23.3% of everything billed**. Measured components: CLAUDE.md 7.9 k + MEMORY.md 5.2 k + skill listing 4.2 k + deferred tool names 2.0 k + agent listing 1.3 k = ~20.6 k. The other ~68% is the harness system prompt and tool schemas, outside our control.
- **Spend is extremely concentrated: the top 23 sessions (6.7%) carry 50% of all cache_read; the top 73 (21%) carry 80%.** 22 of the top 25 invoked `/spec-next`, `/spec-all` or `/spec-prerelease`.
- Compaction is late and manual: **159 of 161 compaction boundaries were manual**, at a **median of 389 k tokens** already accumulated (p90 649 k, max 1,004 k). Every turn after that point re-bills ~390 k.
- Subagents are **8.1%** of total spend and are *not* the waste - see section 5.

The mental model that matters: a long session costs roughly `64,000 x N + (d/2) x N²` where `d` is the average per-turn context growth (~2,000 tokens). **300 requests in one unbroken block costs ~109 M tokens; the same 300 requests split into 6 blocks of 50 costs ~34 M.** Same work, 69% less.

---

## 2. The structural levers, in order

### L1. Bound the autonomous loop session [D - diagnosis solid, size disputed]

This is the whole game. `.claude/commands/spec-next.md` line 9 mandates "loop until no eligible spec remains .. never ask the operator a question mid-loop", and line 147 says explicitly: "do **not** stop or cut the session short to avoid a large context .. run `/compact`". That sentence is the direct cause of the 2,354-request sessions.

Why every previous attempt failed, also written down at line 145: `processed[]`, the round tally, `DEVICE_ONLINE` and `selectedDevice` live **in memory only**. `/compact` can carry them in a summary; `/clear` destroys them. So the loop can never reset, only compact - and compaction at 389 k median is 2.7x past the economically right point.

**Fix:** persist round state to disk, then make the round boundary a hard stop-and-resume.
1. New `scripts/spec_catalog/spec-next-session.ps1` writing `temp/spec-next-session.json`: `processed[]`, tally, `DEVICE_ONLINE`, `selectedDevice`, `startedAt`, `round`. (`temp/` is gitignored - Rule 1 legal. `temp/spec-next-skip-cache.json` cannot be reused: `skip-cache.ps1` hard-validates `-Id` as `^S\d{4}$` with a fixed record schema.)
2. `spec-next.md`: write state **before** any context action at the Stage 5 boundary; rewrite line 147 from "`/compact`, never stop" to a stop-and-resume; add `--resume` that re-enters Stage 1 with `-Exclude <processed-csv>` (the `-Exclude` plumbing already exists).
3. Amend the line 9 mandate: "keep the machine busy" becomes "keep the *backlog* moving", satisfied by N bounded sessions.

**Size, honestly:** a T=200 k reset simulation on the real per-session sequences gives -52% of main-thread cache_read; re-based on the all-in denominator, -26.5%; discounted for re-priming at 50% of discarded context, **-14% of all cache_read = ~10% of total spend**. The verifier disputed the higher figures and I have used the discounted one. Even if re-priming costs 100% of discarded context the simulation stays positive, because a cold 64 k preamble is 23% of a fresh request but only 16% of a 390 k one.

**Do not ship this as another advisory line.** `docs/AGENT_COST_PLAYBOOK.md:14` has carried a ">150 k" trigger marked "Advise, not gate" since 2026-07-02 - day 3 of a 32-day corpus. The two heaviest days in the entire window are the last two. An advisory version of this exact recommendation has been in force for 29 of 31 days and moved nothing.

**Needs owner sign-off.** See Q1.

### L2. Make the cost visible at the moment of decision [V]

`.claude/statusline.ps1` renders `used_percentage` as a bar. On a 1M window, 390 k renders as **`ctx ###------- 39%`** - the moment of maximum cost looks like a third full. The file's own header says its purpose is to tell the operator when to compact.

Replace lines 20-32: compute `$tok = [double]$ctx.total_input_tokens` (already dereferenced at line 27, so the field is proven present) and emit **both** magnitude and fraction (`ctx 396k (40%)`), with a band chosen by `max(absolute, fraction)` so whichever is worse wins:
- absolute: <150 k none; 150-250 k `[!] reset at next round boundary`; >250 k `[!!] Nk x every turn - reset NOW`
- fraction: >=70% `[!]`; >=85% `[!!]` - this protects 200 k-window sessions, which exist in this corpus
- fall back to `used_percentage` alone if `total_input_tokens` is null

Constraints: pure arithmetic only (fresh `pwsh -NoProfile` per render), keep `$ErrorActionPreference = 'SilentlyContinue'`.

**Saving on its own: exactly zero, and it must be reported as zero.** The owner already compacts 114 times unaided - just at 402 k median. This is the sensor L1 depends on. Effort: trivial. Caveat: wiring lives in per-machine `settings.local.json`.

### L3. Gate the opening bulk read [V]

The single largest mechanically-gateable read pattern: first touch of a file in a segment, no `offset`/`limit`, >=8 KB. **1,226 calls = 10.7% of Reads carrying 43.8% of all Read bytes.** Only 21.7% had a Grep/Glob in the preceding 3 turns.

Build a PreToolUse hook on Read, modelled on the two existing global guards (`guard-find-command.ps1`, `guard-ps1-in-bash.ps1` - block before the call, exit 2, explain the fix). Logic: no offset, no limit, target >~200 lines -> block with "Grep for the symbol, then Read with offset/limit around the hit. Re-issue with an explicit `limit` if you genuinely need the whole file."

**The escape hatch is mandatory, not optional.** Rule 8 (read KDoc in the affected area), `/spec-check` auditing end to end, and Rule 2's 1500-LOC ceiling mean a fully compliant Kotlin file legitimately reaches ~60 KB. An explicit re-issue with a large `limit` must pass.

**Net saving ~0.93% of cache_read = ~0.67% of bill** after charging +2 turns of Grep-then-window overhead. Break-even is 4.42 extra turns per capped read, so it cannot go net-negative. As a prompt line instead of a hook, model it at 10-25% of that: **the identical advice already ships in the Read tool schema on every turn and gets 22% compliance.** That is the whole argument for hooks over prose.

### L4. Route the cheap work to cheap models [U - never verified, plausibly the second-largest lever]

The one routing mechanism already installed is **provably inert**: `model: sonnet` appears in 14 command frontmatter files and **115 of 115 invocations kept the session model**. Command frontmatter does not route; agent frontmatter and Workflow `opts.model` do.

- `android-rd-specialist` took **138 spawns, 89% on Opus**; ~130 of them are device-driving, read-only research and mechanical script-running.
- ~30% of main-session requests are mechanical: running `post-change`, catalog CLI, assert gates, doc-registry, device taps.
- Estimated **$2.3-2.6 k/month each** for the device tier and the mechanical tier at a 5:1 Opus:Sonnet ratio.

Concretely: split `android-rd-specialist` into a Sonnet research/device variant and keep Opus only for implementation and architecture; add a Haiku-tier `repo-mechanic` agent for closure chores; pin `android-kotlin-developer` to `model: opus` explicitly rather than inheriting.

**This axis was never measured properly in this audit** and is plausibly larger than L3 combined with everything in section 4. It is the top candidate for the next measurement pass.

---

## 3. Wall-clock levers - these are cheap and independent of everything above

These do not need owner sign-off and do not touch the spec lifecycle.

### B1. `assert-detekt.ps1` is the only gradle caller that omits `--configuration-cache` [U]
It pays a ~23 s cold configuration phase on **every** run, and detekt ran ~929 times in 31 days (519 direct + 410 inside `post-change.ps1`). Crediting only 10 s of the 23 s: **~2.6 h/month for a one-line change.** Highest ratio of saving to effort in the whole audit.

### B2. One single-walk multi-matcher gate runner [U]
12 gates each do their own full walk of `app_v2/src`. Measured: `a.ps1 fg` = **26.5 s**, of which 28.8 s across the corpus scans is duplicated walking plus 14 pwsh cold starts (~5.7 s of pure process startup). One combined pass -> **~5 s**. Over 561 fg runs in the window: **~3.35 h recovered.**

### B3. `assert-fast-gates.ps1` forwards `-ChangedFiles` to detekt only [V - re-verified by hand, and the agent's version was wrong]
The audit agent claimed the parameter is never forwarded. That is false - line 102 forwards it to `assert-detekt.ps1`. The real defect is narrower and more precise:

Of the 13 gates in the `$gates` table (`assert-fast-gates.ps1:51-81`), **five accept `-ChangedFiles`** - `assert-flavor-flags-not-growing`, `assert-neuroslop`, `assert-public-mutable-flow`, `assert-deprecated-pm-flags`, `assert-listener-symmetry` - and **all five are invoked with no arguments at all** (`= @()`), so they run project-wide. Only detekt receives the scope, and only under `-IncludeDetekt`.

Consequence: `a.ps1 fg` goes red on any other ticket's in-flight drift, reported at **42% FAIL**. Fix is one line per gate in the table: pass `-ChangedFiles` through to the five that support it. Note this is deliberate for a release/CI run, so the pass-through should be conditional on `-ChangedFiles` actually being supplied, exactly as the detekt branch already does.

### B4. Eleven gates have never fired [U]
In 858 closures and 561 fg runs, 11 gates fired zero times, costing **8,563 s = 33% of all gate wall clock**. `assert-fgs-notifications` alone: 3,909 ms per run, 646 runs, zero fires. Meanwhile the gates with 60-75% hit rates run only 4-39 times. Fold the zero-fire `.kt` scanners into the single pass (B2) and move the high-yield gates into the hot path.

### B5. `post-change.ps1` runs its two mutating steps **before** the gates [U]
430 failed closures therefore paid 69 min of wasted `catalog-sync` and wrote **183 duplicate changelog rows**. Reorder: gates first, mutations after. This also makes the facade idempotent by construction and enables a free `-Plan` mode.

### B6. app_v2 still runs Hilt/Room/Glide through kapt while `wear/` already uses KSP [U]
The KSP plugin is already on the classpath. ~35% of the measured 44 s compile+AP chain is stub generation and javac annotation processing (~15 s/run), across 1,836 compile-graph runs in the window. Effort: large, and it is the one item here with real regression risk - but it is a solved problem inside this very repo.

### B7. No gradle-backed gate has a timeout [U]
One `post-change.ps1` run hung for **3 hours and still reported PASS**. The tail is 5 runs over 300 s out of 311, so a 600 s ceiling never fires on a healthy run.

### B8. 793 lock-status polls exist only because BUILD.LOCK refuses instead of waiting [U]
Add a `-Wait` mode. ~793 turns of pure polling, plus 48 hand-rolled `until` loops. Related: the blanket "background every gradle target" rule is miscalibrated for the fast checks - the agent hand-polls with `cat`/`sleep` (~1,297 polling turns, 81 min/month of literal `sleep`) instead of letting the harness notify.

---

## 4. Correctness and trust levers - what actually makes the work wrong

This section matters more than the money.

### C1. The closure facade certifies work it never checked [U, high confidence]
`post-change.ps1 -ScopeToFile` narrows detekt to **exactly one file**, while **62% of closures span more than one** (mean 4.34 files). A green PASS therefore certifies ~23% of the change. Worse:
- **PASS is printed on 19% of runs that contain a gate FAIL**, and 66% of callers only read the tail.
- `-File` is never validated - an unexpanded shell variable produces a full green PASS certifying nothing.
- The facade has no exit-code contract, violating this repo's own Rule 7 in its most-executed script.
- `CLAUDE.md` section 12 describes `-ScopeToFile` behaviour the script **has not had since S0848/S0850**.

**Fix:** add `[string[]]$Files`, validate it, print PASS only when every gate passed, publish an exit-code contract. **29 agent-memory files totalling 101,864 bytes exist to document workarounds for this one script's UX.** That is the clearest signal in the audit that a mechanical fix was replaced by institutional memory.

### C2. The gates that exist are not the gates that are needed [U]
- Rule 17 (system-bar insets) has **no gate**, and the same defect reached the owner **twice**.
- The detekt baseline has drifted to **83 files with new findings**, so the project-wide ratchet is never actually enforced - only the scoped verdict survives, and that one cannot catch a regression in an untouched file.
- Ratchet baselines are never ratcheted *down*, so full-scan mode currently ships 10 em-dashes, 5 unsafe collects and 2 `!!` for free.
- Four gates are red right now and nothing runs them.
- `assert-detekt` rejects **50% of runs** - the agent writes code that fails the style gate half the time, and Rule 19's "detekt-clean-first" prose is not preventing it. A lexical preflight on changed files (ImportOrdering, MaxLineLength, MagicNumber = 58% of findings) would catch ~35% before paying the 23 s gradle round-trip.

### C3. The device gate never closes [U]
**87 tickets sit in `BlockNeedUserTest` against 39 Verified** - the loop ships 6.9x faster than it verifies, and 38% of the catalog is shipped-but-unproven. **154 live `Timber` probes** are in source right now. The owner withdrew device access on cost grounds and became the test harness himself.

Two consequences: the autonomous loop should be **gated on the unverified backlog** (stop chaining when BNUT exceeds ~15), and `BlockNeedUserTest` should stop being the bucket that absorbs everything the agent cannot prove.

### C4. Ungated mandates teach that rules are optional [U]
This is the sharpest quality finding and it is structural.

- Gated rules hold at ~**99%** (Rule 2 debug probes: 79 of 80 clean).
- Ungated rules hold at **1-8%**. The document-registry mandate is stated in five always-on places and obeyed at ~0.6-3% of its own stated cadence. `/ui-clarify` was invoked **once** while **33% of all owner corrections were UI placement**. Catalog-before-grep: 8.3%.
- The `temp/` layout rule is violated 200:1 while being re-read on every one of 141,939 turns.

Every turn the agent reads a rule it is actively violating, which calibrates it that mandates are advisory. **Either gate a rule or delete it.** Concretely: scope the document-registry loop to the pipelines where it is real and trigger it from `post-change.ps1`; wire `/ui-clarify` plus a screenshot step into the pipelines that actually build UI; delete the timestamp rule from its three prose homes (a hook already supplies it, compliance is 47.6% either way).

### C5. 28% of all turns run past the last point a correction was possible [U]
The unsupervised tail is not automatically waste, but it is 100% uncorrectable. Checkpointing even a quarter of it puts a large slice of work back under the owner's eye before it compounds. The measured median rework segment after a correction is 30 turns; a whole-ticket redo costs 4-7x that.

### C6. Memory has started feeding itself [U]
- `MEMORY.md` is 18,839 B billed on every turn; **58 of 230 pointers were never opened** in 347 sessions; **40% of memory bytes have never been read once**; only 20% of sessions do any recall read.
- **The corpus is written 2.3x more often than it is consulted.**
- 55% of memory bytes are anchored to tickets that no longer exist; 20% restate rules already in the always-loaded preamble (double and triple billing); 11 files / 36 KB cover the single topic of detekt and cross-link into a read cascade.
- Two manual index compactions were both undone within a week - nothing enforces the ceiling, and regrowth measures 1.1 KB/day.
- Memory once wrote a **false architectural claim into strategic spec S1233**, which cost a spec correction plus a compile run to disprove.

**Fix:** a byte budget on `MEMORY.md` enforced by an `assert-*` gate (target 6-9 KB), an expiry mechanism keyed to ticket liveness, and a rule that memory may not restate CLAUDE.md.

---

## 5. Do not touch - verified non-problems

Each of these was proposed by an audit agent and killed under verification. Listed so the same idea is not re-litigated.

- **Do not trim the command surface for token savings.** Deleting the 8 never-invoked command files removes 57,919 B from disk but only ~377 tokens from the per-turn floor (0.137%) - only the one-line descriptions ship until invocation. It also breaks CLAUDE.md section 3 routing and the `/caveman` owner preference. (Deleting them for *routing clarity* is still defensible; deleting them for cost is not.)
- **Do not chase prose or output verbosity.** Output is 11.9% of the deduped bill and ~90% of it is thinking tokens that are billed but never persisted, so char-based prose optimisation is optimising 9% of a 12% bucket. Russian chat is cheap and is a stated preference.
- **Do not build a UserPromptSubmit context-pricing hook.** Timing-blind by construction: it fires when the owner types, and the tax accrues inside autonomous blocks where no prompt is submitted. Measured: hook silent in 33 of 59 `/spec-all` blocks; 67% of boundaries sit below any 150 k threshold at fire time. And it cannot act - `/clear` is a harness built-in, not a tool.
- **Do not suppress within-segment re-reads.** The eliminable pool is 378 calls = **0.084%** of cache_read. The worst-looking offender (43 Reads / 62 Edits on one file) is 33 micro-windows of 300-3,000 chars immediately before an Edit, while the file drifts 964 -> 686 lines. That is `Edit` needing an exact `old_string`, not sloppiness. Suppressing it raises the failed-Edit rate.
- **Do not treat tool failures as a cost bucket.** Real rate 4.18%, worth ~3.8% of cache_read. Right-size it and move on. (Individual fixes below are still worth doing on *quality* grounds.)
- **Do not expand or cut subagent usage on cost grounds.** The apparent "-55% per turn" is a length-mix artifact; like-for-like the discount is 10-21%. Subagents burn 1,667 cache_read per output token against main's 260. Keep the existing spawn policy.
- **Do not route bulk reading to a subagent** as a cost trick - refuted; and do not build a full-file-read guard for "huge files", which measurement shows are not being read whole (that guard would protect 0.0005% of cache_read).
- **Do not decompose `app_v2/build.gradle.kts`** on read-cost grounds - the causal story behind that proposal does not hold and the change is a regression.
- **Do not argue context hygiene on quality grounds.** No measured quality degradation with context size here. Argue it on cost only, or the real fix gets attached to a false rationale.

**Keep - these pay for themselves:** the ~40 `assert-*.ps1` gates as a class; `enable_mcp_tools: false` on subagents; `run_in_background` for gradle plus `temp/BUILD.LOCK`; `mobile-mcp` restricted to exploratory walks; the Sxxxx lifecycle itself; **phase-boundary audits** (corroborated: median rework after an in-flight correction is 30 turns against 4-7x that for a whole-ticket redo); and the 1% subagent retry rate, which is the strongest measured property of the whole pipeline.

---

## 6. Cheap fixes worth doing regardless

Each is trivial or small, and each removes a recurring failure class rather than saving tokens.

- `dev/CATALOG/scripts/query.ps1` - give `-Module` a default of `app_v2`; it is mandatory today and only two modules exist. ~85 recorded failures.
- `scripts/document_registry/query.ps1` - "no matches" exits 1; the mandatory loop hits that 39% of the time, so a normal outcome reads as a failure.
- Four sibling spec CLIs spell the same argument four ways and none supports `-Help`. ~90 failures.
- 480 failures return only `Exit code 1` with no reason. Upgrade `assert-exit-contract.ps1` to require a message with every non-zero exit.
- `CLAUDE.md` section 7 teaches PowerShell batching syntax without saying it is PowerShell-tool-only - 137 interop failures.
- Rule 24 bans `find` while the hook only requires `-maxdepth`: 134 blocks, zero decay in a month. Align the rule text with the hook, and make `guard-ps1-in-bash` heredoc- and quote-aware (it blocked a legitimate Python string literal during this very audit).
- Status-query scripts fail the tool call to report normal state (lock free, device offline). Return 0 with a status field.
- `ticket-log-audit` is invoked with `-Quiet`, which suppresses exactly the File:Line list the agent needs to fix it.
- The detekt gate prints the file and discards the rule and line it already parsed, costing **2.38 gradle re-runs per failure**.
- Spec-file mutators rewrite the file under an open edit, producing 44 stale-file Edit failures.
- `spec-dev.md` still names a stale `build-debug.PS1` instruction that contradicts CLAUDE.md on the most expensive operation.
- `CLAUDE.md:77` authorises behaviour the owner banned and Rule 23 blocks mechanically - a live self-contradiction in the always-on text.
- `AGENTS.md` and `.github/copilot-instructions.md` are **never loaded by Claude Code**, have drifted, and one contradicts CLAUDE.md.

---

## 7. Sequencing

**This week:**
1. A0 - fix the extractor (trivial, and everything else is measured against it).
2. B1 - `--configuration-cache` on `assert-detekt.ps1` (one line, ~2.6 h/month).
3. B3 - forward `-ChangedFiles` in `assert-fast-gates.ps1` (kills a 42% false-FAIL rate).
4. C1 - `[string[]]$Files` on `post-change.ps1` plus honest PASS (closes a 77% certification hole).
5. L2 - statusline shows magnitude (trivial, and it is the sensor for step 6).

**Next, after the owner answers Q1:**
6. L1 - persist `/spec-next` round state and bound the rounds.
7. L3 - PreToolUse read hook, advisory-with-consent.
8. B2/B4 - single-walk gate runner, retire the zero-fire gates.

**Then measure.** Re-run A0's extractor after two weeks and compare: median pre-compaction `preTokens` (today 389,197), p90 session request count (today 308.6), all-in cache_read per calendar day. Honour `docs/AGENT_COST_PLAYBOOK.md:84` - land it, then measure; do not publish a percentage as a promise.

**Separately:** commission a proper model-routing measurement pass (L4). It was the one large axis this audit never measured.

---

## 8. Open questions - owner only

1. **Is a bounded `/spec-next` acceptable?** The "never stop mid-loop" mandate is the direct cause of the marathon sessions and ~10% of spend. L1 trades autonomy-per-invocation - one ticket, then hand back - against that. L1 is not worth building if the answer is no.
2. **How much re-derivation cost per reset is acceptable?** The estimate assumes re-priming costs 50% of discarded context. If a fresh round genuinely needs to re-read the ticket, the spec and the touched files, the saving falls toward 7%.
3. **Read hook: hard block or advisory-with-consent?** Advisory is recommended - Rule 8 and `/spec-check` legitimately need whole files. Hard block is worth ~0.2% more and will occasionally make a needed read impossible.
4. **Should the device gate throttle the loop?** Gating `/spec-next` on the unverified backlog would slow shipping to match verification. Today the ratio is 6.9:1 and 87 tickets are unproven.
5. **Is model routing on the table?** Plausibly the second-largest lever and entirely unmeasured. Splitting `android-rd-specialist` by tier is the concrete first step.
6. **Should this measurement become a ritual?** A0 produces a repeatable extractor. Promote it to `scripts/` for a monthly run, or leave it in `temp/scratch/` and start from zero at the next cost question.

---

## 9. Hand-verified against the working tree, 2026-07-31

Checked directly rather than taken from an agent report:

- Token inflation is exactly **3.13x** on the main tier and 2.28x on the nested tier; all-in deduped total **14.784 G cache_read across 67,905 requests**. Re-derived with an independent scanner.
- `scripts/quality/assert-detekt.ps1` contains **no** `--configuration-cache` anywhere. Confirmed.
- `.claude/commands/spec-next.md` line 9 mandate and the "do **not** stop or cut the session short .. run `/compact`" rule are verbatim as quoted, and round state is explicitly documented as memory-only (`processed`, session tally, `DEVICE_ONLINE`, `selectedDevice`). Confirmed.
- `.claude/statusline.ps1` renders `ctx $(Bar $ctxPct) $ctxPct%` from `used_percentage`, and `total_input_tokens` is already dereferenced in the same block, so the magnitude fix needs no new data. Confirmed.
- Exactly **14** command files carry `model: sonnet` in frontmatter. Agent frontmatter: `android-rd-specialist` and `android-kotlin-developer` are both `model: inherit`; `android-solution-researcher` and `friendly-android-doc-writer` are `model: sonnet`. Confirmed.
- Spec catalog right now: **228 tickets - 87 `BlockNeedUserTest`, 39 `Verified`**, 36 `Draft`, 26 `Implemented`, 14 `BlockExternal`, 10 `Approved`, 8 `BlockByOtherTask`, 6 `Tactical`, 2 `In Progress`. The 6.9:1 ship-to-verify ratio and the 38% unproven share are exact.
- **Corrected in place:** the claim that `assert-fast-gates.ps1` never forwards `-ChangedFiles` is false. See B3 for the accurate defect.

## 10. Document registry impact

Queried `docs/DOCUMENT_REGISTRY.jsonl` (24 records) by product areas `workflow` / `agents` / `documentation`.

- **Affected if the recommendations land:** `repository-rules` (CLAUDE.md, AGENTS.md, `.claude/agents/*`, `.claude/commands/*`, `.claude/skills/*/SKILL.md`), `developer-operations` (`docs/AGENT_COST_PLAYBOOK.md`), `project-routing` (`dev/AGENT_WORKFLOW.md`, `dev/PROJECT_OPERATIONS_INDEX.md`), `script-cheatsheet` (new/changed script parameters).
- **Unchanged:** all `site`, `public`, `legal`, `wear`, `vr`, `settings`, `icon` and `feature` records - this audit touches no user-facing surface, no product behaviour and no release artefact.
- **This file** is a point-in-time analysis under `dev/`, consistent with the existing `dev/*_audit.md` precedent, and is deliberately not registered. The durable rules distilled from it belong in `docs/AGENT_COST_PLAYBOOK.md`, which is registered under `developer-operations`.
