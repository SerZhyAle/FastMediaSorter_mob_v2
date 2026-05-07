package com.sza.fastmediasorter.domain.model

enum class PermissionGroup {
    STORAGE, NETWORK, MICROPHONE, NOTIFICATION, CAMERA, SYSTEM, VR
}

enum class PermissionStatus {
    GRANTED, DENIED, PERMANENTLY_DENIED, NOT_APPLICABLE
}

data class PermissionEntry(
    val id: String,
    val manifestName: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val group: PermissionGroup,
    val optional: Boolean,
    val minSdk: Int = 0,
    val maxSdk: Int = Int.MAX_VALUE,
    val flavorGates: Set<String> = emptySet(),
)

data class PermissionGroupHeader(
    val group: PermissionGroup,
    val titleRes: Int,
)
