package com.sza.fastmediasorter.data.network.lifecycle

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks last forced-recreate (preemptive token-refresh) timestamp per cloud provider key. S0067. */
@Singleton
class CloudRecreateTracker @Inject constructor() {

    private val map = ConcurrentHashMap<String, Long>()

    fun recordRecreate(resourceKey: String) {
        map[resourceKey] = System.currentTimeMillis()
    }

    fun lastRecreateMs(resourceKey: String): Long? = map[resourceKey]

    fun keyForProvider(providerId: String): String = "cloud://$providerId"
}
