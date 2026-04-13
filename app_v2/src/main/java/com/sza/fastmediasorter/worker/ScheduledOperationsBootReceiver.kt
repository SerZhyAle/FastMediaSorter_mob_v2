package com.sza.fastmediasorter.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ScheduledOperationsBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var workManagerScheduler: WorkManagerScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Timber.i("ScheduledOperationsBootReceiver: BOOT_COMPLETED — rescheduling all operations")
        
        // Use goAsync() to keep broadcast alive until rescheduleAll completes (ML-009)
        val pendingResult = goAsync()
        CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            try {
                workManagerScheduler.rescheduleAll()
                Timber.i("ScheduledOperationsBootReceiver: rescheduleAll completed")
            } catch (e: Exception) {
                Timber.e(e, "ScheduledOperationsBootReceiver: rescheduleAll failed")
            } finally {
                pendingResult.finish()  // Release broadcast lifecycle (ML-009)
            }
        }
    }
}
