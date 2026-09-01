package com.sza.fastmediasorter.ui.launcher

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellDraft
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherWallpaper
import com.sza.fastmediasorter.domain.repository.LauncherSectionVisibilityRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ExecuteScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.ExportResourcesToFileUseCase
import com.sza.fastmediasorter.domain.usecase.companion.ExportCompanionConfigUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ExecuteLauncherCommandUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.IsCameraWallpaperAvailableUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.PickContactShortcutUseCase
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherSectionCollapseManager
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarComposition
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarIcon
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayComposition
import com.sza.fastmediasorter.ui.main.helpers.ResourceScanCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

@Suppress("LongParameterList", "TooManyFunctions")
@HiltViewModel
class LauncherHomeViewModel @Inject constructor(
    private val visibility: LauncherSectionVisibilityRepository,
    private val desktopDependencies: LauncherDesktopDependencies,
    private val taskbarDependencies: LauncherTaskbarDependencies,
    private val shortcutDependencies: LauncherShortcutDependencies,
    // S1424: everything the resource/channel long-press menu needs that is not an intent.
    private val cellMenuDependencies: LauncherCellMenuDependencies,
    private val executeCommand: ExecuteLauncherCommandUseCase,
    private val settingsRepository: SettingsRepository,
    private val observeStreams: ObserveStreamSourcesUseCase,
    private val executeScheduledOperation: ExecuteScheduledOperationUseCase,
    // S2076: the camera wallpaper's own precondition - hardware plus a live CAMERA grant.
    private val isCameraWallpaperAvailable: IsCameraWallpaperAvailableUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase,
) : ViewModel() {

    // Rotation swaps which layout is observed. The collection itself is never torn down: the
    // orientation is an input to the stream, not a reason to restart it.
    private val _orientation = MutableStateFlow(LauncherOrientation.PORTRAIT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val cells: StateFlow<List<LauncherCellUi>> = _orientation
        .flatMapLatest { desktopDependencies.resolveDesktop(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    /** S1428: folded sections and the tap that folds them - see [LauncherSectionCollapseManager]. */
    val sections = LauncherSectionCollapseManager(visibility, viewModelScope, cells, _orientation)

    /**
     * S1680: the seed every settings-derived flow below starts from, so a seed and the model's default
     * cannot drift apart the next time a default is changed. Behaviour is unchanged today - the literals
     * this replaced were measured equal to these defaults.
     */
    private val settingsDefaults = AppSettings()

    val launcherDesktopSettings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsDefaults)

    val densityFactor: StateFlow<Float> = settingsRepository.getSettings()
        .map { it.launcherDensityFactor }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsDefaults.launcherDensityFactor)

    /**
     * S1904: how opaque a gadget's backdrop is drawn at rest. S1748 stored the setting and showed it in
     * the launcher settings, but no renderer ever read it, so every value looked identical on screen.
     */
    val widgetBackdropAlpha: StateFlow<Float> = settingsRepository.getSettings()
        .map { it.launcherWidgetBackdropAlpha }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsDefaults.launcherWidgetBackdropAlpha)

    /**
     * S2213: the place last picked for a weather gadget, handed to a cell that has none of its own.
     * Seeded from [settingsDefaults] rather than a literal so the seed cannot drift from the model
     * default - the reason that shared defaults object exists.
     */
    val weatherLastLocation: StateFlow<String> = settingsRepository.getSettings()
        .map { it.launcherWeatherLastLocation }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsDefaults.launcherWeatherLastLocation)

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
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsDefaults.launcherDesktopLocked)

    /**
     * S2210: the frame the last instant-photo capture produced, held only for this process.
     *
     * Deliberately not a setting: the frame is a transient camera capture, and the one persisted wallpaper
     * path belongs to the image the user picked. Storing it there would overwrite that pick the first time
     * instant-photo mode ran, and would carry a dead capture path into backup and restore.
     */
    private val instantPhotoFrame = MutableStateFlow<InstantPhotoFrame?>(null)

    /**
     * S1101: what the desktop draws behind its cells. Image mode degrades to the branded animation when
     * the stored copy is gone (cleared app data, manual delete): a desktop that silently turns blank
     * reads as a bug, and the branded default is always available. The existence probe is disk I/O, so
     * the mapping runs off the main thread.
     */
    val wallpaper: StateFlow<LauncherWallpaper> = combine(
        settingsRepository.getSettings(),
        instantPhotoFrame,
    ) { settings, instantPhoto ->
        val imageAvailable = settings.launcherWallpaperMode == AppSettings.LAUNCHER_WALLPAPER_IMAGE &&
            settings.launcherWallpaperImagePath.isNotBlank() && File(settings.launcherWallpaperImagePath).isFile
        val cameraAvailable = (
            settings.launcherWallpaperMode == AppSettings.LAUNCHER_WALLPAPER_CAMERA ||
                settings.launcherWallpaperMode == AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO
            ) &&
            isCameraWallpaperAvailable()
        resolveLauncherWallpaper(
            mode = settings.launcherWallpaperMode,
            imagePath = settings.launcherWallpaperImagePath,
            imageAvailable = imageAvailable,
            cameraId = settings.launcherWallpaperCameraId,
            cameraAvailable = cameraAvailable,
            instantPhoto = instantPhoto,
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            // imageAvailable is false because a seed must not probe the disk on the main thread; the
            // default mode is not IMAGE, so the probe would decide nothing anyway. cameraAvailable is
            // false for the same reason (S2076): the default mode is not CAMERA either.
            resolveLauncherWallpaper(
                mode = settingsDefaults.launcherWallpaperMode,
                imagePath = settingsDefaults.launcherWallpaperImagePath,
                imageAvailable = false,
                cameraId = settingsDefaults.launcherWallpaperCameraId,
                cameraAvailable = false,
            ),
        )

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
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsDefaults.launcherReplaceSystemStatusArea)

    /**
     * S1643: whether the taskbar composition is anchored to the top screen edge instead of the bottom one.
     *
     * A [StateFlow] rather than a plain flow because the Start menu reads the current value synchronously
     * while it builds its dialog, before any collector could have delivered a first value.
     */
    val taskbarAtTop: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherTaskbarPlacement == AppSettings.LAUNCHER_TASKBAR_PLACEMENT_TOP }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsDefaults.launcherTaskbarPlacement == AppSettings.LAUNCHER_TASKBAR_PLACEMENT_TOP,
        )

    /**
     * S1431: whether the clock and the indicator set are drawn on the freed top band instead of the taskbar
     * tray. Both conditions are folded together here rather than in each of the three consumers, because a
     * mode left on with the status area no longer replaced would take the indicators off the tray and have
     * nowhere to put them (strategic §3.3).
     */
    val topStatusStripMode: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherTopStatusStripMode && it.launcherReplaceSystemStatusArea }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsDefaults.launcherTopStatusStripMode && settingsDefaults.launcherReplaceSystemStatusArea,
        )

    /**
     * S1431: whether the TASKBAR tray is the placement currently in use. The tray renderer treats this as
     * its visibility gate, so moving the indicators up to the strip also releases the taskbar copy's battery
     * receiver, network callback and SIM permission request instead of leaving two renderers subscribed to
     * the same sources at once (strategic ADR-5).
     */
    val taskbarTrayContentVisible: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherReplaceSystemStatusArea && !it.launcherTopStatusStripMode }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsDefaults.launcherReplaceSystemStatusArea && !settingsDefaults.launcherTopStatusStripMode,
        )

    /**
     * S1415: which indicators the tray shows, one switch per indicator. Only decides whether an indicator
     * is subscribed at all - a switched-off indicator holds neither receiver nor callback (strategic §5.2).
     */
    val trayComposition: StateFlow<LauncherTrayComposition> = settingsRepository.getSettings()
        .map { LauncherTrayComposition.from(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherTrayComposition.from(settingsDefaults))

    /**
     * One-shot: true once the first-rotation hint has been shown, so it never repeats. The write goes
     * back through settings so it survives a process kill.
     *
     * S1680: a cold [Flow] on purpose, not a `StateFlow`. Its one reader decides by it - whether to show
     * a hint the user has already dismissed - and S1535 recorded the rule that a decision reads the
     * stored value, never a seed. A `StateFlow` handed out `false` for the ~100 ms before DataStore
     * answered, so a rotation inside that window showed the hint a second time.
     */
    val rotationHintShown: Flow<Boolean> = settingsRepository.getSettings()
        .map { it.launcherRotationHintShown }

    // S1741: launcher-private screen blackout timeout in seconds (0 = Off).
    val screenBlackoutTimeoutSeconds: StateFlow<Int> = settingsRepository.getSettings()
        .map { it.launcherScreenBlackoutTimeoutSeconds }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

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
     * Places a new cell at the square the user pointed at.
     *
     * S1772: a taken square is no longer a silent no-op - the repository pushes the desktop's tail down
     * to make room, and the only outcome the user still has to be told about is a footprint wider than
     * the grid, which nothing can seat.
     */
    fun addCell(rowIndex: Int, colIndex: Int, draft: LauncherCellDraft, columns: Int) {
        viewModelScope.launch {
            rememberResourceFileList(draft.rememberFileListResourceId)
            val placement = desktopDependencies.desktopRepository.addCell(
                LauncherCell(
                    id = 0,
                    orientation = _orientation.value,
                    rowIndex = rowIndex,
                    colIndex = colIndex,
                    spanW = draft.spanW,
                    spanH = draft.spanH,
                    kind = draft.kind,
                    target = draft.target,
                    labelOverride = draft.labelOverride,
                    addedAt = System.currentTimeMillis(),
                ),
                columns = columns,
            )
            // Only TooWide reaches the user, and the message says what to do about it: every other
            // outcome now succeeds by pushing the desktop down, and a refused header is a duplicate the
            // user is never offered in the first place, so it stays in the log.
            if (placement is LauncherCellPlacement.TooWide) {
                _events.send(LauncherHomeEvent.Message(R.string.launcher_home_cell_too_wide))
            }
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
        // S1742: a user-created section carries its name from the moment it is placed - it has no preset
        // label to fall back on, so a header written without one would draw as unavailable.
        labelOverride: String? = null,
        // S2247: answers whether a slot was found, so a programmatic placement can speak its refusal.
        onPlaced: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            rememberResourceFileList(rememberFileListResourceId)
            val id = desktopDependencies.desktopRepository.addCellInFirstFreeSlot(
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
                    labelOverride = labelOverride,
                    addedAt = System.currentTimeMillis(),
                ),
                columns,
            )
            // S2033: the repository scans row-major and knows nothing about folding (strategic ADR-2), so
            // the first free square it finds can sit inside a collapsed section - where the render plan
            // drops the cell and the user is left with an item that was written and cannot be seen.
            id?.let { sections.revealSectionHolding(it) }
            Timber.d("S2033: addCellInFirstFreeSlot id=%s", id)
            onPlaced(id != null)
        }
    }

    val renameSection: (cellId: Long, newName: String) -> Unit = { cellId, newName ->
        viewModelScope.launch {
            desktopDependencies.desktopRepository.updateCellLabel(cellId, newName.trim())
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
            val cell = cells.value.find { it.cell.id == id }?.cell
            if (cell != null && cell.kind == LauncherCellKind.SECTION) {
                sections.clear(cell)
            }
            // S1930: a configured widget cell owns a stored instance, and the row is the only thing
            // that still knows its token - reading it after the delete would find nothing, and the
            // snapshot would sit in prefs forever with no cell pointing at it (strategic §11.4). The
            // clear runs here rather than in LauncherDesktopRepository because that repository lives
            // in src/main and knows nothing about gadgets (Rule 14).
            if (cell != null && cell.kind == LauncherCellKind.GADGET) {
                desktopDependencies.configuredWidgetInstances.clearInstanceOf(cell.target)
            }
            desktopDependencies.desktopRepository.removeCell(id)
        }
    }

    /**
     * S2301: moves the cell - or, for a section header, the whole group - onto another screen.
     *
     * [columns] is read at the moment of the tap for the same reason the app menu reads it there: the
     * column count belongs to the screen drawing the desktop, not to the stored desktop.
     */
    fun moveCellToScreen(cellId: Long, screenIndex: Int, columns: Int) {
        viewModelScope.launch {
            val moved = desktopDependencies.desktopRepository.moveCellToScreen(
                orientation = _orientation.value,
                cellId = cellId,
                screenIndex = screenIndex,
                columns = columns,
            )
            Timber.d("S2301: moveCellToScreen id=%s screen=%d moved=%s", cellId, screenIndex, moved)
        }
    }

    fun deleteSection(cellId: Long) {
        viewModelScope.launch {
            val header = cells.value.find { it.cell.id == cellId }?.cell
            val removed = desktopDependencies.desktopRepository.removeSection(_orientation.value, cellId)
            removed.forEach(desktopDependencies.configuredWidgetInstances::clearInstanceOf)
            header?.let(sections::clear)
            Timber.d("S2222: deleteSection id=%s removed=%d", cellId, removed.size)
        }
    }

    fun resortSection(cellId: Long, columns: Int) {
        viewModelScope.launch {
            val header = cells.value.find { it.cell.id == cellId }?.cell ?: return@launch
            sections.reveal(header)
            val moved = desktopDependencies.desktopRepository.resortSection(_orientation.value, cellId, columns)
            Timber.d("S2222: resortSection id=%s columns=%d moved=%s", cellId, columns, moved)
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

    /** Pins a recents entry through the same slot-allocation path as every other taskbar pin. */
    fun pinRecentToTaskbar(command: LauncherCellCommand) {
        addPin(command)
        viewModelScope.launch {
            _events.send(LauncherHomeEvent.Message(R.string.launcher_app_action_pinned))
        }
    }

    /** Hides the command from recents until it is launched again. */
    fun removeRecentCommand(command: LauncherCellCommand) {
        viewModelScope.launch {
            taskbarDependencies.removeRecentCommand(command)
        }
    }

    /**
     * S2210: publishes the frame a capture just wrote, so the desktop swaps to it.
     *
     * Stamped with the file's own mtime rather than a wall clock: every capture reuses one path, so the
     * timestamp is the only thing that distinguishes this frame from the previous one downstream of
     * `distinctUntilChanged`.
     */
    fun onInstantPhotoCaptured(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val capturedAtMillis = File(path).lastModified()
            instantPhotoFrame.value = InstantPhotoFrame(path, capturedAtMillis)
        }
    }

    /**
     * S2213: remembers the place the user just picked, outside the desktop cell that holds it, so a
     * launcher reset cannot carry it away with the cell it clears.
     */
    fun rememberWeatherLocation(encoded: String) {
        viewModelScope.launch {
            Timber.d("S2213: weather place remembered outside the desktop cell")
            settingsRepository.updateSettings { it.withLauncher { copy(weatherLastLocation = encoded) } }
        }
    }

    /** Remembers that the first-rotation hint has been shown, so it never appears again. */
    fun markRotationHintShown() {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.withLauncher { copy(rotationHintShown = true) } }
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
    /**
     * @param screenOnly S1767: for a status indicator, whose tap must open a settings section rather
     * than toggle the radio the shared OsShortcut path would try first (ADR-1). A flag on this funnel
     * rather than a second entry point, so the launch guard, the journal and the "cannot open" message
     * keep covering every surface exactly once.
     */
    fun run(command: LauncherCellCommand, screenOnly: Boolean = false) {
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
                    if (!executeCommand.launch(command, screenOnly)) {
                        // Nothing opened, so nothing will bring the user back: re-arm now or the cell
                        // would stay dead until the next resume.
                        launchInFlight = false
                        _events.send(LauncherHomeEvent.Message(R.string.launcher_home_cannot_open))
                    }
                }
            }
        }
    }

    /**
     * S2025: opens our Monitor section when available, else the Android settings screen the indicator reports on.
     */
    fun openNetworkSurface(sectionKey: String, osShortcutKey: String) {
        viewModelScope.launch {
            val availability = resolveRouteAvailability(InternalRouteCatalog.KEY_NETWORK_MONITOR)
            val command = if (availability.isLaunchable) {
                LauncherCellCommand.FeatureSection(InternalRouteCatalog.KEY_NETWORK_MONITOR, sectionKey)
            } else {
                LauncherCellCommand.OsShortcut(osShortcutKey)
            }
            val screenOnly = !availability.isLaunchable
            run(command, screenOnly)
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
    // S1832: [cellKey] is the channel's identity, or a row id for a cell written before that ticket.
    // Matched in that order for the same reason the repository resolves it that way - the long-press
    // menu must open on the channel the cell's tap would play, not on a different one.
    suspend fun streamById(cellKey: String): StreamSourceEntity? {
        val sources = observeStreams().first()
        return sources.firstOrNull { it.identityKey == cellKey }
            ?: sources.firstOrNull { it.id == cellKey }
    }

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
            Timber.d(
                "S2320: seeding desktop density=%s portraitColumns=%s landscapeColumns=%s",
                density,
                portraitColumns,
                landscapeColumns,
            )
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

    /**
     * S2060: the add-flow's chosen target square, durable across process death via [SavedStateHandle].
     * [LauncherAddFlowManager] held this as two plain fields and lost them whenever the OS killed the
     * process mid-flow (a system contact/app/resource picker), landing the next cell at `(0, 0)`
     * instead of the square the user tapped.
     *
     * One property pairing both coordinates rather than two, so a partial write can never pair a
     * fresh row with a stale column - the manager reads and writes them together for the same reason.
     * A property rather than a named function, like [recentsCapacity] above: this class sits exactly
     * at detekt's `TooManyFunctions` ceiling.
     */
    var pendingSlot: Pair<Int, Int>
        get() = (savedStateHandle[KEY_PENDING_ROW] ?: 0) to (savedStateHandle[KEY_PENDING_COL] ?: 0)
        set(value) {
            savedStateHandle[KEY_PENDING_ROW] = value.first
            savedStateHandle[KEY_PENDING_COL] = value.second
        }

    /**
     * S2099: which contact action the in-flight pick is for, durable across process death via
     * [SavedStateHandle] for the same reason as [pendingSlot] above. `LauncherContactPickManager` held
     * this as a plain field, but that manager is rebuilt by the Activity's field initialiser on every
     * process restart - so a kill while the system contact or number picker was in front lost the
     * action, and the restored result aborted on a toast instead of placing the cell.
     *
     * Stored by enum name and read back by matching the entries, so a value this build no longer knows
     * reads as "no pick in flight" rather than throwing on a restored state bundle. A property rather
     * than a named function, like [pendingSlot] and [recentsCapacity]: this class sits exactly at
     * detekt's `TooManyFunctions` ceiling.
     */
    var pendingContactAction: LauncherContactAction?
        get() {
            val stored = savedStateHandle.get<String>(KEY_PENDING_CONTACT_ACTION)
            return LauncherContactAction.entries.firstOrNull { it.name == stored }
        }
        set(value) {
            savedStateHandle[KEY_PENDING_CONTACT_ACTION] = value?.name
        }

    /**
     * S2102: the contact action of the step the branch is on **before** the system picker takes over -
     * the pending `READ_CONTACTS` answer, the number-source picker, and the manual number dialog.
     *
     * Distinct from [pendingContactAction] above rather than merged into it, because the two have
     * different lifetimes and the merge would silently re-open S2099: that one is written when the
     * system picker launches and cleared when its result arrives, while this one is written on entry to
     * the contact branch and handed over the moment the system picker starts. They never overlap -
     * `launchSystemPicker` clears this as it writes that.
     *
     * `LauncherContactPickManager` used to hold both the action and a `pendingPick` closure in fields,
     * and that manager is rebuilt by the Activity's field initialiser on every process restart, so the
     * contacts answer arrived at a manager with nothing to resume and the flow ended without a word.
     *
     * Stored by enum name and read back by matching the entries, like [pendingContactAction], so a value
     * this build no longer knows reads as "no step in flight" rather than throwing on a restored bundle.
     * A property rather than a named function, like the three above: this class sits exactly at detekt's
     * `TooManyFunctions` ceiling.
     */
    var pendingContactStep: LauncherContactAction?
        get() {
            val stored = savedStateHandle.get<String>(KEY_PENDING_CONTACT_STEP)
            return LauncherContactAction.entries.firstOrNull { it.name == stored }
        }
        set(value) {
            savedStateHandle[KEY_PENDING_CONTACT_STEP] = value?.name
        }

    /**
     * S2102: the messaging channels the user is choosing between, durable across process death.
     *
     * The list is produced by a contacts query the restored process never repeats, and the picker
     * showing it cannot restore itself - `SearchableOptionPickerDialog` keeps its options in a plain
     * field because an option may carry a `Drawable`, so a restored instance closes itself in `onStart`.
     * Both re-presenting the choice and mapping the answer back to a placeable target therefore depend
     * on this value surviving.
     *
     * Stored as two parallel string lists rather than one joined list: the label is whatever the
     * messaging app wrote on the contact's row and may contain anything at all, including the codec's
     * separator, so joining it to the encoded target would need a second escaping layer over one that
     * already percent-encodes every field (strategic ADR-3). The target itself rides in
     * [LauncherCellCommand.Contact]'s existing cell encoding, which keeps the domain model a plain data
     * class - a `@Parcelize` on it would drag an Android type into `domain/`.
     *
     * Reads back as null unless the whole list is intact: a half-decodable list would offer the user
     * rows that cannot be placed, which is the failure this ticket exists to remove rather than reshape.
     * The app icon beside each row is deliberately absent - it is re-fetched from `PackageManager` by
     * the stored `messagePackage`, exactly as the first showing did.
     */
    var pendingContactChannels: List<LauncherContactChannel>?
        get() = decodePendingChannels(
            savedStateHandle.get<ArrayList<String>>(KEY_PENDING_CHANNEL_LABELS),
            savedStateHandle.get<ArrayList<String>>(KEY_PENDING_CHANNEL_TARGETS),
        )
        set(value) {
            savedStateHandle[KEY_PENDING_CHANNEL_LABELS] =
                value?.mapTo(ArrayList()) { it.label }
            savedStateHandle[KEY_PENDING_CHANNEL_TARGETS] =
                value?.mapTo(ArrayList()) { LauncherCellCommand.Contact(it.target).encode() }
        }

    /**
     * S1930: the gadget key and minted token of the configurable widget whose configuration screen is
     * in front, or null when none is. In [SavedStateHandle] for the same reason as the two above -
     * that screen is a separate Activity, which is precisely when the OS is free to kill this one, and
     * a lost token would leave a written snapshot no cell ever points at.
     *
     * One property carrying both halves, like [pendingSlot]: a token without its key names no widget
     * to place, and a key without its token names no instance to place it with.
     */
    var pendingConfiguredWidget: Pair<String, Int>?
        get() {
            val key = savedStateHandle.get<String>(KEY_PENDING_WIDGET_KEY)
            val token = savedStateHandle.get<Int>(KEY_PENDING_WIDGET_TOKEN)
            return if (key != null && token != null) key to token else null
        }
        set(value) {
            savedStateHandle[KEY_PENDING_WIDGET_KEY] = value?.first
            savedStateHandle[KEY_PENDING_WIDGET_TOKEN] = value?.second
        }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        /** As many recents as fit a phone taskbar beside the Start button and the tray. */
        const val RECENTS_LIMIT = 6

        const val KEY_PENDING_ROW = "launcher_pending_row"
        const val KEY_PENDING_COL = "launcher_pending_col"
        const val KEY_PENDING_CONTACT_ACTION = "launcher_pending_contact_action"
        const val KEY_PENDING_CONTACT_STEP = "launcher_pending_contact_step"
        const val KEY_PENDING_CHANNEL_LABELS = "launcher_pending_channel_labels"
        const val KEY_PENDING_CHANNEL_TARGETS = "launcher_pending_channel_targets"
        const val KEY_PENDING_WIDGET_KEY = "launcher_pending_widget_key"
        const val KEY_PENDING_WIDGET_TOKEN = "launcher_pending_widget_token"
    }
}

