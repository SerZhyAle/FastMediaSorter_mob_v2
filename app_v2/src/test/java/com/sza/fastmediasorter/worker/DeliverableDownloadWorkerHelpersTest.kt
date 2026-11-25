package com.sza.fastmediasorter.worker

import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure decisions of [DeliverableDownloadWorker]: which set a work request names, and whether a run
 * has anything left to fetch. A forced run over an installed payload is the stale-payload update path.
 */
class DeliverableDownloadWorkerHelpersTest {

    @Test
    fun `a known set name resolves to that set`() {
        DeliverableSet.entries.forEach { set ->
            assertEquals(set, DeliverableDownloadWorker.deliverableSetNamed(set.name))
        }
    }

    @Test
    fun `an unknown or missing set name resolves to nothing`() {
        assertNull(DeliverableDownloadWorker.deliverableSetNamed("NO_SUCH_SET"))
        assertNull(DeliverableDownloadWorker.deliverableSetNamed(""))
        assertNull(DeliverableDownloadWorker.deliverableSetNamed(null))
    }

    @Test
    fun `an unforced run over an installed payload has nothing to do`() {
        assertTrue(DeliverableDownloadWorker.isAlreadyDone(force = false, installed = true))
    }

    @Test
    fun `a forced run over an installed payload still downloads`() {
        assertFalse(DeliverableDownloadWorker.isAlreadyDone(force = true, installed = true))
    }

    @Test
    fun `a run with no payload on disk downloads whether forced or not`() {
        assertFalse(DeliverableDownloadWorker.isAlreadyDone(force = false, installed = false))
        assertFalse(DeliverableDownloadWorker.isAlreadyDone(force = true, installed = false))
    }
}
