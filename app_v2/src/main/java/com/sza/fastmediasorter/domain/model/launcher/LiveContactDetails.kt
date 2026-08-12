package com.sza.fastmediasorter.domain.model.launcher

/**
 * S1206: what the address book says about a person **right now**, as opposed to
 * [LauncherContactTarget], which holds what it said at pin time.
 *
 * The two are kept apart rather than merged because the snapshot stays the fallback: a contact that
 * was deleted, or a read that could not happen, leaves the cell showing what it was pinned with.
 */
data class LiveContactDetails(
    val displayName: String,
    /**
     * The thumbnail URI, not the full-size photo: the cell draws it in a 44dp box, so decoding the
     * full-size picture would cost memory for pixels nothing ever shows.
     */
    val photoUri: String?,
)
