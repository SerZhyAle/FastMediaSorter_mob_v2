package com.sza.fastmediasorter.data.network.lifecycle

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks last forced-recreate timestamp per FTP resource key. S0067. */
@Singleton
class FtpRecreateTracker @Inject constructor() {

    private val map = ConcurrentHashMap<String, Long>()

    fun recordRecreate(resourceKey: String) {
        map[resourceKey] = System.currentTimeMillis()
    }

    fun lastRecreateMs(resourceKey: String): Long? = map[resourceKey]

    fun keyForServer(server: String, port: Int): String = "ftp://$server:$port"
}
