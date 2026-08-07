---
name: transcript-cost-mining
description: How to measure agent token cost from ~/.claude/projects transcripts without the 3x counting error
metadata:
  type: reference
---

Session transcripts live at `~/.claude/projects/p--ANDROID-FastMediaSorter-mob-v2/*.jsonl` (main tier) **and** `<session-id>/subagents/**/*.jsonl` (nested tier, ~2.5x more files, no requestId overlap, fully additive).

**Cold tier (2026-08-07):** everything older than 2026-07-17 was moved out of `~/.claude/projects` into `C:\Users\serzh\claude-transcripts-archive\transcripts-before-2026-07-17.zip` (2,091 files, 786 MB -> 339 MB, relative paths preserved, both tiers included). A miner that only walks `~/.claude/projects` now silently reports a shorter window than it thinks - unzip the archive to a scratch dir and walk both roots when the question spans more than three weeks. Same treatment is likely for later cutoffs, so check the archive dir for additional zips before quoting any period total.

Three traps, all of which silently inflate results:

- **One API response is written as several JSONL records** (thinking block, text block, each `tool_use`), and every record repeats the same `usage` object verbatim. Forked sessions replay history on top. Summing records instead of unique `requestId` inflates tokens **3.13x** and counts (tool calls, reads, spawns) **1.43x**. Always dedup by `requestId`, keeping the max per id - one placeholder `(0,0,0,0)` row exists.
- **`os.listdir` misses the nested tier** - 17.3% of all traffic. Use `os.walk`, and report tiers separately as well as combined: per-turn context averages are only meaningful within a tier, since subagent conversations are structurally shorter.
- **Never regex a tool result body for failure words.** A Kotlin file containing `catch (e: Exception)` scores as a failed Read - that alone over-counts Read failures 24.7x. Use `is_error` for hard failures, and keep a separate soft band (gate FAIL / `BUILD FAILED` / non-zero exit) restricted to Bash/PowerShell/Agent/Skill, because gradle runs backgrounded and returns a clean tool_result.

Compaction boundaries are `system` records with `subtype=compact_boundary`, carrying `compactMetadata.preTokens` - the authoritative way to see how large context got before a reset.

**Why:** the first pass of the 2026-07-31 process audit priced the month at ~$83k and a 9.3% tool-failure rate; both were artifacts. Real figures: 14.784 G cache_read over 67,905 requests, ~$30.6k Opus-list equivalent, 2.71% hard failure rate. An adversarial verifier caught it; the wrong numbers had already been circulated.

**How to apply:** before quoting any agent-cost number, confirm the extractor dedups by `requestId`, walks recursively, and uses `is_error`. Percentages *of cache_read* survive the bug (numerator and denominator inflate together); absolute totals and all counts do not. See [[process-audit-2026-07]].
