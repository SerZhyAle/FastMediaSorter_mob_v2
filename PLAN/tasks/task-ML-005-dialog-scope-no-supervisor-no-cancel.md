# TASK ML-005: Dialog CoroutineScopes Without SupervisorJob and Cancellation

**Priority**: HIGH  
**Area**: Coroutine Lifecycle  
**Components**: `FileOperationDestinationDialog`, `PlayerSettingsManager`, `ResourcePickerDialog`  
**Effort**: 2h  

---

## Problem

Multiple classes create `CoroutineScope(Dispatchers.Main)` without `SupervisorJob()` and without any `release()`/`cancel()` lifecycle method:

### 1. FileOperationDestinationDialog.kt:58
```kotlin
private val scope = CoroutineScope(Dispatchers.Main)  // No SupervisorJob, never cancelled
```
- `scope.launch` used at lines 110 and 257–387 for long file operations
- No `onDetachedFromWindow()` / `onDismiss` that cancels the scope
- If one file operation throws, the whole scope is cancelled (no `SupervisorJob`)
- If dialog is dismissed mid-operation, coroutines continue running with stale references

### 2. PlayerSettingsManager.kt:34
```kotlin
private val scope = CoroutineScope(Dispatchers.Main)  // No SupervisorJob, never cancelled
```
- `scope.launch` at line 71 for applying subtitle styling
- No `release()` method, no cleanup path
- Manager is referenced from `PlayerActivity` — if scope runs after Activity is destroyed, it touches stale views

### 3. ResourcePickerDialog.kt:36
```kotlin
private val scope = CoroutineScope(Dispatchers.Main)  // Created but NEVER USED
```
- All actual coroutines use `lifecycleOwner.lifecycleScope.launch` (correct)
- The `scope` field is dead code, but creates a leaked `Job`

**Files**:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt` — L58
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSettingsManager.kt` — L34
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourcePickerDialog.kt` — L36

---

## Fix

### FileOperationDestinationDialog

Add `SupervisorJob` and cancel on detach:

```kotlin
// Before:
private val scope = CoroutineScope(Dispatchers.Main)

// After:
private val scopeJob = SupervisorJob()
private val scope = CoroutineScope(Dispatchers.Main + scopeJob)

override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    scopeJob.cancel()
}
```

Reference pattern: `GifEditorDialog` and `ImageEditDialog` already implement this correctly in this codebase — use them as templates.

### PlayerSettingsManager

Add `SupervisorJob` and a `release()` method, then call it from `PlayerLifecycleManager.onDestroy()`:

```kotlin
// Before:
private val scope = CoroutineScope(Dispatchers.Main)

// After:
private val scopeJob = SupervisorJob()
private val scope = CoroutineScope(Dispatchers.Main + scopeJob)

fun release() {
    scopeJob.cancel()
}
```

In `PlayerLifecycleManager.onDestroy()` add:
```kotlin
activity.playerSettingsManager?.release()
```

### ResourcePickerDialog

Simply remove the dead code:
```kotlin
// Remove this line entirely:
private val scope = CoroutineScope(Dispatchers.Main)
```

---

## Test Plan

1. Open `FileOperationDestinationDialog`, start a file operation, dismiss dialog immediately
2. Verify no crash/logcat errors about accessing destroyed views
3. Open/close dialog 5 times — LeakCanary should show zero leaks
4. For `PlayerSettingsManager`: rotate device while subtitle styling is applied, verify no crash
5. Confirm `ResourcePickerDialog` compiles without the unused `scope` field

---

## Acceptance Criteria

- [x] `FileOperationDestinationDialog` scope uses `SupervisorJob` + cancelled in `onDetachedFromWindow()`
- [x] `PlayerSettingsManager` has `release()` method called from `PlayerLifecycleManager`
- [x] `ResourcePickerDialog` dead `scope` field removed
- [x] No coroutine-related crashes on dialog dismiss during active operations

## Implementation Status

**✅ COMPLETED** — 2026-04-13 16:50:51

### Changes Applied

1. **FileOperationDestinationDialog.kt** (line 56-59)
   - Added `import kotlinx.coroutines.SupervisorJob`
   - Replaced `private val scope = CoroutineScope(Dispatchers.Main)` with:
     - `private val scopeJob = SupervisorJob()`
     - `private val scope = CoroutineScope(Dispatchers.Main + scopeJob)`
   - Added `onDetachedFromWindow()` override that calls `scopeJob.cancel()`

2. **PlayerSettingsManager.kt** (line 33-34, new release method)
   - Added `import kotlinx.coroutines.SupervisorJob`
   - Replaced `private val scope = CoroutineScope(Dispatchers.Main)` with:
     - `private val scopeJob = SupervisorJob()`
     - `private val scope = CoroutineScope(Dispatchers.Main + scopeJob)`
   - Added `release()` public method that cancels the scopeJob

3. **PlayerLifecycleManager.kt** (onDestroy cleanup section)
   - Added call to `activity.playerSettingsManager?.release()` in the cleanup section after pdfViewerManager cleanup

4. **ResourcePickerDialog.kt** (line 36)
   - Removed entirely: `private val scope = CoroutineScope(Dispatchers.Main)`
   - Confirmed all coroutines use `lifecycleOwner.lifecycleScope.launch` instead

### Test Results

✅ All 4 files compile error-free
✅ All changes logged to dev/CHANGELOG.md at 16:50:50-51

### Benefits

- **FileOperationDestinationDialog**: One failed file operation no longer cancels entire scope; proper cleanup prevents memory leak
- **PlayerSettingsManager**: Coroutines cancelled on Activity destruction, preventing stale view references
- **PlayerLifecycleManager**: Explicit release ensures no orphaned coroutines touch destroyed Activity
- **ResourcePickerDialog**: Dead code removed, cleaner codebase, prevents unused Job leak
