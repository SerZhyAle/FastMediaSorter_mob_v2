package com.sza.fastmediasorter.ui.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherWallpaper
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ExecuteScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.ExportResourcesToFileUseCase
import com.sza.fastmediasorter.domain.usecase.companion.ExportCompanionConfigUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ExecuteLauncherCommandUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.PickContactShortcutUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherSectionCollapseManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarComposition
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarIcon
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayComposition
import com.sza.fastmediasorter.ui.main.helpers.ResourceScanCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** One-shot messages the home surface shows; the Activity resolves the string. */
sealed interface LauncherHomeEvent {
    data class Message(@StringRes val messageResId: Int) : LauncherHomeEvent

    /** Device has at least one channel: open the normal stream picker. */
    data object OpenStreamPicker : LauncherHomeEvent

    /** No channels yet: route the user to Settings > Media > Streams instead of an empty picker. */
    data object OpenStreamsSettings : LauncherHomeEvent

    /** A scheduled-op cell was tapped: confirm before running (it may modify or delete files). */
    data class ConfirmScheduledOp(val operationId: Long) : LauncherHomeEvent

    /**
     * S1402: a launcher-action cell was tapped and needs the activity - a screen to start, a dialog to
     * show, or the home role to hand back. Edit mode is not here: the ViewModel owns that state and
     * flips it itself.
     */
    data class PerformLauncherAction(val actionKey: String) : LauncherHomeEvent
}

