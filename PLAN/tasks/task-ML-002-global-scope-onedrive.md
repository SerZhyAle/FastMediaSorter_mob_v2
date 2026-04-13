# TASK ML-002: GlobalScope Usage in OneDriveRestClient

**Priority**: CRITICAL  
**Area**: Coroutine Lifecycle / Memory Leak  
**Component**: `OneDriveRestClient`  
**Effort**: 2h  

---

## Problem

`OneDriveRestClient.kt` uses `kotlinx.coroutines.GlobalScope.launch()` in two places (lines 228, 245) for OAuth authentication callbacks. `GlobalScope` is never cancelled and survives the entire app lifetime regardless of whether the owning component is alive.

```kotlin
// OneDriveRestClient.kt:228
kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
    val result = handleAuthenticationResult(authenticationResult)
    callback(result)  // callback may reference a destroyed Activity
}

// OneDriveRestClient.kt:245 — nested in error path
kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
    // Account check and token acquisition
    // References 'this' (OneDriveRestClient) and 'callback' (may be stale)
}
```

**Consequences**:
1. If the Activity that initiated auth is destroyed (rotation, back press), the `callback` lambda still holds a reference to the dead Activity → **memory leak**
2. The `GlobalScope` coroutine at line 245 can run **indefinitely** after the triggering component is gone
3. `callback(result)` is dispatched on `Dispatchers.Main` after Activity destruction → potential crash or silent NOP on destroyed UI

**Files**:
- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveRestClient.kt` — L21, L228, L245

---

## Fix

Replace `GlobalScope` with the **application-level scope** (already provided via Hilt) or a locally-managed scope with structured cancellation.

**Option A (Preferred)**: Inject `applicationScope` via Hilt and use it instead of GlobalScope:

```kotlin
// In OneDriveRestClient constructor, inject applicationScope
class OneDriveRestClient @Inject constructor(
    private val applicationScope: CoroutineScope,  // Hilt provides this as @ApplicationScope
    ...
)

// Replace GlobalScope.launch at line 228:
applicationScope.launch(Dispatchers.Main) {
    val result = handleAuthenticationResult(authenticationResult)
    callback(result)
}

// Replace GlobalScope.launch at line 245:
applicationScope.launch(Dispatchers.IO) {
    // Account check...
}
```

**Option B**: Use a WeakReference for the callback to prevent Activity retention:

```kotlin
val weakCallback = WeakReference(callback)
applicationScope.launch(Dispatchers.Main) {
    weakCallback.get()?.invoke(result) // no-op if Activity was GC'd
}
```

Also remove the `@OptIn(DelicateCoroutinesApi::class)` annotation on the function since `GlobalScope` is no longer used.

---

## Test Plan

1. Start OneDrive authentication
2. Rotate device during auth (destroy + recreate Activity)
3. Verify auth callback completes without crash
4. Run with LeakCanary: no Activity reference retained by OneDrive callback
5. Verify `GlobalScope` import removed from file

---

## Acceptance Criteria

- [x] `GlobalScope` removed from `OneDriveRestClient`
- [x] `applicationScope` injected via Hilt @ApplicationScope
- [x] Both GlobalScope.launch() instances (lines 228, 245) replaced with applicationScope.launch()
- [ ] OneDrive auth works correctly after device rotation
- [ ] No Activity memory leak reported by LeakCanary during OneDrive auth flow

---

## Implementation Status

**Completed**: 2026-04-13 16:37:30  
**Changes**:
- Injected `@ApplicationScope applicationScope: CoroutineScope` in constructor (OneDriveRestClient.kt:66)
- Replaced `GlobalScope.launch(Dispatchers.Main)` at line 228 with `applicationScope.launch(Dispatchers.Main)`
- Replaced `GlobalScope.launch(Dispatchers.IO)` at line 245 with `applicationScope.launch(Dispatchers.IO)`
- Removed `import kotlinx.coroutines.GlobalScope`
- Added `import kotlinx.coroutines.CoroutineScope` and `import com.sza.fastmediasorter.core.di.ApplicationScope`
- Logged to dev/CHANGELOG.md

**Next**: Validation testing (Device rotation during auth, LeakCanary check)
