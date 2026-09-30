package com.sza.fastmediasorter.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3764: the non-Wear flavors' inert [PhoneBatteryReportSender] - the phone battery bar is a
 * companion feature, and a flavor with no watch pair has nothing to publish to. The collector this
 * returns is completed at once, so the app's startup coroutine drops it without holding anything.
 */
@Singleton
class NoOpPhoneBatteryReportSender @Inject constructor() : PhoneBatteryReportSender {

    override fun observeAndPush(scope: CoroutineScope): Job = Job().apply { complete() }
}