@HiltViewModel
class LauncherHomeViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    private val desktopDependencies: LauncherDesktopDependencies,
    private val taskbarDependencies: LauncherTaskbarDependencies,
    private val shortcutDependencies: LauncherShortcutDependencies,
    // S1424: everything the resource/channel long-press menu needs that is not an intent.
    private val cellMenuDependencies: LauncherCellMenuDependencies,
    private val executeCommand: ExecuteLauncherCommandUseCase,
    private val settingsRepository: SettingsRepository,
    private val observeStreams: ObserveStreamSourcesUseCase,
    private val executeScheduledOperation: ExecuteScheduledOperationUseCase,
) : ViewModel() {

    // Rotation swaps which layout is observed. The collection itself is never torn down: the
    // orientation is an input to the stream, not a reason to restart it.
    private val _orientation = MutableStateFlow(LauncherOrientation.PORTRAIT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val cells: StateFlow<List<LauncherCellUi>> = _orientation
        .flatMapLatest { desktopDependencies.resolveDesktop(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    /** S1428: folded sections and the tap that folds them - see [LauncherSectionCollapseManager]. */
    val sections = LauncherSectionCollapseManager(appContext, viewModelScope, cells, _orientation)

    val densityFactor: StateFlow<Float> = settingsRepository.getSettings()
        .map { it.launcherDensityFactor }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_DENSITY_FACTOR)

    val taskbarComposition: Flow<LauncherTaskbarComposition> = settingsRepository.getSettings()
        .map {
            LauncherTaskbarComposition(
                showRecents = it.launcherTaskbarShowRecents,
                showPinned = it.launcherTaskbarShowPinned,
                showTray = it.launcherTaskbarShowTray,
                topStatusStripMode = it.launcherTopStatusStripMode && it.launcherReplaceSystemStatusArea,
            )
        }
        .distinctUntilChanged()

    /**
     * S1431 ADR-4: how many recent icons the taskbar row can actually show, reported by the row itself once
     * it has a width. Seeded at [RECENTS_LIMIT], which is also the floor - a measured row is allowed to ask
     * for more than the six the list showed before this ticket, never for fewer.
     */
    private val _recentsCapacity = MutableStateFlow(RECENTS_LIMIT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentIcons: Flow<List<LauncherTaskbarIcon>> = _recentsCapacity
        .flatMapLatest { taskbarDependencies.queryRecentCommands(it) }
        .map { entries ->
            entries.map { entry ->
                LauncherTaskbarIcon(
                    // The id is the encoded command (like a pin), so a recents tap decodes and reruns
                    // the exact command - not a bare package name.
                    id = entry.command.encode(),
                    label = entry.visual.label,
                    iconRes = entry.visual.iconRes,
                    iconDrawable = entry.visual.iconDrawable,
                    iconKey = entry.visual.iconKey,
                )
            }
        }

    val pinnedIcons: Flow<List<LauncherTaskbarIcon>> = taskbarDependencies.pinsRepository.observePins()
        .map { pins ->
            pins.mapNotNull { (position, command) ->
                taskbarDependencies.resolveVisual(command)?.let { visual ->
                    LauncherTaskbarIcon(
                        id = command.encode(),
                        label = visual.label,
                        iconRes = visual.iconRes,
                        iconDrawable = visual.iconDrawable,
                        iconKey = visual.iconKey,
                        // Carried so edit mode can unpin the exact slot; the id is the command, not the slot.
                        position = position,
                    )
                }
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * Editing is an explicit mode the user enters and leaves. Long-press on the desktop was rejected as
     * the *only* entry (owner quiz 2026-07-17: it collides with interactive gadgets and a remote has no
     * long-press) and reinstated by S1090 as an *additional* one: the Start-menu row stays the remote-
     * reachable path, the gesture is the discoverable one, and gadgets keep first claim on the press
     * because the listener sits on the desktop container, not on its children.
     */
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode

    /**
     * S1090: when true the long-press gesture must not open edit mode. Deliberately does not gate
     * [setEditMode] itself - the Start-menu row is an explicit action and stays reachable while locked,
     * and a lock flipped mid-session does not eject the user from an open edit session.
     */
    val desktopLocked: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherDesktopLocked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * S1101: what the desktop draws behind its cells. Image mode degrades to the branded animation when
     * the stored copy is gone (cleared app data, manual delete): a desktop that silently turns blank
     * reads as a bug, and the branded default is always available. The existence probe is disk I/O, so
     * the mapping runs off the main thread.
     */
    val wallpaper: StateFlow<LauncherWallpaper> = settingsRepository.getSettings()
        .map { settings ->
            val imageAvailable = settings.launcherWallpaperMode == AppSettings.LAUNCHER_WALLPAPER_IMAGE &&
                settings.launcherWallpaperImagePath.isNotBlank() && File(settings.launcherWallpaperImagePath).isFile
            resolveLauncherWallpaper(
                mode = settings.launcherWallpaperMode,
                imagePath = settings.launcherWallpaperImagePath,
                imageAvailable = imageAvailable,
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherWallpaper.Branded)

    /**
     * S1087: who owns the status area while the launcher is on screen. False (the owner's default) keeps
     * the Android status bar and therefore drops the tray's clock/network/battery, which would otherwise
     * be a second copy of what the system bar already shows one row above. True hands the area to the
     * launcher: the system bar goes, the tray content stays. Recents and pinned strips are a separate
     * choice and are not touched by this policy.
     */
    val replaceSystemStatusArea: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherReplaceSystemStatusArea }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * S1643: whether the taskbar composition is anchored to the top screen edge instead of the bottom one.
     *
     * A [StateFlow] rather than a plain flow because the Start menu reads the current value synchronously
     * while it builds its dialog, before any collector could have delivered a first value.
     */
    val taskbarAtTop: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherTaskbarPlacement == AppSettings.LAUNCHER_TASKBAR_PLACEMENT_TOP }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * S1431: whether the clock and the indicator set are drawn on the freed top band instead of the taskbar
     * tray. Both conditions are folded together here rather than in each of the three consumers, because a
     * mode left on with the status area no longer replaced would take the indicators off the tray and have
     * nowhere to put them (strategic §3.3).
     */
    val topStatusStripMode: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherTopStatusStripMode && it.launcherReplaceSystemStatusArea }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * S1431: whether the TASKBAR tray is the placement currently in use. The tray renderer treats this as
     * its visibility gate, so moving the indicators up to the strip also releases the taskbar copy's battery
     * receiver, network callback and SIM permission request instead of leaving two renderers subscribed to
     * the same sources at once (strategic ADR-5).
     */
    val taskbarTrayContentVisible: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherReplaceSystemStatusArea && !it.launcherTopStatusStripMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * S1415: which indicators the tray shows, one switch per indicator. Only decides whether an indicator
     * is subscribed at all - a switched-off indicator holds neither receiver nor callback (strategic §5.2).
     */
    val trayComposition: StateFlow<LauncherTrayComposition> = settingsRepository.getSettings()
        .map { LauncherTrayComposition.from(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherTrayComposition.from(AppSettings()))

    /**
     * One-shot: true once the first-rotation hint has been shown, so it never repeats. Seeds the edit
     * manager's decision on the next orientation change; the write goes back through settings so it
     * survives a process kill.
     */
    val rotationHintShown: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherRotationHintShown }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _events = Channel<LauncherHomeEvent>(Channel.BUFFERED)
    val events: Flow<LauncherHomeEvent> = _events.receiveAsFlow()

    // The target takes a few hundred ms to appear, and Home stays touchable the whole time, so a
    // second tap must not start it again. The guard is held until the user is actually back on the
    // desktop ([onHomeResumed]) - releasing it when the launch call returns would re-arm the cell
    // while the target is still opening, which is precisely the window it guards.
    private var launchInFlight = false

    fun setOrientation(orientation: LauncherOrientation) {
        _orientation.value = orientation
    }

    fun setEditMode(on: Boolean) {
        _editMode.value = on
    }

    /**
     * Places a new cell. Silently does nothing when the square is taken - the desktop only ever offers
     * free squares to tap, so a collision here means the layout changed underneath and re-rendering
     * says more than a message would.
     */
    fun addCell(
        rowIndex: Int,
        colIndex: Int,
        kind: LauncherCellKind,
        target: String,
        spanW: Int,
        spanH: Int,
        rememberFileListResourceId: Long? = null,
    ) {
        viewModelScope.launch {
            rememberResourceFileList(rememberFileListResourceId)
            desktopDependencies.desktopRepository.addCell(
                LauncherCell(
                    id = 0,
                    orientation = _orientation.value,
                    rowIndex = rowIndex,
                    colIndex = colIndex,
                    spanW = spanW,
                    spanH = spanH,
                    kind = kind,
                    target = target,
                    labelOverride = null,
                    addedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * S1209: the second add entry, the one with no square behind it. The repository scans row-major for
     * the first free position and opens a new row under everything when no existing row has room, so a
     * desktop whose visible part is full still takes a new cell. [addCell] stays the path for a square
     * the user pointed at, and both write through the same repository - the rule for where a new cell
     * lands has one home.
     *
     * The column count is passed in rather than read back from desktop state because it belongs to the
     * screen currently rendering the desktop, which is the contract the repository operation documents.
     */
    fun addCellInFirstFreeSlot(
        columns: Int,
        kind: LauncherCellKind,
        target: String,
        spanW: Int,
        spanH: Int,
        rememberFileListResourceId: Long? = null,
    ) {
        viewModelScope.launch {
            rememberResourceFileList(rememberFileListResourceId)
            desktopDependencies.desktopRepository.addCellInFirstFreeSlot(
                LauncherCell(
                    id = 0,
                    orientation = _orientation.value,
                    // Ignored: the repository scans for the anchor and overwrites both.
                    rowIndex = 0,
                    colIndex = 0,
                    spanW = spanW,
                    spanH = spanH,
                    kind = kind,
                    target = target,
                    labelOverride = null,
                    addedAt = System.currentTimeMillis(),
                ),
                columns,
            )
        }
    }

    /**
     * ADR-10: a resource-backed gadget draws a live file list on the desktop, so its resource must keep
     * that list across restarts - a network resource otherwise reads "Unavailable" after every reboot.
     * The write lives here, not in the picker, so this data mutation is visible to every reader of the
     * add-flow instead of hidden behind a dialog dismissal.
     */
    private suspend fun rememberResourceFileList(resourceId: Long?) {
        val id = resourceId ?: return
        val resource = desktopDependencies.resourceRepository.getResourceById(id) ?: return
        if (!resource.rememberFileList) {
            desktopDependencies.resourceRepository.updateResource(resource.copy(rememberFileList = true))
        }
    }

    /** Rejected when the footprint is not free; the cell simply snaps back where it was. */
    fun moveCell(id: Long, rowIndex: Int, colIndex: Int) {
        viewModelScope.launch {
            desktopDependencies.desktopRepository.moveCell(id, rowIndex, colIndex)
        }
    }

    /** Rejected when the new footprint overlaps another cell; the gesture keeps the last valid size. */
    fun resizeCell(id: Long, spanW: Int, spanH: Int) {
        viewModelScope.launch {
            desktopDependencies.desktopRepository.resizeCell(id, spanW, spanH)
        }
    }

    /** S0426: repoints an existing cell - used when the weather gadget is given another place. */
    fun updateCellTarget(id: Long, target: String) {
        viewModelScope.launch {
            desktopDependencies.desktopRepository.updateCellTarget(id, target)
        }
    }

    fun removeCell(id: Long) {
        viewModelScope.launch {
            desktopDependencies.desktopRepository.removeCell(id)
        }
    }

    /**
     * Pins a command to the first free slot. The position is a stable key, not a visual order, so the
     * lowest unused integer is enough - a gap an unpin left behind is simply reused rather than growing
     * the strip forever.
     */
    fun addPin(command: LauncherCellCommand) {
        viewModelScope.launch {
            val used = taskbarDependencies.pinsRepository.observePins().first().map { it.first }.toSet()
            var position = 0
            while (position in used) position++
            taskbarDependencies.pinsRepository.setPin(position, command)
        }
    }

    fun removePin(position: Int) {
        viewModelScope.launch {
            taskbarDependencies.pinsRepository.removePin(position)
        }
    }

    /** Remembers that the first-rotation hint has been shown, so it never appears again. */
    fun markRotationHintShown() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(launcherRotationHintShown = true) }
        }
    }

    /** The desktop is in front again: whatever was launched is done, so taps are live once more. */
    fun onHomeResumed() {
        launchInFlight = false
    }

    fun onCellTapped(cellUi: LauncherCellUi) {
        // Edit mode is for arranging, not launching, and a gadget owns the taps inside its own view -
        // either way the cell wrapper does nothing.
        if (_editMode.value || cellUi.cell.kind == LauncherCellKind.GADGET) return
        when (val command = LauncherCellCommand.decode(cellUi.cell.target)) {
            null -> emitCannotOpen()
            else -> run(command)
        }
    }

    /** Runs a scheduled op after the user confirmed it: background, with a start then result toast. */
    fun executeScheduledOp(operationId: Long) {
        viewModelScope.launch {
            _events.send(LauncherHomeEvent.Message(R.string.launcher_scheduled_op_started))
            val result = executeScheduledOperation(operationId)
            _events.send(
                LauncherHomeEvent.Message(
                    if (result.isSuccess) {
                        R.string.launcher_scheduled_op_done
                    } else {
                        R.string.launcher_scheduled_op_failed
                    },
                ),
            )
        }
    }

    /** Shared by the desktop, the taskbar strips, the Start menu and the gadgets - one guard for all. */
    fun run(command: LauncherCellCommand) {
        // A scheduled op may copy, move or delete files, so it is confirmed before it runs (S1103).
        // This belongs here rather than in onCellTapped: every surface reaches commands through this one
        // entry point, and ExecuteLauncherCommandUseCase deliberately refuses ScheduledOp because it can
        // only start activities. While the branch lived in the tap handler, the same operation pinned to
        // the taskbar or opened from the Start menu fell through to a bare "cannot open".
        // S1402: the second branch is here for the same reason - a launcher action pinned to the taskbar
        // or opened from the Start menu reaches commands through this one funnel, and the shared executor
        // has no Intent to start for it either. Written as one `when` rather than guard-and-return:
        // the third early return crossed detekt's ReturnCount limit.
        when {
            command is LauncherCellCommand.ScheduledOp -> {
                viewModelScope.launch {
                    _events.send(LauncherHomeEvent.ConfirmScheduledOp(command.operationId))
                }
            }

            command is LauncherCellCommand.LauncherAction -> runLauncherAction(command.actionKey)

            !launchInFlight -> {
                launchInFlight = true
                viewModelScope.launch {
                    if (!executeCommand.launch(command)) {
                        // Nothing opened, so nothing will bring the user back: re-arm now or the cell
                        // would stay dead until the next resume.
                        launchInFlight = false
                        _events.send(LauncherHomeEvent.Message(R.string.launcher_home_cannot_open))
                    }
                }
            }
        }
    }

    /** Edit mode is state this ViewModel already owns; everything else needs the activity. */
    private fun runLauncherAction(actionKey: String) {
        if (actionKey == LauncherActionCatalog.KEY_EDIT_DESKTOP) {
            setEditMode(true)
            return
        }
        viewModelScope.launch {
            _events.send(LauncherHomeEvent.PerformLauncherAction(actionKey))
        }
    }

    /**
     * S1424: the long-press menu needs the resource behind a cell to decide which rows it offers -
     * the cell stores the identifier and nothing more. Off the main thread, because a network
     * resource's row can be a disk read.
     */
    suspend fun resourceById(resourceId: Long): MediaResource? = withContext(Dispatchers.IO) {
        desktopDependencies.resourceRepository.getResourceById(resourceId)
    }

    /**
     * S1424: reached only after the shared confirmation dialog, which the desktop raises rather than
     * copies (strategic 6.2).
     *
     * The cell itself is left where it is on purpose: a cell whose target has gone renders as
     * unavailable, which is what every other vanished target already does, and silently rearranging
     * the desktop under a delete would be a second surprise on top of the first.
     */
    fun deleteResource(resourceId: Long) {
        viewModelScope.launch {
            val result = cellMenuDependencies.deleteResource(resourceId)
            if (result.isFailure) {
                Timber.e(result.exceptionOrNull(), "Deleting resource %d from the desktop failed", resourceId)
            }
            _events.send(
                LauncherHomeEvent.Message(
                    if (result.isSuccess) R.string.resource_deleted else R.string.error_unknown,
                ),
            )
        }
    }

    /**
     * S1424: the channel behind a `stream:` cell, or null when it is gone from the catalog. Read from
     * the same flow the picker reads, so the desktop menu cannot describe a channel the streams
     * screen has already dropped.
     */
    suspend fun streamById(streamId: String): StreamSourceEntity? =
        observeStreams().first().firstOrNull { it.id == streamId }

    /**
     * S1500: what backs the desktop's edit-a-channel row. A passthrough property rather than three
     * wrapper methods: this ViewModel adds nothing on the way to those two use cases, and the action
     * manager that reads them already owns the scope the writes need.
     */
    val streamEditDependencies: LauncherStreamEditDependencies
        get() = cellMenuDependencies.streamEdit

    /** S1424: the pinned block is what decides whether a channel's reorder rows have anywhere to go. */
    suspend fun pinnedStreams(): List<StreamSourceEntity> = observeStreams().first().filter { it.pinned }

    /** S1424: same toggle the streams screen offers - pins to top if loose, unpins if pinned. */
    fun toggleStreamPin(source: StreamSourceEntity) {
        viewModelScope.launch {
            if (source.pinned) {
                cellMenuDependencies.unpinStreamSource(source.id)
            } else {
                cellMenuDependencies.pinStreamSource(source.id)
            }
        }
    }

    /**
     * S1424: reached only after the shared confirmation dialog (strategic 6.2). The persisted last
     * frame goes with the channel, exactly as it does on the streams screen (S0712) - otherwise a
     * removal from the desktop would leave an orphan file behind.
     */
    fun removeStream(source: StreamSourceEntity) {
        viewModelScope.launch {
            cellMenuDependencies.removeStreamSource(source)
            cellMenuDependencies.streamFrameStore.remove(source.url)
            // The streams screen needs no message - the row vanishes from its list. A desktop cell
            // does not vanish; it turns unavailable, which alone would not read as "I removed it".
            _events.send(LauncherHomeEvent.Message(R.string.launcher_home_channel_removed))
        }
    }

    /**
     * S1424: rescans one resource and reports whether it is reachable. The desktop shows the same
     * "unavailable" message the main window shows; an available resource says nothing, because the
     * scan's whole effect is the refreshed record behind the cell.
     */
    suspend fun scanResource(resource: MediaResource): Boolean = withContext(Dispatchers.IO) {
        val result = cellMenuDependencies.scanCoordinator.scanAndRefreshSingleResource(resource)
        result is ResourceScanCoordinator.SingleScanResult.Available
    }

    /**
     * S1424: writes the exported resource to [target] and reports whether anything landed there.
     *
     * The caller owns the file, because the cache directory and the share sheet both belong to the
     * Activity; this end owns only the export itself.
     */
    suspend fun exportResource(resourceId: Long, target: Uri): Boolean {
        val result = cellMenuDependencies.exportResourcesToFile(listOf(resourceId), target)
        return result is ExportResourcesToFileUseCase.ExportResult.Success && result.exported > 0
    }

    /** S1424: the SFTP access payload as a file, or null when it could not be written. */
    suspend fun exportCompanionConfig(resource: MediaResource, includePassword: Boolean): File? =
        cellMenuDependencies.exportCompanionConfig(resource, includePassword).getOrNull()

    /** S1424: the same access payload as a QR string, or null when it could not be built. */
    suspend fun companionQrPayload(
        resource: MediaResource,
        includePassword: Boolean,
    ): ExportCompanionConfigUseCase.CompanionQrExport? =
        cellMenuDependencies.exportCompanionConfig.exportQrPayload(resource, includePassword).getOrNull()

    private fun emitCannotOpen() {
        viewModelScope.launch {
            _events.send(LauncherHomeEvent.Message(R.string.launcher_home_cannot_open))
        }
    }

    // Choosing "Channel" for a new cell: snapshot the catalog once. Empty means streams were never
    // enabled, so route to their setting rather than show a picker with nothing to pick.
    fun requestStreamCell() {
        viewModelScope.launch {
            val hasChannels = observeStreams().first().isNotEmpty()
            _events.send(
                if (hasChannels) LauncherHomeEvent.OpenStreamPicker else LauncherHomeEvent.OpenStreamsSettings,
            )
        }
    }

    /** Records the grid width the surface actually resolved, so seeding and edit mode agree with it. */
    fun persistColumns(orientation: LauncherOrientation, columns: Int) {
        viewModelScope.launch {
            desktopDependencies.desktopRepository.updateColumns(orientation, columns)
        }
    }

    /**
     * Seeds an empty desktop with the profile's starter set (ADR-4).
     *
     * Re-entry is guarded by the persisted seeded flags inside the seed use case, not by an in-memory
     * one-shot. S1400 needs exactly that difference: a reset lowers those flags, so the seed must be
     * able to run again in the same process, while a desktop the user emptied by hand keeps them
     * raised and is left alone.
     *
     * Columns are resolved INSIDE the coroutine from the real persisted density
     * (getSettings().first()), not densityFactor.value - that StateFlow's initial default is still in
     * place at seed time until the async DataStore read lands, and a wrong column count would
     * misplace/overlap cells permanently (audit 2026-07-17, P1).
     */
    fun seedDesktopIfNeeded(widthDp: Float, heightDp: Float, startedPortrait: Boolean) {
        viewModelScope.launch {
            val density = settingsRepository.getSettings().first().launcherDensityFactor
            val widthColumns = LauncherGridGeometry.columns(widthDp, density)
            val heightColumns = LauncherGridGeometry.columns(heightDp, density)
            val portraitColumns = if (startedPortrait) widthColumns else heightColumns
            val landscapeColumns = if (startedPortrait) heightColumns else widthColumns
            desktopDependencies.seedLauncherDesktop(portraitColumns, landscapeColumns)
        }
    }

    /**
     * S1401: puts [packageName] on the first free square of the current orientation. The placement
     * lives here rather than in the menu for the same reason the add flow does - a data mutation stays
     * visible to every reader of the desktop stream instead of hiding inside a popup.
     */
    fun placeAppOnDesktop(packageName: String, columns: Int) {
        viewModelScope.launch {
            val placed = desktopDependencies.desktopRepository.addCellInFirstFreeSlot(
                LauncherCell(
                    id = 0,
                    orientation = _orientation.value,
                    // Ignored: the repository scans for the anchor and overwrites both.
                    rowIndex = 0,
                    colIndex = 0,
                    spanW = 1,
                    spanH = 1,
                    kind = LauncherCellKind.SHORTCUT,
                    target = LauncherCellCommand.App(packageName).encode(),
                    labelOverride = null,
                    addedAt = System.currentTimeMillis(),
                ),
                columns,
            )
            if (placed == null) {
                _events.send(LauncherHomeEvent.Message(R.string.launcher_app_action_desktop_full))
            }
        }
    }

    /** S1401: the menu's "Pin to taskbar" - same pin path the taskbar's own "+" uses. */
    fun pinAppToTaskbar(packageName: String) {
        addPin(LauncherCellCommand.App(packageName))
        viewModelScope.launch {
            _events.send(LauncherHomeEvent.Message(R.string.launcher_app_action_pinned))
        }
    }

    /** S1401: null when no installed activity can show this app's details page. */
    fun appInfoIntent(packageName: String): Intent? =
        shortcutDependencies.buildAppSystemActionIntent.appInfoIntent(packageName)

    /** S1401: null for a system app, and for any device whose build offers no uninstall screen. */
    fun uninstallIntent(packageName: String): Intent? =
        shortcutDependencies.buildAppSystemActionIntent.uninstallIntent(packageName)

    /** S0427 long-press popup: the quick actions [packageName] publishes, empty when it publishes none. */
    suspend fun appShortcutsOf(packageName: String): List<AppShortcut> =
        shortcutDependencies.queryAppShortcuts(packageName)

    /** Launches [shortcut] with [bounds] as the animation origin; false when the platform refused. */
    suspend fun launchAppShortcut(shortcut: AppShortcut, bounds: Rect): Boolean =
        shortcutDependencies.startAppShortcut(shortcut, bounds)

    /** S1176: the system picker to open for [action] - phone numbers for DIAL, contacts otherwise. */
    fun contactPickIntent(action: LauncherContactAction): Intent =
        shortcutDependencies.pickContactShortcut.pickIntent(action)

    /** S1176: reads the [picked] contact into the snapshot to pin, or reports why it cannot be pinned. */
    suspend fun resolveContactPick(
        action: LauncherContactAction,
        picked: Uri,
    ): PickContactShortcutUseCase.Outcome = shortcutDependencies.pickContactShortcut(action, picked)

    /**
     * S1431 ADR-4: the recents row's own measurement of how many icons it fits, written by the row after
     * layout. Floored at [RECENTS_LIMIT] on write, so a narrow row scrolls through the same six as before
     * rather than losing entries, and raised above it when the tray leaves the bar or the device turns
     * landscape.
     *
     * A property rather than a setter function on purpose: this class sits exactly at detekt's
     * `TooManyFunctions` ceiling of 40, and one more named function would trip it. The decomposition that
     * would earn the 41st is a ticket of its own, not a side effect of adding one measurement input.
     */
    var recentsCapacity: Int
        get() = _recentsCapacity.value
        set(value) {
            _recentsCapacity.value = value.coerceAtLeast(RECENTS_LIMIT)
        }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        const val DEFAULT_DENSITY_FACTOR = 1.0f

        /** As many recents as fit a phone taskbar beside the Start button and the tray. */
        const val RECENTS_LIMIT = 6
    }
}

/** Pure setting-to-render-model mapping; image availability is supplied because the file probe is I/O. */
internal fun resolveLauncherWallpaper(
    mode: String,
    imagePath: String,
    imageAvailable: Boolean,
): LauncherWallpaper = when (mode) {
    AppSettings.LAUNCHER_WALLPAPER_NONE -> LauncherWallpaper.None
    AppSettings.LAUNCHER_WALLPAPER_STATIC_STRIPES -> LauncherWallpaper.StaticStripes
    AppSettings.LAUNCHER_WALLPAPER_IMAGE ->
        imagePath.takeIf { imageAvailable }?.let { LauncherWallpaper.Image(it) } ?: LauncherWallpaper.Branded

    else -> LauncherWallpaper.Branded
}
