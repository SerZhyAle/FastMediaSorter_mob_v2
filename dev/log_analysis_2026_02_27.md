# Log Analysis (2026-02-27)

Analyzed the provided log file `temp/current.log` (first 800 lines). The following issues and potential improvements were identified:

### 1. Missing strategy for `cloud://` during `temp` files cleanup
**Log snippet:**
```text
BrowseViewModel.loadMediaFiles: Cleanup failed (non-critical, continuing) (Fix with AI)
java.lang.IllegalArgumentException: No strategy supports path: cloud://google_drive/1xfuTfRebicfPoWelNuN1-UCFyIgAGt7n
```
**Cause:** `CleanupOrphanedTempFilesUseCase` calls `BaseFileOperationHandler.listFiles` for a cloud path, but a suitable strategy for the `cloud://` scheme is not found (it reports missing strategy while using `SmbFileOperationHandler`).
**Solution:**
- Ignore cloud paths in `CleanupOrphanedTempFilesUseCase` if temporary files are not created there:
```kotlin
if (path.startsWith("cloud://")) return
```
- Alternatively, implement the corresponding `CloudFileOperationStrategy`.

### 2. Slow Scanning (Slow Scan)
**Log snippet:**
```text
ScanMetrics: SLOW SCAN detected — 24454ms (threshold: 6000ms) resourceId=12 type=CLOUD
ScanMetrics: scan_complete resourceId=12 type=CLOUD duration_ms=24454 file_count=831
```
**Cause:** Scanning a Google Drive folder (831 files) took ~24.5 seconds.
**Solution:** Implement incremental scanning and caching for cloud resources (this aligns with the "Scan Optimization Planning" task mentioned in previous sessions). It is highly recommended to use pagination (fetch page by page) from the Google Drive API instead of loading the entire list at once.

### 3. Main Thread Blocking (Skipped frames / Choreographer)
**Log snippet:**
```text
Choreographer: Skipped 78 frames! The application may be doing too much work on its main thread. (At MainActivity startup)
Choreographer: Skipped 61 frames! (Transitioning to SettingsActivity)
Choreographer: Skipped 62 frames! (Transitioning to AddResourceActivity)
```
**Cause:** Heavy operations are being performed on the `Main` thread during Activity lifecycle events (`onCreate` / `onResume`).
**Solution:**
- Profile the `onCreate` methods and `ViewModel` initialization in these Activities.
- Check the repeated calls to `SettingsRepo.getSettings()`:
```text
SettingsRepo: getSettings() returning allFiles=false from DataStore
```
The logs show that `SettingsRepo` is queried very frequently within short time frames. If this is a synchronous read from `DataStore` (e.g., using `runBlocking`), it might be blocking the thread. If it's a log from a `Flow` collector, ensure that heavy UI redrawing logic is not executed on every emission unnecessarily.

### 4. Reflection Error `rebase()` (API 28)
**Log snippet:**
```text
Failed to retrieve rebase() method (Fix with AI)
java.lang.NoSuchMethodException: rebase []
at androidx.core.content.res.ResourcesCompat$ThemeCompat$Api23Impl.rebase(ResourcesCompat.java:774)
```
**Cause:** On API 28 (specifically on this emulator), `AppCompatDelegate` attempts to call the hidden `rebase()` method for the theme via reflection. This is a known behavior of AndroidX libraries (`ResourcesCompat` or `AppCompat`) and can be safely ignored.
**Solution:** If custom theme manipulation within `BaseActivity.attachBaseContext()` is not being explicitly used, this platform warning can be safely ignored.

### Summary of Action Items
1. Add an early return for `cloud://` resources in the orphaned temp files cleanup function. Throwing an exception and unwinding the stack is expensive.
2. Review the implementation of `SettingsRepo.getSettings()` to minimize synchronous accesses or excessive `Flow` triggers on the `Main` thread.
3. Proceed with the Scan Optimization implementation for cloud resources.
