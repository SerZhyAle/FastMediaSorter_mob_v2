# Phase 04 — Review Integration

**Strategic spec:** [`../S0135_play-store-listing-optimization.md`](../S0135_play-store-listing-optimization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Create `ReviewRequestManager` (UI helper) and wire it into `BrowseFileOperationsManager` via the existing `FileOperationCallbacks` interface. `BrowseActivity` provides the `Activity` reference at callback time — no Activity stored in any manager or use case.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] File `BrowseActivity.kt` (508 LOC) → backup to `temp/BrowseActivity_backup_<timestamp>.kt` before edit.
- [ ] File `BrowseFileOperationsManager.kt` (350 LOC) — under 500, no backup required.
- [ ] File `BrowseManagerInitializer.kt` (706 LOC) → backup to `temp/BrowseManagerInitializer_backup_<timestamp>.kt` before edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/ReviewRequestManager.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | 350 + ~15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | 508 + ~15 |

No changes to `BrowseManagerInitializer.kt` — the callback is defined in `BrowseActivity` and already passed through the initializer without new constructor parameters.

---

## Steps

### Step 04.1 — Create ReviewRequestManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/ReviewRequestManager.kt`
**Depends on:** Phase 03 complete

**Prompt for developer:**

> Create `ReviewRequestManager.kt` in `ui/browse/helpers/`. This is a `@Singleton` UI helper.
> It holds the Google Play `ReviewManager`, gates the session-counter call to once per process
> lifetime, and launches the native review flow. It never stores an `Activity` reference.
> On any failure (Play Services unavailable, quota, etc.) it is silent — no user-visible message.
>
> `BuildConfig.DEBUG && forceReview` skips threshold; used for manual QA.
>
> Implement as shown:

```kotlin
package com.sza.fastmediasorter.ui.browse.helpers

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.usecase.RecordSortSuccessUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Google Play In-App Review request lifecycle (S0135).
 *
 * - One session increment per process lifetime (guarded by [sessionRecorded]).
 * - Fail-silent: any Play Services error is logged via Timber, never surfaced to the user.
 * - Activity reference is never stored; it is received per invocation.
 */
@Singleton
class ReviewRequestManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordSortSuccessUseCase: RecordSortSuccessUseCase
) {

    private val reviewManager = ReviewManagerFactory.create(context)
    private val sessionRecorded = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Called after a successful Move or Copy operation in the Browse screen.
     *
     * @param activity  The currently foregrounded Activity (required for launchReviewFlow).
     * @param count     Number of files processed in this operation.
     */
    fun onSortOperationSuccess(activity: Activity, count: Int) {
        scope.launch {
            try {
                if (sessionRecorded.compareAndSet(false, true)) {
                    recordSortSuccessUseCase.recordSession()
                }
                val eligible = recordSortSuccessUseCase.record(count)
                if (eligible) {
                    Timber.d("ReviewRequestManager: eligible — requesting review flow")
                    withContext(Dispatchers.Main) { launchReviewFlow(activity) }
                }
            } catch (e: Exception) {
                Timber.d("ReviewRequestManager: error in record — ${e.message}")
            }
        }
    }

    /**
     * Force-triggers the review flow immediately, bypassing thresholds.
     * Debug builds only. Use to verify Play integration without accumulating ops.
     */
    fun forceReviewDebug(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        Timber.d("ReviewRequestManager: forceReviewDebug called")
        launchReviewFlow(activity)
    }

    private fun launchReviewFlow(activity: Activity) {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Timber.d("ReviewRequestManager: reviewInfo obtained — launching flow")
                reviewManager.launchReviewFlow(activity, task.result)
                    .addOnCompleteListener {
                        Timber.d("ReviewRequestManager: launchReviewFlow complete (platform-controlled outcome)")
                    }
                // Mark shown regardless of actual platform decision — quota is enforced by platform
                scope.launch {
                    try { recordSortSuccessUseCase.store.markReviewShown() }
                    catch (e: Exception) { Timber.d("ReviewRequestManager: markReviewShown error — ${e.message}") }
                }
            } else {
                Timber.d("ReviewRequestManager: requestReviewFlow failed — ${task.exception?.message}")
            }
        }
    }
}
```

> Note: `recordSortSuccessUseCase.store` is a direct DataStore access from the manager. To avoid
> exposing the store reference, add a `suspend fun markReviewShown()` delegator to
> `RecordSortSuccessUseCase` instead, and call that. The delegator body is one line:
> `suspend fun markReviewShown() = store.markReviewShown()`.
> Add it to `RecordSortSuccessUseCase` as part of this step.

