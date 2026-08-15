# Phase 02 - Filter regression suite

**Strategic spec:** [`../S1332_bugfix-adb-log-hides-app-timber-lines.md`](../S1332_bugfix-adb-log-hides-app-timber-lines.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Pin the fixed selection rule with a hermetic, device-free suite driven by a recorded threadtime capture, so the exact reported line - a Timber probe under a bare class tag - can never be silently dropped again.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] `scripts/devtest/lib/adb-log-filter.ps1` exists and dot-sources cleanly.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/adb-log-filter.tests/fixtures/logcat_threadtime_sample.txt` | New | <= 30 |
| `scripts/devtest/adb-log-filter.tests/Run-Tests.ps1` | New | <= 150 |

> Naming and shape follow the six existing suites (`scripts/quality.tests/`, `scripts/guard.tests/`, `scripts/spec_catalog/preview.tests/`): a `<subject>.tests/Run-Tests.ps1` that dot-sources the lib, prints one `PASS | ..` or `FAIL | ..` line per case, and exits 0 or 1. `scripts/quality/assert-exit-contract.ps1` already skips `*.tests` directories, so the fixtures cannot trip that gate.

---

## Steps

### Step 02.1 - Record the fixture capture

**Files:** `scripts/devtest/adb-log-filter.tests/fixtures/logcat_threadtime_sample.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Write a small hand-authored threadtime capture that contains one line of each class the filter must decide about. Use `24469` as the app pid, `1234` as system_server's, and `9001` as an unrelated app's. Include exactly these rows, in buffer order:
>
> 1. `ActivityManager: Start proc 24469:com.sza.fastmediasorter.debug/u0a231 for next-top-activity` at pid `1234` - the pid-donor line, kept by the text arm.
> 2. `D AudioWaveParticleView: S1277: animators off, painting static backdrop 2400x1080` at pid `24469` - the verbatim line from the strategic spec section 0. This row is the ticket.
> 3. `E MainViewModel: boom` at pid `24469` - an app error under a bare class tag.
> 4. `E AndroidRuntime: FATAL EXCEPTION: main` at pid `24469` - the crash header, which names neither the package nor `FastMediaSorter`.
> 5. `E AndroidRuntime: Process: com.sza.fastmediasorter.debug, PID: 24469` at pid `24469` - the crash line that does name the package.
> 6. `--------- beginning of crash` - a buffer separator with no pid column.
> 7. `I Finsky: unrelated other-app chatter` at pid `9001` - a foreign line that must be dropped.
> 8. `I ActivityManager: ANR in com.sza.fastmediasorter.debug` at pid `1234` - kept by the text arm.
>
> Plain ASCII, one line per row, no trailing blank line beyond the final newline. No real device data, no personal paths.

**Verification:**

- `Glob` - `scripts/devtest/adb-log-filter.tests/fixtures/logcat_threadtime_sample.txt` exists.
- `Grep` - `S1277: animators off, painting static backdrop` matches exactly once in the fixture.
- `Grep` - `FATAL EXCEPTION: main` matches exactly once in the fixture.
- `Grep` - `beginning of crash` matches exactly once in the fixture.
- Value equality - the fixture has exactly 8 lines: `(Get-Content <fixture>).Count`.

**Status:** `[x]` done

---

### Step 02.2 - Write the suite

