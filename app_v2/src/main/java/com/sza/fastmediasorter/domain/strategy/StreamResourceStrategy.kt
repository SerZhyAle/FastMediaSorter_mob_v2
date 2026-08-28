package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceValidationResult

/**
 * S2041: HTTP_STREAM and RTSP_STREAM as an editable resource.
 *
 * A stream is one address, not a tree. MediaScannerFactory throws for both types, so the scanning
 * switches a folder's schema carries - subdirectories, all files, hidden files, subfolders as items -
 * would drive a scan that never runs; ResourceWriteCapability denies both types, so a destination
 * flag would promise a copy the app then refuses. Those keys are absent from the schema rather than
 * merely disabled, following WearWatchResourceStrategy: a field the editor never renders cannot be
 * set by accident.
 *
 * PATH stays in the schema but invisible. The URL is the resource, so validation must refuse a blank
 * one, while no editor field writes it today - both path inputs are gated on the local and network
 * resource groups, which a stream belongs to neither of.
 */
class StreamResourceStrategy : ResourceStrategy {

    override fun validate(formData: ResourceFormData): ResourceValidationResult {
        val errors = mutableMapOf<ResourceFieldKey, ResourceErrorCode>()

        if (formData.name.isBlank()) {
            errors[ResourceFieldKey.NAME] = ResourceErrorCode.EMPTY
        }
        if (formData.path.isBlank()) {
            errors[ResourceFieldKey.PATH] = ResourceErrorCode.EMPTY
        }

        // No media-type rule: a stream is a single item whose kind is settled by playback, not by a
        // filter set the user picks, so an empty selection is never a reason to refuse the save.

        return if (errors.isEmpty()) {
            ResourceValidationResult.valid()
        } else {
            ResourceValidationResult.invalid(fieldErrors = errors)
        }
    }

    override suspend fun testConnection(formData: ResourceFormData): ResourceConnectionTestResult {
        // Reachability of a stream is answered by the player when it opens the URL; asserting it from
        // this screen would probe a server the editor has no business contacting.
        return ResourceConnectionTestResult(
            status = ResourceConnectionStatus.NOT_SUPPORTED,
            diagnosticMessage = "Stream reachability is answered by playback, not by the editor"
        )
    }

    override fun normalizeBeforeSave(formData: ResourceFormData): ResourceFormData {
        // Credentials are cleared rather than preserved: stream playback receives the path alone, so
        // anything stored here would be written once and read never.
        return formData.copy(
            name = formData.name.trim(),
            path = formData.path.trim(),
            host = "",
            username = "",
            password = "",
            port = null
        )
    }

    override fun fieldSchema(): List<ResourceFieldSchema> = listOf(
        ResourceFieldSchema(ResourceFieldKey.NAME, required = true),
        ResourceFieldSchema(ResourceFieldKey.PATH, required = true, visible = true),
        ResourceFieldSchema(ResourceFieldKey.COMMENT, required = false),
        ResourceFieldSchema(ResourceFieldKey.ACCESS_PIN, required = false)
    )
}
