package com.sza.fastmediasorter.ui.addresource.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.icon.ResourceIconComposer
import com.sza.fastmediasorter.widget.ResourceShortcutPinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1423: pins one home-screen shortcut per just-created resource. Reached only from the success
 * branch of the Add Resource flow, so a cancelled or failed creation arrives with nothing to pin.
 *
 * Pinning itself stays in [ResourceShortcutPinManager] unchanged - this class only resolves the rows
 * and folds the outcomes into the single message the caller shows.
 */
@Singleton
class CreatedResourcePinManager @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val shortcutPinManager: ResourceShortcutPinManager
) {

    /**
     * Requests a pinned shortcut for every id in [resourceIds]; returns the message res the caller
     * must show, or null when every pin was accepted for request.
     *
     * [context] must be the visible Activity rather than the application: the composed icon resolves
     * theme attributes, and an application context would resolve them against the wrong theme.
     */
    @StringRes
    suspend fun pinCreatedResources(context: Context, resourceIds: List<Long>): Int? {
        var anyUnsupported = false
        resourceIds.forEach { id ->
            val resource = withContext(Dispatchers.IO) { resourceRepository.getResourceById(id) }
            if (resource != null) {
                val icon = ResourceIconComposer.compose(context, resource)
                val result = shortcutPinManager.requestPin(resource.id, resource.name, icon)
                if (result == ResourceShortcutPinManager.PinResult.Unsupported) {
                    anyUnsupported = true
                }
            }
        }
        return if (anyUnsupported) R.string.resource_shortcut_unsupported else null
    }
}
