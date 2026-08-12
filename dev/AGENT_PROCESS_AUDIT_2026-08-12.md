# Agent process audit - week of 2026-08-05 .. 2026-08-11

Follow-up to `dev/AGENT_PROCESS_AUDIT_2026-07-31.md` and its 2026-08-05 retrospective. That audit
asked *where the money goes*; this one asks a narrower question the owner put on 2026-08-12: **which
repeated manual sequences can be turned into a script, a hook or a gate.**

Every finding below is parked as its own Draft ticket. Nothing here is a re-run of the PRE/POST cost
comparison - the retrospective's own instruction was not to re-measure that before ~2026-08-15, and
this pass measures absolute action patterns instead, which that instruction does not cover.

## Method

- Corpus: `~/.claude/projects/p--ANDROID-FastMediaSorter-mob-v2`, both tiers (`os.walk`), window
  2026-08-05 .. 2026-08-11. 954 session files, 497 sessions, 31,776 unique requests, 38,699 tool
  calls, 6.618 G cache_read.
- Token/count dedup by `requestId`, keeping max per id; tool-call dedup by `tool_use` block id.
  Hard failures by `is_error` only; soft band restricted to process-output tools. These are the three
  traps recorded in `[[transcript-cost-mining]]` - a naive extractor inflates tokens 3.13x.
- Gate verdicts parsed from the `  [<label>] <PASS|FAIL|SKIP> (<N> ms)` lines `post-change.ps1` prints.
  The printed ms includes waiting for `BUILD.LOCK`, so it is scheduling cost, not compute cost.
- Extractors: `scripts/metrics/mine-agent-transcripts.py` (existing, cost) plus four throwaway passes
  under `temp/scratch/week-audit/` (patterns, failure dig, empty-result tax, hook effectiveness).
  Raw output is in that directory and is not durable - the numbers below are the record.
- Reference figures for converting turns to tokens: main tier averages 251.7 k cache_read per turn
  (5.74 G over 22,803 turns), nested tier 97.7 k (877 M over 8,975 turns).

## Findings

### F1 - Session warm-up is 28 turns before the first edit -> S1596

Median 28 turns from session start to the first `Edit`/`Write`, mean 35.1, p90 67, over 367 sessions.
Roughly 10,000 turns per week elapse before anything changes. Tools consumed before the first edit:
Read 2,026, Bash 1,923, Grep 1,047, PowerShell 991.

Inside the warm-up sits a fixed five-call ritual, visible as consecutive-action pairs:
`spec-next-session Init` -> `spec-next-preflight` (59), `spec-next-preflight` -> `ticket-lease` (63),
`ticket-lease` -> `Skill:spec-all` (33), `spec-next-session` -> `device-ready` (44),
`device-ready` -> `spec-next-session` (38).

Second element of the same ticket: 231 consecutive `Edit` calls against one `INDEX.md` - `/spec-dev`
ticks `[ ]` -> `[x]` one checkbox per edit.

### F2 - The closure facade fails on one run in four -> S1598 (detekt half -> S1595)

`post-change.ps1`: 824 runs, 215 failures (26.1%). `-ScopeToFile` on 736 of them. Distance from a
failed run to the next run: median 8 turns, mean 15.2, p90 34, across 203 cycles.

FAIL labels: detekt-gate 113, ticket-log-audit 27, neuroslop-gate 8, listener-symmetry-gate 7,
doc-pin-drift 5, settings-doc-sync 2, detekt-baseline-absorption 2, flavor-matrix-doc 2, and one each
for script-cheatsheet-sync, strings-audit, oss-notices, flavor-flag. The remaining ~46 failures are
hard tool errors - bad arguments or an unexpanded file glob. `Exit code 1` with no reason is still
the single most common failure signature in the corpus at 574 occurrences.

### F3 - detekt is 75% of all gate wall time, and preflight does not cover what fails it -> S1595

`detekt-gate`: 458 runs, 113 failures (24.7%), 40,216 s = 11.2 h. All 33 other gates together: ~13,000 s
= 3.6 h. `detekt-preflight` runs in ~1.5 s and prints PASS after checking only MaxLineLength,
ImportOrdering and MagicNumber; the ~87 s gate then fails on `LargeClass` (launcherhomeactivity.kt:85,
11+ times this week), `LongMethod` (commandpanellayoutplanner.kt:287) and `ImportOrdering`.

Four detekt-debt tickets sit in Draft while their files are touched weekly: S1198, S1247, S1311, S1541.
S1541 is the LargeClass above.

### F4 - 181 "command not found" failures, three mechanical causes -> S1594

`exit 127` 181 times. `python3` 91 - the machine has `python` only, and `~/bin` is already on PATH, so
a one-line shim closes the class. PowerShell cmdlets piped inside the Bash tool ~89: `Select-Object` 43,
`if` 24, `Out-String` 24, `Select-String` 22, `Get-ChildItem` 8, `Write-Output` 5. `node` 22 - not
installed at all. CLAUDE.md section 7 already states the PowerShell-tool-only rule; it is ungated, and
performs like every ungated rule.

