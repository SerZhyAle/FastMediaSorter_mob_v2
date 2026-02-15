package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceValidationResult

data class ResourceFieldSchema(
    val key: ResourceFieldKey,
    val required: Boolean,
    val editable: Boolean = true,
    val visible: Boolean = true
)

interface ResourceStrategy {
    fun validate(formData: ResourceFormData): ResourceValidationResult
    suspend fun testConnection(formData: ResourceFormData): ResourceConnectionTestResult
    fun normalizeBeforeSave(formData: ResourceFormData): ResourceFormData
    fun fieldSchema(): List<ResourceFieldSchema>
}