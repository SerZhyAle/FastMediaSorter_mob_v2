package com.sza.fastmediasorter.wear.util

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Waiters for one-shot replies, each addressed by the key its request carried.
 *
 * A single shared slot is enough only while requests never overlap; the second request would
 * replace the first waiter and the first reply would then find nobody to wake (S3797).
 */
class KeyedReplies<K : Any, V> {

    private val waiting = ConcurrentHashMap<K, CompletableDeferred<V>>()

    /** Registers a waiter for [key]; register before the request leaves, a reply is never replayed. */
    fun register(key: K): CompletableDeferred<V> {
        val answer = CompletableDeferred<V>()
        waiting[key] = answer
        return answer
    }

    /**
     * True when a waiter for [key] was present and received [value].
     *
     * [key] is nullable because it comes out of a Gson-parsed payload, which fills a non-null Kotlin
     * field with null when the sender omitted it, and the map below throws on a null key.
     */
    fun complete(key: K?, value: V): Boolean {
        if (key == null) return false
        return waiting.remove(key)?.complete(value) ?: false
    }

    /** Drops [answer] only if it is still the one registered, so a newer waiter under the same key survives. */
    fun remove(key: K, answer: CompletableDeferred<V>) {
        waiting.remove(key, answer)
    }
}
