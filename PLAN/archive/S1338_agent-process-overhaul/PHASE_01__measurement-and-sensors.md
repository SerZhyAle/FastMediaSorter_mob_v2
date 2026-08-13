# Phase 01 - Measurement and sensors

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** every later phase
**Steps done:** 9 / 9
**Started:** -
**Completed:** 2026-07-31

---

## Objective

Promote the transcript extractor into `scripts/metrics/`, fix its four defects so the before/after baseline is trustworthy, make the statusline report context magnitude rather than window fraction, and settle the `--configuration-cache` question on `assert-detekt.ps1` with a measurement instead of an assumption.

---

## Prerequisites

- [ ] `temp/S1338/` exists for scratch artifacts.
- [ ] Python is available on PATH - the extractor is a `.py` script.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/metrics/mine-agent-transcripts.py` | New (moved from `temp/scratch/mine_transcripts.py`) | ≤ 320 |
| `scripts/metrics/agent-cost-report.ps1` | New | ≤ 150 |
| `.claude/statusline.ps1` | Modified | ≤ 70 |
| `.claude/settings.json` | Modified | n/a |
| `scripts/quality/assert-detekt.ps1` | Modified | ≤ 230 |
| `temp/S1338/baseline-2026-07-31.json` | New (artifact, gitignored) | n/a |

---

## Steps

### Step 01.1 - Move the extractor out of scratch

**Files:** `scripts/metrics/mine-agent-transcripts.py`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/metrics/` and move `temp/scratch/mine_transcripts.py` (217 lines, flat top-level script) to `scripts/metrics/mine-agent-transcripts.py`. Keep the behaviour identical in this step - the four corrections land in steps 01.2 to 01.5, each independently verifiable. Restructure the flat body into named functions as you move it, so the later steps have seams to edit. Per strategic §9 the script must stay stack-agnostic: it reads Claude Code transcripts, which every project has, so it must contain nothing Android-specific and must take the transcript root as an argument rather than hardcoding the project directory.

**Verification:**

- `Glob` - `scripts/metrics/mine-agent-transcripts.py` exists.
- `Glob` - `temp/scratch/mine_transcripts.py` no longer exists.
- `Grep` - `FastMediaSorter` returns zero hits in the new file.
- Run the script against the transcript root - it completes and writes its JSON output.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 4/4 PASS. Moved to `scripts/metrics/mine-agent-transcripts.py` (267 LOC), restructured into `parse_args` / `discover_sessions` / `Aggregate` / `scan_session` / `build_result` / `render_digest` / `main`. Root and output are now `--root` / `--out` arguments; added `--since` / `--until`. Scratch original deleted. Smoke run: exit 0, 1153 sessions. Deviation from prompt: the enumeration became `os.walk` here rather than in step 01.3, because the flat `os.listdir` could not survive the extraction into `discover_sessions` without being rewritten anyway.

---

### Step 01.2 - Deduplicate usage by `requestId`

**Files:** `scripts/metrics/mine-agent-transcripts.py`
**Depends on:** Step 01.1

**Prompt for developer:**

> One API response is written as several JSONL records - thinking, text, and each `tool_use` - that repeat the same `usage` object verbatim, and forked sessions replay them on top. The script currently aggregates every assistant line unconditionally, inflating tokens 3.13x on the main tier and 2.28x on the nested tier, and all counts 1.43x. Key every assistant record by its `requestId` and keep the maximum `usage` per id rather than summing. Records with no `requestId` count once each.

**Verification:**

