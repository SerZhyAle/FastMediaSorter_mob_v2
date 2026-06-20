package com.sza.fastmediasorter.core.input

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyBindingManager @Inject constructor(
    repository: InputBindingRepository,
    @ApplicationScope scope: CoroutineScope
) {

    @Volatile private var triggerToCommands: Map<InputTrigger, List<String>> = emptyMap()
    @Volatile private var commandToTriggers: Map<String, List<InputTrigger>> = emptyMap()

    init {
        repository.observeResolvedBindings()
            .onEach { bindings ->
                triggerToCommands = bindings.groupBy(
                    keySelector = { it.trigger },
                    valueTransform = { it.commandId }
                )
                commandToTriggers = bindings.groupBy(
                    keySelector = { it.commandId },
                    valueTransform = { it.trigger }
                )
            }
            .launchIn(scope)
    }

    /**
     * Resolves a trigger to a commandId for the given [surface].
     *
     * A trigger can carry more than one commandId when defaults bind the same
     * physical input to commands of different surfaces (gamepad A is
     * playback.pause_play in the player, browser.select in the browser).
     * Single-candidate triggers return immediately - identical to the flat-map
     * behaviour for every existing player / keyboard / mouse path.
     */
    fun resolve(trigger: InputTrigger, surface: InputSurface): String? {
        val candidates = triggerToCommands[trigger] ?: return null
        if (candidates.size == 1) return candidates.first()
        return candidates.firstOrNull { surfaceOf(it) == surface } ?: candidates.first()
    }

    fun resolveKeyAction(keyCode: Int, modifiers: Int, surface: InputSurface): String? {
        return resolve(InputTrigger.Key(keyCode, modifiers), surface)
    }

    /** Triggers currently bound to [commandId] (default + overrides). Used to label slot shortcuts with their real key. */
    fun triggersFor(commandId: String): List<InputTrigger> = commandToTriggers[commandId] ?: emptyList()

    // Surface a commandId belongs to, inferred from its namespace prefix:
    // browser.* is browser-only, vr.* is VR-only, everything else is player-surface.
    // Used only to disambiguate a trigger shared across surfaces.
    private fun surfaceOf(commandId: String): InputSurface = when {
        commandId.startsWith("browser.") -> InputSurface.BROWSER
        commandId.startsWith("vr.") -> InputSurface.VR
        else -> InputSurface.PLAYER
    }
}
