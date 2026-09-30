package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.PhoneCameraFailure
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S3883: a late send failure of an older camera command must not refuse the newer one. */
class PhoneCameraSessionHolderTest {

    private val holder = PhoneCameraSessionHolder()

    @Test
    fun `failure of the outstanding command refuses it`() {
        holder.markRequested("a")

        assertTrue(holder.markRefusedIfAwaiting("a", PhoneCameraFailure.NO_PHONE))
        assertEquals(PhoneCameraSessionState.Refused(PhoneCameraFailure.NO_PHONE), holder.state.value)
    }

    @Test
    fun `late failure of an older command leaves the newer request outstanding`() {
        holder.markRequested("old")
        holder.markRequested("new")

        assertFalse(holder.markRefusedIfAwaiting("old", PhoneCameraFailure.NO_PHONE))
        assertEquals("new", holder.awaitingRequestId)
    }

    @Test
    fun `late failure after the session went live leaves it live`() {
        holder.markRequested("a")
        val live = PhoneCameraSessionState.Live(
            requestId = "a",
            url = "http://192.0.2.1:8080/cam",
            lenses = emptyList(),
            activeLensId = null,
        )
        holder.markLive(live)

        assertFalse(holder.markRefusedIfAwaiting("a", PhoneCameraFailure.NO_PHONE))
        assertEquals(live, holder.state.value)
    }
}
