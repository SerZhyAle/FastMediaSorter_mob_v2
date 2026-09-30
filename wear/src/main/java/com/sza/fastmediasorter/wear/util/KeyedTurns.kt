package com.sza.fastmediasorter.wear.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One caller at a time per key, callers of different keys never waiting on each other.
 *
 * An entry lives only while someone holds or waits for its key, so a stream of distinct keys - file
 * names, say - does not grow the map for the life of the process.
 */
class KeyedTurns<K : Any> {

    private val guard = Any()
    private val turns = HashMap<K, Turn>()

    suspend fun <T> withTurn(key: K, block: suspend () -> T): T {
        val turn = synchronized(guard) {
            turns.getOrPut(key) { Turn() }.also { it.holders++ }
        }
        try {
            return turn.mutex.withLock { block() }
        } finally {
            synchronized(guard) {
                turn.holders--
                if (turn.holders == 0) turns.remove(key)
            }
        }
    }

    /** Keys currently held or awaited; exposed for the leak assertion in tests. */
    internal fun activeKeyCount(): Int = synchronized(guard) { turns.size }

    private class Turn {
        val mutex = Mutex()
        var holders = 0
    }
}
