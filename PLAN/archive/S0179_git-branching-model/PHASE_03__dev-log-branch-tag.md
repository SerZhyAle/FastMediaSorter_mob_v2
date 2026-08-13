# Phase 03 — dev-log-branch-tag

**Strategic spec:** [`../S0179_git-branching-model.md`](../S0179_git-branching-model.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Update `scripts/add_to_dev_log.ps1` to automatically append a `[branch: <name>]` tag to every `dev/CHANGELOG.md` entry, using the current git branch name. Add optional `-Branch` override parameter for CI/test contexts. Handle detached HEAD gracefully.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean.
- [ ] Read `scripts/add_to_dev_log.ps1` in full before editing (file is ~77 lines).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/add_to_dev_log.ps1` | Modified | ≤ 110 total (was 77) |

---

## Steps

### Step 03.1 — Add `-Branch` parameter and branch-detection logic

**Files:** `scripts/add_to_dev_log.ps1`
**Depends on:** — start of phase (Phase 01 done)

**Prompt for developer:**

> Read `scripts/add_to_dev_log.ps1`. The script currently has three mandatory parameters: `$FilePath`, `$Target`, `$Description`. Add a fourth optional parameter:
>
> ```powershell
> [Parameter(Mandatory = $false)]
> [string]$Branch = ""
> ```
>
> After the `$ErrorActionPreference = "Stop"` line and before the path-resolution block, insert the branch-detection block:
>
> ```powershell
> # Detect current git branch for changelog context
> if ([string]::IsNullOrEmpty($Branch)) {
>     $detectedBranch = (git branch --show-current 2>$null).Trim()
>     if ([string]::IsNullOrEmpty($detectedBranch)) {
>         # Detached HEAD — use short SHA
>         $shortSha = (git rev-parse --short HEAD 2>$null).Trim()
>         $detectedBranch = if ($shortSha) { "detached/$shortSha" } else { "unknown" }
>     }
>     $Branch = $detectedBranch
> }
> ```

**Verification:**

- `Grep` — `\$Branch` appears in `scripts/add_to_dev_log.ps1` (at least 3 times: param, detection block, usage).
- `Grep` — `detached/` appears in `scripts/add_to_dev_log.ps1`.
- `Grep` — `git branch --show-current` appears in `scripts/add_to_dev_log.ps1`.

**Status:** `[ ]` not done

---

### Step 03.2 — Append branch tag to the log entry and console output

**Files:** `scripts/add_to_dev_log.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `scripts/add_to_dev_log.ps1`, locate the line that builds `$entry`:
>
> ```powershell
> $entry = "| $timestamp | ``$safeFile`` | ``$safeTarget`` | $safeDesc |"
> ```
>
> Replace it with:
>
> ```powershell
> $branchTag = "[branch: $Branch]"
> $entry = "| $timestamp | ``$safeFile`` | ``$safeTarget`` | $safeDesc $branchTag |"
> ```
>
> Also update the `Write-Host` line below it to include the branch tag:
>
> ```powershell
> Write-Host "[DEV_LOG] $timestamp | $safeFile | $safeTarget | $safeDesc $branchTag" -ForegroundColor Green
> ```

**Verification:**

- `Grep` — `\$branchTag` appears in `scripts/add_to_dev_log.ps1`.
- `Grep` — `branch:` appears in `scripts/add_to_dev_log.ps1` (the tag string).
- Manual smoke test: run `.\scripts\add_to_dev_log.ps1 "test/file.md" "test" "smoke test"` — console output ends with `[branch: main]` (or current branch name).
- `Grep -n "Log\.d\("` in `scripts/add_to_dev_log.ps1` returns zero hits (PowerShell script — N/A, predicate trivially passes).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Smoke test passes: `.\scripts\add_to_dev_log.ps1 "scripts/add_to_dev_log.ps1" "add_to_dev_log" "Add branch tag to dev log entries"` — entry in `dev/CHANGELOG.md` contains `[branch: ...]`.
- [ ] Dev log entry added for `scripts/add_to_dev_log.ps1` itself via the updated script (so the first real entry shows the branch tag).

---

## Handoff Notes to Next Phase

After Phase 03, every subsequent `add_to_dev_log.ps1` call (including from Phases 04 and 05) will automatically tag entries with the active branch. No further changes to other scripts are required for branch-tagging.

---

## Rollback Plan

Revert `scripts/add_to_dev_log.ps1` to prior state — existing `dev/CHANGELOG.md` entries are unaffected (no backfill).
