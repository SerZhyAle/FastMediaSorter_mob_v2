package com.sza.fastmediasorter.perf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.domain.usecase.ProvisionDefaultResourcesUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BenchmarkSetupReceiver : BroadcastReceiver() {

    @Inject
    lateinit var provisionDefaultResourcesUseCase: ProvisionDefaultResourcesUseCase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BenchmarkContract.ACTION_PREPARE_APP) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                markWelcomeAsCompleted(context)
                val provisioned = provisionDefaultResourcesUseCase()
                Timber.i(
                    "BenchmarkSetupReceiver: prepared benchmark state (provisioned=%s)",
                    provisioned
                )
            } catch (error: Exception) {
                Timber.e(error, "BenchmarkSetupReceiver: prepare failed")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun markWelcomeAsCompleted(context: Context) {
        context.getSharedPreferences(WELCOME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WELCOME_COMPLETED, true)
            .putBoolean(KEY_FIRST_RUN_AFTER_WELCOME, false)
            .apply()
    }

    private companion object {
        const val WELCOME_PREFS_NAME = "welcome_prefs"
        const val KEY_WELCOME_COMPLETED = "welcome_completed"
        const val KEY_FIRST_RUN_AFTER_WELCOME = "first_run_after_welcome"
    }
}
