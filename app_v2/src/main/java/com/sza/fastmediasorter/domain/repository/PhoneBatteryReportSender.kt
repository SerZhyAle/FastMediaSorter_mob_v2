package com.sza.fastmediasorter.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * S3764: how this phone publishes its battery charge to the watch face.
 *
 * An interface rather than the type FastMediaSorterApp injects by concrete name for the S3220
 * seam's reason: the Application lives in `src/main`, which cannot reference a `wearGms` type, so
 * the capability is declared here and bound per source set - the GMS sender under `wearGms`, an
 * inert no-op under `wearStub` (the WatchCameraSessionAnnouncer pattern).
 */
interface PhoneBatteryReportSender {
    fun observeAndPush(scope: CoroutineScope): Job
}
