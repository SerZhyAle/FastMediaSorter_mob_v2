package com.sza.fastmediasorter.domain.usecase.streams

import com.google.gson.Gson
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.BroadcastSealDto
import com.sza.fastmediasorter.data.broadcast.KeystoreBroadcastSecretBox
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialPair
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore
import com.sza.fastmediasorter.testing.fakes.FakeBroadcastSecretBox
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** S4261: which form a broadcast travels to the watch in, and what the descriptor form carries. */
class BuildWatchBroadcastHandoffUseCaseTest {

    private val passwordStore: BroadcastPasswordStore = mockk()
    private val announcer: ExchangeBroadcastAnnouncer = mockk()
    private val registry: BroadcastCredentialRegistry = mockk()
    private val useCase = BuildWatchBroadcastHandoffUseCase(passwordStore, announcer, registry, Gson())

    private val lan = BroadcastEndpointDto(url = "http://192.168.1.20:8080/live", transport = "HTTP")
    private val relay = BroadcastEndpointDto(url = "https://relay.example/v2/b/b42/stream", transport = "RELAY")
    private val sealedRelay = relay.copy(seal = BroadcastSealDto(key = GROUP_KEY))
    private val box = FakeBroadcastSecretBox()

    @Before
    fun installBox() {
        BroadcastEndpointCodec.secretBox = box
    }

    @After
    fun restoreBox() {
        BroadcastEndpointCodec.secretBox = KeystoreBroadcastSecretBox
    }

    @Test
    fun `a manual row is plain`() = runBlocking {
        coEvery { passwordStore.get("s1") } returns null

        assertSame(BuildWatchBroadcastHandoffUseCase.Result.Plain, useCase.forStoredChannel(row(endpoints = null)))
    }

    @Test
    fun `a lan-only unprotected row is plain`() = runBlocking {
        coEvery { passwordStore.get("s1") } returns null

        assertSame(BuildWatchBroadcastHandoffUseCase.Result.Plain, useCase.forStoredChannel(row(listOf(lan))))
    }

    @Test
    fun `a relay row with its pair travels with auth and the pass`() = runBlocking {
        coEvery { passwordStore.get("s1") } returns BroadcastPasswordStore.StoredPair("viewer", "secret")

        val result = useCase.forStoredChannel(row(listOf(lan, relay)))

        assertTrue(result is BuildWatchBroadcastHandoffUseCase.Result.Handoff)
        result as BuildWatchBroadcastHandoffUseCase.Result.Handoff
        assertEquals("b42", result.broadcastId)
        assertEquals("AUDIO", result.mediaKind)
        assertEquals("viewer", result.descriptor.getAsJsonObject("auth").get("user").asString)
        assertEquals(2, result.descriptor.getAsJsonArray("endpoints").size())
        assertEquals("secret", result.pass?.password)
    }

    @Test
    fun `a pair without its password is password missing`() = runBlocking {
        coEvery { passwordStore.get("s1") } returns BroadcastPasswordStore.StoredPair("viewer", null)

        assertSame(
            BuildWatchBroadcastHandoffUseCase.Result.PasswordMissing,
            useCase.forStoredChannel(row(listOf(lan, relay))),
        )
    }

    @Test
    fun `a live lan-only broadcast is plain`() {
        val live = BroadcastDescriptorDto(url = lan.url, mode = "AUDIO_ONLY", endpoints = listOf(lan))
        every { registry.isProtected } returns false
        every { registry.ownPair } returns null
        every { announcer.ownDeviceDescriptor(live) } returns live

        assertSame(BuildWatchBroadcastHandoffUseCase.Result.Plain, useCase.forLiveBroadcast(live))
    }

    @Test
    fun `a live announced broadcast travels with the own pair`() {
        val live = BroadcastDescriptorDto(url = lan.url, mode = "VIDEO_AUDIO", endpoints = listOf(lan))
        val announced = live.copy(endpoints = listOf(lan, relay))
        every { registry.isProtected } returns true
        every { registry.ownPair } returns BroadcastCredentialPair("own", "own-secret")
        every { announcer.ownDeviceDescriptor(live) } returns announced
        every { announcer.announcedBroadcastId } returns "b42"

        val result = useCase.forLiveBroadcast(live)

        assertTrue(result is BuildWatchBroadcastHandoffUseCase.Result.Handoff)
        result as BuildWatchBroadcastHandoffUseCase.Result.Handoff
        assertEquals("b42", result.broadcastId)
        assertEquals("VIDEO", result.mediaKind)
        assertEquals("own", result.pass?.user)
        assertEquals("own-secret", result.pass?.password)
        assertEquals("own", result.descriptor.getAsJsonObject("auth").get("user").asString)
        assertNull(result.descriptor.get("credentials"))
    }

    // S4675: a saved row keeps its relay key behind the device's box and still hands it to the watch.

    @Test
    fun `a sealed relay row saved behind the box hands the opened key to the watch`() = runBlocking {
        coEvery { passwordStore.get("s1") } returns null
        val stored = savedBehindTheBox(listOf(lan, sealedRelay))
        assertFalse(stored.contains(GROUP_KEY))

        val result = useCase.forStoredChannel(rowStored(stored))

        result as BuildWatchBroadcastHandoffUseCase.Result.Handoff
        val seal = result.descriptor.getAsJsonArray("endpoints")[1].asJsonObject.getAsJsonObject("seal")
        assertEquals(GROUP_KEY, seal.get("key").asString)
        assertNull(seal.get("box"))
    }

    @Test
    fun `a row whose key was lost hands the watch a seal with no key and never a clear relay`() = runBlocking {
        coEvery { passwordStore.get("s1") } returns null
        val stored = savedBehindTheBox(listOf(lan, sealedRelay))
        box.loseKey()

        val result = useCase.forStoredChannel(rowStored(stored))

        result as BuildWatchBroadcastHandoffUseCase.Result.Handoff
        val seal = result.descriptor.getAsJsonArray("endpoints")[1].asJsonObject.getAsJsonObject("seal")
        assertEquals(BroadcastSealDto.VERSION, seal.get("v").asInt)
        assertNull(seal.get("key"))
        assertNull(seal.get("box"))
    }

    private fun savedBehindTheBox(endpoints: List<BroadcastEndpointDto>): String {
        val outcome = BroadcastEndpointCodec.protect(BroadcastEndpointCodec.encode(endpoints))
        return (outcome as BroadcastEndpointCodec.Protection.Protected).value
    }

    private fun rowStored(stored: String?) = row(null).copy(broadcastEndpoints = stored)

    private fun row(endpoints: List<BroadcastEndpointDto>?) = StreamSourceEntity(
        id = "s1",
        url = lan.url,
        title = "Phone live",
        mediaKind = "AUDIO",
        sourceOrigin = "IMPORTED",
        sortIndex = 0,
        addedAt = 0L,
        broadcastEndpoints = BroadcastEndpointCodec.encode(endpoints),
    )

    private companion object {
        const val GROUP_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
