package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.CreateTextNoteUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.create.BrowseCreateEntityCommand
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages the create-text-note action for the Browse screen.
 *
 * S0189 Phase 09: implements the shared [BrowseCreateEntityCommand] pattern so the toolbar
 * registrar can host this command alongside future entity commands (drawings - S0191).
 *
 * Responsibilities:
 * - Call [CreateTextNoteUseCase] with the current resource and the target parent path.
 * - Emit user-visible error events; success path is deferred-create (no toast, no list reload).
 * - Notify the editor-open hook ([notifyCreatedForOpen]) after successful creation.
 *
 * Modelled on [BrowseDirectoryOpsManager]. Wired by [BrowseManagerInitializer].
 */
class BrowseTextNoteCreateManager(
    private val context: Context,
    private val createTextNoteUseCase: CreateTextNoteUseCase,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val reloadResource: () -> Unit,
    /** Called on Main thread after a note is successfully created. Phase 04 wires editor launch here. */
    private val notifyCreatedForOpen: (createdPath: String) -> Unit
) : BrowseCreateEntityCommand {

    /**
     * Text notes are offered for every resource the Browse screen can show; deeper
     * writability checks live inside [CreateTextNoteUseCase] (S0189 §6.2).
     */
    override fun isAvailableFor(resource: MediaResource): Boolean = true

    /**
     * Shared-pattern entry point: equivalent to [createTextNote] but with explicit
     * parent path (used by the future toolbar registrar; the existing call-site
     * [createTextNote] keeps its in-state resolution for backward compatibility).
     */
    override fun requestCreate(parentPath: String, name: String) {
        scope.launch(ioDispatcher) {
            val resource = stateFlow.value.resource ?: return@launch
            dispatchCreate(resource, parentPath, name)
        }
    }

    /**
     * Create a new text note named [name] inside the current Browse path.
     * Resolves the parent path from [stateFlow] (current path or resource root).
     * Runs on [ioDispatcher]; success/error and open-notification run on Main.
     */
    fun createTextNote(name: String) {
        scope.launch(ioDispatcher) {
            val resource = stateFlow.value.resource ?: return@launch
            val currentPath = stateFlow.value.currentPath ?: resource.path
            dispatchCreate(resource, currentPath, name)
        }
    }

    private suspend fun dispatchCreate(resource: MediaResource, parentPath: String, name: String) {
        val result = createTextNoteUseCase(resource, parentPath, name)

        withContext(Dispatchers.Main) {
            result.onSuccess { createdPath ->
                // S0189: defer-creation contract - no file on disk yet, so no list reload
                // and no "created" toast. Both fire on actual Save (TextEditorSaveFlow).
                notifyCreatedForOpen(createdPath)
            }.onFailure { error ->
                Timber.e(error, "BrowseTextNoteCreateManager.createTextNote: FAILED name=$name")
                sendEvent(BrowseEvent.ShowError(
                    message = context.getString(R.string.error_text_note_create_failed)
                ))
            }
        }
    }
}
