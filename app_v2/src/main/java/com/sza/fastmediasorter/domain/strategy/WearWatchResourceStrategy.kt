package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceValidationResult
import timber.log.Timber

/**
 * S1976: the paired watch as an editable resource.
 *
 * The watch has no filesystem the phone can address - its path is the scheme token the scanner
 * recognises, not a location - so the fields that describe a directory tree are absent from the
 * schema entirely rather than merely disabled: a field the editor never renders cannot be typed
 * into, and a hand-edited path used to leave the resource unrecognisable to the scanner.
 */
class WearWatchResourceStrategy : ResourceStrategy {

    override fun validate(formData: ResourceFormData): ResourceValidationResult {
        val errors = mutableMapOf<ResourceFieldKey, ResourceErrorCode>()

        if (formData.name.isBlank()) {
            errors[ResourceFieldKey.NAME] = ResourceErrorCode.EMPTY
        }

        // The supported media types come from the bridge when the resource is created - they are a
        // fact about the watch, not a preference - so an empty set is never the editor's fault and
        // must not block a save the user cannot fix from this screen.

        return if (errors.isEmpty()) {
            ResourceValidationResult.valid()
        } else {
            ResourceValidationResult.invalid(fieldErrors = errors)
        }
    }

    override suspend fun testConnection(formData: ResourceFormData): ResourceConnectionTestResult {
        // Reachability here means the Data Layer bridge, which the scanner answers live; asserting
        // a failure from this screen would be a guess, so the strategy declines rather than fails.
        return ResourceConnectionTestResult(
            status = ResourceConnectionStatus.NOT_SUPPORTED,
            diagnosticMessage = "Watch reachability is answered by the Wear bridge, not by the editor"
        )
    }

    override fun normalizeBeforeSave(formData: ResourceFormData): ResourceFormData {
        // The path is restored unconditionally, not merely left alone: whatever a stale form or a
        // future editor change puts there, the saved resource keeps the token the scanner matches.
        return formData.copy(
            name = formData.name.trim(),
            path = WATCH_RESOURCE_PATH,
            host = "",
            username = "",
            password = "",
            port = null
        )
    }

    override fun fieldSchema(): List<ResourceFieldSchema> {
        Timber.d("S1976: watch resource editor schema requested - name, destination, slideshow only")
        return listOf(
            ResourceFieldSchema(ResourceFieldKey.NAME, required = true),
            ResourceFieldSchema(ResourceFieldKey.IS_DESTINATION, required = false),
            ResourceFieldSchema(ResourceFieldKey.SLIDESHOW_INTERVAL, required = false)
        )
    }

    companion object {
        /**
         * Must stay identical to the value AddResourceWatchCoordinator writes when the resource is
         * created - the scanner matches this token, and a resource saved with anything else stops
         * being the watch.
         */
        const val WATCH_RESOURCE_PATH = "wear://watch"
    }
}
