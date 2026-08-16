package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

const val WEAR_PHONE_RESOURCE_SCHEMA_VERSION = 1

enum class WearPhoneResourceRequestKind {
    @SerializedName("ROOT")
    ROOT,

    @SerializedName("CHILDREN")
    CHILDREN,

    @SerializedName("OPEN")
    OPEN
}

enum class WearPhoneResourceResponseStatus {
    @SerializedName("OK")
    OK,

    @SerializedName("EMPTY")
    EMPTY,

    @SerializedName("PHONE_UNAVAILABLE")
    PHONE_UNAVAILABLE,

    @SerializedName("ACCESS_DENIED")
    ACCESS_DENIED,

    @SerializedName("UNSUPPORTED_MEDIA")
    UNSUPPORTED_MEDIA,

    @SerializedName("TRANSFER_REJECTED")
    TRANSFER_REJECTED,

    @SerializedName("NOT_FOUND")
    NOT_FOUND
}

data class WearPhoneResourceRequest(
    @SerializedName("schemaVersion") val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    @SerializedName("requestId") val requestId: String,
    @SerializedName("kind") val kind: WearPhoneResourceRequestKind,
    @SerializedName("parentToken") val parentToken: String? = null,
    @SerializedName("pageToken") val pageToken: String? = null,
    @SerializedName("itemToken") val itemToken: String? = null
)

data class WearPhoneResourceItem(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("sizeBytes") val sizeBytes: Long? = null,
    @SerializedName("isDirectory") val isDirectory: Boolean
)

data class WearPhoneResourcePage(
    @SerializedName("schemaVersion") val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    @SerializedName("requestId") val requestId: String,
    @SerializedName("status") val status: WearPhoneResourceResponseStatus,
    @SerializedName("items") val items: List<WearPhoneResourceItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)
