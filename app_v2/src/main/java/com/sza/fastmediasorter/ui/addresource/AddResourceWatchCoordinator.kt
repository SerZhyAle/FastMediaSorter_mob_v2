package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.EnsureWatchResourceUseCase
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1861: adds the paired watch as a resource.
 *
 * The shortest of the coordinators on purpose: a watch has no address and no credentials, so the
 * wizard asks for a name and nothing else. There is exactly one paired watch, so a second resource
 * would point at the same device - an existing one is reported rather than duplicated.
 *
 * S2034: the row's field values moved to [EnsureWatchResourceUseCase], because the companion window
 * now creates the same row from its own button and two builders would drift.
 */
internal class AddResourceWatchCoordinator(
    private val context: Context,
    private val ensureWatchResourceUseCase: EnsureWatchResourceUseCase,
    private val bridge: AddResourceBridge
) {

    fun addPairedWatch(name: String) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)
            try {
                emitOutcome(name)
            } finally {
                bridge.markLoading(false)
            }
        }
    }

    private suspend fun emitOutcome(name: String) {
        val resourceName = name.ifBlank { context.getString(R.string.resource_type_wear_watch) }
        val outcome = ensureWatchResourceUseCase(resourceName).getOrElse { e ->
            Timber.w(e, "Failed to insert the paired watch resource")
            bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.friendly_copy_error_generic)))
            return
        }
        if (!outcome.created) {
            bridge.emit(AddResourceEvent.ShowMessage(context.getString(R.string.paired_watch_already_added)))
            return
        }
        bridge.emit(
            AddResourceEvent.ShowMessage(
                context.getString(R.string.paired_watch_resource_added, resourceName)
            )
        )
        bridge.emit(AddResourceEvent.ResourcesAdded(listOf(outcome.resourceId)))
    }
}
