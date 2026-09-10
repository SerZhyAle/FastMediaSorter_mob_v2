package com.sza.fastmediasorter.domain.repository

/**
 * S2330: the set of shortcut routes the launcher desktop has already accounted for.
 *
 * Absent and empty are different answers, and the distinction is the whole point of this store.
 * Null means the sync has never run on this install, so the desktop it finds was composed by the
 * starter set alone and every launchable route must be adopted silently - adding a cell for each
 * would rewrite a desktop the user already accepted. An empty set means the sync did run and
 * accounted for nothing, so every launchable route is genuinely new and gets its cell.
 *
 * S2564: it carries a second baseline, for the aggregate resource tiles, under the same rules. A
 * resource tile is a SHORTCUT cell like a route tile, so a repository of its own would buy a second
 * Hilt binding for one more string set in the same store (strategic ADR-3).
 */
interface LauncherShortcutSyncRepository {

    /** Null when no baseline was ever written - see the class note on absent versus empty. */
    suspend fun syncedRoutes(): Set<String>?

    /** Replaces the whole baseline; the set is never merged into the stored one. */
    suspend fun setSyncedRoutes(routeKeys: Set<String>)

    /**
     * Returns the store to its never-written state, so [syncedRoutes] answers null and not an empty
     * set. The launcher reset needs exactly that: an empty baseline would read the whole launchable
     * set as newly enabled and bury the freshly re-seeded desktop in cells.
     */
    suspend fun clearSyncedRoutes()

    /** Whether S2791's one-time Stopwatch desktop correction has already been applied. */
    suspend fun isStopwatchShortcutBackfilled(): Boolean

    /** Records completion of S2791's one-time Stopwatch desktop correction. */
    suspend fun setStopwatchShortcutBackfilled()

    /**
     * S2564: the aggregate virtual paths whose desktop tile is already accounted for.
     *
     * Paths and not resource ids: a deleted aggregate is created again by the next provisioning pass
     * and comes back under a new id, which would read as a tile the desktop never had. Null carries
     * the same meaning it does for [syncedRoutes].
     */
    suspend fun syncedResourcePaths(): Set<String>?

    /** Replaces the whole resource baseline; the set is never merged into the stored one. */
    suspend fun setSyncedResourcePaths(paths: Set<String>)

    /** Returns the resource baseline to its never-written state, for the same reason [clearSyncedRoutes] does. */
    suspend fun clearSyncedResourcePaths()
}
