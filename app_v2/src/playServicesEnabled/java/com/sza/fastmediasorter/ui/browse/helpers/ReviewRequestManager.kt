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
                    Timber.d("ReviewRequestManager: eligible - requesting review flow")
                    withContext(Dispatchers.Main) { launchReviewFlow(activity) }
                }
            } catch (e: Exception) {
                Timber.d("ReviewRequestManager: error in record - ${e.message}")
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
                Timber.d("ReviewRequestManager: reviewInfo obtained - launching flow")
                reviewManager.launchReviewFlow(activity, task.result)
                    .addOnCompleteListener {
                        Timber.d("ReviewRequestManager: launchReviewFlow complete (platform-controlled outcome)")
                    }
                // Mark shown regardless of actual platform decision - quota is enforced by platform
                scope.launch {
                    try {
                        recordSortSuccessUseCase.markReviewShown()
                    } catch (e: Exception) {
                        Timber.d("ReviewRequestManager: markReviewShown error - ${e.message}")
                    }
                }
            } else {
                Timber.d("ReviewRequestManager: requestReviewFlow failed - ${task.exception?.message}")
            }
        }
    }
}