**Verification:**

- `Glob` — `ui/browse/helpers/ReviewRequestManager.kt` exists.
- `Grep` — `class ReviewRequestManager` matches exactly once.
- `Grep` — `@Singleton` present in the file.
- `Grep` — `fun onSortOperationSuccess` present.
- `Grep` — `fun forceReviewDebug` present.
- `Grep` — `Log\.d\(` returns zero hits in the new file.

**Status:** `[x] done (auto-build PASS)`

---

### Step 04.2 — Add onSortOperationSuccess callback and call sites to BrowseFileOperationsManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `BrowseFileOperationsManager.kt`, add one method to the `FileOperationCallbacks` interface and
> call it from the two Move/Copy success paths. Do not add `ReviewRequestManager` as a constructor
> parameter — the callback carries the trigger to `BrowseActivity`, which holds the manager.
>
> 1. In `interface FileOperationCallbacks`, add:
>    ```kotlin
>    /** Called after a successful Move or Copy operation. [count] = number of processed files. */
>    fun onSortOperationSuccess(count: Int) {}
>    ```
>    (default no-op body so existing anonymous implementations in other call sites are unaffected)
>
> 2. In `executeMoveDirectly()`, in the `is FileOperationResult.Success ->` branch, after the
>    existing Toast call, add:
>    ```kotlin
>    callbacks.onSortOperationSuccess(result.processedCount)
>    ```
>
> 3. In `executeOperationToPath()`, in the `is FileOperationResult.Success ->` branch (for
>    `FileOperationType.MOVE` and `COPY`), after the existing Toast call, add:
>    ```kotlin
>    callbacks.onSortOperationSuccess(result.processedCount)
>    ```
>    (this branch already handles both MOVE and COPY — one call covers both)
>
> Do not add the callback to Rename, Delete, or PartialSuccess paths.

**Verification:**

- `Grep` — `fun onSortOperationSuccess` present in `BrowseFileOperationsManager.kt`.
- `Grep` — `callbacks.onSortOperationSuccess` has exactly 2 hits (executeMoveDirectly + executeOperationToPath).
- `Grep` — `Log\.d\(` returns zero hits in touched lines.

**Status:** `[x] done (auto-build PASS)`

---

### Step 04.3 — Wire ReviewRequestManager into BrowseActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> `BrowseActivity` is Hilt-injected — add `ReviewRequestManager` as an `@Inject lateinit var` field.
> Then override `onSortOperationSuccess` in the anonymous `FileOperationCallbacks` object inside
> `BrowseManagerInitializer(...)` call in `setupViews()`.
>
> 1. Add the Hilt injection field near the other injected fields at the top of `BrowseActivity`:
>    ```kotlin
>    @Inject lateinit var reviewRequestManager: ReviewRequestManager
>    ```
>
> 2. In the `BrowseManagerInitializer(...)` call inside `setupViews()`, the anonymous
>    `FileOperationCallbacks` object is passed as the `callbacks` parameter. Add the override:
>    ```kotlin
>    override fun onSortOperationSuccess(count: Int) {
>        reviewRequestManager.onSortOperationSuccess(this@BrowseActivity, count)
>    }
>    ```
>    Place it after the existing `override fun onOperationCompleted()` for readability.
>
> 3. Add the import for `ReviewRequestManager` at the top of the file.
>
> File is 508 LOC — create timestamped backup in `temp/` before editing.

**Verification:**

- `Grep` — `lateinit var reviewRequestManager: ReviewRequestManager` present in `BrowseActivity.kt`.
- `Grep` — `reviewRequestManager.onSortOperationSuccess` present in `BrowseActivity.kt`.
- `Grep` — `@Inject` count in `BrowseActivity.kt` is incremented by exactly 1 vs. pre-edit state.
- `Grep` — `Log\.d\(` returns zero hits in touched lines.
- `/build` — project compiles without error.

**Status:** `[x] done (auto-build PASS)`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] On a debug build: calling `reviewRequestManager.forceReviewDebug(activity)` from a debug menu or via ADB intent shows the native Play dialog (or a test dialog if Play Services is in test mode).

---

## Handoff Notes to Next Phase

In-App Review is wired end-to-end. The review dialog will trigger in release builds after 20 cumulative Move/Copy operations across ≥3 sessions, with a 90-day cooldown. Debug builds expose `forceReviewDebug()` for QA verification without accumulating operations.

---

## Rollback Plan

Revert phase commits. `BrowseActivity.kt` and `BrowseManagerInitializer.kt` backups are in `temp/`. `ReviewEligibilityDataStore` keys are additive and survive a revert with no data loss.
