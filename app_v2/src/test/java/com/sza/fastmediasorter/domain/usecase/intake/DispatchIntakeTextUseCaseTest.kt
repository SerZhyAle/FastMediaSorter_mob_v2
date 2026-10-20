package com.sza.fastmediasorter.domain.usecase.intake

import com.sza.fastmediasorter.domain.model.ExchangeConnectionString
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringCodec
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringLookalike
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringResult
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.model.IntakeTextDispatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4155: the dispatcher is the one classification point every intake surface calls (ADR-1), so each
 * kind is pinned here before any UI consumes it.
 */
class DispatchIntakeTextUseCaseTest {

    private val useCase = DispatchIntakeTextUseCase()

    @Test
    fun `a full connection string dispatches to the exchange string with the same value`() {
        val value = fullValue()
        val dispatch = useCase.dispatch(value.encode())
        assertTrue(dispatch is IntakeTextDispatch.ExchangeString)
        assertEquals(value, (dispatch as IntakeTextDispatch.ExchangeString).value)
    }

    @Test
    fun `outer white space is trimmed before the codec sees the text`() {
        val dispatch = useCase.dispatch(" \r\n$MINIMAL\t")
        assertTrue(dispatch is IntakeTextDispatch.ExchangeString)
    }

    @Test
    fun `a later major is refused before any field is read`() {
        val dispatch = useCase.dispatch("FMSEXCH2:x=192.0.2.10%3A22022&k=v")
        assertEquals(IntakeTextDispatch.NewerExchangeMajor(2), dispatch)
    }

    // DEVICE-EXCHANGE 1.3: a higher prefix carrying fields this version could read is still not imported.
    @Test
    fun `a later major with readable fields yields no exchange string`() {
        val readable = fullValue().encode().removePrefix(ExchangeConnectionStringCodec.PREFIX)
        val dispatch = useCase.dispatch("FMSEXCH2:$readable")
        assertEquals(IntakeTextDispatch.NewerExchangeMajor(2), dispatch)
    }

    @Test
    fun `the later-major refusal is the format-unsupported row with its own advice`() {
        val row = ExchangeMessage.FORMAT_UNSUPPORTED
        assertEquals("format-unsupported", row.code)
        assertEquals(ExchangeMessage.Kind.LOCAL, row.kind)
        assertNull(row.step)
    }

    @Test
    fun `a complete pairing code dispatches to the share code`() {
        val dispatch = useCase.dispatch(
            "FMSSFTP1:h=example.org&p=22&u=alice&pw=secret&fp=SHA256:Ul2rC03A44auS8B7ibLjJaAApHooIxAbCdEf",
        )
        assertTrue(dispatch is IntakeTextDispatch.ShareCode)
        val payload = (dispatch as IntakeTextDispatch.ShareCode).payload
        assertEquals("alice", payload.username)
    }

    @Test
    fun `a compressed companion config dispatches by its prefix`() {
        assertEquals(IntakeTextDispatch.CompanionConfig, useCase.dispatch("FMSCFG1:QUJD"))
    }

    @Test
    fun `plain text and an URL are unrecognized, the URL keeps its lookalike`() {
        assertEquals(IntakeTextDispatch.Unrecognized(), useCase.dispatch("hello"))
        assertEquals(
            IntakeTextDispatch.Unrecognized(ExchangeConnectionStringLookalike.URL),
            useCase.dispatch("https://a.example:1/"),
        )
    }

    @Test
    fun `a damaged share code is unrecognized and keeps the share-code lookalike`() {
        assertEquals(
            IntakeTextDispatch.Unrecognized(ExchangeConnectionStringLookalike.SHARE_CODE),
            useCase.dispatch("FMSSFTP2:AbCdEf"),
        )
    }

    private fun fullValue(): ExchangeConnectionString =
        (
            ExchangeConnectionStringCodec.decode(
                "$MINIMAL&fp=SHA256%3AUl2rC03A44auS8B7ibLjJaAApHooIx%2F4R%2BSSgDTXUtY&n=Office&u=alice"
            )
                as ExchangeConnectionStringResult.Read
            ).value

    private companion object {
        const val MINIMAL = "${ExchangeConnectionStringCodec.PREFIX}x=192.0.2.10%3A22022"
    }
}
