package com.sza.fastmediasorter.wear.domain.model

/**
 * S2499: what kind of thing a history entry stands for, which is what decides how its `id` reads.
 *
 * The two kinds address their target differently and there is no shape test that could tell them
 * apart safely: a catalog row id changes across a re-import while a channel address does not, so the
 * kind is recorded rather than inferred.
 */
enum class LastUsedKind {
    /** [LastUsedResource.id] is a network source id, looked up in the source store. */
    RESOURCE,

    /** [LastUsedResource.id] is a channel address, normalized by `normalizeWearStreamUrl`. */
    STREAM
}

/**
 * S1836: the network source the watch opened last - [id] addresses it, [name] captions its home cell.
 *
 * S2499: a channel can be the thing opened last too, so [kind] says which store [id] addresses. It
 * defaults to [LastUsedKind.RESOURCE] because every entry written before that ticket was one.
 */
data class LastUsedResource(
    val id: String,
    val name: String,
    val kind: LastUsedKind = LastUsedKind.RESOURCE,
    // S2129: filled on the read path from the live source, never stored - the history encodes id and
    // name only, so a remembered entry is null here and picks up the icon the phone last sent.
    val iconId: String? = null
) {
    companion object {
        /**
         * S1974: the width of the widest grid the home screen can draw, so a longer history would
         * store entries no view mode has a cell for.
         */
        const val HISTORY_LIMIT: Int = 3
    }
}
