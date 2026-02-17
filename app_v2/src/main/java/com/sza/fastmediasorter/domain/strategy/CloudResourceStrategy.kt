package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceValidationResult

class CloudResourceStrategy(
    private val connectionTester: (suspend (ResourceFormData) -> ResourceConnectionTestResult)? = null
) : ResourceStrategy {

    override fun validate(formData: ResourceFormData): ResourceValidationResult {
        val errors = mutableMapOf<ResourceFieldKey, ResourceErrorCode>()

        if (formData.name.isBlank()) {
            errors[ResourceFieldKey.NAME] = ResourceErrorCode.EMPTY
        }
        if (formData.cloudProvider == null) {
            errors[ResourceFieldKey.CLOUD_PROVIDER] = ResourceErrorCode.EMPTY
        }
        if (formData.cloudFolderId.isNullOrBlank() && formData.path.isBlank()) {
            errors[ResourceFieldKey.CLOUD_FOLDER] = ResourceErrorCode.EMPTY
        }
        if (formData.supportedMediaTypes.isEmpty() && !formData.allFiles) {
            errors[ResourceFieldKey.MEDIA_TYPES] = ResourceErrorCode.EMPTY
        }

        return if (errors.isEmpty()) {
            ResourceValidationResult.valid()
        } else {
            ResourceValidationResult.invalid(fieldErrors = errors)
        }
    }

    override suspend fun testConnection(formData: ResourceFormData): ResourceConnectionTestResult {
        val tester = connectionTester
            ?: return ResourceConnectionTestResult(
                status = ResourceConnectionStatus.NOT_SUPPORTED,
                diagnosticMessage = "Cloud connection tester is not configured"
            )
        return tester(formData)
    }

    override fun normalizeBeforeSave(formData: ResourceFormData): ResourceFormData {
        val normalizedPath = formData.path.trim().replace(Regex("/+"), "/")
        return formData.copy(
            name = formData.name.trim(),
            path = normalizedPath,
            host = "",
            port = null,
            username = "",
            password = ""
        )
    }

    override fun fieldSchema(): List<ResourceFieldSchema> {
        return listOf(
            ResourceFieldSchema(ResourceFieldKey.NAME, required = true),
            ResourceFieldSchema(ResourceFieldKey.CLOUD_PROVIDER, required = true),
            ResourceFieldSchema(ResourceFieldKey.CLOUD_FOLDER, required = false),
            ResourceFieldSchema(ResourceFieldKey.MEDIA_TYPES, required = false),
            ResourceFieldSchema(ResourceFieldKey.SCAN_SUBDIRECTORIES, required = false),
            ResourceFieldSchema(ResourceFieldKey.ALL_FILES, required = false),
            ResourceFieldSchema(ResourceFieldKey.DISABLE_THUMBNAILS, required = false),
            ResourceFieldSchema(ResourceFieldKey.SHOW_HIDDEN_FILES, required = false),
            ResourceFieldSchema(ResourceFieldKey.SHOW_SUBFOLDERS_AS_ITEMS, required = false),
            ResourceFieldSchema(ResourceFieldKey.IS_DESTINATION, required = false),
            ResourceFieldSchema(ResourceFieldKey.IS_READ_ONLY, required = false),
            ResourceFieldSchema(ResourceFieldKey.COMMENT, required = false),
            ResourceFieldSchema(ResourceFieldKey.ACCESS_PIN, required = false),
            ResourceFieldSchema(ResourceFieldKey.SLIDESHOW_INTERVAL, required = false)
        )
    }
}