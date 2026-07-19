package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A recent launcher launch, paired with the visual the taskbar draws for it. */
data class RecentLauncherCommand(
    val command: LauncherCellCommand,
    val visual: LauncherCommandVisual,
)

/**
 * S0404/S1097: the taskbar's "recent" strip. Built from the app's own journal (ADR-7) and, unlike the
 * first iteration, spans every command kind the user launched - internal features, resources, streams
 * and OS shortcuts as well as third-party apps - because the seeded desktop launches mostly internal
 * targets and an apps-only strip stayed empty in normal use (owner decision 2026-07-18).
 *
 * Each journalled command is resolved to its current visual through the same
 * [ResolveLauncherCommandLabelUseCase] the pins use, so labels/icons stay in one place. A target that
 * is gone now (uninstalled app, deleted resource, missing channel) is dropped rather than shown dead:
 * recents are a shortcut to reopen something, not a headstone like a desktop cell.
 */
class QueryRecentLauncherCommandsUseCase @Inject constructor(
    private val journal: LauncherJournalRepository,
    private val resolveVisual: ResolveLauncherCommandLabelUseCase,
    @ApplicationContext private val context: Context,
) {

    operator fun invoke(limit: Int): Flow<List<RecentLauncherCommand>> =
        journal.recentCommands(limit)
            .map { commands -> commands.mapNotNull { resolve(it) } }
            .flowOn(Dispatchers.IO)

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
