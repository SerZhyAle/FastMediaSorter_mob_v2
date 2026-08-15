# Phase 04 - Request Logger

**Strategic spec:** [`../S0268_agent_continuity_layer.md`](../S0268_agent_continuity_layer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05, Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Ship `scripts/agent_continuity/request-log.ps1` - a JSONL append-only request logger - plus a `.gitignore` entry for `dev/agent-continuity/` so accumulated logs never enter version control.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/agent_continuity/request-log.ps1` | New | ≤ 200 |
| `.gitignore` | Modified | ≤ 300 |

---

## Steps

### Step 04.1 - Implement request-log.ps1

**Files:** `scripts/agent_continuity/request-log.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/agent_continuity/request-log.ps1`. Parameters (all optional except `-Request`): `-Request` (mandatory string - raw user prompt or its essential excerpt), `-Route` (slash command or skill cascade), `-Module` (`app_v2` / `wear` / empty), `-Flavor` (string or empty), `-Ticket` (`S\d{4}` or empty), `-FilesTouched` (string array, may be empty), `-ValidationKind` (string, e.g. `dry-run`, `assembleStandardDebug`, `none`), `-ValidationExit` (int - 0 / non-zero), `-InterruptionMarker` (string, empty if none), `-Outcome` (one of `done`, `partial`, `aborted`, `escalated`).
>
> Compute timestamp `yyyy-MM-ddTHH:mm:ss` in local time. Build one JSON object with keys in this exact order: `ts`, `request`, `route`, `module`, `flavor`, `ticket`, `files_touched`, `validation_kind`, `validation_exit`, `interruption_marker`, `outcome`. Use empty string for absent string fields and an empty array `[]` for absent `files_touched`.
>
> Append the object as a single-line JSON to `dev/agent-continuity/requests.jsonl`. Create the directory if missing. UTF-8 without BOM, LF line ending. The append must be a single `Add-Content` call - never rewrite the whole file. Print the timestamp on stdout on success. Exit 0 on success, 1 on write failure.

**Verification:**

- `Glob` - `scripts/agent_continuity/request-log.ps1` exists.
- `Grep` - `^\[CmdletBinding\(\)\]` matches at least once.
- `Grep` - all eleven JSON keys present in the script: `ts`, `request`, `route`, `module`, `flavor`, `ticket`, `files_touched`, `validation_kind`, `validation_exit`, `interruption_marker`, `outcome`.
- `Grep` - the literal `dev/agent-continuity/requests.jsonl` appears at least once.
- File size < 200 lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 5/5 PASS. Files: scripts/agent_continuity/request-log.ps1 (+80 LOC). All 11 JSON keys present, JSONL path literal in comment. Dev log recorded.

---

### Step 04.2 - Gitignore dev/agent-continuity/

**Files:** `.gitignore`
**Depends on:** Step 04.1

**Prompt for developer:**

> Append a new section at the end of `.gitignore` (before the last blank line, after the existing noLegal block):
>
> ```
> # Agent continuity layer artifacts (S0268)
> dev/agent-continuity/
> ```
>
> Do not modify any other line. Preserve the file's existing trailing structure.

**Verification:**

- `Grep` - the literal `dev/agent-continuity/` appears exactly once in `.gitignore`.
- `Grep` - the literal `Agent continuity layer artifacts (S0268)` appears exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: .gitignore (+3 lines). Both literals appear exactly once.

---

### Step 04.3 - Append + read-back smoke

**Files:** none (verification-only)
**Depends on:** Step 04.2

**Prompt for developer:**

> Invoke the logger with a marker payload and confirm the JSONL line is appended and parses:
>
> ```pwsh
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/agent_continuity/request-log.ps1 `
>   -Request "smoke-phase04" -Route "/spec-all" -Module "app_v2" -Ticket S0268 `
>   -ValidationKind "dry-run" -ValidationExit 0 -Outcome done
> ```
>
> Then verify that the last line of `dev/agent-continuity/requests.jsonl` contains the literal substring `smoke-phase04` and that it parses as JSON via `ConvertFrom-Json`.

**Verification:**

- Bash: logger exits 0.
- Bash: last line of `dev/agent-continuity/requests.jsonl` contains the substring `smoke-phase04`.
- Bash: `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -Command "(Get-Content dev/agent-continuity/requests.jsonl -Tail 1) | ConvertFrom-Json | Out-Null; \$LASTEXITCODE"` returns 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Logger exit=0; tail line contains `smoke-phase04`; ConvertFrom-Json succeeds with all 11 keys. (Route value mangled by Git Bash MSYS path translation in the test invocation - cosmetic, not a script bug; pwsh callers in skills are unaffected.)

---

## Phase Done Criteria

- [x] Steps 04.1, 04.2, 04.3 are all `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entries added for `scripts/agent_continuity/request-log.ps1` and `.gitignore` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`dev/agent-continuity/requests.jsonl` is the canonical log source for Phase 05's digest. Schema is frozen by the eleven-key contract in step 04.1; any future field must be additive (appended to the JSON object) so older lines remain parseable.

---

## Rollback Plan

Revert the phase commit. Accumulated log lines under `dev/agent-continuity/` remain on disk (gitignored) but become orphaned - safe to delete manually.
