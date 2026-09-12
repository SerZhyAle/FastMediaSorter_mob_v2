package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.usecase.ApplyWearSettingsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ReportWearSettingsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * S2461: the settings push is applied and answered on the responder's own scope, so the exchange
 * completes even when whoever handed it over is gone - the listener service that receives a push is
 * destroyed shortly after its callback returns, and its scope with it.
 */
class SettingsPushResponderTest {

    private val envelopeCodec = WearEventEnvelopeCodec()
    private lateinit var apply: ApplyWearSettingsUseCase
    private lateinit var report: ReportWearSettingsUseCase
    private lateinit var responder: SettingsPushResponder

    @Before
    fun setup() {
        apply = mockk()
        report = mockk()
        coEvery { apply(any(), any(), any(), any()) } returns Unit
        coEvery { report() } returns Result.success(Unit)
        responder = SettingsPushResponder(Gson(), apply, report)
    }

    @Test
    fun `a push is applied and then answered with a report`() {
        responder.respond(push("""{"audioEnabled":true}""", sentAt = SENT_AT), RECEIVED_AT)

        coVerify(timeout = WAIT_MILLIS) { report() }
        coVerifyOrder {
            apply(any(), SENT_AT, RECEIVED_AT, setOf("audioEnabled"))
            report()
        }
    }

    @Test
    fun `the exchange completes after the caller that handed it over is cancelled`() = runTest {
        val applyStarted = CompletableDeferred<Unit>()
        val applyMayFinish = CompletableDeferred<Unit>()
        coEvery { apply(any(), any(), any(), any()) } coAnswers {
            applyStarted.complete(Unit)
            applyMayFinish.await()
        }

        // Stands in for the listener service: alive while the push is handed over, then destroyed
        // while the apply is still running.
        val caller = launch {
            responder.respond(push("""{"audioEnabled":true}"""), RECEIVED_AT)
            awaitCancellation()
        }
        applyStarted.await()
        caller.cancelAndJoin()
        applyMayFinish.complete(Unit)

        coVerify(timeout = WAIT_MILLIS) { report() }
    }

    @Test
    fun `a push with nothing decodable is neither applied nor answered`() {
        responder.respond(push("not json"), RECEIVED_AT)
        // Exchanges run one at a time, so once the second one has reported the first has finished.
        responder.respond(push("""{"audioEnabled":true}"""), RECEIVED_AT)

        coVerify(timeout = WAIT_MILLIS) { report() }
        coVerify(exactly = 1) { apply(any(), any(), any(), any()) }
        coVerify(exactly = 1) { report() }
    }

    private fun push(json: String, sentAt: Long = SENT_AT): ByteArray = envelopeCodec.encode(
        WearEventEnvelope(eventType = "SETTINGS_PUSH", sentAt = sentAt, data = json.toByteArray())
    )

    private companion object {
        const val SENT_AT = 1_000L
        const val RECEIVED_AT = 1_500L
        const val WAIT_MILLIS = 5_000L
    }
}