**Files:** `scripts/devtest/adb-log-filter.tests/Run-Tests.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Write a hermetic runner in the house shape: `Set-StrictMode -Version Latest`, `$ErrorActionPreference = 'Stop'`, resolve the repo root from `$PSScriptRoot`, dot-source `scripts/devtest/lib/adb-log-filter.ps1`, define `Assert-Equal`, count passes and failures, exit 1 when any case fails. No adb call, no network, no writes outside `temp/`.
>
> Load the fixture once. Define `$textPatterns` as the same three entries `adb.ps1` uses, and `$appPids` as `@(24469)`. Cover these cases:
>
> - `Get-AppPidsFromLog` over the fixture returns exactly `@(24469)` - the pid is recovered from the `Start proc` line alone, which is the path used when the process has already exited.
> - `Select-AppLogLines` keeps the `AudioWaveParticleView` probe row. Assert on the row's presence, not only on the count - a count assertion would still pass if the wrong row survived.
> - It keeps the `MainViewModel: boom` row and both `AndroidRuntime` rows.
> - It keeps the `Start proc` row and the `ANR in` row through the text arm, even though their pid is `1234`.
> - It drops the `Finsky` row at pid `9001`.
> - It drops the `--------- beginning of crash` separator. Name this case for what it protects: re-adding the separator would make `prerelease-prepare.ps1` report a crash on any device whose crash buffer is non-empty.
> - Kept-row count over the fixture equals 6.
> - **The defect itself, stated as an assertion.** Reproduce the old rule inline as a one-line text-only `Where-Object` over the fixture, then feed both results to `Measure-FilterCoverage` with `-Pattern 'S1277'`. Assert `suppressed` is 1 for the old rule and 0 for `Select-AppLogLines`. This is the case that goes red if anyone reinstates a text-only pre-filter, and it also proves the runtime self-check would have fired on the original report.
>
> Keep the inline reproduction of the old rule to a single expression, and comment it as the historical rule under test so no reader mistakes it for live code.

**Verification:**

- `Glob` - `scripts/devtest/adb-log-filter.tests/Run-Tests.ps1` exists.
- `Grep` - `adb-log-filter.ps1` matches exactly once in the runner and the line begins with `. ` (dot-source).
- `Grep` - `Set-StrictMode -Version Latest` matches exactly once.
- `Grep` - `Measure-FilterCoverage` matches at least twice (old rule and new rule).
- `Grep` - `AudioWaveParticleView` matches at least once in the runner.
- `Grep` - `beginning of crash` matches at least once in the runner.
- `Grep` - `adb` as a command invocation returns zero hits in the runner (no device dependency).

**Status:** `[x]` done

---

### Step 02.3 - Prove the suite reddens

**Files:** `scripts/devtest/adb-log-filter.tests/Run-Tests.ps1` (unchanged), `scripts/devtest/lib/adb-log-filter.ps1` (temporarily)
**Depends on:** Step 02.2

**Prompt for developer:**

> A green suite proves nothing until it has been seen to fail. Temporarily neuter the pid arm of `Select-AppLogLines` so it matches on `-TextPatterns` alone, run the suite, and record which cases fail and with what exit code. Then restore the file and run it again. Record both `expected: X | actual: Y` pairs (CLAUDE.md section 12).
>
> Restore by re-reading the file, not by memory - and confirm the restored file is byte-identical to the Step 01.1 output before moving on.

**Verification:**

- Value equality - neutered run: `pwsh -NoProfile -File scripts/devtest/adb-log-filter.tests/Run-Tests.ps1` exits 1, and the probe-row case and the `suppressed = 0` case both print `FAIL`.
- Value equality - restored run: the same command exits 0 and prints no `FAIL` line.
- `Grep` - `-TextPatterns` and the pid-column regex are both present in `scripts/devtest/lib/adb-log-filter.ps1` after restore.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/devtest/adb-log-filter.tests/Run-Tests.ps1` exits 0.
- [x] The redden-then-restore evidence from Step 02.3 is recorded in the ticket's `## Last Audit` notes, not only in chat.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The suite is standalone, like the six that precede it: nothing in `a.ps1`, `post-change.ps1` or CI invokes it. That is deliberate and matches the house pattern, and it is why the runtime `WARN` verdict from Step 01.5 - not this suite - is the always-on guard. Phase 03 records the suite in the generated cheatsheet so it is discoverable.

---

## Rollback Plan

Delete `scripts/devtest/adb-log-filter.tests/`. Nothing else depends on it.
