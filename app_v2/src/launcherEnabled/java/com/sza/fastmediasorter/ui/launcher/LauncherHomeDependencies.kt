package com.sza.fastmediasorter.ui.launcher

import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.launcher.PickContactShortcutUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.QueryAppShortcutsUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.QueryRecentLauncherCommandsUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherCommandLabelUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherDesktopUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.SeedLauncherDesktopUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.StartAppShortcutUseCase
import javax.inject.Inject

// S1314: plain classes on purpose, never data classes - detekt's LongParameterList.ignoreDataClasses
// defaults to true, so a data holder would be invisible to the very gate these bundles exist to satisfy.
// One holder per launcher-home surface, so a dependency added later joins its surface instead of the
// ViewModel constructor.

/** Serves the desktop grid - the launcher's own cell surface, and the first-run seeding behind it. */
class LauncherDesktopDependencies @Inject constructor(
    val resolveDesktop: ResolveLauncherDesktopUseCase,
    val desktopRepository: LauncherDesktopRepository,
    val seedLauncherDesktop: SeedLauncherDesktopUseCase,
    val resourceRepository: ResourceRepository,
)

/** Serves the taskbar strips - the recents row, the pinned row and what each icon renders as. */
class LauncherTaskbarDependencies @Inject constructor(
    val queryRecentCommands: QueryRecentLauncherCommandsUseCase,
    val pinsRepository: LauncherPinsRepository,
    val resolveVisual: ResolveLauncherCommandLabelUseCase,
)

/** Serves the long-press shortcut popup and the contact pick it can start. */
class LauncherShortcutDependencies @Inject constructor(
    val queryAppShortcuts: QueryAppShortcutsUseCase,
    val startAppShortcut: StartAppShortcutUseCase,
    val pickContactShortcut: PickContactShortcutUseCase,
)
