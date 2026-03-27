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
        // goAsync not needed here since WorkManager enqueue is fast
        CoroutineScope(Dispatchers.IO).launch {
            try {
                workManagerScheduler.rescheduleAll()
            } catch (e: Exception) {
                Timber.e(e, "ScheduledOperationsBootReceiver: rescheduleAll failed")
            }
        }
    }
}
