package com.sza.fastmediasorter.domain.model

enum class ResourceConnectionStatus {
    SUCCESS,
    FAILED,
    PARTIAL,
    NOT_SUPPORTED
}

data class ResourceConnectionTestResult(
    val status: ResourceConnectionStatus,
    val latencyMs: Long? = null,
    val errorCode: ResourceErrorCode? = null,
    val diagnosticMessage: String? = null
)