package com.sza.fastmediasorter.data.network

interface IdleDisconnectPolicy {
    fun arm(transport: String, idleMs: Long, onTimeout: suspend () -> Unit)

    fun touch(transport: String)

    fun disarm(transport: String)
}