package com.sza.fastmediasorter.domain.model

enum class ResourceFieldKey {
    NAME,
    PATH,
    TYPE,
    HOST,
    PORT,
    USERNAME,
    PASSWORD,
    ACCESS_PIN,
    CLOUD_PROVIDER,
    CLOUD_FOLDER,
    MEDIA_TYPES,
    MAX_RECIPIENTS,
    GENERIC
}

enum class ResourceErrorCode {
    EMPTY,
    INVALID,
    TOO_SHORT,
    TOO_LONG,
    OUT_OF_RANGE,
    UNSUPPORTED,
    UNREACHABLE,
    ACCESS_DENIED,
    TIMEOUT,
    CONFLICT,
    UNKNOWN
}

data class ResourceValidationResult(
    val isValid: Boolean,
    val fieldErrors: Map<ResourceFieldKey, ResourceErrorCode> = emptyMap(),
    val globalErrors: List<ResourceErrorCode> = emptyList()
) {
    companion object {
        fun valid(): ResourceValidationResult {
            return ResourceValidationResult(isValid = true)
        }

        fun invalid(
            fieldErrors: Map<ResourceFieldKey, ResourceErrorCode> = emptyMap(),
            globalErrors: List<ResourceErrorCode> = emptyList()
        ): ResourceValidationResult {
            return ResourceValidationResult(
                isValid = false,
                fieldErrors = fieldErrors,
                globalErrors = globalErrors
            )
        }
    }
}