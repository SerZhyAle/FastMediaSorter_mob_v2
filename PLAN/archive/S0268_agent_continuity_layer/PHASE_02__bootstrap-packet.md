# Phase 02 - Bootstrap Packet

**Strategic spec:** [`../S0268_agent_continuity_layer.md`](../S0268_agent_continuity_layer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Ship `scripts/agent_continuity/start-packet.ps1` - a single PowerShell invocation that prints all seven information blocks defined in strategic §5.1 and exits with code 0 on success.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/agent_continuity/start-packet.ps1` | New | ≤ 250 |

---

## Steps

### Step 02.1 - Implement start-packet.ps1

**Files:** `scripts/agent_continuity/start-packet.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/agent_continuity/start-packet.ps1`. The script accepts one optional parameter `-Ticket` (a `S\d{4}` id). It prints seven labelled blocks to stdout in this exact order, each block separated by a blank line:
>
> 1. `## branch` - output of `git branch --show-current` plus a one-word role label: `release-stable` if `main`, `debug-current` if `DEBUG-v00N`, `feature` otherwise.
> 2. `## dirty-tree` - count of files reported by `git status --porcelain` and a steady-state indicator: `steady` if count <= 30, `active` if 31..125, `surge` if > 125.
> 3. `## active-ticket` - if `-Ticket` is provided, print that id and the corresponding title pulled from `PLAN/spec-catalog.jsonl`; otherwise pick the spec record from the journal with the most-recent `updated` field whose status is one of `Draft`, `Approved`, `Tactical`, `In Progress`, `BlockNeedUserTest`, and print `<id> - <name>`. If no candidate matches, print `none`.
> 4. `## modules` - the file paths from `git diff --name-only HEAD~10..HEAD` (or fewer if the branch is shorter) bucketed by top-level prefix (`app_v2/`, `wear/`, `scripts/`, `dev/`, `docs/`, `PLAN/`, other) with a count per bucket. Output one bucket per line, sorted descending by count.
> 5. `## prompt-routing` - the literal line `See CLAUDE.md "Mandatory Skills" table - route by situation column.` followed by a compact reminder list (one line each) of the five most-relevant skills for the current bucket-mix: any of `/quick`, `/spec`, `/spec-tech`, `/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-update`, `/spec-all`, `/build`, `/log-reader`, `/catalog`, `/git`. Choose the five whose mandate matches the dominant bucket from block 4.
> 6. `## docs-vs-gradle` - read `app_v2/build.gradle.kts` for `compileSdk`, `minSdk` (standard flavor), Kotlin version pin (`org.jetbrains.kotlin.android` line in `build.gradle.kts` or the toml), and ExoPlayer/Media3 pin if present; cross-check against `docs/TECH_STACK.md`. For each mismatch print `MISMATCH: <key> docs=<X> gradle=<Y>`. If everything matches print `OK - in sync`.
> 7. `## ux-volatility` - print the last 10 lines of `dev/FUNCTIONALITY.log` filtered to `CHANGE` or `FIX` ops (use the most recent 10 matching lines; if fewer exist, print whatever is available, then a one-line note).
>
> Mandatory: the script starts with `[CmdletBinding()] param(...)`, uses `$ErrorActionPreference = 'Stop'`, never invokes gradle/git in a destructive way, never writes to anywhere outside stdout. Exit code 0 on success, 1 on any unrecoverable parse error (e.g. missing `PLAN/spec-catalog.jsonl`).

**Verification:**

- `Glob` - `scripts/agent_continuity/start-packet.ps1` exists.
- `Grep` - `^\[CmdletBinding\(\)\]` matches at least once in the file.
- `Grep` - all seven block headers present: `## branch`, `## dirty-tree`, `## active-ticket`, `## modules`, `## prompt-routing`, `## docs-vs-gradle`, `## ux-volatility`.
- File size < 250 lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: scripts/agent_continuity/start-packet.ps1 (+168 LOC). Dev log recorded.

---

### Step 02.2 - Smoke-run start-packet.ps1

**Files:** none (verification-only)
**Depends on:** Step 02.1

**Prompt for developer:**

> Invoke the bootstrap packet in dry-run mode on the current branch via `pwsh -NoProfile -File scripts/agent_continuity/start-packet.ps1 > temp/agent_continuity_smoke_phase02.txt`. Confirm exit code is 0 and stdout contains all seven block headers. Do not commit the smoke output file - leave it under `temp/` which is gitignored.

**Verification:**

- Bash: `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/agent_continuity/start-packet.ps1 > temp/agent_continuity_smoke_phase02.txt 2>&1; echo "exit=$?"` - record exit code, expected `exit=0`.
- `Grep` on `temp/agent_continuity_smoke_phase02.txt` - all seven headers (`## branch`, `## dirty-tree`, `## active-ticket`, `## modules`, `## prompt-routing`, `## docs-vs-gradle`, `## ux-volatility`) each match exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. exit=0; 7 headers each exactly once in smoke output. Active ticket correctly identified as S0268 by heuristic.

---

## Phase Done Criteria

- [x] Steps 02.1 and 02.2 are `[x] done`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `scripts/agent_continuity/start-packet.ps1` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`start-packet.ps1` is the single canonical entry point an agent invokes at session start. No other phase depends on its internals - they share the directory and the README contract only.

---

## Rollback Plan

Revert the phase commit. The utility lives under `scripts/agent_continuity/` and has no dependents in the build or in app code.
