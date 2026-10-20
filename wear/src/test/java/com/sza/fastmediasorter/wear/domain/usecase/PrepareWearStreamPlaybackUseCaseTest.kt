package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.google.gson.JsonObject
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPassStore
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPlaybackResolver
import com.sza.fastmediasorter.wear.data.exchange.WearStoredBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPass
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.foldWearStreamIdentity
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamUsageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S4261: a channel handed off with its descriptor is resolved before any player sees an address. */
class PrepareWearStreamPlaybackUseCaseTest {

    private val selectedMediaManager = SelectedMediaManager()
    private val usageRepository: WearStreamUsageRepository = mockk(relaxed = true)
    private val passStore: WearBroadcastPassStore = mockk(relaxed = true)
    private val resolver: WearBroadcastPlaybackResolver = mockk()

    private val useCase = PrepareWearStreamPlaybackUseCase(
        selectedMediaManager = selectedMediaManager,
        playbackSetManager = mockk<PlaybackSetManager>(relaxed = true),
        usageRepository = usageRepository,
        preferencesRepository = mockk<WearPreferencesRepository>(relaxed = true),
        passStore = passStore,
        resolver = resolver,
    )

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `a channel without a descriptor plays its own url`() = runBlocking {
        val target = useCase(channel(hasBroadcast = null))

        assertFalse(target.unplayable)
        assertEquals(LAN_URL, selectedMediaManager.selectedFile.value?.streamUri)
        coVerify(exactly = 0) { resolver.resolve(any()) }
    }

    @Test
    fun `a playable descriptor selects the loopback url and counts the identity url`() = runBlocking {
        coEvery { passStore.get(LAN_URL) } returns stored()
        coEvery { resolver.resolve(any()) } returns WearBroadcastPlayback.Playable(LOOPBACK_URL, "Live", true)

        val target = useCase(channel(hasBroadcast = true))

        assertFalse(target.unplayable)
        assertTrue(target.isVideo)
        assertEquals(LOOPBACK_URL, selectedMediaManager.selectedFile.value?.streamUri)
        coVerify { usageRepository.recordPlay(foldWearStreamIdentity(LAN_URL)) }
        coVerify {
            resolver.resolve(match { it.credentials?.password == "secret" && it.broadcastId == "b1" })
        }
    }

    @Test
    fun `an unavailable descriptor selects nothing`() = runBlocking {
        coEvery { passStore.get(LAN_URL) } returns stored()
        coEvery { resolver.resolve(any()) } returns
            WearBroadcastPlayback.Unavailable(WearBroadcastPlayback.Reason.UNREACHABLE)

        val target = useCase(channel(hasBroadcast = true))

        assertTrue(target.unplayable)
        assertNull(selectedMediaManager.selectedFile.value)
        coVerify(exactly = 0) { usageRepository.recordPlay(any()) }
    }

    @Test
    fun `a marked channel whose stored broadcast is gone is unplayable`() = runBlocking {
        coEvery { passStore.get(LAN_URL) } returns null

        val target = useCase(channel(hasBroadcast = true))

        assertTrue(target.unplayable)
        assertNull(selectedMediaManager.selectedFile.value)
    }

    private fun channel(hasBroadcast: Boolean?) = WearStreamChannel(
        id = "c1",
        name = "Live",
        url = LAN_URL,
        mediaKind = "AUDIO",
        origin = WearStreamChannel.ORIGIN_PHONE,
        hasBroadcast = hasBroadcast,
    )

    private fun stored() = WearStoredBroadcast(
        broadcastId = "b1",
        descriptor = JsonObject().apply { addProperty("mode", "VIDEO_AUDIO") },
        pass = WearBroadcastPass("own", "secret"),
    )

    private companion object {
        const val LAN_URL = "http://192.168.1.20:8080/live"
        const val LOOPBACK_URL = "http://127.0.0.1:41234/live"
    }
}
