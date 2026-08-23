# Phase 03 - Gates Frequency Measurement, Timeouts and Architecture Linting

**Strategic spec:** [`../S1786_ai-agent-development-efficiency-optimization.md`](../S1786_ai-agent-development-efficiency-optimization.md)  
**Tactical index:** [`INDEX.md`](INDEX.md)  
**Status:** ✅ Done
**Depends on:** Phase 02  
**Blocks:** Phase 04  
**Steps done:** 3 / 3 (03.1 via S1794, 03.3 via S1795)
**Started:** 2026-08-17
**Completed:** 2026-08-17

---

## Objective

Add class suffix architecture linting in `assert-source-gates.ps1`, introduce execution timeouts for Gradle-backed quality assertions, and implement automated measurement of gate firing frequency across the 71 assertions.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-source-gates.ps1` | Modified | ≤ 400 |
| `scripts/quality/assert-detekt.ps1` | Modified | ≤ 450 |
| `scripts/quality/assert-settings-doc-sync.ps1` | Modified | ≤ 300 |
| `scripts/quality/measure-gate-frequency.ps1` | New | ≤ 250 |

---

## Steps

### Step 03.1 - Add class suffix architecture linting to assert-source-gates.ps1

**Status:** `[x]` done (Carrier S1794 Verified 2026-08-18)

**Step Log:**

- 2026-08-17 - Added architecture linting, timeouts on gradle gates, implemented measure-gate-frequency.ps1 for 71 gates
- 2026-08-18 - Reopened on re-verification: the rule is wired and prints its row, but cannot fire. Probe - `domain/usecase/S1786NamingProbeHelper.kt` holding `class S1786NamingProbeHelper` gave `class-architecture-naming in src/main: baseline 0 | actual 0 | delta 0` and `assert-source-gates: PASS`, exit 0. The matcher only counts names that already contain `usecase`/`repository`; Rule 6 requires the opposite. Carrier: S1794.
- 2026-08-18 - S1794 Verified: matcher inverted to check every class/interface in `domain/usecase/` and `data/repository/` for the required suffix, roots expanded to all source sets and `wear/`.
**Files:** `scripts/quality/assert-source-gates.ps1`  
**Depends on:** start of phase  

**Prompt for developer:**

> In `scripts/quality/assert-source-gates.ps1`, add a lexical check verifying Rule 6 naming conventions for touched Kotlin files: files under `domain/usecase/` must have class/interface names matching `*UseCase.kt`, and files under `data/repository/` must end in `Repository.kt` or `RepositoryImpl.kt`.

**Why:**

Rule 6 mandates strict architectural suffixes, and catching deviations automatically during fast gates avoids manual code review rework.

**Verification:**

- `Grep` - `domain/usecase` or `UseCase` check in `scripts/quality/assert-source-gates.ps1`.

---

### Step 03.2 - Enforce execution timeouts on Gradle-backed gates

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Added architecture linting, timeouts on gradle gates, implemented measure-gate-frequency.ps1 for 71 gates
**Files:** `scripts/quality/assert-detekt.ps1`, `scripts/quality/assert-settings-doc-sync.ps1`  
**Depends on:** Step 03.1  

**Prompt for developer:**

> Add a hard execution timeout (~600 seconds) for Gradle child process invocations inside `assert-detekt.ps1` and `assert-settings-doc-sync.ps1`. If Gradle hangs or exceeds the bound, terminate the child process and exit with code 2 ("could not verify"), preventing false PASS results from hung processes.

**Why:**

July audit finding B7 revealed cases where a frozen Gradle daemon hung for hours and silently reported PASS.

**Verification:**

- `Grep` - timeout handling present in `assert-detekt.ps1` and `assert-settings-doc-sync.ps1`.

---

### Step 03.3 - Implement measure-gate-frequency.ps1 across 71 assertions

**Status:** `[x]` done (Carrier S1795 Verified 2026-08-18)

**Step Log:**

- 2026-08-17 - Added architecture linting, timeouts on gradle gates, implemented measure-gate-frequency.ps1 for 71 gates
- 2026-08-18 - Reopened on re-verification: the script reads `top_commands` / `top_failing_commands`; the metrics file carries `top_bash` / `top_bash_fail`, so all 71 rows print 0 invocations under a "Telemetry source" header. Per-gate firing counts are not in that corpus at all - gates run inside `post-change.ps1`, not as separate Bash calls. Carrier: S1795.
- 2026-08-18 - S1795 Verified: per-gate outcome logging instrumented in `post-change.ps1` / `assert-fast-gates.ps1`, `measure-gate-frequency.ps1` reads from machine journal.
**Files:** `scripts/quality/measure-gate-frequency.ps1`  
**Depends on:** Step 03.2  

**Prompt for developer:**

> Create `scripts/quality/measure-gate-frequency.ps1` to parse recent transcript and gate invocation logs under `temp/` and `logs/`, counting run frequency, defect findings, and elapsed execution time for each of the 71 `assert-*.ps1` scripts. Output a ranked markdown summary table identifying zero-yield and high-cost gates.

**Why:**

The number of quality assertions grew from ~40 to 71 without re-evaluating which gates have zero hit frequency and should be merged into `assert-source-gates.ps1`.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/measure-gate-frequency.ps1 -Help` exits 0.

---

## Phase Done Criteria

- [x] All 3 steps show `[x] done` (03.1 via S1794, 03.2 direct, 03.3 via S1795).
- [x] `assert-source-gates.ps1` catches invalid class suffixes (S1794 Verified).
- [x] `measure-gate-frequency.ps1` produces a valid report (S1795 Verified).
