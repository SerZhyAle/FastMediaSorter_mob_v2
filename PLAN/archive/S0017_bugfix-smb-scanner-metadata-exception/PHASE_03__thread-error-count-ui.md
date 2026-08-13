# Phase 03 — Thread Error Count to BrowseViewModel

**Status:** [x]

## Steps

### 3.1 BrowseLoadingManager — implement onMetadataErrors in callback

File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/loading/BrowseLoadingManager.kt`

**3.1.1** Add new method to `LoadingCallbacks` interface:
```kotlin
suspend fun onScanMetadataErrors(count: Int) = Unit
```

**3.1.2** In the `progressCallback` anonymous object (the `object : ScanProgressCallback { ... }` created inside `loadFilesStandard()`), override `onMetadataErrors`:
```kotlin
override suspend fun onMetadataErrors(errorCount: Int) {
    if (errorCount > 0) callbacks.onScanMetadataErrors(errorCount)
}
```

**Verification:** `grep -n "onMetadataErrors\|onScanMetadataErrors" BrowseLoadingManager.kt` → 2 occurrences.

### 3.2 BrowseEvent — add ShowMetadataWarning

File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseEvent.kt`

Add to `sealed class BrowseEvent`:
```kotlin
data class ShowMetadataWarning(val errorCount: Int) : BrowseEvent()
```

**Verification:** `grep -n "ShowMetadataWarning" BrowseEvent.kt` → 1 line.

### 3.3 BrowseResourceLoadManager — implement onScanMetadataErrors

File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt`

The `LoadingCallbacks` anonymous object is created inside `BrowseResourceLoadManager.loadMediaFilesStandard()` (not in `BrowseViewModel`). Add the override:
```kotlin
override suspend fun onScanMetadataErrors(count: Int) {
    sendEvent(BrowseEvent.ShowMetadataWarning(count))
}
```

`sendEvent` is a constructor parameter of `BrowseResourceLoadManager` — no extra wiring needed.

**Verification:** `grep -n "ShowMetadataWarning\|onScanMetadataErrors" BrowseResourceLoadManager.kt` → 2 lines.

### 3.4 BrowseEventHandler — handle ShowMetadataWarning

File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt`

Find the `when (event)` block. Add a branch for `ShowMetadataWarning`:
```kotlin
is BrowseEvent.ShowMetadataWarning -> {
    val msg = context.getString(R.string.smb_metadata_errors_warning, event.errorCount)
    Snackbar.make(rootView, msg, Snackbar.LENGTH_LONG).show()
}
```

Use the same `rootView` / `view` reference already used by the existing `ShowMessage` or `ShowError` branch. Check and match the exact parameter name in the handler.

**Verification:** `grep -n "ShowMetadataWarning" BrowseEventHandler.kt` → 1 line.
