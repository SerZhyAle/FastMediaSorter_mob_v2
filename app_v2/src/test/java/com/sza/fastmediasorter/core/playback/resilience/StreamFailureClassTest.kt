package com.sza.fastmediasorter.core.playback.resilience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StreamFailureClassTest {

    @Test
    fun `behind live window is its own class, not a network failure`() {
        assertEquals(StreamFailureClass.BEHIND_LIVE_WINDOW, classify(CODE_BEHIND_LIVE))
    }

    @Test
    fun `the four unconditionally recoverable codes are transient`() {
        assertEquals(TRANSIENT, classify(CODE_TIMEOUT))
        assertEquals(TRANSIENT, classify(CODE_IO_UNSPECIFIED))
        assertEquals(TRANSIENT, classify(CODE_NET_FAILED))
        assertEquals(TRANSIENT, classify(CODE_NET_TIMEOUT))
    }

    @Test
    fun `the wider IO block the audio sites accept is transient too`() {
        assertEquals(TRANSIENT, classify(CODE_READ_POSITION))
        assertEquals(TRANSIENT, classify(MID_IO_ERROR_CODE))
    }

    @Test
    fun `a code outside every accepted set is not retryable`() {
        assertEquals(NOT_RETRYABLE, classify(DECODING_ERROR_CODE))
        assertEquals(NOT_RETRYABLE, classify(BELOW_IO_BLOCK_ERROR_CODE))
        assertEquals(NOT_RETRYABLE, classify(ABOVE_IO_BLOCK_ERROR_CODE))
    }

    @Test
    fun `a bad HTTP status is retryable only for 429 and 5xx`() {
        assertEquals(RETRYABLE_HTTP, classifyBadHttp(STATUS_TOO_MANY_REQUESTS))
        assertEquals(RETRYABLE_HTTP, classifyBadHttp(STATUS_INTERNAL_SERVER_ERROR))
        assertEquals(RETRYABLE_HTTP, classifyBadHttp(STATUS_SERVICE_UNAVAILABLE))
        assertEquals(RETRYABLE_HTTP, classifyBadHttp(STATUS_LAST_SERVER_ERROR))
    }

    @Test
    fun `a client-error status is not retryable`() {
        assertEquals(NOT_RETRYABLE, classifyBadHttp(STATUS_NOT_FOUND))
        assertEquals(NOT_RETRYABLE, classifyBadHttp(STATUS_BELOW_SERVER_BLOCK))
        assertEquals(NOT_RETRYABLE, classifyBadHttp(STATUS_ABOVE_SERVER_BLOCK))
    }

    @Test
    fun `an absent HTTP status is never read as a healthy one`() {
        val withoutStatus = StreamFailureClass.classify(
            errorCode = CODE_BAD_HTTP_STATUS,
            httpStatus = NO_HTTP_STATUS,
        )

        // ADR-4: no status observed is not evidence that the server was fine, so a null input must
        // not reach the retryable answer an observed 5xx does.
        assertEquals(NOT_RETRYABLE, withoutStatus)
        assertNotEquals(classifyBadHttp(STATUS_SERVICE_UNAVAILABLE), withoutStatus)
        assertEquals(classifyBadHttp(STATUS_NOT_FOUND), withoutStatus)
    }

    private fun classify(errorCode: Int): StreamFailureClass =
        StreamFailureClass.classify(errorCode = errorCode, httpStatus = NO_HTTP_STATUS)

    private fun classifyBadHttp(httpStatus: Int): StreamFailureClass =
        StreamFailureClass.classify(errorCode = CODE_BAD_HTTP_STATUS, httpStatus = httpStatus)

    private companion object {
        val TRANSIENT = StreamFailureClass.TRANSIENT_NETWORK
        val RETRYABLE_HTTP = StreamFailureClass.RETRYABLE_HTTP_STATUS
        val NOT_RETRYABLE = StreamFailureClass.NOT_RETRYABLE

        /** No response code was found in the cause chain - the ADR-4 "no evidence" input. */
        val NO_HTTP_STATUS: Int? = null

        const val CODE_BEHIND_LIVE = StreamFailureClass.ERROR_CODE_BEHIND_LIVE_WINDOW
        const val CODE_TIMEOUT = StreamFailureClass.ERROR_CODE_TIMEOUT
        const val CODE_IO_UNSPECIFIED = StreamFailureClass.ERROR_CODE_IO_UNSPECIFIED
        const val CODE_NET_FAILED = StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        const val CODE_NET_TIMEOUT = StreamFailureClass.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        const val CODE_BAD_HTTP_STATUS = StreamFailureClass.ERROR_CODE_IO_BAD_HTTP_STATUS
        const val CODE_READ_POSITION = StreamFailureClass.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE

        const val MID_IO_ERROR_CODE = 2005
        const val BELOW_IO_BLOCK_ERROR_CODE = 1999
        const val ABOVE_IO_BLOCK_ERROR_CODE = 2009
        const val DECODING_ERROR_CODE = 4001

        const val STATUS_TOO_MANY_REQUESTS = 429
        const val STATUS_NOT_FOUND = 404
        const val STATUS_BELOW_SERVER_BLOCK = 499
        const val STATUS_INTERNAL_SERVER_ERROR = 500
        const val STATUS_SERVICE_UNAVAILABLE = 503
        const val STATUS_LAST_SERVER_ERROR = 599
        const val STATUS_ABOVE_SERVER_BLOCK = 600
    }
}
