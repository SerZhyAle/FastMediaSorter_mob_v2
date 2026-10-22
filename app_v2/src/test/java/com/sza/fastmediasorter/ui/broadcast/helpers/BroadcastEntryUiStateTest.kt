package com.sza.fastmediasorter.ui.broadcast.helpers

import com.sza.fastmediasorter.broadcast.BroadcastFailure
import com.sza.fastmediasorter.broadcast.BroadcastLensChoice
import com.sza.fastmediasorter.broadcast.BroadcastLensOption
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2818: proves the two machine-checkable entry-screen behaviors - a controller without broadcast
 * support ends in Unavailable (no dead end in disabled flavors, strategic criterion 4) and a live
 * session renders the stop state, never a second start (ADR-2).
 */
class BroadcastEntryUiStateTest {

    @Test
    fun `unavailable controller maps every state to Unavailable`() {
        val states = listOf(
            BroadcastState.Idle,
            liveState(),
            failedState(),
        )
        states.forEach { state ->
            assertEquals(
                BroadcastEntryUi.UiState.Unavailable,
                BroadcastEntryUi.mapState(state, isAvailable = false),
            )
        }
    }

    @Test
    fun `idle with available controller renders Confirm`() {
        assertEquals(
            BroadcastEntryUi.UiState.Confirm,
            BroadcastEntryUi.mapState(BroadcastState.Idle, isAvailable = true),
        )
    }

    @Test
    fun `live session renders Live`() {
        assertEquals(
            BroadcastEntryUi.UiState.Live,
            BroadcastEntryUi.mapState(liveState(), isAvailable = true),
        )
    }

    @Test
    fun `failed session falls back to Confirm layout`() {
        assertEquals(
            BroadcastEntryUi.UiState.Confirm,
            BroadcastEntryUi.mapState(failedState(), isAvailable = true),
        )
    }

    @Test
    fun `settings card shows before the session and never during it`() {
        assertTrue(BroadcastEntryUi.showsSettingsCard(BroadcastState.Idle))
        assertTrue(BroadcastEntryUi.showsSettingsCard(failedState()))
        assertFalse(BroadcastEntryUi.showsSettingsCard(liveState()))
    }

    @Test
    fun `listener count shows only during the session`() {
        assertTrue(BroadcastEntryUi.showsListenerCount(liveState()))
        assertFalse(BroadcastEntryUi.showsListenerCount(BroadcastState.Idle))
        assertFalse(BroadcastEntryUi.showsListenerCount(failedState()))
    }

    @Test
    fun `settings card and listener count never share the screen`() {
        listOf(BroadcastState.Idle, failedState(), liveState()).forEach { state ->
            assertFalse(
                "both blocks visible in $state",
                BroadcastEntryUi.showsSettingsCard(state) && BroadcastEntryUi.showsListenerCount(state),
            )
        }
    }

    @Test
    fun `light row shows only for a live session with a camera of its own`() {
        assertTrue(BroadcastEntryUi.showsLightControls(liveState(mode = "VIDEO_AUDIO")))
        assertTrue(BroadcastEntryUi.showsLightControls(liveState(mode = "VIDEO_ONLY")))
        assertFalse(BroadcastEntryUi.showsLightControls(liveState(mode = "AUDIO_ONLY")))
        assertFalse(BroadcastEntryUi.showsLightControls(liveState(mode = "VIDEO_AUDIO", source = "file")))
        assertFalse(BroadcastEntryUi.showsLightControls(liveState(mode = "VIDEO_AUDIO", source = "screen")))
        assertFalse(BroadcastEntryUi.showsLightControls(BroadcastState.Idle))
        assertFalse(BroadcastEntryUi.showsLightControls(failedState()))
    }

