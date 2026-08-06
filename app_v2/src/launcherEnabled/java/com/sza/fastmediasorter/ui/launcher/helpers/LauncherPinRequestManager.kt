package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Intent
import com.sza.fastmediasorter.data.launcher.AppShortcutDataSource
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.usecase.launcher.AcceptPinnedShortcutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What hosting one pin request came to. */
enum class LauncherPinRequestOutcome {
    /** A widget request, another profile's, or one already spent - nothing was promised to the user. */
    NOT_OURS,
    PLACED,
    NOT_PLACED,
}

/**
 * S1205: everything the `CONFIRM_PIN_SHORTCUT` host does, so the host itself stays a host.
 *
 * The activity may not hold the data source and the use case directly (Rule 3, gate
 * `activity-logic`), and there is no ViewModel to put them in either - the screen has no state and no
 * lifecycle worth surviving, it accepts one request and ends.
 */
class LauncherPinRequestManager @Inject constructor(
    private val dataSource: AppShortcutDataSource,
    private val acceptPinnedShortcut: AcceptPinnedShortcutUseCase,
) {

    /**
     * [isLandscape] rather than a [LauncherOrientation] because reading its own configuration is the
     * one thing the host legitimately knows and this class does not; the two desktop layouts are
     * independent, so a request has to land on the one the user is looking at.
     */
    suspend fun handle(
        intent: Intent,
        isLandscape: Boolean,
        addedAt: Long,
    ): LauncherPinRequestOutcome = withContext(Dispatchers.IO) {
        val request = dataSource.pinRequestFrom(intent)
            ?: return@withContext LauncherPinRequestOutcome.NOT_OURS
        val orientation = if (isLandscape) {
            LauncherOrientation.LANDSCAPE
        } else {
            LauncherOrientation.PORTRAIT
        }
        if (acceptPinnedShortcut(request, orientation, addedAt)) {
            LauncherPinRequestOutcome.PLACED
        } else {
            LauncherPinRequestOutcome.NOT_PLACED
        }
    }
}
