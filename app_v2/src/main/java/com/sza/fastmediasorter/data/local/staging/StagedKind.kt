package com.sza.fastmediasorter.data.local.staging

/**
 * S0189 (Phase 09): content-type discriminator for entries in [LocalStagingRegistry].
 *
 * Drives two concerns:
 * - The player's deferred-bypass loader picks the matching viewer (text editor for
 *   [TEXT_NOTE], drawing editor - future S0191 - for [DRAWING]). [WATCH_RECEIVED] has no
 *   viewer: it names a staging location only and is never opened by the player.
 * - [StagingDirectoryProvider] uses one subdirectory per kind so the feature
 *   surfaces never clash on `${resourceId}_${name}` collisions.
 *
 * [DRAWING] is declared now for S0191 consumption; no S0189 code path produces it yet.
 */
enum class StagedKind {
    TEXT_NOTE,
    DRAWING,

    /** S2044: a file received from the watch, waiting to be uploaded to a remote destination. */
    WATCH_RECEIVED,
}
