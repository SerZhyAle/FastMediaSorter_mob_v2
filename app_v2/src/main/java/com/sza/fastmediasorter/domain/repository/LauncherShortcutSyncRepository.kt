package com.sza.fastmediasorter.domain.repository

/**
 * S2330: the set of shortcut routes the launcher desktop has already accounted for.
 *
 * Absent and empty are different answers, and the distinction is the whole point of this store.
 * Null means the sync has never run on this install, so the desktop it finds was composed by the
 * starter set alone and every launchable route must be adopted silently - adding a cell for each
 * would rewrite a desktop the user already accepted. An empty set means the sync did run and
 * accounted for nothing, so every launchable route is genuinely new and gets its cell.
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
}
