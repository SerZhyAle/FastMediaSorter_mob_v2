# Phase 01 - Pid-aware log filter

**Strategic spec:** [`../S1332_bugfix-adb-log-hides-app-timber-lines.md`](../S1332_bugfix-adb-log-hides-app-timber-lines.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 6 / 6
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Make `adb.ps1 log` select the app's own lines by process id, keep the existing package-text patterns as a second arm so system-side lines about the app survive, and make a filter that swallows matching lines announce itself instead of printing a clean `OK 0 line(s)`.

---

## Prerequisites

- [x] `temp/CODE.LOCK` acquired: `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S1332 phase 01"`.
- [x] `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build` reports no live build.
- [x] Backup taken - see Step 01.2. `scripts/devtest/adb.ps1` is 487 lines now and passes 500 after this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/lib/adb-log-filter.ps1` | New | <= 90 |
| `scripts/devtest/adb.ps1` | Modified | <= 540 |

> `scripts/quality/lib/` is the house precedent for a dot-sourced, device-free helper consumed by both a production script and a `*.tests` suite (`changed-files.ps1`, `detekt-report.ps1`). `scripts/devtest/lib/` mirrors it.

---

## Steps

### Step 01.1 - Create the pure filter lib

**Files:** `scripts/devtest/lib/adb-log-filter.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a new dot-sourceable helper holding the whole line-selection decision, with no adb call and no device access, so both `adb.ps1` and the Phase 02 suite can drive it. Define exactly three functions.
>
> `Get-AppPidsFromLog -Lines <string[]> -BasePackage <string>` returns the distinct process ids the capture itself attributes to the app: scan for `Start proc (?<p>\d+):<BasePackage>` (regex-escape the package). This deliberately prefix-matches, so it catches `com.sza.fastmediasorter`, `com.sza.fastmediasorter.debug` and any `:sub` process name in one pass, and it recovers a process that has already exited but whose lines are still in the buffer.
>
> `Select-AppLogLines -Lines <string[]> -AppPids <int[]> -TextPatterns <string[]>` returns the kept lines in their original order. Keep a line when EITHER its threadtime pid column is in `-AppPids`, OR it matches any entry of `-TextPatterns`. Parse the pid column with `'^\s*\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(?<pid>\d+)\s+\d+\s+[VDIWEF]\s'` - the same shape already proven in `scripts/devtest/prerelease-log-audit.ps1`. Evaluate each line once. Do not add a rule for buffer separator lines such as `--------- beginning of crash`: they carry neither a pid column nor package text and must stay dropped (see INDEX invariants).
>
> `Measure-FilterCoverage -RawLines <string[]> -KeptLines <string[]> -Pattern <string>` returns `[pscustomobject]` with `rawMatched`, `keptMatched` and `suppressed` (`rawMatched - keptMatched`), counting how many lines match `-Pattern` before and after filtering. This is the primitive the self-check and the regression suite both consume.
>
> The file is dot-sourced, so it must declare no `param()` block, must not set `$ErrorActionPreference`, and must produce no output at load time.

**Verification:**

- `Glob` - `scripts/devtest/lib/adb-log-filter.ps1` exists.
- `Grep` - `function Get-AppPidsFromLog`, `function Select-AppLogLines` and `function Measure-FilterCoverage` each match exactly once.
- `Grep` - `Start proc` matches at least once in the file.
- `Grep` - `beginning of crash` returns zero hits in the file.
- `Grep` - `^param\(|^\[CmdletBinding` returns zero hits in the file.
- Value equality - `pwsh -NoProfile -Command ". scripts/devtest/lib/adb-log-filter.ps1; exit 0"` exits 0 and prints nothing.

**Status:** `[x]` done

---

### Step 01.2 - Back up adb.ps1 before editing

**Files:** `temp/S1332/adb.ps1.<yyyyMMdd_HHmmss>.bak`
**Depends on:** Step 01.1

**Prompt for developer:**

> Copy `scripts/devtest/adb.ps1` to `temp/S1332/` with a timestamped name before any edit. The file crosses 500 lines after this phase, which triggers the backup rule (CLAUDE.md Rule 5). Create `temp/S1332/` if absent - ticket-bound scratch lives in a per-ticket subdirectory.

**Verification:**

- `Glob` - `temp/S1332/adb.ps1.*.bak` matches at least one file.

**Status:** `[x]` done

---

### Step 01.3 - Resolve the app's live process ids

**Files:** `scripts/devtest/adb.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> In the `log` verb, after `Resolve-Package` and before the filtering, build the app's pid set. Union three sources and de-duplicate:
>
> 1. `Invoke-Adb $id @('shell', 'pidof', $pkg) -AllowFail` - the main process. Split on whitespace, keep tokens that are all digits. `pidof` is toybox and needs API 24 or newer, so treat an empty or error result as "no live pid", never as a failure.
> 2. `Invoke-Adb $id @('shell', 'ps', '-A', '-o', 'PID,NAME') -AllowFail` - catches any `:sub` process, which `pidof` cannot match because it compares the whole process name. Keep rows whose NAME equals `$pkg` or starts with `"${pkg}:"`. An unsupported `ps` form yields nothing; that is acceptable.
> 3. `Get-AppPidsFromLog` over the captured window - recovers a process that died inside the window.
>
> Both adb calls use `-AllowFail`, so neither can raise `Fail 7`. An empty pid set is a legal state: the filter then degrades to exactly today's text-only behaviour and the Step 01.5 self-check reports the shortfall. Add no new exit code and no new `Fail` call.
>
> Dot-source the lib once near the top of the script, next to the `$BASE_PACKAGE` constants, using `$PSScriptRoot`.

**Verification:**

- `Grep` - `lib/adb-log-filter.ps1` matches exactly once in `scripts/devtest/adb.ps1` and the line begins with `. ` (dot-source).
- `Grep` - `'pidof'` and `'PID,NAME'` each match exactly once in `scripts/devtest/adb.ps1`.
- `Grep` - both new `Invoke-Adb` calls carry `-AllowFail` on the same line.
- `Grep` - `Fail 7` count in `scripts/devtest/adb.ps1` is unchanged from before the edit.
- `Grep` - `Get-AppPidsFromLog` matches exactly once in `scripts/devtest/adb.ps1`.

**Status:** `[x]` done

---

### Step 01.4 - Replace the text pre-filter with the union filter

**Files:** `scripts/devtest/adb.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Replace the inline `Where-Object` block that currently keeps only lines matching `$patterns` (the package id, `$BASE_PACKAGE`, and the literal `FastMediaSorter`) with a single call to `Select-AppLogLines`, passing the pid set from Step 01.3 and the same `$patterns` array as `-TextPatterns`. Keep `$patterns` intact: those three entries are what preserves the system-side lines that name the app - `ActivityManager: Start proc`, `ANR in <pkg>`, `AndroidRuntime: Process: <pkg>` - which have system_server's pid and would otherwise vanish.
>
> Pin the capture format by adding `'-v', 'threadtime'` to the `logcat` argument array. `threadtime` is already the default, so no output changes, but pinning it makes the pid-column parse deterministic rather than dependent on the device's default.
>
> Apply `-Grep` after the union filter, exactly as today - the `-Grep` contract does not change. Keep writing the unfiltered window to the capture file under `temp/scratch/`, unchanged: it is the reference the self-check compares against.
>
> Replace the stale comment `# Keep app lines + the project's named tags; logcat does not stamp every line with the pid.` The premise is wrong - threadtime stamps every line with its pid, which is precisely why the text heuristic was never needed. Write one WHY line about pid being the ownership signal and text being the second arm for system-side lines.

**Verification:**

- `Grep` - `Select-AppLogLines` matches exactly once in `scripts/devtest/adb.ps1`.
- `Grep` - `logcat does not stamp every line with the pid` returns zero hits in the repository.
- `Grep` - `'-v', 'threadtime'` matches exactly once in `scripts/devtest/adb.ps1`.
- `Grep` - `[regex]::Escape($pkg)` still matches in `scripts/devtest/adb.ps1` (the text arm survives).
- `Grep` - `Out-File -FilePath $logFile` still matches, and the variable written to it is still `$raw`, not the filtered set (the capture file must keep holding the unfiltered window - it is the reference the self-check compares against).
- Value equality - `pwsh -NoProfile -File scripts/devtest/adb.ps1 help` exits 0.

**Status:** `[x]` done

---

### Step 01.5 - Add the suppressed-line self-check

**Files:** `scripts/devtest/adb.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> This is the permanent guard against the whole defect class, so it must run on every invocation, not only in tests. When `-Grep` is supplied, call `Measure-FilterCoverage` with the raw window, the filtered lines and the caller's pattern. When `suppressed` is greater than zero, replace the `OK` verdict with a `WARN` verdict and print one extra diagnostic line naming the suppressed count and the capture file path.
>
> The diagnostic text must never interpolate `$Grep`. `smoke.ps1` and `prerelease-prepare.ps1` both `-match` their own crash pattern against this script's entire stdout, so echoing the pattern back would be read as a crash. Say "pattern" in words, print counts and the file path, print nothing of the pattern itself.
>
> Keep the exit code at 0 in both branches. Zero matches after filtering is a legitimate answer; the warning exists to stop it being mistaken for proof of absence.
>
> Extend the `-Json` payload with `rawMatched`, `suppressed` and `appPids` so a machine caller sees the same signal. Leave `ok`, `exitCode`, `matched` and `file` untouched - `-Json` consumers depend on them.

**Verification:**

- `Grep` - `Measure-FilterCoverage` matches exactly once in `scripts/devtest/adb.ps1`.
- `Grep` - `rawMatched`, `suppressed` and `appPids` each match at least once in `scripts/devtest/adb.ps1`.
- `Grep` - `\$Grep` inside any `Write-Host` or `Write-Line` argument returns zero hits in `scripts/devtest/adb.ps1`. This is the caller-contract predicate; check every printed string, not only the new ones.
- `Grep` - `exit 0` still terminates both the warning and the normal branch of the `log` verb; no `exit` with a value other than 0 was added to the verb.
- `Grep` - the script header's exit-code table still lists exactly `0`, `1`, `2`, `3`, `4`, `7`.

**Status:** `[x]` done

---

### Step 01.6 - Update the script header

**Files:** `scripts/devtest/adb.ps1`
**Depends on:** Step 01.5

**Prompt for developer:**

> Rewrite the `log` line in the `.DESCRIPTION` verb list so it states the real selection rule: lines whose pid belongs to the app process, plus lines whose text names the package. Add a short paragraph explaining that the pid set comes from `pidof`, from `ps -A`, and from `Start proc` lines in the capture, and that an empty pid set degrades to text-only matching with a `WARN` verdict rather than an error.
>
> State in the header that the exit-code table is unchanged and that a suppressed-line warning is deliberately not an error, so a later reader does not add a code for it. Leave the `.EXAMPLE` blocks as they are - the invocation form does not change.

**Verification:**

- `Grep` - `pidof` matches in the header block of `scripts/devtest/adb.ps1` (before the `param(` line).
- `Grep` - `logcat -d tail for the app` returns zero hits (the old, now-misleading description is gone).
- `Grep` - the header still contains `7 - the underlying adb command returned non-zero`.
- Value equality - `pwsh -NoProfile -Command "Get-Help scripts/devtest/adb.ps1 -Full | Out-Null; exit 0"` exits 0 (the comment-based help still parses).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/devtest/adb.ps1 help` exits 0 (Validation Ladder, Script rung).
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Path scripts/devtest/adb.ps1` exits 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `scripts/devtest/adb.ps1` is at or under 540 lines: `(Get-Content scripts/devtest/adb.ps1).Count`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md section 13). The audit trigger here is a change to the log-reading path that every device verification depends on.

---

## Handoff Notes to Next Phase

`scripts/devtest/lib/adb-log-filter.ps1` exposes `Get-AppPidsFromLog`, `Select-AppLogLines` and `Measure-FilterCoverage` as pure functions over string arrays. Phase 02 drives them directly with a fixture and never calls adb.

---

## Rollback Plan

Restore `scripts/devtest/adb.ps1` from `temp/S1332/adb.ps1.*.bak` and delete `scripts/devtest/lib/adb-log-filter.ps1`. No device state, no data migration, no user-facing surface.
