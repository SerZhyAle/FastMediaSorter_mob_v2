package com.sza.fastmediasorter.domain.filler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FillerEngineTest {
    private val target = FillerTarget("local-tree", "primary")
    private val journal = FakeJournal()
    private val storage = FakeStorage(journal)
    private val engine = FillerEngine(storage, journal)

    @Test
    fun `writes only available space beyond reserve and cleans up`() = runTest {
        val result = engine.run(target, RESERVE)

        assertEquals(FillerResult.Finished(INITIAL_SPACE - RESERVE, true), result)
        assertEquals(RESERVE, storage.available)
        assertTrue(storage.cleaned)
        assertTrue(journal.finished)
        assertTrue(journal.preparedBeforeCreate)
    }

    @Test
    fun `unknown free space refuses before creating session`() = runTest {
        storage.unknownSpace = true

        assertEquals(FillerResult.MeasurementUnavailable, engine.run(target, RESERVE))
        assertFalse(journal.began)
    }

    @Test
    fun `invalid target and insufficient space refuse without writing`() = runTest {
        storage.valid = false
        assertEquals(FillerResult.InvalidTarget, engine.run(target, RESERVE))
        storage.valid = true
        storage.available = RESERVE
        assertEquals(FillerResult.InsufficientSpace, engine.run(target, RESERVE))
        assertFalse(journal.began)
    }

    @Test
    fun `write failure retains journal when cleanup fails`() = runTest {
        storage.writeFails = true
        storage.cleanupSucceeds = false

        assertEquals(FillerResult.Failed(false), engine.run(target, RESERVE))
        assertTrue(storage.cleaned)
        assertFalse(journal.finished)
    }

    @Test
    fun `cancellation attempts cleanup and remains cancellation`() = runTest {
        storage.cancelWrite = true
        var cancelled = false
        try {
            engine.run(target, RESERVE)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertTrue(storage.cleaned)
        assertTrue(journal.finished)
    }

    @Test
    fun `second operation is refused until the first is finished`() = runTest {
        val gate = CompletableDeferred<Unit>()
        storage.writeGate = gate
        val first = async { engine.run(target, RESERVE) }
        runCurrent()

        assertEquals(FillerResult.Busy, FillerEngine(storage, journal).run(target, RESERVE))
        gate.complete(Unit)
        first.await()
    }

    @Test
    fun `cancelled coroutine attempts cleanup before it finishes`() = runTest {
        storage.writeGate = CompletableDeferred()
        val job = async { engine.run(target, RESERVE) }
        runCurrent()
        job.cancelAndJoin()

        assertTrue(storage.cleaned)
        assertTrue(journal.finished)
    }

    @Test
    fun `restart exposes pending cleanup and only accepts known sessions`() = runTest {
        val pending = FillerSession("pending", target)
        journal.pending = pending
        val restarted = FillerEngine(storage, journal)

        assertEquals(FillerResult.PendingCleanup, restarted.run(target, RESERVE))
        assertFalse(restarted.retryCleanup(FillerSession("unknown", target)))
        assertFalse(storage.cleaned)
        assertTrue(restarted.retryCleanup(pending))
        assertTrue(storage.cleaned)
        assertEquals(null, journal.pending)
    }

    @Test
    fun `ambiguous cleanup preserves the pending session`() = runTest {
        val pending = FillerSession("pending", target)
        journal.pending = pending
        storage.cleanupSucceeds = false

        assertFalse(engine.retryCleanup(pending))
        assertEquals(pending, journal.pending)
    }
    private class FakeJournal : FillerJournal {
        var pending: FillerSession? = null
        var began = false
        var prepared = false
        var preparedBeforeCreate = false
        var finished = false

        override suspend fun pendingSessions(): List<FillerSession> = listOfNotNull(pending)

        override suspend fun begin(target: FillerTarget): FillerSession {
            began = true
            val session = FillerSession("session", target)
            pending = session
            return session
        }

        override suspend fun prepareCreate(session: FillerSession, index: Int) {
            prepared = true
        }

        override suspend fun recordCreated(session: FillerSession, index: Int, file: FillerFile) = Unit

        override suspend fun finish(session: FillerSession) {
            finished = true
            pending = null
        }
    }

    private class FakeStorage(private val journal: FakeJournal) : FillerStorage {
        var valid = true
        var unknownSpace = false
        var available = INITIAL_SPACE
        var writeFails = false
        var cancelWrite = false
        var cleanupSucceeds = true
        var cleaned = false
        var writeGate: CompletableDeferred<Unit>? = null

        override suspend fun validate(target: FillerTarget): Boolean = valid
        override suspend fun availableBytes(target: FillerTarget): Long? = if (unknownSpace) null else available

        override suspend fun create(session: FillerSession, index: Int): FillerFile {
            journal.preparedBeforeCreate = journal.prepared
            return FillerFile("created-$index")
        }

        override suspend fun append(file: FillerFile, count: Int) {
            writeGate?.await()
            if (cancelWrite) throw CancellationException("test")
            check(!writeFails)
            available -= count
        }

        override suspend fun cleanup(session: FillerSession): Boolean {
            cleaned = true
            return cleanupSucceeds
        }
    }

    private companion object {
        const val INITIAL_SPACE = 2048L
        const val RESERVE = 1024L
    }
}
