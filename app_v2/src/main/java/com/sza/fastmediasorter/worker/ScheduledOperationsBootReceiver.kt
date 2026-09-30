package com.sza.fastmediasorter.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.di.bootReceiverEntryPointOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/** Not `@AndroidEntryPoint`: see `BootReceiverEntryPoint` for why the graph is resolved by hand. */
class ScheduledOperationsBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val deps = bootReceiverEntryPointOrNull(context, "ScheduledOperationsBootReceiver") ?: return
        val workManagerScheduler = deps.workManagerScheduler()
        val settingsRepository = deps.settingsRepository()
        Timber.i("ScheduledOperationsBootReceiver: BOOT_COMPLETED - rescheduling all operations")

        // Use goAsync() to keep broadcast alive until rescheduleAll completes (ML-009)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (settingsRepository.getSettings().first().scheduledOperationsPaused) {
                    // pendingResult.finish() is owned by the finally block below - calling it here
                    // too would double-finish the broadcast (IllegalStateException).
                    Timber.i("ScheduledOperationsBootReceiver: scheduler paused - skip reschedule")
                    return@launch
                }
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
