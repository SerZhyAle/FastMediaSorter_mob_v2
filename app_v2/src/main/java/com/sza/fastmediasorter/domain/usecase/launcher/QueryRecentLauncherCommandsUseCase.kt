package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/** A recent launcher launch, paired with the visual the taskbar draws for it. */
data class RecentLauncherCommand(
    val command: LauncherCellCommand,
    val visual: LauncherCommandVisual,
)

/**
 * S0404/S1097/S2242: the taskbar's "recent" strip. Built from the app's own journal (ADR-7) and, unlike the
 * first iteration, spans every command kind the user launched - internal features, resources, streams
 * and OS shortcuts as well as third-party apps.
 *
 * S2242: deduplicates commands already pinned to the taskbar via [LauncherPinsRepository] so a pinned
 * shortcut does not appear twice in the recent strip / Start panel.
 */
class QueryRecentLauncherCommandsUseCase @Inject constructor(
    private val journal: LauncherJournalRepository,
    private val pins: LauncherPinsRepository,
    private val resolveVisual: ResolveLauncherCommandLabelUseCase,
    @ApplicationContext private val context: Context,
) {

    operator fun invoke(limit: Int): Flow<List<RecentLauncherCommand>> =
        combine(journal.recentCommands(limit * 2), pins.observePins()) { recentCommands, pinnedList ->
            val pinnedCommands = pinnedList.map { it.second }.toSet()
            recentCommands
                .filter { command -> command !in pinnedCommands }
                .mapNotNull { resolve(it) }
                .take(limit)
        }.flowOn(Dispatchers.IO)

    private suspend fun resolve(command: LauncherCellCommand): RecentLauncherCommand? {
        // An app must still be launchable to earn a recents slot. ResolveLauncherCommandLabelUseCase
        // keeps an uninstalled package as a named placeholder (right for a desktop cell the user
        // removes by hand), but a recents entry that cannot open is noise, so drop it here.
        if (command is LauncherCellCommand.App) {
            if (command.packageName == context.packageName) return null
            if (context.packageManager.getLaunchIntentForPackage(command.packageName) == null) return null
        }
        val visual = resolveVisual(command) ?: return null
        return RecentLauncherCommand(command, visual)
    }
}
