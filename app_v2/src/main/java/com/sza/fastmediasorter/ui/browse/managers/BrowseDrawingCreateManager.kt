package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.CreateDrawingUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.create.BrowseCreateEntityCommand
import com.sza.fastmediasorter.util.DrawingTargetPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S0191 Browse-side command owner for blank drawing creation.
 *
 * The heavy work lives in [CreateDrawingUseCase]; this manager only resolves the current
 * Browse path, dispatches the IO call, and opens PlayerActivity in draw mode on success.
 */
class BrowseDrawingCreateManager(
    private val context: Context,
    private val createDrawingUseCase: CreateDrawingUseCase,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateFlow: StateFlow<BrowseState>,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val notifyCreatedForOpen: (createdPath: String) -> Unit,
) : BrowseCreateEntityCommand {

    override fun isAvailableFor(resource: MediaResource): Boolean = DrawingTargetPolicy.canCreateDrawing(resource)

    override fun requestCreate(parentPath: String, name: String) {
        scope.launch(ioDispatcher) {
            val resource = stateFlow.value.resource ?: return@launch
            dispatchCreate(resource, parentPath, name)
        }
    }

    fun createDrawing(name: String) {
        scope.launch(ioDispatcher) {
            val resource = stateFlow.value.resource ?: return@launch
            val currentPath = stateFlow.value.currentPath ?: resource.path
            dispatchCreate(resource, currentPath, name)
        }
    }

    private suspend fun dispatchCreate(resource: MediaResource, parentPath: String, name: String) {
        val result = createDrawingUseCase(resource, parentPath, name)

        withContext(Dispatchers.Main) {
            result.onSuccess { createdPath ->
                notifyCreatedForOpen(createdPath)
            }.onFailure { error ->
                Timber.e(error, "BrowseDrawingCreateManager.createDrawing: FAILED name=%s", name)
                sendEvent(BrowseEvent.ShowError(
                    message = context.getString(R.string.error_drawing_create_failed)
                ))
            }
        }
    }
}