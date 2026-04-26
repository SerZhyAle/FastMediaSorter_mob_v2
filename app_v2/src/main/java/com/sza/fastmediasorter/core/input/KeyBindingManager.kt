package com.sza.fastmediasorter.core.input

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyBindingManager @Inject constructor(
    repository: InputBindingRepository,
    @ApplicationScope scope: CoroutineScope
) {

    @Volatile private var triggerToCommand: Map<InputTrigger, String> = emptyMap()
    @Volatile private var commandToTriggers: Map<String, List<InputTrigger>> = emptyMap()

    init {
        repository.observeResolvedBindings()
            .onEach { bindings ->
                triggerToCommand = bindings.associate { it.trigger to it.commandId }
                commandToTriggers = bindings.groupBy(
                    keySelector = { it.commandId },
                    valueTransform = { it.trigger }
                )
            }
            .launchIn(scope)
    }

    fun resolve(trigger: InputTrigger, surface: InputSurface): String? {
        return triggerToCommand[trigger]
    }

    fun resolveKeyAction(keyCode: Int, modifiers: Int, surface: InputSurface): String? {
        return resolve(InputTrigger.Key(keyCode, modifiers), surface)
    }
}