/**
 * S2102: rebuilds what [LauncherHomeViewModel.pendingContactChannels] stored, or null when what came
 * back cannot be trusted as a whole.
 *
 * Top-level rather than a member, so the round-trip is testable without constructing the ViewModel and
 * every collaborator it injects, and so the class stays under detekt's `TooManyFunctions` ceiling.
 *
 * Every rejection is the same rejection. A caller handed a short list would present rows that place
 * nothing - the exact failure this ticket removes - so a missing key, a length mismatch and one
 * undecodable entry all yield null rather than a best effort. An empty list is nothing to choose
 * between, so it reads as "no channel step in flight" too.
 */
internal fun decodePendingChannels(
    labels: List<String>?,
    encodedTargets: List<String>?,
): List<LauncherContactChannel>? {
    if (labels == null || encodedTargets == null || labels.size != encodedTargets.size) return null
    val decoded = encodedTargets.map { LauncherCellCommand.decode(it) as? LauncherCellCommand.Contact }
    val complete = decoded.filterNotNull()
    return if (complete.isEmpty() || complete.size != decoded.size) {
        null
    } else {
        complete.mapIndexed { index, command ->
            LauncherContactChannel(target = command.target, label = labels[index])
        }
    }
}

/**
 * Pure setting-to-render-model mapping; image and camera availability are supplied because probing either
 * one is I/O - a disk stat for the image, a package-manager and permission read for the camera.
 */
