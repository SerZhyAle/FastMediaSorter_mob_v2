# Phase 05 - Memory and Storage benchmarks

**Strategic spec:** [`../S0337_system-info-extended-fields.md`](../S0337_system-info-extended-fields.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Add a Benchmarks section with an approximate memory-throughput benchmark and an approximate internal-storage read/write benchmark, both budget-limited and run on the existing off-main-thread gather path.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/SystemInfoBenchmark.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/systeminfo/SystemInfoBenchmarkTest.kt` | New | ≤ 100 |

> If the use case projects >500 lines after edit, create a timestamped backup in `temp/` first.

---

## Steps

### Step 05.1 - Add benchmark runner

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/SystemInfoBenchmark.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `SystemInfoBenchmark` with two functions, each blocking (called from the IO gather, never the main thread) and budget-limited: (1) `measureMemoryThroughputMbps(): Double` - allocate a small fixed buffer (e.g. a few MB), run sequential read/write/copy passes under a hard time cap (conservative default, e.g. ≤ 200 ms), compute MB/s. (2) `measureStorageThroughputMbps(dir: File): Pair<Double, Double>` - write a small temp file (conservative default size) into the given private dir with forced flush/sync, then read it back, measure write and read MB/s, and DELETE the temp file in a `finally`. Both must be defensive (return a sentinel like `-1.0` on failure). Add a `// WHY` comment noting budgets are conservative defaults to be tuned on device (strategic §6.7). No `Log.d`.

**Verification:**

- `Glob` - `SystemInfoBenchmark.kt` exists.
- `Grep` - `fun measureMemoryThroughputMbps` and `fun measureStorageThroughputMbps` present.
- `Grep` - `finally` present (temp-file cleanup).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS (mem fn=1, storage fn=1, finally=2, Log.d=0). Conservative budget defaults (4MB/200ms mem, 8MB storage); WHY comment notes §6.7 tuning.

---

### Step 05.2 - Wire Benchmarks section into the use case

**Files:** `GatherSystemInfoUseCase.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a Benchmarks section that runs `SystemInfoBenchmark` (memory + storage into `context.cacheDir`) and formats results as human-readable MB/s with an "approximate" marker; on the failure sentinel show `unknown`. This runs inside the existing IO-dispatched `invoke()` so it executes off the main thread automatically when opened. Defensive wrappers around each measurement.

**Verification:**

- `Grep` - `measureMemoryThroughputMbps` and `measureStorageThroughputMbps` referenced in the use case.
- `Grep` - `sysinfo_section_benchmark` referenced.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS (mem ref=1, storage ref=1, section_benchmark=1, Log.d=0). Runs inside IO-dispatched invoke(); storage uses context.cacheDir. Compile gate at phase end.

---

### Step 05.3 - Benchmark unit test + localized strings (EN/RU/UK)

**Files:** `SystemInfoBenchmarkTest.kt`, `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add a unit test asserting `measureMemoryThroughputMbps()` returns a positive number within the time budget, and that `measureStorageThroughputMbps(tempDir)` returns non-negative values AND deletes the temp file (assert the dir has no leftover benchmark file after the call). Add localized strings: `sysinfo_section_benchmark` header, field labels (memory throughput, storage write, storage read) and an "approximate" qualifier string. Real EN/RU/UK values; Author Style; §6 tone checklist.

**Verification:**

- `Glob` - `SystemInfoBenchmarkTest.kt` exists.
- `Grep` - `sysinfo_section_benchmark` present in each of the three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "sysinfo_"` exits 0 (expected 0 | actual record).
- Affected unit test passes - run `testStandardDebugUnitTest --tests "*SystemInfoBenchmarkTest"`; XML failures=0 (expected 0 | actual record).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. Test XML tests=2 failures=0 errors=0 (expected 0 | actual 0); 5 benchmark keys EN/RU/UK present (full sysinfo_ audit EXIT=0). Fix: benchmark switched from android SystemClock to System.nanoTime() so Robolectric's frozen clock no longer yields a 0-duration FAILED sentinel. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public type) - deferred to Phase 06 batch.

---

## Handoff Notes to Next Phase

Both benchmarks run within the off-main-thread gather; budgets are conservative defaults (strategic §6.7) to be tuned on device.

---

## Rollback Plan

Revert phase commit(s) - benchmark runner is isolated; temp files are deleted in `finally`, no residue.
