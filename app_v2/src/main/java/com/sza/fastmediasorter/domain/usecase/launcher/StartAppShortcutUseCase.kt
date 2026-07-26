package com.sza.fastmediasorter.domain.usecase.launcher

import android.graphics.Rect
import com.sza.fastmediasorter.data.launcher.AppShortcutDataSource
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S0427: starts one published quick action. Returns whether the platform accepted the launch, so the
 * caller can tell the user instead of failing silently.
 *
 * Deliberately outside the launcher journal: that journal stores `LauncherCellCommand` values and a
 * third-party shortcut is not one.
 */
class StartAppShortcutUseCase @Inject constructor(
    private val dataSource: AppShortcutDataSource,
) {

    suspend operator fun invoke(shortcut: AppShortcut, sourceBounds: Rect?): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.start(shortcut.packageName, shortcut.id, sourceBounds)
        }
}
