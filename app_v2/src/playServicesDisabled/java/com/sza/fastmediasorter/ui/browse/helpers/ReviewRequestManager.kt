package com.sza.fastmediasorter.ui.browse.helpers

import android.app.Activity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0403: the Play-free half of the in-app-review seam.
 *
 * In-App Review is a Play store surface: it opens the app's store page rating widget. A build
 * installed from a FOSS catalogue has no such page, so there is no flow to launch and no threshold
 * worth counting towards one - which is why this copy also drops the usage-recording dependency
 * rather than accumulating counters nothing reads. Same FQCN and same contract as the enabled copy,
 * so no call site branches on the flavor (Rule 14).
 */
@Singleton
class ReviewRequestManager @Inject constructor() {

    /** No store page to rate in this distribution channel. */
    @Suppress("UnusedParameter")
    fun onSortOperationSuccess(activity: Activity, count: Int) = Unit

    /** Debug-only trigger of the flow above, which does not exist here. */
    @Suppress("UnusedParameter")
    fun forceReviewDebug(activity: Activity) = Unit
}
