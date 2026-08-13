# Phase 02 - Context-threshold check and handoff

**Strategic spec:** [`../S1339_spec-next-bounded-rounds.md`](../S1339_spec-next-bounded-rounds.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Add the mechanical sensor (`-Verb CheckContext`, reads the live session transcript, compares to the threshold) and the mechanical recommendation (`-Verb Handoff`, composes the one-screen stop block) to `scripts/spec_catalog/spec-next-session.ps1`. Per strategic §3, the threshold check must be mechanical, not the agent's guess about its own size.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `-Verb Init`/`Record`/`Device`/`Resume`/`Report` exist and pass their verification.
- [ ] Confirm `$env:CLAUDE_CODE_SESSION_ID` is set in the current shell and matches a file at `~/.claude/projects/*/<value>.jsonl` (`ls -t ~/.claude/projects/*/*.jsonl` after filtering by the env var's value) - this was confirmed live during research for this spec; re-confirm it still holds before writing Step 02.1, since the whole design depends on it.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/spec-next-session.ps1` | Modified (adds ~80 lines to Phase 01's file) | ≤ 400 total after this phase |

---

## Steps

### Step 02.1 - `-Verb CheckContext`

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** Step 01.1 (shared param block, `-Threshold`)

**Prompt for developer:**

> Locate the current session's live transcript and read the newest carried-context figure:
>
> 1. `$sessionId = $env:CLAUDE_CODE_SESSION_ID`. Missing/empty -> exit 2 ("cannot verify: no session id in environment"), print a one-line reason to stdout.
> 2. `$transcript = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.claude\projects') -Recurse -Filter "$sessionId.jsonl" -ErrorAction SilentlyContinue | Select-Object -First 1`. No match -> exit 2 ("cannot verify: no transcript file for session $sessionId").
> 3. Read the file with `[System.IO.File]::ReadLines($transcript.FullName)`, iterate once, and remember the last line where the parsed object has `.type -eq 'assistant'` and `.message.usage.cache_read_input_tokens` is non-null. Wrap each line's `ConvertFrom-Json` in `try/catch` and skip malformed lines rather than aborting the scan (a single corrupt line must not fail the whole read). No matching line found after a full scan -> exit 2 ("cannot verify: no assistant usage record yet - session just started").
> 4. `$tokens = <that line>.message.usage.cache_read_input_tokens`. Compare against the effective threshold: `-Threshold` if the caller passed a non-default value, else the state file's own `threshold` field (falls back to 300000 if no state file - `CheckContext` must work even before `-Verb Init` has run, since it is a read-only sensor).
> 5. Print `{ "tokens": <n>, "threshold": <n>, "crossed": <bool> }` to stdout. Exit 3 if `$tokens -ge $effectiveThreshold`, else exit 0. (Exit 2 is reserved for "could not determine" per step 3/1/2 above - never conflate "under threshold" with "unknown".)
>
> This is a **read-only** verb - it never writes the state file itself. The driver (Phase 03) decides what to do with the exit code.

**Verification:**

- Run `pwsh -NoProfile -File scripts/spec_catalog/spec-next-session.ps1 -Verb CheckContext` in this live session -> exit 0 or 3 (never 2, since this session has assistant records with usage by now), stdout parses as JSON with a `tokens` field that is a positive integer.
- Run the same with `-Threshold 1` -> exit 3 (any real session already exceeds 1 token).
- Temporarily run with `$env:CLAUDE_CODE_SESSION_ID` unset (`Remove-Item Env:\CLAUDE_CODE_SESSION_ID` in a throwaway subshell, or pass a bogus session id via a debug override if one is added) -> exit 2.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS against the *live session this pipeline is running in* (real end-to-end proof, not a mock): plain `-Verb CheckContext` -> exit 0, `{"tokens":288611,"threshold":300000,"crossed":false}`; `-Threshold 1` -> exit 3, `{"tokens":289132,"threshold":1,"crossed":true}`; bogus `$env:CLAUDE_CODE_SESSION_ID` -> exit 2, "no transcript file for session ...". Files: scripts/spec_catalog/spec-next-session.ps1 (+38 LOC, new `Get-ContextCheck` helper + `CheckContext` case). `post-change.ps1 -ChangeType Script` PASS after `help.ps1 -Generate`. Dev log recorded by post-change.ps1.

---

### Step 02.2 - `-Verb Handoff`

**Files:** `scripts/spec_catalog/spec-next-session.ps1`
**Depends on:** Step 02.1, Step 01.4 (`-Verb Report` shape), Phase 01's state file (`processed[]`, `tally`)

**Prompt for developer:**

> Compose the fixed-order stop block from strategic §4.4, in this exact section order:
>
> 1. **What just happened.** Same source as `-Verb Report`: `processed[]` entries from the state file with their outcomes, plus the tally line. **Cap the listed entries to the last 5** (`Select-Object -Last 5`), with a `.. and N more earlier this session` line when truncated - a session can run many single-ticket rounds (one `/spec-all` delegation each) before accumulating 300k tokens, so an uncapped list would blow past "one screen" on any long session. The tally line is never truncated - it always covers the whole session.
> 2. **Why it stopped.** Call `CheckContext` logic in-process (reuse the function from Step 02.1, do not shell out to self) and print the absolute token count against the threshold - **never a percentage**, per S1338 package B's own finding that a percentage-of-window hides the real cost. If the in-process check itself cannot determine (edge case), print `context: unavailable` rather than aborting - the handoff's value is the recommended commands, which do not depend on knowing the exact number.
> 3. **What is next in the queue.** Shell out to `pwsh -NoProfile -File scripts/spec_catalog/spec-next-preflight.ps1 -Exclude <processed-ids-csv> -Format json`, parse `.selected.id` / `.selected.name`. `null` selected -> print "queue: backlog exhausted" instead of a candidate line.
> 4. **The recommended commands, in order:**
>    ```text
>    1. /clear - all state is on disk (temp/spec-next-session.json); a /compact summary would only re-carry what the files already hold.
>    2. /spec-next --resume - continue bounded.
>    3. /spec-do --resume - continue unbounded (deliberate escape hatch, spends tokens on purpose).
>    ```
> 5. **What needs the human.** Count `processed[]` entries with `outcome -eq 'blocked'` from the state file, plus a live `BlockNeedUserTest` count via `pwsh -NoProfile -File scripts/spec_catalog/search.ps1 -Status BlockNeedUserTest -Format json` (count the array). Print both counts; if both are zero, print "nothing waiting on you this round".
>
> Keep the whole block at or under 20 printed lines - constraint from strategic §4.4 ("must fit on one screen"). Exit 0 always (this verb reports, it does not fail the loop - if a sub-call like `spec-next-preflight.ps1` errors, catch it and print "queue: could not determine" rather than propagating a non-zero exit).

**Verification:**

- Run `-Verb Init`, `-Verb Record -Id S0001 -Outcome verified`, `-Verb Record -Id S0002 -Outcome blocked`, then `-Verb Handoff` -> exit 0.
- Stdout contains all five section labels ("What just happened", "Why it stopped", "What is next", "/clear", "What needs the human") and the literal strings `/clear`, `/spec-next --resume`, `/spec-do --resume` in that order.
- `($handoffOutput -split "`n").Count -le 20`.
- Stdout does **not** contain a `%` character (percentage ban from strategic §4.4).
- Run with 8 `-Verb Record` calls before `-Verb Handoff` -> "What just happened" header reads `(last 5 of 8)`, lists exactly 5 entries plus a `.. and 3 more earlier this session` line, and the tally line still reads `processed 8` (uncapped) - confirms the cap bounds the block regardless of session length.

**Status:** `[x]` done

**Step Log:**

- 2026-08-01 - Verification 5/5 PASS, against the *live session this pipeline is running in* (real preflight + real BlockNeedUserTest count, not mocked). Found and fixed a real design flaw during this step's first run: with the strategic §4.4 "one screen" prompt taken literally against only a 2-ticket test, the first implementation passed at 21 lines (over the intended 20) with "What just happened" listing every processed entry unbounded - which would blow the budget on any real long session (many single-ticket rounds run before one threshold stop, not one round per stop). Fixed by capping the list to the last 5 entries with a "N more" summary line; re-verified at 8 entries (20 lines, capped) and at 2 entries (16 lines). Files: scripts/spec_catalog/spec-next-session.ps1 (+62 LOC, `Handoff` case). `post-change.ps1 -ChangeType Script` PASS after `help.ps1 -Generate`. Dev log recorded by post-change.ps1.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Script runs standalone: both new verbs return their documented exit codes against a live state file.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `scripts/spec_catalog/spec-next-session.ps1` - `post-change.ps1`'s `[dev-log]` gate logged one line per step.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Reviewed the two new blocks: `Get-ContextCheck` has no broad/empty catch (the per-line `try/catch` inside the transcript scan has an explicit, intentional `continue` - skip one malformed JSONL line, not swallow a real error - which is the documented, deliberate behavior, not a silent-failure smell); `Handoff`'s two external `& $pwshExe ...` calls are each wrapped in `try/catch` with an explicit fallback string ("could not determine" / `$needTestCount = -1`), never a bare swallow. No P0/P1. One P2 noted and accepted rather than fixed: `Handoff` shells out to `spec-next-preflight.ps1` and `search.ps1` as separate child processes (two extra `pwsh.exe` cold-starts) rather than dot-sourcing - acceptable because `Handoff` fires once per threshold stop (rare, not hot-path) and dot-sourcing `spec-next-preflight.ps1` would pull in its own `_lib.ps1`/global scope into this script, a coupling risk not worth taking for an infrequent call.

---

## Handoff Notes to Next Phase

`spec-next-session.ps1` now exposes all six verbs (`Init`, `Record`, `Device`, `CheckContext`, `Resume`, `Report`, `Handoff` - seven, counting both bookkeeping and sensor/recommendation). Phase 03 wires these into `.claude/commands/spec-next.md`'s Stage 0 and Stage 5; Phase 04 reuses the identical verb set for `/spec-do`.

---

## Rollback Plan

Revert phase commit(s) - both verbs are additive to Phase 01's file and have no callers until Phase 03/04. No data migration, no user-facing surface changed.
