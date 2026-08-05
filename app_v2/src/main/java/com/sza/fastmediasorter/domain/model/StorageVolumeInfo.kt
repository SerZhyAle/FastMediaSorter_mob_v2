package com.sza.fastmediasorter.domain.model

/**
 * One storage volume of the device - built-in storage or a mounted removable medium.
 *
 * The registry reports an unmounted volume instead of dropping it, so a resource bound to an
 * ejected card can be told apart from a resource bound to a volume this device never had.
 *
 * @param id platform volume uuid, or [PRIMARY_VOLUME_ID] for built-in storage.
 * @param mountPath filesystem root, null while the volume is not mounted.
 * @param totalBytes capacity, 0 while the volume is not mounted.
 * @param availableBytes free space, 0 while the volume is not mounted.
 */
data class StorageVolumeInfo(
    val id: String,
    val displayName: String,
    val isRemovable: Boolean,
    val isMounted: Boolean,
    val mountPath: String?,
    val totalBytes: Long,
    val availableBytes: Long
) {
    val isPrimary: Boolean get() = id == PRIMARY_VOLUME_ID

    companion object {
        /** Document-tree ids use this literal for built-in storage instead of a uuid. */
        const val PRIMARY_VOLUME_ID = "primary"
    }
}
