# Phase 2 — MemoryEnduranceTracker Implementation

**Status:** [x] Done

## Goal

Implement a debug-only singleton `MemoryEnduranceTracker` in `core/debug/`. Zero overhead in release builds — gated by `BuildConfig.DEBUG`. No new Hilt module required (singleton object).

## Target file

`app_v2/src/main/java/com/sza/fastmediasorter/core/debug/MemoryEnduranceTracker.kt`

## Implementation spec

### API surface

```kotlin
object MemoryEnduranceTracker {
    // Start a named scenario. Resets internal state for this scenario.
    fun startScenario(scenarioId: String)

    // Record a named checkpoint within the active scenario.
    fun checkpoint(label: String)

    // End the scenario and emit final summary to Logcat.
    fun endScenario()

    // Schedule a cooldown-end checkpoint (call 30 s after endScenario).
    fun cooldownCheckpoint()
}
```

All methods are no-ops when `!BuildConfig.DEBUG`.

### Checkpoint data captured per call

- Java heap used MB: `(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576`
- Java heap max MB: `Runtime.getRuntime().maxMemory() / 1_048_576`
- Native heap allocated MB: `android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576`
- Transition counter (incremented on each `checkpoint()` call after BASELINE).
- Timestamp ms: `SystemClock.elapsedRealtime()`

### Log format

Each checkpoint logs a single Timber.d line in a machine-parseable format for `/log-reader` analysis:

```
MEM_ENDURANCE | scenario=<id> | checkpoint=<label> | transitions=<n> | heapUsed=<x>MB | heapMax=<y>MB | nativeAlloc=<z>MB | elapsedMs=<t>
```

### Cycle delta analysis

On each CYCLE_END checkpoint, compute delta vs previous CYCLE_END:

```
delta = (heapUsed_current - heapUsed_previous) / heapUsed_previous * 100
```

Log classification:
```
MEM_ENDURANCE | scenario=<id> | cycle_delta=<delta>% | classification=<PLATEAU|SUSPICIOUS|FAIL>
```

Rules:
- PLATEAU: abs(delta) < 15
- SUSPICIOUS: delta ≥ 15 && delta ≤ 40
- FAIL: delta > 40 OR monotonic_count ≥ 5 (tracked internally)

### Summary on endScenario()

```
MEM_ENDURANCE | SUMMARY | scenario=<id> | total_transitions=<n> | baseline=<x>MB | peak=<p>MB | final=<f>MB | verdict=<PLATEAU|SUSPICIOUS|FAIL>
```

## Line budget

Target: < 200 LOC. No business logic, no coroutines, no DI injection.

## Verification predicates

- [ ] `MemoryEnduranceTracker.kt` exists at target path.
- [ ] All methods are no-ops when `!BuildConfig.DEBUG` (checked by simple `if (!BuildConfig.DEBUG) return` guard at each entry).
- [ ] Logcat emits `MEM_ENDURANCE |` lines during a real scenario run.
- [ ] `SUMMARY` line emits `verdict=PLATEAU|SUSPICIOUS|FAIL`.
- [ ] No `Log.d` calls — only `Timber.d`.
- [ ] File LOC ≤ 200.