    @Test
    fun `torch button needs a flash on the lens on air and a camera that is on`() {
        val flash = liveState(mode = "VIDEO_AUDIO", torchAvailable = true, cameraEnabled = true)
        assertTrue(BroadcastEntryUi.showsTorchButton(flash))
        assertFalse(
            "a front lens has no flash",
            BroadcastEntryUi.showsTorchButton(flash.copy(torchAvailable = false)),
        )
        assertFalse(
            "a muted camera sends black frames",
            BroadcastEntryUi.showsTorchButton(flash.copy(cameraEnabled = false)),
        )
        assertFalse(BroadcastEntryUi.showsTorchButton(flash.copy(source = "screen")))
        assertFalse(BroadcastEntryUi.showsTorchButton(BroadcastState.Idle))
    }

    @Test
    fun `an empty lens answer keeps the remembered lenses while the session holds the camera`() {
        val remembered = lensChoice("0", "1")
        assertEquals(
            remembered,
            BroadcastEntryUi.lensChoiceFor(BroadcastLensChoice.EMPTY, remembered, holdsCamera = true),
        )
    }

    @Test
    fun `an empty lens answer is believed when no session holds the camera`() {
        assertEquals(
            BroadcastLensChoice.EMPTY,
            BroadcastEntryUi.lensChoiceFor(BroadcastLensChoice.EMPTY, lensChoice("0"), holdsCamera = false),
        )
    }

    @Test
    fun `a non-empty lens answer always wins over the remembered one`() {
        val fresh = lensChoice("0", "1", "2")
        assertEquals(fresh, BroadcastEntryUi.lensChoiceFor(fresh, lensChoice("0"), holdsCamera = true))
        assertEquals(fresh, BroadcastEntryUi.lensChoiceFor(fresh, lensChoice("0"), holdsCamera = false))
    }

    @Test
    fun `nothing remembered stays empty even while the session holds the camera`() {
        assertEquals(
            BroadcastLensChoice.EMPTY,
            BroadcastEntryUi.lensChoiceFor(BroadcastLensChoice.EMPTY, BroadcastLensChoice.EMPTY, holdsCamera = true),
        )
    }

    @Test
    fun `a live landscape picture is fullscreen until the controls are asked for`() {
        assertTrue(
            BroadcastEntryUi.showsFullscreenPreview(isLandscape = true, pictureOnScreen = true, controlsShown = false),
        )
        assertFalse(
            BroadcastEntryUi.showsFullscreenPreview(isLandscape = true, pictureOnScreen = true, controlsShown = true),
        )
    }

    @Test
    fun `portrait never goes fullscreen`() {
        listOf(true, false).forEach { controlsShown ->
            assertFalse(
                BroadcastEntryUi.showsFullscreenPreview(
                    isLandscape = false,
                    pictureOnScreen = true,
                    controlsShown = controlsShown,
                ),
            )
        }
    }

    @Test
    fun `landscape without a live picture keeps the two-pane layout`() {
        listOf(true, false).forEach { controlsShown ->
            assertFalse(
                BroadcastEntryUi.showsFullscreenPreview(
                    isLandscape = true,
                    pictureOnScreen = false,
                    controlsShown = controlsShown,
                ),
            )
        }
    }

    private fun lensChoice(vararg ids: String) = BroadcastLensChoice(
        options = ids.map { id ->
            BroadcastLensOption(id = id, facing = BroadcastLensOption.FACING_BACK, zoomMultiplier = 1f)
        },
        initialLensId = ids.firstOrNull(),
    )

    private fun liveState(
        mode: String = "AUDIO_ONLY",
        source: String? = null,
        torchAvailable: Boolean = false,
        cameraEnabled: Boolean = false,
    ) = BroadcastState.Live(
        descriptor = BroadcastDescriptorDto(url = "http://192.168.1.5:8080/stream", mode = mode),
        startedAtElapsedRealtimeMs = 1_000L,
        cameraEnabled = cameraEnabled,
        source = source,
        torchAvailable = torchAvailable,
    )

    private fun failedState() = BroadcastState.Failed(
        failure = BroadcastFailure.NETWORK_UNAVAILABLE,
        detail = "port busy",
    )
}
