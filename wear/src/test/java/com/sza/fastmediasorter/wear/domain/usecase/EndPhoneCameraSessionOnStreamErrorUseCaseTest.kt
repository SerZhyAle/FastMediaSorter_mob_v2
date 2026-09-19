package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.PhoneCameraFailure
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraSessionState
import com.sza.fastmediasorter.wear.domain.repository.PhoneCameraSessionHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndPhoneCameraSessionOnStreamErrorUseCaseTest {

    @Test
    fun `a dead stream on the served address ends the session`() {
        val holder = holderServing(SERVED_URL)

        val ended = EndPhoneCameraSessionOnStreamErrorUseCase(holder)(SERVED_URL)

        assertTrue(ended)
        assertEquals(
            PhoneCameraSessionState.Refused(PhoneCameraFailure.ENDED),
            holder.state.value
        )
    }

    @Test
    fun `a dead stream on another address leaves the session alone`() {
        val holder = holderServing(SERVED_URL)

        val ended = EndPhoneCameraSessionOnStreamErrorUseCase(holder)(OTHER_URL)

        assertFalse(ended)
        assertTrue(holder.state.value is PhoneCameraSessionState.Live)
    }

    @Test
    fun `nothing is ended when no session is running`() {
        val holder = PhoneCameraSessionHolder()

        val ended = EndPhoneCameraSessionOnStreamErrorUseCase(holder)(SERVED_URL)

        assertFalse(ended)
        assertEquals(PhoneCameraSessionState.Idle, holder.state.value)
    }

    private fun holderServing(url: String) = PhoneCameraSessionHolder().apply {
        markLive(
            PhoneCameraSessionState.Live(
                requestId = "request-1",
                url = url,
                lenses = emptyList(),
                activeLensId = null
            )
        )
    }

    private companion object {
        const val SERVED_URL = "rtsp://192.168.1.195:8554/camera"
        const val OTHER_URL = "rtsp://192.168.1.200:8554/camera"
    }
}
