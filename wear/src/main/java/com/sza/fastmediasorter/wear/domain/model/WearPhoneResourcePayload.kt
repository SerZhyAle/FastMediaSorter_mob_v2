package com.sza.fastmediasorter.wear.domain.model

const val WEAR_PHONE_RESOURCE_SCHEMA_VERSION = 1

enum class WearPhoneResourceRequestKind {
    ROOT,
    CHILDREN,
    OPEN
}

enum class WearPhoneResourceResponseStatus {
    OK,
    EMPTY,
    PHONE_UNAVAILABLE,
    ACCESS_DENIED,
    UNSUPPORTED_MEDIA,
    TRANSFER_REJECTED,
    NOT_FOUND
}

data class WearPhoneResourceRequest(
    val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    val requestId: String,
    val kind: WearPhoneResourceRequestKind,
    val parentToken: String? = null,
    val pageToken: String? = null,
    val itemToken: String? = null
)

data class WearPhoneResourceItem(
    val token: String,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val isDirectory: Boolean
)

data class WearPhoneResourcePage(
    val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    val requestId: String,
    val status: WearPhoneResourceResponseStatus,
    val items: List<WearPhoneResourceItem> = emptyList(),
    val nextPageToken: String? = null
)
