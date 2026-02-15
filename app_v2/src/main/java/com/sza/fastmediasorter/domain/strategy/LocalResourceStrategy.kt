package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceValidationResult

class LocalResourceStrategy : ResourceStrategy {

    override fun validate(formData: ResourceFormData): ResourceValidationResult {
        val errors = mutableMapOf<ResourceFieldKey, ResourceErrorCode>()

        if (formData.name.isBlank()) {
            errors[ResourceFieldKey.NAME] = ResourceErrorCode.EMPTY
        }
        if (formData.path.isBlank()) {
            errors[ResourceFieldKey.PATH] = ResourceErrorCode.EMPTY
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
        return ResourceConnectionTestResult(
            status = ResourceConnectionStatus.NOT_SUPPORTED,
            diagnosticMessage = "Connection test is not required for local resources"
        )
    }

    override fun normalizeBeforeSave(formData: ResourceFormData): ResourceFormData {
        return formData.copy(
            name = formData.name.trim(),
            path = formData.path.trim(),
            host = "",
            username = "",
            password = "",
            port = null
        )
    }

    override fun fieldSchema(): List<ResourceFieldSchema> {
        return listOf(
            ResourceFieldSchema(ResourceFieldKey.NAME, required = true),
            ResourceFieldSchema(ResourceFieldKey.PATH, required = true),
            ResourceFieldSchema(ResourceFieldKey.MEDIA_TYPES, required = false),
            ResourceFieldSchema(ResourceFieldKey.ACCESS_PIN, required = false)
        )
    }
}