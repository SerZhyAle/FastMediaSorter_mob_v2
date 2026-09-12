package com.sza.fastmediasorter.ui.settings.helpers

import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S2797: renders the destination-resource label of a settings row, one render pass at a time.
 *
 * A set destination shows the resource name, which only a suspend lookup can supply, while a
 * cleared one is written straight away. Settings emissions are not serialised against those
 * lookups: [com.sza.fastmediasorter.ui.settings.SettingsViewModel] drops its optimistic override as
 * soon as the write returns, so for one emission the flow republishes the value that was stored
 * before it (S2800). The lookup started on that emission resumes after the cleared render and
 * overwrites it, leaving the screen naming a destination the settings no longer point at.
 *
 * [cancelPending] closes that window: a lookup still in flight from the previous pass is cancelled
 * before the current one starts, so it never reaches its [render] setter and the last write always
 * belongs to the newest settings value.
 */
class DestinationLabelResolver(
    private val scopeProvider: () -> CoroutineScope,
    private val resourceRepository: ResourceRepository,
) {

    private val pending = mutableListOf<Job>()

    /** Call once at the start of a settings render pass, before every [render] call in it. */
    fun cancelPending() {
        pending.forEach { it.cancel() }
        pending.clear()
    }

    /**
     * @param unsetLabel shown when no destination is stored.
     * @param missingLabel shown when the stored id no longer resolves to a resource.
     */
    fun render(
        resourceId: Long?,
        unsetLabel: CharSequence,
        missingLabel: CharSequence = unsetLabel,
        setLabel: (CharSequence) -> Unit,
    ) {
        Timber.d("S2797: destination label render id=$resourceId inFlight=${pending.size}")
        if (resourceId == null) {
            setLabel(unsetLabel)
            return
        }
        pending += scopeProvider().launch {
            val resource = resourceRepository.getResourceById(resourceId)
            setLabel(resource?.name ?: missingLabel)
        }
    }
}
