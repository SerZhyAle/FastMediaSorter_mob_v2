package com.sza.fastmediasorter.data.network.datasource

import com.sza.fastmediasorter.data.remote.sftp.SftpFailureCategory
import com.sza.fastmediasorter.data.remote.sftp.SftpOperationFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException

class SftpDataSourceTest {

    @Test
    fun expectedClose_pipeClosedDuringOpenStreamClose_isExpectedStreamClose() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            throwable = IOException("Pipe closed"),
            channelAlreadyBroken = false,
            streamWasOpen = true,
        )

        assertEquals(SftpFailureCategory.EXPECTED_STREAM_CLOSE, failure.category)
    }

    @Test
    fun expectedClose_interruptedClose_isExpectedStreamClose() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            throwable = InterruptedIOException("interrupted"),
            channelAlreadyBroken = false,
            streamWasOpen = true,
        )

        assertEquals(SftpFailureCategory.EXPECTED_STREAM_CLOSE, failure.category)
    }

    @Test
    fun activeFailure_connectionResetDuringClose_remainsTransient() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            throwable = IOException("Connection reset"),
            channelAlreadyBroken = false,
            streamWasOpen = true,
        )

        assertEquals(SftpFailureCategory.TRANSIENT, failure.category)
    }

    @Test
    fun idempotentClose_afterBrokenChannel_isExpectedStreamClose() {
        val failure = SftpOperationFailure.fromStreamCloseThrowable(
            throwable = IOException("any close failure after broken read"),
            channelAlreadyBroken = true,
            streamWasOpen = false,
        )

        assertEquals(SftpFailureCategory.EXPECTED_STREAM_CLOSE, failure.category)
    }
}