- `Grep` - `requestId` matches in `scripts/metrics/mine-agent-transcripts.py`.
- Run over the 2026-06-30..2026-07-31 window - reported request count is ~67,900, not ~97,000.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `Aggregate.seen_requests` keys on `requestId` globally (not per session - a forked session replays another session's records) and credits the delta against the previously-recorded maximum, so a repeated record corrects the total instead of re-adding. Added `requests` to the JSON and the digest header. Measured over the window: **64,664 unique requests, cache_read 14.25 G**, against the audit's ~67,900 and ~14.78 G - 4.8% and 3.6% below. Not the ~97,000 the pre-fix summing produced. The residual gap is carried into step 01.7 for the formal acceptance comparison rather than being explained away here.

---

### Step 01.3 - Walk recursively and report the tiers separately

**Files:** `scripts/metrics/mine-agent-transcripts.py`
**Depends on:** Step 01.2

**Prompt for developer:**

> The script enumerates with a flat `os.listdir` filtered on `.jsonl`, so it never descends into `<session-id>/subagents/**` - 869 files carrying 17.3% of traffic with zero `requestId` overlap, fully additive. Replace the listing with `os.walk`. Report the main tier and the nested tier separately as well as combined: a flat average corrupts the headline per-turn figure because subagent conversations are structurally shorter.

**Verification:**

- `Grep` - `os.walk` matches and `os.listdir` returns zero hits.
- Run the script - the output JSON contains distinct main-tier, nested-tier and combined sections.
- Nested-tier file count is ~869.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/3 PASS, 1 explained. `discover_sessions` now returns a tier per file (`nested` when the path has a directory component, i.e. `<session-id>/subagents/**`), and `build_result` reports per-tier totals plus `tokens_per_turn`. Measured: main 323 files / 43,002 turns / 1,016.3 out-tokens per turn; nested 830 files / 21,687 turns / 857.4 - confirming subagent conversations are structurally shorter, which is why a flat average was wrong. Nested cache_read is 2.47 G of 14.26 G = **17.3%**, exactly the audit's figure. File count 830 against the expected ~869: an **unfiltered** run returns the same 323/830, so the corpus on disk is smaller than when the audit ran (347/869) - sessions have been pruned since. This is corpus drift, not an extractor defect, and it also accounts for the request-count shortfall recorded in step 01.2.

---

### Step 01.4 - Classify hard failures by `is_error` alone

**Files:** `scripts/metrics/mine-agent-transcripts.py`
**Depends on:** Step 01.3

**Prompt for developer:**

> The failure test is `bool(c.get("is_error")) or bool(ERR.search(body[:4000]))`. The regex arm scores a Kotlin file containing `catch (e: Exception)` as a failed `Read`, over-counting Read failures 24.7x. Make the hard-failure classification `is_error` alone. Keep the regex as a separately counted soft band restricted to `Bash`, `PowerShell`, `Agent` and `Skill` result payloads - that band is mandatory, not optional, because gradle runs backgrounded and a failed build returns a clean `tool_result` with no `is_error`. Report the two bands as distinct numbers.

**Verification:**

- `Grep` - the `is_error` test and the regex test are no longer combined with `or` in one expression.
- `Grep` - the soft band is guarded by a tool-name allowlist containing `Bash` and `Agent`.
- Run the script - hard failure rate ~2.71%, combined-with-soft-band ~4.18%.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/3 PASS, 1 deviation recorded. `hard` is now `is_error` alone; the regex survives as `soft`, gated by `SOFT_BAND_TOOLS` = Bash, PowerShell, Agent, Task, Skill - the tools whose result body is process output. Added `tool_soft_fail` and a `failure_rates` block to the JSON, and a FAILURES line to the digest. Measured: **hard 2,689 = 2.71%**, exactly the audit's figure. Read hard failures fell from 3,712 to **148**, a **25.1x** over-count against the audit's claimed 24.7x. Deviation: combined hard+soft is **5.81%** against the audit's 4.18%; the audit's soft band was evidently narrower than the five tools named in strategic §4. Carried to step 01.7 rather than tuned to match - fitting the band to the expected answer would defeat the purpose of the measurement.

---

### Step 01.5 - Segment on compaction boundaries

**Files:** `scripts/metrics/mine-agent-transcripts.py`
**Depends on:** Step 01.4

**Prompt for developer:**

> The event dispatch branches only on `type == "assistant"` and `type == "user"`; every other record falls through silently. Add handling for `system` records with `subtype == "compact_boundary"`, and report the percentiles of `compactMetadata.preTokens` across them. That series is the acceptance metric for S1339, so it must exist before S1339 can be judged.

**Verification:**

- `Grep` - `compact_boundary` and `preTokens` both match in `scripts/metrics/mine-agent-transcripts.py`.
- Run the script - output contains a percentile block for pre-compaction tokens, with a median near 389,197.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Added a `system` / `subtype == compact_boundary` branch, a `record_compaction` collector, a nearest-rank `percentiles` helper and a `compaction` block in the JSON plus a digest line. Measured: **160 compactions, 157 manual against 3 auto**, preTokens **p50 = 389,197** - an exact match with the audit's recorded median - p75 489,404, p90 648,995, p99 789,470. Side finding worth recording: this series independently confirms strategic §2's reasoning for the owner's revised threshold. 300,000 sits below the p50, so it will actually fire; 400,000 would have sat at the existing manual median and changed almost nothing.

---

### Step 01.6 - Add the operator entry point

**Files:** `scripts/metrics/agent-cost-report.ps1`
**Depends on:** Step 01.5

**Prompt for developer:**

> Write `scripts/metrics/agent-cost-report.ps1` as the operator front end: resolve the transcript root, invoke the Python extractor, and write a readable report plus the raw JSON under `temp/`. Accept `-Since` and `-Until` date bounds and an `-OutputPath`. Follow CLAUDE.md Rule 7 - publish the exit codes in the header and make them reachable: 0 report written, 1 error, 2 cannot verify (Python missing, transcript root absent). Use `Write-Error $msg -ErrorAction Continue` before any `exit 2`.

**Verification:**

- `Glob` - `scripts/metrics/agent-cost-report.ps1` exists.
- `Grep` - `Exit codes:` matches in its header.
- Run `pwsh -NoProfile -File scripts/metrics/agent-cost-report.ps1 -Since 2026-06-30 -Until 2026-07-31` - exit code 0, report written.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Path scripts/metrics/agent-cost-report.ps1 -Gate` - exit code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 4/4 PASS. `agent-cost-report.ps1` resolves the transcript root, runs the extractor and prints the headline figures; `-Since` / `-Until` / `-TranscriptRoot` / `-OutputPath` / `-Json` supported. Exit codes documented and reachable via `Write-Error -ErrorAction Continue` before `exit 2`; gate PASS with 0 unreachable exit sites. Run over the window: exit 0, report written to `temp/metrics/`. Cannot-verify path exercised with a bogus root: exit 2.
- 2026-07-31 - Defect found and fixed during verification, not worked around (Rule 13). The first implementation derived the transcript directory name by collapsing repeated dashes and inherited the drive letter's case from PowerShell, producing `P-ANDROID-FastMediaSorter_mob_v2` against the real `p--ANDROID-FastMediaSorter-mob-v2`. Claude Code replaces `:`, both separators and `_` with `-` and does **not** collapse repeats. The fix derives the name with that rule and then resolves it case-insensitively against the actual directory listing, so the drive-letter casing cannot break it again.

---

### Step 01.7 - Record the baseline and check it against the spec

**Files:** `temp/S1338/baseline-2026-07-31.json`
**Depends on:** Step 01.6

**Prompt for developer:**

> Run the report over 2026-06-30..2026-07-31 and save the output to `temp/S1338/baseline-2026-07-31.json`. Compare against the acceptance figures in strategic §4 package A: ~67,900 requests, ~14.78 G cache_read all-in, 2.71% hard failure rate, 4.18% including the soft band. A material deviation means the extractor is still wrong - fix it before this phase closes, because every later package is judged against these numbers. Write the measured values into strategic §6 beside the recorded baselines.

**Verification:**

- `Glob` - `temp/S1338/baseline-2026-07-31.json` exists.
- The four acceptance figures are within a stated tolerance of the strategic §4 values, or the deviation is explained in `## Last Audit`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Baseline written to `temp/S1338/baseline-2026-07-31.json`; the comparison table is now strategic §6.1. Three figures reproduce **exactly** - hard failure rate 2.71%, preTokens median 389,197, nested-tier share 17.3% - which is the evidence the corrected extractor is trustworthy. Requests 64,698 against ~67,900 and cache_read 14.26 G against ~14.78 G are both explained by corpus drift: 323/830 sessions on disk now against the audit's 347/869, confirmed by an unfiltered run returning identical counts. The combined failure rate of 5.81% against 4.18% is a definitional difference in the soft band, left unadjusted deliberately - tuning the band until it matched the expected answer is exactly the failure mode this package exists to correct.

---

### Step 01.8 - Report context magnitude, not window fraction

**Files:** `.claude/statusline.ps1`, `.claude/settings.json`
**Depends on:** - independent of steps 01.1 to 01.7

**Prompt for developer:**

> Replace the context block at lines 21-33. `$ctx.total_input_tokens` is already dereferenced at line 28, so no new data source is needed. Emit both magnitude and fraction, for example `ctx 396k (40%)`. Choose the warning band by `max(absolute, fraction)` so whichever is worse wins - absolute: under 150k plain, 150-250k `[!]`, over 250k `[!!]`; fraction: at or above 70% `[!]`, at or above 85% `[!!]`. The fraction band protects a 200k-window session, which exists in this corpus and would otherwise sit permanently plain at 75% fill. Fall back to `used_percentage` alone when `total_input_tokens` is null or zero rather than printing `0k`. Keep it pure arithmetic with no file reads - it re-runs in a fresh `pwsh -NoProfile` process on every render - and keep `$ErrorActionPreference = 'SilentlyContinue'` so a schema change never blanks the line. Then promote the `statusLine` wiring from the gitignored `.claude/settings.local.json` into the committed `.claude/settings.json`, otherwise this lands on one machine only.

**Verification:**

- `Grep` - `total_input_tokens` matches in the emitted string construction, not only in the fallback branch.
- `Grep` - both `[!]` and `[!!]` band markers match.
- `Grep` - `statusLine` matches in `.claude/settings.json`.
- Render the statusline with a mocked payload at 396,000 tokens on a 1M window - output contains `396k` and `[!!]`.
- Render with `total_input_tokens` null - output shows a percentage and no `0k`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 5/5 PASS. Replaced the bar with magnitude-first reporting and a `Band` helper taking `max(absolute, fraction)`. The `Bar` function is gone - it had no remaining caller. Rendered cases: 396k/40% on a 1M window gives `ctx 396k (40%) [!!]`, where the old code showed a bar reading 40%; 150k/15% gives `[!]`; 100k/10% plain; **100k at 83% of a 120k window gives `[!]` and 110k at 92% gives `[!!]`**, proving the fraction band fires independently below the 150k absolute threshold - the case strategic §4 package B named as the reason to have it. Null `total_input_tokens` renders `ctx 42%` with no `0k`. A non-JSON payload degrades to `[Claude]` and exit 0, so `$ErrorActionPreference = 'SilentlyContinue'` still protects the line against a schema change. Wiring promoted from the gitignored `.claude/settings.local.json` into the committed `.claude/settings.json`; JSON validated.

---

### Step 01.9 - Settle the `--configuration-cache` question by measurement

**Files:** `scripts/quality/assert-detekt.ps1`
**Depends on:** - independent of steps 01.1 to 01.8

**Prompt for developer:**

> Strategic §4 package C calls this a one-line change worth ~2.6 h/month, but `gradle.properties:28` sets `org.gradle.configuration-cache=false` deliberately, because Chaquopy 17.x breaks the configuration-cache store for the noLegal task graph. Do not add the flag blind. Measure first: time three cold `:app_v2:detekt` runs as they are today, then three with `--configuration-cache` appended to the invocation at line 100, and confirm the detekt task graph does not pull in the Chaquopy tasks that forced the global opt-out. If the flag holds and saves time, add it to that invocation only and record the measured saving. If it fails or conflicts, leave the invocation unchanged and record the finding in `## Last Audit` - the item is then closed as "explicit recorded decision not to land", which strategic §10 accepts.

**Verification:**

- Both timing sets recorded in `temp/S1338/` with the command lines used.
- Either `Grep` finds `--configuration-cache` in `scripts/quality/assert-detekt.ps1` and a green `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate` run, or `## Last Audit` carries the recorded reason for not landing it.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Measured before deciding, per the step's own instruction. Six runs of `:app_v2:detekt` via `temp/S1338/measure-cc.ps1`, results in `temp/S1338/cc-measurement.txt`:
  - baseline 25.4 / 22.8 / 21.9 s, mean **23.4 s**
  - `--configuration-cache` 19.9 (entry stored) / 18.7 (reused) / 18.9 (reused) s, warm mean **18.8 s**
  - configuration-cache problems reported: **0**. The detekt task graph does not pull in the Chaquopy tasks that forced the global `org.gradle.configuration-cache=false`.
  - **Landed.** Flag added to the invocation at `assert-detekt.ps1` line 100 only, so the global opt-out stays intact for every other caller.
- 2026-07-31 - Correction to strategic §4 package C's arithmetic. The spec credits "only 10 s of the 23 s" for **~2.6 h/month**. The measured saving is **4.6 s** per warm run, so at ~929 detekt runs a month the real figure is **~1.19 h/month** - worthwhile for a one-line change, but under half what the spec claimed. The spec assumed a cold configuration phase; these runs used a warm daemon, which is the realistic case.
- 2026-07-31 - The `-Gate` run is **not** green, and it is not this change that made it red. The three baseline runs taken *before* the flag was added also exited 1, and the failures are `MagicNumber` findings in `src/vr/.../VrHudBannerRenderer.kt` and `VrTextureDecoder.kt` - files this ticket never touched. Diff-scoped to the changed file the gate reports `PASS [scoped] - 167 file(s) with new findings project-wide, none among changed files`. That 167 is the dirty-tree reality phases 02 and 04 exist to fix.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` re-run - two new scripts entered the cheatsheet, and `assert-script-cheatsheet-sync.ps1` gates on that drift. `-Generate` wrote 248 scripts; `-Check` reports in sync.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` - exit code 0 (0 unreachable exit sites, 0 silent scripts).
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Phase-boundary audit - 2026-07-31

Layer 1 applies. Layers 2 to 4 do not: this phase added no Android code, no coroutines or listeners, and no Room surface.

- **P3 - unbounded collectors in the extractor.** `big_results` and `compactions` append without a cap and are only truncated at render time. Acceptable for a batch tool run over a fixed corpus; it would matter if this ever streamed continuously. Recorded, not fixed.
- **P3 - configuration cache and report freshness.** `assert-detekt.ps1` checks the Checkstyle report's mtime to tell a stale report from a real finding. The configuration cache stores the *configuration* phase, not task outputs, and `org.gradle.caching=true` already cached outputs before this change - so the flag introduces no new staleness risk. Confirmed empirically: a full `-Gate` run and a `-ChangedFiles` run back to back both read a fresh report.
- **Verified, not a finding - dedup correctness.** `record_assistant` keeps the maximum usage per `requestId` and credits only the delta, so a replayed record corrects rather than re-adds, and `assistant_turns` increments on first sighting only. `percentiles` uses nearest rank: for 160 samples p50 resolves to index 79, the 80th value, which is why the median reproduced the audit's 389,197 exactly.
- **Verified, not a finding - statusline null safety.** A payload with no `context_window` yields `[double]$null` = 0 and falls through to the fraction branch; a non-JSON payload renders `[Claude]` at exit 0. No path can blank the line.

---

## Handoff Notes to Next Phase

`temp/S1338/baseline-2026-07-31.json` is the reference every later package is measured against; strategic §6 requires re-running `agent-cost-report.ps1` two weeks after the first four phases land. The statusline is the sensor S1339 acts on - its saving on its own is zero and must be reported as zero, because the owner already compacts unaided. Whichever way step 01.9 resolved, record it: a later phase must not re-litigate it.

---

## Rollback Plan

Revert `.claude/statusline.ps1` and the `assert-detekt.ps1` line. The new `scripts/metrics/` files are additive - deleting them restores the prior state, and the extractor's former scratch copy is recoverable from the move in step 01.1.
