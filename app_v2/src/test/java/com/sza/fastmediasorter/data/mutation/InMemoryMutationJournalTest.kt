package com.sza.fastmediasorter.data.mutation

import com.sza.fastmediasorter.domain.mutation.Mutation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [InMemoryMutationJournal].
 *
 * Covers: ordering, applied filtering, lastAppliedSeq advance, clear, cap-overflow
 * eviction of applied entries (pending preserved), concurrent record() correctness.
 */
class InMemoryMutationJournalTest {

    private lateinit var journal: InMemoryMutationJournal

    @Before
    fun setUp() {
        journal = InMemoryMutationJournal()
    }

    @Test
    fun `pendingFor returns recorded entries in seq order`() {
        val rid = 1L
        val m1 = delete(rid, "/a/1", "op-1")
        val m2 = delete(rid, "/a/2", "op-2")
        val m3 = delete(rid, "/a/3", "op-3")
        journal.record(m1)
        journal.record(m2)
        journal.record(m3)

        val pending = journal.pendingFor(rid, sinceAppliedSeq = 0L)

        assertEquals(3, pending.size)
        assertEquals(listOf(1L, 2L, 3L), pending.map { it.seq })
        assertEquals(listOf("op-1", "op-2", "op-3"), pending.map { it.mutation.opId })
    }

    @Test
    fun `markApplied flips entries and advances lastAppliedSeq`() {
        val rid = 2L
        journal.record(delete(rid, "/a/1", "op-1"))
        journal.record(delete(rid, "/a/2", "op-2"))
        journal.record(delete(rid, "/a/3", "op-3"))

        journal.markApplied(rid, listOf("op-1", "op-2"))

        val pending = journal.pendingFor(rid, sinceAppliedSeq = 0L)
        assertEquals(1, pending.size)
        assertEquals("op-3", pending[0].mutation.opId)
        assertEquals(2L, journal.lastAppliedSeq(rid))
    }

    @Test
    fun `pendingFor respects sinceAppliedSeq filter`() {
        val rid = 3L
        journal.record(delete(rid, "/a/1", "op-1"))
        journal.record(delete(rid, "/a/2", "op-2"))
        journal.record(delete(rid, "/a/3", "op-3"))

        val pendingAfter1 = journal.pendingFor(rid, sinceAppliedSeq = 1L)
        assertEquals(listOf(2L, 3L), pendingAfter1.map { it.seq })
    }

    @Test
    fun `clearResource drops pending and resets lastAppliedSeq`() {
        val rid = 4L
        journal.record(delete(rid, "/a/1", "op-1"))
        journal.markApplied(rid, listOf("op-1"))

        journal.clearResource(rid)

        assertTrue(journal.pendingFor(rid, sinceAppliedSeq = 0L).isEmpty())
        assertEquals(0L, journal.lastAppliedSeq(rid))
    }

    @Test
    fun `cap evicts oldest applied entries first and preserves all pending`() {
        val rid = 5L
        // Record 600 BatchDelete entries on the same resource. Mark the first 400 applied
        // so the eviction logic has something to drop; the last 200 stay pending and must
        // survive the cap (512).
        repeat(600) { i ->
            journal.record(batch(rid, listOf("/cap/$i"), "op-$i"))
        }
        val appliedOpIds = (0 until 400).map { "op-$it" }
        journal.markApplied(rid, appliedOpIds)
        // Force another record() so the cap is re-evaluated after marking applied.
        journal.record(batch(rid, listOf("/cap/extra"), "op-extra"))

        val pending = journal.pendingFor(rid, sinceAppliedSeq = 0L)
        // All originally pending (200 from indices 400..599 + 1 extra) survive.
        assertEquals(201, pending.size)
        // lastAppliedSeq advanced to the max applied seq (400 - seqs are 1-based).
        assertEquals(400L, journal.lastAppliedSeq(rid))
    }

    @Test
    fun `concurrent record assigns unique monotonic seq`() {
        val rid = 6L
        val threadCount = 10
        val opsPerThread = 50
        val pool = Executors.newFixedThreadPool(threadCount)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threadCount)

        repeat(threadCount) { t ->
            pool.submit {
                startGate.await()
                repeat(opsPerThread) { i ->
                    journal.record(delete(rid, "/c/$t/$i", "op-$t-$i"))
                }
                doneGate.countDown()
            }
        }
        startGate.countDown()
        assertTrue("threads must finish within timeout", doneGate.await(10, TimeUnit.SECONDS))
        pool.shutdown()
        pool.awaitTermination(2, TimeUnit.SECONDS)

        val pending = journal.pendingFor(rid, sinceAppliedSeq = 0L)
        assertEquals(threadCount * opsPerThread, pending.size)
        val seqs = pending.map { it.seq }
        assertEquals(seqs.size, seqs.toSet().size) // all unique
        assertEquals((1L..(threadCount * opsPerThread).toLong()).toList(), seqs.sorted())
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun delete(rid: Long, path: String, opId: String): Mutation.Delete =
        Mutation.Delete(resourceId = rid, canonicalPath = path, opId = opId, timestampMs = 0L)

    private fun batch(rid: Long, paths: List<String>, opId: String): Mutation.BatchDelete =
        Mutation.BatchDelete(resourceId = rid, canonicalPaths = paths, opId = opId, timestampMs = 0L)
}
