# S0152 — bugfix: moveInProgress not reset after batch-delete permission flow

## Status
Draft → Approved → In Progress → Implemented

## Problem

`FileOperationsHandler.performMove()` and `performMoveToPath()` set `moveInProgress = true`
before launching the coroutine, but only reset it in two places:

- `onMoveSuccess` / `onMoveToPathSuccess` callbacks (called from the success branch)
- the `catch (e: Exception)` block

The `FileOperationResult.PermissionRequired` branch is missing the reset:

```kotlin
// FileOperationsHandler.kt, performMove(), ~line 268
is FileOperationResult.PermissionRequired -> {
    callback.onBatchDeletePermissionRequired(result.pendingIntent, currentFile.path)
    // moveInProgress stays true — BUG
}
```

Same in `performMoveToPath()` (~line 375):
```kotlin
is FileOperationResult.PermissionRequired ->
    callback.onBatchDeletePermissionRequired(result.pendingIntent, currentFile.path)
```

`Failure` and `AuthenticationRequired` branches also skip the reset — though those paths
show an error toast that does not block the user from retrying, the flag still leaks.

**User-visible effect**: first move succeeds (the local→SMB move triggers a batch-delete
permission dialog). After the user grants permission, the batch-delete completes, but
`moveInProgress` is never cleared for that `Activity` instance. All subsequent move button
presses are silently dropped by the `if (moveInProgress) return` guard at line 199.
Reproduced in logs: first file moves cleanly, second and subsequent presses are ignored.

## Root Cause

`PlayerLifecycleManager.onBatchDeletePermissionGranted()` (which runs after the system
permission dialog returns) calls `lifecycleManager.storePendingBatchDeleteFilePath(null)`
and tracks the modified file, but does NOT call
`activity.fileOperationsHandler.resetMoveInProgress()`.

`FileOperationsHandler` itself also never resets the flag on `PermissionRequired` —
it relies on the callback path to do so, but `onBatchDeletePermissionRequired` in
`PlayerManagerInitializer` (line 345–363) only launches the permission launcher and stores
the path; it does not reset the flag either.

## Affected Files

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/FileOperationsHandler.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`

## Fix

### Option A — reset eagerly in FileOperationsHandler (preferred)

Add `moveInProgress = false` before every non-success, non-exception result branch in both
`performMove()` and `performMoveToPath()`:

```kotlin
// performMove — PermissionRequired
is FileOperationResult.PermissionRequired -> {
    moveInProgress = false                              // ← add
    callback.onBatchDeletePermissionRequired(result.pendingIntent, currentFile.path)
}
// performMove — Failure
is FileOperationResult.Failure -> {
    moveInProgress = false                              // ← add
    val message = formatFailureMessage(...)
    callback.onOperationError(message, null)
}
// performMove — AuthenticationRequired
is FileOperationResult.AuthenticationRequired -> {
    moveInProgress = false                              // ← add
    callback.onAuthenticationRequired(result.provider, result.message)
}
```

Apply the same three additions to `performMoveToPath()`.

### Option B — reset in PlayerLifecycleManager.onBatchDeletePermissionGranted (alternative)

Call `activity.fileOperationsHandler.resetMoveInProgress()` inside
`onBatchDeletePermissionGranted()`. This is narrower (only fixes the PermissionRequired
path) and creates cross-component coupling between the lifecycle manager and the operations
handler. Prefer Option A.

## Implementation Notes

- `resetMoveInProgress()` already exists at line 73 — no new public API needed.
- The `catch` block already resets on exception — no change there.
- After fix, `moveInProgress` resets on: Success (via `onMoveSuccess`), PermissionRequired
  (eagerly in-handler), Failure (eagerly), AuthenticationRequired (eagerly), Exception
  (catch block).
- No UI changes required.

## Verification

- Move file 1 → local→SMB → permission dialog appears → grant → file deleted.
- Move file 2 immediately after → move must not be silently blocked.
- Move file N (N ≥ 2) must all work identically to file 1.
- Check `moveInProgress` is false after each of the four non-exception result branches
  in both `performMove` and `performMoveToPath`.

## Last Audit

**Date:** 2026-05-11 | **Result:** Verified ✅

### What was checked

- `performMove()` — `PermissionRequired`, `Failure`, `AuthenticationRequired` branches: `moveInProgress = false` present before callback ✅
- `performMoveToPath()` — same three branches: `moveInProgress = false` present before callback ✅
- `catch` blocks in both functions: already reset `moveInProgress` (unchanged) ✅
- `Success` / `PartialSuccess` paths: delegate to `onMoveSuccess` / `onMoveToPathSuccess` which reset the flag ✅
- `resetMoveInProgress()` at line 73: public API unchanged, no new surface added ✅
- No stale `Timber.d("S0152:` tags remaining ✅
- Build `assembleStandardDebug`: **PASS** (32 s)

### Implementation match

Option A applied as specified. All five `FileOperationResult` branches in both functions now correctly clear `moveInProgress` before returning to the UI.

### Informational follow-up (out of scope)

In `performMove()`, the `checkSmbDestinationReachability` early-exit path (before `fileOperationUseCase.execute()`) calls `callback.onOperationError(…)` then does `return@launch` without resetting `moveInProgress`. This is a separate adjacent bug not covered by S0152 and does not invalidate the fix.
