package com.sza.fastmediasorter.ui.launcher

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.launcher.ExecuteLauncherCommandUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.QueryRecentLauncherCommandsUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherCommandLabelUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.ResolveLauncherDesktopUseCase
import com.sza.fastmediasorter.domain.usecase.ExecuteScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.SeedLauncherDesktopUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarComposition
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarIcon
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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
}

@HiltViewModel
class LauncherHomeViewModel @Inject constructor(
    private val resolveDesktop: ResolveLauncherDesktopUseCase,
    private val executeCommand: ExecuteLauncherCommandUseCase,
    private val desktopRepository: LauncherDesktopRepository,
    private val resourceRepository: ResourceRepository,
    private val resolveVisual: ResolveLauncherCommandLabelUseCase,
    private val pinsRepository: LauncherPinsRepository,
    queryRecentCommands: QueryRecentLauncherCommandsUseCase,
    private val settingsRepository: SettingsRepository,
    private val seedLauncherDesktop: SeedLauncherDesktopUseCase,
    private val observeStreams: ObserveStreamSourcesUseCase,
    private val executeScheduledOperation: ExecuteScheduledOperationUseCase,
) : ViewModel() {

    // Rotation swaps which layout is observed. The collection itself is never torn down: the
    // orientation is an input to the stream, not a reason to restart it.
    private val _orientation = MutableStateFlow(LauncherOrientation.PORTRAIT)

    @OptIn(ExperimentalCoroutinesApi::class)
    val cells: StateFlow<List<LauncherCellUi>> = _orientation
        .flatMapLatest { resolveDesktop(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    val densityFactor: StateFlow<Float> = settingsRepository.getSettings()
        .map { it.launcherDensityFactor }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_DENSITY_FACTOR)

    val taskbarComposition: Flow<LauncherTaskbarComposition> = settingsRepository.getSettings()
        .map {
            LauncherTaskbarComposition(
                showRecents = it.launcherTaskbarShowRecents,
                showPinned = it.launcherTaskbarShowPinned,
                showTray = it.launcherTaskbarShowTray,
            )
        }
        .distinctUntilChanged()

    val recentIcons: Flow<List<LauncherTaskbarIcon>> = queryRecentCommands(RECENTS_LIMIT)
        .map { entries ->
            entries.map { entry ->
                LauncherTaskbarIcon(
                    // The id is the encoded command (like a pin), so a recents tap decodes and reruns
                    // the exact command - not a bare package name.
                    id = entry.command.encode(),
                    label = entry.visual.label,
                    iconRes = entry.visual.iconRes,
                    iconDrawable = entry.visual.iconDrawable,
                )
            }
        }
        .onEach { Timber.d("S1097: recents strip -> %d item(s)", it.size) }

    val pinnedIcons: Flow<List<LauncherTaskbarIcon>> = pinsRepository.observePins()
        .map { pins ->
            pins.mapNotNull { (position, command) ->
                resolveVisual(command)?.let { visual ->
                    LauncherTaskbarIcon(
                        id = command.encode(),
                        label = visual.label,
                        iconRes = visual.iconRes,
                        iconDrawable = visual.iconDrawable,
                        // Carried so edit mode can unpin the exact slot; the id is the command, not the slot.
                        position = position,
                    )
                }
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * Editing is an explicit mode the user enters and leaves (owner quiz 2026-07-17 - long-press was
     * rejected: it collides with interactive gadgets and a remote has no long-press).
     */
    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode

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
            desktopRepository.addCell(
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
     * ADR-10: a resource-backed gadget draws a live file list on the desktop, so its resource must keep
     * that list across restarts - a network resource otherwise reads "Unavailable" after every reboot.
     * The write lives here, not in the picker, so this data mutation is visible to every reader of the
     * add-flow instead of hidden behind a dialog dismissal.
     */
    private suspend fun rememberResourceFileList(resourceId: Long?) {
        val id = resourceId ?: return
        val resource = resourceRepository.getResourceById(id) ?: return
        if (!resource.rememberFileList) {
            resourceRepository.updateResource(resource.copy(rememberFileList = true))
        }
    }

    /** Rejected when the footprint is not free; the cell simply snaps back where it was. */
    fun moveCell(id: Long, rowIndex: Int, colIndex: Int) {
        viewModelScope.launch {
            desktopRepository.moveCell(id, rowIndex, colIndex)
        }
    }

    /** Rejected when the new footprint overlaps another cell; the gesture keeps the last valid size. */
    fun resizeCell(id: Long, spanW: Int, spanH: Int) {
        viewModelScope.launch {
            desktopRepository.resizeCell(id, spanW, spanH)
        }
    }

    fun removeCell(id: Long) {
        viewModelScope.launch {
            desktopRepository.removeCell(id)
        }
    }

    /**
     * Pins a command to the first free slot. The position is a stable key, not a visual order, so the
     * lowest unused integer is enough - a gap an unpin left behind is simply reused rather than growing
     * the strip forever.
     */
    fun addPin(command: LauncherCellCommand) {
        viewModelScope.launch {
            val used = pinsRepository.observePins().first().map { it.first }.toSet()
            var position = 0
            while (position in used) position++
            pinsRepository.setPin(position, command)
        }
    }

    fun removePin(position: Int) {
        viewModelScope.launch {
            pinsRepository.removePin(position)
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
            // A scheduled op may modify or delete files, so confirm before running (S1103).
            is LauncherCellCommand.ScheduledOp ->
                viewModelScope.launch {
                    _events.send(LauncherHomeEvent.ConfirmScheduledOp(command.operationId))
                }
            else -> run(command)
        }
    }

    /** Runs a scheduled op after the user confirmed it: background, with a start then result toast. */
    fun executeScheduledOp(operationId: Long) {
        viewModelScope.launch {
            Timber.d("S1103: executeScheduledOp $operationId")
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

    /** Shared by the desktop, the taskbar strips and the Start menu - one guard for all of them. */
    fun run(command: LauncherCellCommand) {
        if (launchInFlight) return
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
            Timber.d("S1092: requestStreamCell hasChannels=$hasChannels")
            _events.send(
                if (hasChannels) LauncherHomeEvent.OpenStreamPicker else LauncherHomeEvent.OpenStreamsSettings,
            )
        }
    }

    /** Records the grid width the surface actually resolved, so seeding and edit mode agree with it. */
    fun persistColumns(orientation: LauncherOrientation, columns: Int) {
        viewModelScope.launch {
            desktopRepository.updateColumns(orientation, columns)
        }
    }

    // Runs once per process; the repository's seedIfEmpty also guards, so a real desktop is never
    // overwritten. Columns are resolved INSIDE the coroutine from the real persisted density
    // (getSettings().first()), not densityFactor.value - that StateFlow's initial default is still in
    // place at seed time until the async DataStore read lands, and seeding is one-shot, so a wrong
    // column count would misplace/overlap cells permanently (audit 2026-07-17, P1).
    private var seedTriggered = false

    /** First entry only: seed an empty desktop with the profile's starter set (ADR-4). */
    fun seedDesktopIfNeeded(widthDp: Float, heightDp: Float, startedPortrait: Boolean) {
        if (seedTriggered) return
        seedTriggered = true
        viewModelScope.launch {
            val density = settingsRepository.getSettings().first().launcherDensityFactor
            val widthColumns = LauncherGridGeometry.columns(widthDp, density)
            val heightColumns = LauncherGridGeometry.columns(heightDp, density)
            val portraitColumns = if (startedPortrait) widthColumns else heightColumns
            val landscapeColumns = if (startedPortrait) heightColumns else widthColumns
            seedLauncherDesktop(portraitColumns, landscapeColumns)
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        const val DEFAULT_DENSITY_FACTOR = 1.0f

        /** As many recents as fit a phone taskbar beside the Start button and the tray. */
        const val RECENTS_LIMIT = 6
    }
}