### F5 - guard-uncapped-read: 381 blocks, a third of them buy nothing -> S1594

Measured by finding the next Read of the same file after each block: 45.5% (146) retry with a 300-799
line window, 22.4% (72) with under 300, **31.8% (102) retry with `limit >= 1500`, i.e. read the whole
file anyway**, and 34 files are never re-read. Median gap block -> retry is 2 turns. The most-blocked
file is the command driver `spec-next.md` (52) - `.claude/commands/`, `.claude/templates/` and
`.claude/skills/` are read whole by their nature.

Verified capability: a PreToolUse hook can return `updatedInput` inside `hookSpecificOutput` and modify
the tool input rather than only allow/deny/ask, so the hook could inject `limit` instead of refusing.
Documented but thinly specified per tool - confirm the Read-tool shape against the running Claude Code
version before relying on it. Confirmed *not* possible: PostToolUse cannot rewrite a tool result, and
no hook can fix-and-retry a failed Bash command.

### F6 - The document registry answers nothing 57% of the time -> S1597

720 calls to `document_registry/query.ps1`, 408 empty (56.7%). Empty by area: launcher 52, spec 37,
camera 24, streams 20, permissions 16, localization 16, spec-lifecycle 11, research 11, strings 9,
permission 9, settings 9, device test 9, process 8, player 8. These are exactly the areas the week's
edits concentrated in. The 2026-07-31 audit measured 39%; the exit-code defect it named is fixed, the
coverage gap is not, and the rate has risen as work moved into launcher.

### F7 - Search runs in series, and an eighth of it misses -> S1599

`Grep` 4,264 calls, 544 (12.8%) returning nothing. `Grep` -> `Grep` is the most frequent consecutive
pair in the whole corpus at 1,963; `Glob` -> `Glob` is 300. The class catalog never misses (724 hits,
0 empty) yet is followed by a Grep 73 times and preceded by one 48 times. Zero-hit patterns split into
two kinds: names that no longer exist, and document-structure navigation (`Stage 3`, `Step Log format`,
`Implementation State`) done with full-text search.

> **CORRECTED 2026-08-12 (S1599).** The two sentences above are wrong and the paragraph is kept only
> so its numbers are recognisable when quoted elsewhere. The mining script
> (`temp/scratch/week-audit/dig2.py`) increments its pattern counter on **every** `Grep` call, then
> prints that counter under the zero-hit heading - so the "zero-hit patterns" listed here are simply
> the week's most frequent patterns overall, and the two-kinds split drawn from them is unsupported.
> Re-measured with a zero-hit-only counter: **651 of 4,297 = 15.2%**, not 544 / 12.8%. Document-structure
> navigation is **2.6%** of misses, not half of them. The real shape is scope, not naming: **93.9% of
> empty results carried an explicit `path`, and an unscoped `Grep` missed zero times**; 66.1% were
> abandoned with no follow-up search at all, and 6.9% are absence checks where zero is the correct
> answer. Corrected figures, method and raw output:
> `PLAN/S1599_grep-search-series-and-misses/research/01__zero-hit-anatomy.md`; re-runnable scripts in
> that ticket's `research/tools/`.

## Scale

Turns per week that are mechanically removable, using the per-turn averages above:

| Finding | Turns/week | Share of 6.618 G cache_read |
|---|---:|---:|
| F1 bootstrap chain + checkbox ticking | ~730 | ~2.8% |
| F2 recovery overhead (conservative 2.3 turns/cycle) | ~500 | ~1.9% |
| F4 exit 127 | 181 | 0.7% |
| F5 uncapped-read blocks | 381 | 1.4% |
| F6 empty registry answers | 408 | 1.6% |
| F7 zero-hit Grep | 544 | 2.1% |

About 2,700 turns, ~10% of the week's traffic. F3's 11.2 h of detekt wall time is a wall-clock cost on
top of that, not a token cost.

Removing a turn is not linear in tokens: the turn's own payload also stops riding in every later
request of that session, so these are floors, not ceilings.

## Checked against "do not touch"

None of the six tickets re-litigates a verified non-problem from the 2026-07-31 audit section 5. Prose
and output verbosity, subagent policy, MCP usage, within-segment re-reads, command-surface trimming and
build-file decomposition are all untouched. The one adjacent item - F5 - is not the refuted "full-file
read guard for huge files"; that guard was refuted before it existed, this one exists and is measured
on its own behaviour.

## Tickets

- S1594 `agent-mechanical-command-failures` - F4, F5
- S1595 `detekt-preflight-coverage-gap` - F3, and the detekt half of F2
- S1596 `ticket-session-bootstrap` - F1
- S1597 `document-registry-coverage-gap` - F6
- S1598 `post-change-failure-recovery` - F2 minus detekt
- S1599 `grep-search-series-and-misses` - F7
