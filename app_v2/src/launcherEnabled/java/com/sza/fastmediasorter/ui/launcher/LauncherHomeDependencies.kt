package com.sza.fastmediasorter.ui.launcher

import com.sza.fastmediasorter.data.repository.streams.StreamFramePersistentStore
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.DeleteResourceUseCase
import com.sza.fastmediasorter.domain.usecase.ExportResourcesToFileUseCase
import com.sza.fastmediasorter.domain.usecase.apps.BuildAppSystemActionIntentUseCase
import com.sza.fastmediasorter.domain.usecase.companion.ExportCompanionConfigUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.PickContactShortcutUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.QueryAppShortcutsUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.QueryRecentLauncherCommandsUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.RemoveRecentLauncherCommandUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherCommandLabelUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherDesktopUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.SeedLauncherDesktopUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.StartAppShortcutUseCase
import com.sza.fastmediasorter.domain.usecase.streams.PinStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.RemoveStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.UnpinStreamSourceUseCase
import com.sza.fastmediasorter.domain.usecase.streams.UpdateStreamSourceUseCase
import com.sza.fastmediasorter.ui.launcher.gadget.ConfiguredWidgetInstanceManager
import com.sza.fastmediasorter.ui.main.helpers.ResourceScanCoordinator
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
    // S1930: removing a configured widget cell has to throw its stored instance away, and the cell is
    // the only thing that still knows which one - so the cleanup joins the surface that owns removal.
    val configuredWidgetInstances: ConfiguredWidgetInstanceManager,
)

/** Serves the taskbar strips - the recents row, the pinned row and what each icon renders as. */
class LauncherTaskbarDependencies @Inject constructor(
    val queryRecentCommands: QueryRecentLauncherCommandsUseCase,
    val removeRecentCommand: RemoveRecentLauncherCommandUseCase,
    val pinsRepository: LauncherPinsRepository,
    val resolveVisual: ResolveLauncherCommandLabelUseCase,
)

/** Serves the app cell's long-press menu, the app shortcuts inside it and the contact pick it starts. */
class LauncherShortcutDependencies @Inject constructor(
    val queryAppShortcuts: QueryAppShortcutsUseCase,
    val startAppShortcut: StartAppShortcutUseCase,
    val pickContactShortcut: PickContactShortcutUseCase,
    val buildAppSystemActionIntent: BuildAppSystemActionIntentUseCase,
)

/**
 * S1424: serves the long-press menu over a resource or channel cell - only the rows that need domain
 * work. The rest of that menu is intents and commands the desktop already runs, which need nothing
 * here (strategic 4).
 *
 * Its own holder rather than more fields on [LauncherShortcutDependencies], following this file's
 * rule of one holder per surface: that one answers to app cells, and these dependencies never serve
 * an app cell.
 */
class LauncherCellMenuDependencies @Inject constructor(
    val deleteResource: DeleteResourceUseCase,
    val scanCoordinator: ResourceScanCoordinator,
    val exportResourcesToFile: ExportResourcesToFileUseCase,
    val exportCompanionConfig: ExportCompanionConfigUseCase,
    val pinStreamSource: PinStreamSourceUseCase,
    val unpinStreamSource: UnpinStreamSourceUseCase,
    val removeStreamSource: RemoveStreamSourceUseCase,
    // Removing a channel on the streams screen also drops its persisted last frame (S0712); a removal
    // from the desktop that skipped it would leave that file orphaned.
    val streamFrameStore: StreamFramePersistentStore,
    // S1500: nested rather than flattened into more fields here, because this holder was already at
    // detekt's parameter ceiling and because these two serve one row rather than the whole menu.
    val streamEdit: LauncherStreamEditDependencies,
)

/**
 * S1500: what the desktop's edit-a-channel row needs behind the shared [StreamEditDialog] - the
 * channel row itself, and the per-channel track preference the dialog reads and writes alongside it.
 *
 * Its own holder rather than two more fields on [LauncherCellMenuDependencies], following this file's
 * rule of one holder per surface: these two serve one row, and the edit action manager is the only
 * thing that ever reads them.
 */
class LauncherStreamEditDependencies @Inject constructor(
    val updateStreamSource: UpdateStreamSourceUseCase,
    val streamTrackPreference: StreamTrackPreferenceUseCase,
)
