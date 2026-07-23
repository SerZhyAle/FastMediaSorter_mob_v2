package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.db.DatabaseResetNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S1153: defers the startup notices that read from disk - the S0731 database-reset notice
 * (SharedPreferences) and the S0490 crash-report prompt (log-dir listing) - off the main thread and
 * past first frame. Each read runs on [Dispatchers.IO]; the dialog is shown back on Main only while
 * the Activity is still alive, so the observable outcome (dialog still shown) is preserved without
 * blocking onCreate or the first frame. The return-to-settings read is intentionally NOT deferred -
 * it gates an immediate navigate/finish branch in onCreate and stays synchronous.
 */
class StartupNoticeManager(private val activity: AppCompatActivity) {

    private val crashReportPromptManager = CrashReportPromptManager(activity)

    /**
     * @param showCrashPrompt only true on a fresh launch (savedInstanceState == null), mirroring the
     * prior gate so a configuration-change recreate never re-offers the crash prompt.
     */
    fun presentDeferredNotices(showCrashPrompt: Boolean) {
        activity.lifecycleScope.launch {
            val resetNotice = withContext(Dispatchers.IO) {
                DatabaseResetNotice.consumePending(activity)
            }
            resetNotice?.let {
                DatabaseResetNotice.showNotice(activity, it)
            }

            if (showCrashPrompt) {
                val crashFile = withContext(Dispatchers.IO) {
                    crashReportPromptManager.findPendingCrash()
                }
                crashFile?.let {
                    crashReportPromptManager.showPrompt(it)
                }
            }
        }
    }
}
