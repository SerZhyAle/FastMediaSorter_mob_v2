package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.domain.usecase.ticket.DeliverQueuedTicketsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * S4554: delivers the phone's ticket outbox into the Drive inbox once the network is up.
 *
 * A packet filed offline is complete on disk already, so this work only uploads and reads back; while
 * any packet still waits it asks WorkManager to retry with backoff, so a dead network or a lapsed
 * sign-in delays the ticket rather than losing it.
 */
@HiltWorker
class TicketInboxDeliveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deliver: DeliverQueuedTicketsUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val report = deliver()
        return if (report.waiting > 0 && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "ticket_inbox_delivery"
        private const val MAX_ATTEMPTS = 20
        private const val BACKOFF_SECONDS = 60L

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TicketInboxDeliveryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
