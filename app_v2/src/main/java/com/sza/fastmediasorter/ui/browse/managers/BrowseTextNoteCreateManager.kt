package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.CreateTextNoteUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
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
 * Responsibilities:
 * - Call [CreateTextNoteUseCase] with the current resource and path.
 * - Emit user-visible success/error events and trigger a resource list reload.
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
) {
    /**
     * Create a new text note named [name] inside the current Browse path.
     * Runs on [ioDispatcher]; success/error and open-notification run on Main.
     */
    fun createTextNote(name: String) {
        scope.launch(ioDispatcher) {
            val resource = stateFlow.value.resource ?: return@launch
            val currentPath = stateFlow.value.currentPath ?: resource.path

            Timber.d("S0189: BrowseTextNoteCreateManager.createTextNote name=$name path=$currentPath")

            val result = createTextNoteUseCase(resource, currentPath, name)

            withContext(Dispatchers.Main) {
                result.onSuccess { createdPath ->
                    // S0189: defer-creation contract — no file on disk yet, so no list reload
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
}
