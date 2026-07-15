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

/**
 * Shared leading-slash path normalization for network resource strategies (SFTP/FTP): trims,
 * unifies separators, collapses repeats, forces a single leading slash, drops a trailing slash
 * unless the path is root. Named distinctly from PathUtils.isNetworkPath /
 * CloudFileOperationPathUtils.normalizeNetworkPath (a different rule, S1028) to avoid conflation.
 */
internal fun normalizeNetworkResourcePath(path: String): String {
    val cleaned = path.trim().replace('\\', '/').replace(Regex("/+"), "/")
    if (cleaned.isBlank()) {
        return "/"
    }
    val withLeadingSlash = if (cleaned.startsWith('/')) cleaned else "/$cleaned"
    return if (withLeadingSlash.length > 1) withLeadingSlash.trimEnd('/') else withLeadingSlash
}