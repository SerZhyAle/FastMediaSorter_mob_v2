package com.sza.fastmediasorter.domain.exchange

import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.data.remotecontrol.RemoteCommandBench
import com.sza.fastmediasorter.data.remotecontrol.RemoteCommandVectorsGenerator
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The opening and answering of one `command-request` frame: cases R1, R2, R6 and R8 of REMOTE-CONTROL section 4. */
class RemoteCommandCoordinatorTest {

    private val bench = RemoteCommandBench()

    @Test
    fun `a granted safe verb runs and is answered to the box sender alone`() {
        bench.listenAndGrant()
        val reply = requireNotNull(bench.answer(bench.frame(bench.payload())))
        val clear = requireNotNull(bench.open(reply.envelope))
        assertEquals(RemoteCommandAnswer.STATUS_DONE, clear.get("status").asString)
        assertEquals(bench.controller.deviceId, reply.envelope.string(RemoteCommandAnswerManager.KEY_TO_DEVICE_ID))
        assertEquals(1, bench.safeVerb.runs)
    }

    @Test
    fun `the switch off answers not-permitted and logs the line`() {
        val reply = requireNotNull(bench.answer(bench.frame(bench.payload())))
        assertEquals(RemoteCommandAnswer.REASON_NOT_PERMITTED, bench.open(reply.envelope)?.get("reason")?.asString)
        assertEquals(1, bench.repository.recentLog.value.size)
        assertEquals(0, bench.safeVerb.runs)
    }

    @Test
    fun `a box moved to another requestId is never run nor answered`() {
        bench.listenAndGrant()
        assertNull(bench.answer(bench.frame(bench.payload(), requestId = "req-a", sealedFor = "req-b")))
        assertSealedFailed()
    }

    @Test
    fun `a box from a key the target does not trust is never run nor answered`() {
        bench.listenAndGrant()
        bench.trustController = false
        assertNull(bench.answer(bench.frame(bench.payload())))
        assertSealedFailed()
    }

    @Test
    fun `a clear fromDeviceId that differs from the box is ignored for the grant, the log and the answer`() {
        bench.listenAndGrant()
        val reply = requireNotNull(bench.answer(bench.frame(bench.payload(), clearFrom = "dev-stranger")))
        assertEquals(bench.controller.deviceId, reply.envelope.string(RemoteCommandAnswerManager.KEY_TO_DEVICE_ID))
        assertEquals(bench.controller.deviceId, bench.repository.recentLog.value.single().deviceId)
        assertEquals(1, bench.safeVerb.runs)
    }

    @Test
    fun `the answer box opens only under the result context of its own request`() {
        bench.listenAndGrant()
        val reply = requireNotNull(bench.answer(bench.frame(bench.payload())))
        val box = reply.envelope.members.get(RemoteCommandAnswerManager.KEY_BOX)
        val asRequest = E2eSealedBox.open(
            box,
            E2eSealedBox.commandContext(RemoteCommandBench.REQUEST_ID),
            bench.controller,
        ) { _, _ -> true }
        assertTrue(asRequest is E2eOpenResult.Failed)
    }

    @Test
    fun `the same box twice runs once and answers alike`() {
        bench.listenAndGrant()
        val frame = bench.frame(bench.payload())
        val first = bench.open(requireNotNull(bench.answer(frame)).envelope)
        val second = bench.open(requireNotNull(bench.answer(frame)).envelope)
        assertEquals(first, second)
        assertEquals(1, bench.safeVerb.runs)
    }

    @Test
    fun `a newer payload version is refused as schema`() {
        bench.listenAndGrant()
        val reply = requireNotNull(bench.answer(bench.frame(bench.payload(version = 2))))
        assertEquals(RemoteCommandAnswer.REASON_SCHEMA, bench.open(reply.envelope)?.get("reason")?.asString)
        assertEquals(0, bench.safeVerb.runs)
    }

    @Test
    fun `the transcripts are reproduced byte for byte`() {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val rendered = gson.toJson(RemoteCommandVectorsGenerator.generate())
        assertEquals(rendered, gson.toJson(RemoteCommandVectorsGenerator.generate()))
        File("build/e2e-vectors").mkdirs()
        File("build/e2e-vectors/remote-command-2026-11-20.json").writeText(rendered)
    }

    private fun assertSealedFailed() {
        assertEquals(RemoteCommandLogEntry.STATUS_SEALED_FAILED, bench.repository.recentLog.value.single().status)
        assertEquals(0, bench.safeVerb.runs)
    }
}
