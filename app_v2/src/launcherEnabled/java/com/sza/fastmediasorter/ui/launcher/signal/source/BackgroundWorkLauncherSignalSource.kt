package com.sza.fastmediasorter.ui.launcher.signal.source

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.duplicates.DuplicatesActivity
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignal
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalKind
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalSource
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.worker.DuplicateDetectionWorker
import com.sza.fastmediasorter.worker.NetworkFilesSyncWorker
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * S1421 ADR-1: the long-running background jobs that already post their own notification.
 *
 * Scoped to the WorkManager-backed ones on purpose (strategic §4.6). `NotificationIds` lists nine posters,
 * but it is a registry of ids rather than of live notifications, and the service-backed posters - screen
 * recording, the gesture overlay, the quick recorder - expose no observable state at all. Building one is a
 * change at nine publication points and belongs to its own ticket; those join through ADR-4's seam later.
 */
class BackgroundWorkLauncherSignalSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LauncherSignalSource {

    private val workManager = WorkManager.getInstance(context)

    override fun observe(): Flow<List<LauncherSignal>> = combine(
        // The scheduled-operation family has one unique work name per operation, so the shared tag is the
        // only handle that reaches all of them at once - the same reason it exists for bulk cancellation.
        workManager.getWorkInfosByTagFlow(WorkManagerScheduler.TAG_SCHEDULED_OP),
        workManager.getWorkInfosForUniqueWorkFlow(DuplicateDetectionWorker.UNIQUE_WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(NetworkFilesSyncWorker.WORK_NAME),
    ) { scheduled, duplicates, sync ->
        listOfNotNull(
            BackgroundJob.SCHEDULED_OPS.signalFor(scheduled),
            BackgroundJob.DUPLICATE_SCAN.signalFor(duplicates),
            BackgroundJob.NETWORK_SYNC.signalFor(sync),
        )
    }

    override fun open(signal: LauncherSignal): Intent? {
        val job = BackgroundJob.entries.firstOrNull { it.signalId == signal.id } ?: return null
        val intent = when (job) {
            // The scheduled-operations list is a settings surface, reached by the deep-link extra the
            // settings screen already understands.
            BackgroundJob.SCHEDULED_OPS -> Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED, true)
            // No resource extra: a scan can span several resources and WorkInfo does not carry the input
            // data that named them, so the screen opens on its own default rather than on a guessed one.
            BackgroundJob.DUPLICATE_SCAN -> Intent(context, DuplicatesActivity::class.java)
            BackgroundJob.NETWORK_SYNC -> Intent(context, MainActivity::class.java)
        }
        return intent.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private fun BackgroundJob.signalFor(infos: List<WorkInfo>): LauncherSignal? {
        if (infos.none { !it.state.isFinished }) {
            return null
        }
        return LauncherSignal(
            id = signalId,
            kind = LauncherSignalKind.BACKGROUND_WORK,
            iconRes = iconRes,
            label = context.getString(labelRes),
        )
    }
}

private enum class BackgroundJob(
    val signalId: String,
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
) {
    SCHEDULED_OPS("scheduled-ops", R.drawable.ic_schedule, R.string.launcher_signal_scheduled_ops),
    DUPLICATE_SCAN("duplicate-scan", R.drawable.ic_find_replace, R.string.launcher_signal_duplicate_scan),
    NETWORK_SYNC("network-sync", R.drawable.ic_cloud, R.string.launcher_signal_network_sync),
}
