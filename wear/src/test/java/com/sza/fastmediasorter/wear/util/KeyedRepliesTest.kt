package com.sza.fastmediasorter.wear.util

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** S3797: overlapping requests each get their own reply, and one name is sent by one caller at a time. */
class KeyedRepliesTest {

    @Test
    fun `two overlapping requests each receive their own answer`() = runBlocking {
        val replies = KeyedReplies<String, String>()
        val first = replies.register("first")
        val second = replies.register("second")

        assertTrue(replies.complete("second", "answer-2"))
        assertFalse(first.isCompleted)
        assertTrue(replies.complete("first", "answer-1"))

        assertEquals("answer-1", first.await())
        assertEquals("answer-2", second.await())
    }

    @Test
    fun `a reply with no key or an unknown key wakes nobody`() {
        val replies = KeyedReplies<String, String>()
        val waiting = replies.register("known")

        assertFalse(replies.complete(null, "x"))
        assertFalse(replies.complete("unknown", "x"))
        assertFalse(waiting.isCompleted)
    }

    @Test
    fun `removing a stale waiter keeps a newer one under the same key`() {
        val replies = KeyedReplies<String, String>()
        val stale = replies.register("id")
        val fresh = replies.register("id")

        replies.remove("id", stale)

        assertTrue(replies.complete("id", "late"))
        assertTrue(fresh.isCompleted)
    }

    @Test
    fun `same-key turns never overlap and the map empties afterwards`() = runBlocking {
        val turns = KeyedTurns<String>()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val jobs = (1..4).map { n ->
            launch {
                turns.withTurn("photo.jpg") {
                    events += "start-$n"
                    delay(10)
                    events += "end-$n"
                }
            }
        }
        jobs.forEach { it.join() }

        events.chunked(2).forEach { (start, end) ->
            assertEquals(start.removePrefix("start-"), end.removePrefix("end-"))
        }
        assertEquals(0, turns.activeKeyCount())
    }

    @Test
    fun `different keys do not wait on each other`() = runBlocking {
        val turns = KeyedTurns<String>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val holder = launch { turns.withTurn("a.jpg") { gate.await() } }
        val other = async { turns.withTurn("b.jpg") { "done" } }

        assertEquals("done", other.await())
        gate.complete(Unit)
        holder.join()
    }
}
