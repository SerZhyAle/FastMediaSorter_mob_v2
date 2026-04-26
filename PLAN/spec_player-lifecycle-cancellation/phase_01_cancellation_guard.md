# Phase 1: Cancellation Guard in playVideo Catch

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Lines:** 616-620 (the generic `catch (e: Exception)` block in `playVideo()`)

## Steps

1. Add `import kotlinx.coroutines.CancellationException` at top of `VideoPlayerManager.kt` (if not already present).

2. Replace the generic catch block:

   ```kotlin
   // Before:
   } catch (e: Exception) {
       Timber.e(e, "VideoPlayerManager: Failed to play video")
       playerCallback.onBuffering(false)
       playerCallback.showError("Failed to play video: ${e.message}")
   }

   // After:
   } catch (e: CancellationException) {
       Timber.d("VideoPlayerManager: playVideo cancelled (lifecycle/scope cancel) — path=%s", path)
       playerCallback.onBuffering(false)
       throw e
   } catch (e: Exception) {
       Timber.e(e, "VideoPlayerManager: Failed to play video")
       playerCallback.onBuffering(false)
       playerCallback.showError("Failed to play video: ${e.message}")
   }
   ```

   **Why re-throw `CancellationException`:** coroutine structured concurrency requires cancellation propagation; swallowing it would leave the parent `Job` in an inconsistent state.

## Verification

- [ ] `CancellationException` catch placed BEFORE the generic `Exception` catch.
- [ ] `throw e` present in `CancellationException` branch.
- [ ] `playerCallback.showError` NOT called in `CancellationException` branch.
- [ ] `playerCallback.onBuffering(false)` called in BOTH branches (clears spinner).
- [ ] Log level in cancellation branch: `Timber.d`, not `Timber.e` or `Timber.w`.
- [ ] `import kotlinx.coroutines.CancellationException` present (not already covered by `*` import).
- [ ] No new classes, no DI changes, no Room changes.
- [ ] File stays under 1000 LOC after edit.
