package com.sza.fastmediasorter.ui.keybinding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.BindingSource
import com.sza.fastmediasorter.domain.input.CommandGroup
import com.sza.fastmediasorter.domain.input.InputBinding
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.input.usecase.DetectConflictsUseCase
import com.sza.fastmediasorter.domain.input.usecase.ResetAllUseCase
import com.sza.fastmediasorter.domain.input.usecase.ResetBindingUseCase
import com.sza.fastmediasorter.domain.input.usecase.ResetGroupUseCase
import com.sza.fastmediasorter.domain.input.usecase.SetBindingUseCase
import com.sza.fastmediasorter.ui.keybinding.helpers.KeybindingRowLabelFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class PendingConfirmation {
    data class GroupReset(val group: CommandGroup) : PendingConfirmation()
    object AllReset : PendingConfirmation()
}

data class RemapUiState(
    val rows: List<KeybindingRow> = emptyList(),
    val filter: String = "",
    val expandedGroups: Set<CommandGroup> = CommandGroup.values().toSet(),
    val pendingCapture: CaptureRequest? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val conflicts: Map<InputTrigger, List<String>> = emptyMap()
)

data class KeybindingRow(
    val commandId: String,
    val group: CommandGroup,
    val labelKey: String,      // pre-resolved localised label
    val bindings: Map<String, List<InputTrigger>>,  // device → triggers
    val hasOverride: Boolean,
    val conflictWith: List<String> = emptyList()
)

data class CaptureRequest(val commandId: String, val device: String, val slot: Int)

@HiltViewModel
class KeybindingRemapViewModel @Inject constructor(
    private val repo: InputBindingRepository,
    private val setBinding: SetBindingUseCase,
    private val resetBinding: ResetBindingUseCase,
    private val resetGroup: ResetGroupUseCase,
    private val resetAll: ResetAllUseCase,
    private val detectConflicts: DetectConflictsUseCase,
    private val formatter: KeybindingRowLabelFormatter
) : ViewModel() {

    private val allRows = MutableStateFlow<List<KeybindingRow>>(emptyList())
    private val _state = MutableStateFlow(RemapUiState())
    val state: StateFlow<RemapUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeResolvedBindings().collect { bindings ->
                val conflictMap = detectConflicts(bindings)
                allRows.value = buildRows(bindings, conflictMap)
                _state.update { it.copy(conflicts = conflictMap) }
                refreshState()
            }
        }
    }

    fun onFilterChanged(query: String) {
        _state.update { it.copy(filter = query) }
        refreshState()
    }

    fun onGroupToggle(group: CommandGroup) {
        val current = _state.value.expandedGroups.toMutableSet()
        if (group in current) current.remove(group) else current.add(group)
        _state.update { it.copy(expandedGroups = current) }
    }

    fun onRemapRequested(commandId: String, device: String, slot: Int) {
        _state.update { it.copy(pendingCapture = CaptureRequest(commandId, device, slot)) }
    }

    fun onCaptureCompleted(trigger: InputTrigger) {
        val capture = _state.value.pendingCapture ?: return
        Timber.d("Capture completed: commandId=%s device=%s trigger=%s", capture.commandId, capture.device, trigger.serialize())
        viewModelScope.launch {
            setBinding(capture.commandId, capture.device, capture.slot, trigger)
            _state.update { it.copy(pendingCapture = null) }
        }
    }

    fun onCaptureCancelled() {
        _state.update { it.copy(pendingCapture = null) }
    }

    fun onResetRowRequested(commandId: String) {
        viewModelScope.launch {
            resetBinding(commandId)
        }
    }

    fun onResetGroupRequested(group: CommandGroup) {
        _state.update { it.copy(pendingConfirmation = PendingConfirmation.GroupReset(group)) }
    }

    fun onResetAllRequested() {
        _state.update { it.copy(pendingConfirmation = PendingConfirmation.AllReset) }
    }

    fun onConfirmReset() {
        val pending = _state.value.pendingConfirmation ?: return
        _state.update { it.copy(pendingConfirmation = null) }
        viewModelScope.launch {
            when (pending) {
                is PendingConfirmation.GroupReset -> resetGroup(pending.group)
                PendingConfirmation.AllReset -> resetAll()
            }
        }
    }

    fun onCancelReset() {
        _state.update { it.copy(pendingConfirmation = null) }
    }

    // ----- private helpers -----

    private fun refreshState() {
        val filtered = applyFilter(allRows.value, _state.value.filter)
        _state.update { it.copy(rows = filtered) }
    }

    private fun applyFilter(rows: List<KeybindingRow>, query: String): List<KeybindingRow> {
        if (query.isBlank()) return rows
        val q = query.lowercase()
        return rows.filter { row ->
            row.labelKey.lowercase().contains(q) ||
                row.bindings.values.flatten().any { trigger ->
                    formatter.format(trigger).lowercase().contains(q)
                }
        }
    }

    private fun buildRows(bindings: List<InputBinding>, conflictMap: Map<InputTrigger, List<String>>): List<KeybindingRow> {
        val byCommand = bindings.groupBy { it.commandId }
        return byCommand.map { (commandId, list) ->
            val device2triggers = list
                .groupBy { deviceOf(it.trigger) }
                .mapValues { (_, items) -> items.map { it.trigger } }
            val myTriggers = list.map { it.trigger }
            val conflictingCommands = conflictMap.entries
                .filter { (trigger, _) -> trigger in myTriggers }
                .flatMap { (_, commands) -> commands.filter { it != commandId } }
                .distinct()
            KeybindingRow(
                commandId = commandId,
                group = commandGroupOf(commandId),
                labelKey = formatter.resolveCommandLabel(commandId),
                bindings = device2triggers,
                hasOverride = list.any { it.source == BindingSource.OVERRIDE },
                conflictWith = conflictingCommands
            )
        }.sortedWith(compareBy({ it.group.ordinal }, { it.commandId }))
    }

    private fun commandGroupOf(commandId: String): CommandGroup = when {
        commandId.startsWith("playback.") -> CommandGroup.PLAYBACK_CORE
        commandId.startsWith("navigation.") -> CommandGroup.NAVIGATION
        commandId.startsWith("view.") -> CommandGroup.VIEW_ZOOM
        commandId.startsWith("audio.") -> CommandGroup.AUDIO_SUBTITLES
        commandId.startsWith("system.") -> CommandGroup.SYSTEM_UI
        commandId.startsWith("sorting.") -> CommandGroup.SORTING_ACTIONS
        commandId.startsWith("vr.") -> CommandGroup.VR_ONLY
        else -> CommandGroup.SYSTEM_UI
    }

    private fun deviceOf(trigger: InputTrigger): String = when (trigger) {
        is InputTrigger.Key -> "keyboard"
        is InputTrigger.MouseButton -> "mouse"
        is InputTrigger.GamepadButton, is InputTrigger.GamepadAxis -> "gamepad"
        is InputTrigger.VrEvent -> "vr"
    }
}
