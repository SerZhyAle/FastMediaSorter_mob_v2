package com.sza.fastmediasorter.data.network.lifecycle

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the most recent forced-recreate timestamp per SMB resource key. S0067.
 *
 * Decoupled from `SmbConnectionManager` (988 LOC, near line-budget limit) so the
 * existing legacy manager remains untouched in S0067. A future ticket may opt the
 * manager into calling [recordRecreate] from its `purgeClientForHost` /
 * `invalidateExoPlayerConnection` paths to feed S0066 transient-classification.
 *
 * Resource keys:
 *  - `"smb://<host>:<port>"` — server-level (matches `ConnectionThrottleManager` keys)
 *  - `"smb://<host>:<port>/<share>"` — share-level (matches SMB `PooledConnection` granularity)
 */
@Singleton
class SmbRecreateTracker @Inject constructor() {

    private val map = ConcurrentHashMap<String, Long>()

    fun recordRecreate(resourceKey: String) {
        map[resourceKey] = System.currentTimeMillis()
    }

    fun lastRecreateMs(resourceKey: String): Long? = map[resourceKey]

    fun keyForServer(server: String, port: Int): String = "smb://$server:$port"

    fun keyForShare(server: String, port: Int, shareName: String): String =
        "smb://$server:$port/$shareName"
}
