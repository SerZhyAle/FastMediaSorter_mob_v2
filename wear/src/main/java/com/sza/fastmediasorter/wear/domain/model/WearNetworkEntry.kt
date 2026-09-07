package com.sza.fastmediasorter.wear.domain.model

/**
 * One entry of a network directory listing, before it is turned into either a walk row or a flat one.
 *
 * S2694: the three protocol clients each know whether an entry is a directory - SMB in the attribute
 * bits of the listing record, FTP and SFTP in their own entry types - and each used to drop that on
 * the way to [WearMediaFile], which carries no such field. A walk cannot be built on a listing that
 * has already forgotten which rows can be descended into, so every source now answers in this shape
 * and the mapping to a flat row happens after.
 *
 * **Path invariant.** [path] is what the owning protocol client accepts for a further listing of this
 * entry: share-relative for SMB (the share root being the empty string), an absolute server path for
 * FTP and SFTP. It is produced by the source that listed it and never rebuilt from [name] by a
 * caller - the join differs per protocol, and a caller that guessed it would be right on one of the
 * three.
 */
data class WearNetworkEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val dateModifiedEpochMillis: Long
) {
    companion object {

        /** The listing row naming the level being listed. Every protocol here may return it. */
        const val SELF_ENTRY = "."

        /** The listing row naming the level above. Ascending is the trail's job, never a row's. */
        const val PARENT_ENTRY = ".."
    }
}
