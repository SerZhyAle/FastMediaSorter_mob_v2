package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceValidationResult

class FtpResourceStrategy(
    private val connectionTester: (suspend (ResourceFormData) -> ResourceConnectionTestResult)? = null
) : ResourceStrategy {

    override fun validate(formData: ResourceFormData): ResourceValidationResult {
        val errors = mutableMapOf<ResourceFieldKey, ResourceErrorCode>()

        if (formData.name.isBlank()) {
            errors[ResourceFieldKey.NAME] = ResourceErrorCode.EMPTY
        }
        if (formData.host.isBlank()) {
            errors[ResourceFieldKey.HOST] = ResourceErrorCode.EMPTY
        }
        if (formData.port != null && formData.port !in 1..65535) {
            errors[ResourceFieldKey.PORT] = ResourceErrorCode.OUT_OF_RANGE
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
                diagnosticMessage = "FTP connection tester is not configured"
            )
        return tester(formData)
    }

    override fun normalizeBeforeSave(formData: ResourceFormData): ResourceFormData {
        val normalizedPath = normalizeNetworkResourcePath(formData.path)
        return formData.copy(
            name = formData.name.trim(),
            host = formData.host.trim().replace(',', '.'),
            path = normalizedPath,
            port = formData.port ?: 21
        )
    }

    override fun fieldSchema(): List<ResourceFieldSchema> {
        return listOf(
            ResourceFieldSchema(ResourceFieldKey.NAME, required = true),
            ResourceFieldSchema(ResourceFieldKey.HOST, required = true),
            ResourceFieldSchema(ResourceFieldKey.PORT, required = false),
            ResourceFieldSchema(ResourceFieldKey.PATH, required = false),
            ResourceFieldSchema(ResourceFieldKey.USERNAME, required = false),
            ResourceFieldSchema(ResourceFieldKey.PASSWORD, required = false),
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