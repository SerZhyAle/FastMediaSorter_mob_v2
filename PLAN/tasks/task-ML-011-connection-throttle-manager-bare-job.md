# TASK ML-011: ConnectionThrottleManager Uses Bare Job Instead of SupervisorJob

**Priority**: MEDIUM  
**Area**: Coroutine Stability  
**Component**: `ConnectionThrottleManager`  
**Effort**: 30min  

---

## Problem

`ConnectionThrottleManager.kt:70` creates a `CoroutineScope` with a bare `Job()` instead of `SupervisorJob()`:

```kotlin
// ConnectionThrottleManager.kt:70
private val managerScope = CoroutineScope(Dispatchers.Default + Job())  // ← Bare Job!

// Used for:
private var videoPlayerResumeJob: Job? = null

fun deactivateVideoPlayerMode(resourceKey: String) {
    videoPlayerResumeJob?.cancel()
    videoPlayerResumeJob = managerScope.launch {  // Child of managerScope
        delay(300)
        // Resume thumbnails, notify callbacks...
    }
}
```

**Problem with bare `Job()`**: In a `CoroutineScope(Job())`, if any child coroutine throws an **uncaught exception**, the exception propagates to the parent `Job` which **cancels all other sibling coroutines** and then cancels itself. The entire `managerScope` becomes permanently cancelled.

This is a singleton manager (`object ConnectionThrottleManager`) — once its scope is cancelled, **all future `deactivateVideoPlayerMode()` calls will silently fail** (the scope is dead and `launch` on a cancelled scope throws `CancellationException`).

With `SupervisorJob()`, an exception in one child coroutine is isolated — siblings continue running.

**File**:
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt` — L70

---

## Fix

One-line change:

```kotlin
// Before:
private val managerScope = CoroutineScope(Dispatchers.Default + Job())

// After:
private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
```

Also add the missing import if not already present:
```kotlin
import kotlinx.coroutines.SupervisorJob
```

---

## Why This Is Low Effort but Important

This is a **single-line fix** that prevents a subtle, hard-to-reproduce bug: if `deactivateVideoPlayerMode()` ever throws (e.g., the callback list is modified during iteration), the entire throttle manager goes silent — no further thumbnail resumes, no further slot releases. This could manifest as "thumbnails stopped loading" with no obvious error in logs.

---

## Test Plan

1. In debug build, inject a test exception inside `videoPlayerResumeJob = managerScope.launch { throw RuntimeException("test") }`
2. With bare `Job()`: verify second `deactivateVideoPlayerMode()` call silently does nothing
3. With `SupervisorJob()`: verify second call works correctly after first throws
4. No change in normal (non-exception) behavior

---

## Acceptance Criteria

- [ ] `managerScope` uses `SupervisorJob()` 
- [ ] Exception in one resume job does not cancel other pending resume jobs
- [ ] No change in normal operation behavior