internal fun resolveLauncherWallpaper(
    mode: String,
    imagePath: String,
    imageAvailable: Boolean,
    cameraId: String,
    cameraAvailable: Boolean,
    instantPhoto: InstantPhotoFrame? = null,
): LauncherWallpaper = when (mode) {
    AppSettings.LAUNCHER_WALLPAPER_NONE -> LauncherWallpaper.None
    AppSettings.LAUNCHER_WALLPAPER_STATIC_STRIPES -> LauncherWallpaper.StaticStripes
    AppSettings.LAUNCHER_WALLPAPER_IMAGE ->
        imagePath.takeIf { imageAvailable }?.let { LauncherWallpaper.Image(it) } ?: LauncherWallpaper.Branded

    // S2076: a revoked grant, a camera-less device or a lens that vanished all land here, and all degrade
    // to the branded backdrop rather than to a black layer nobody can explain.
    AppSettings.LAUNCHER_WALLPAPER_CAMERA ->
        cameraId.takeIf { cameraAvailable && it.isNotBlank() }
            ?.let { LauncherWallpaper.LiveCamera(it) }
            ?: LauncherWallpaper.Branded

    // S2210: the frame comes from this session's capture, never from the stored image path - that path is
    // the user's own wallpaper pick, and reading it here would show their picture as if the camera had
    // just taken it.
    AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO ->
        cameraId.takeIf { cameraAvailable && it.isNotBlank() }
            ?.let {
                LauncherWallpaper.InstantPhoto(
                    cameraId = it,
                    imagePath = instantPhoto?.path,
                    capturedAtMillis = instantPhoto?.capturedAtMillis ?: 0L,
                )
            }
            ?: LauncherWallpaper.Branded

    else -> LauncherWallpaper.Branded
}

/** S2210: one captured instant-photo frame - its file and the mtime that tells it from the previous one. */
internal data class InstantPhotoFrame(val path: String, val capturedAtMillis: Long)
