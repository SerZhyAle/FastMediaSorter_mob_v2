package com.sza.fastmediasorter.domain.model

/**
 * Domain representation of a Wear OS watch storage resource.
 *
 * Represents media storage on a connected watch, grouped with local resources and
 * capable of acting as both a media source and operational destination.
 */
data class WearWatchResource(
    val id: String = VIRTUAL_WEAR_RESOURCE_ID,
    val name: String = VIRTUAL_WEAR_RESOURCE_NAME,
    val uri: String = VIRTUAL_WEAR_RESOURCE_URI,
    val isLocalGroup: Boolean = true,
    val isDestination: Boolean = true
) {
    companion object {
        const val VIRTUAL_WEAR_RESOURCE_ID = "virtual_wear_watch_resource"
        const val VIRTUAL_WEAR_RESOURCE_NAME = "WEAR Watch Data"
        const val VIRTUAL_WEAR_RESOURCE_URI = "wear://watch_data"
    }
}
