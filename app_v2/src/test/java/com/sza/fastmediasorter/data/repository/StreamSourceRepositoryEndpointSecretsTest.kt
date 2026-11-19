package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.BroadcastSealDto
import com.sza.fastmediasorter.data.broadcast.BroadcastSecretStorageException
import com.sza.fastmediasorter.data.broadcast.KeystoreBroadcastSecretBox
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoint
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoints
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import com.sza.fastmediasorter.testing.fakes.FakeBroadcastSecretBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** S4675: no relay key or tunnel pin reaches the table in clear text, and no saved broadcast is lost doing so. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamSourceRepositoryEndpointSecretsTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val box = FakeBroadcastSecretBox()
    private val dao get() = dbRule.db.streamSourceDao()
    private val secrets get() = BroadcastEndpointSecretsRepository(dbRule.db, dao)
    private val repo get() = StreamSourceRepository(
        dbRule.db,
        dao,
        dbRule.db.streamQualityMemoryDao(),
        dbRule.db.streamUserStateDao(),
        CoroutineScope(Dispatchers.Unconfined),
    )

    @Before
    fun installBox() {
        BroadcastEndpointCodec.secretBox = box
    }

    @After
    fun restoreBox() {
        BroadcastEndpointCodec.secretBox = KeystoreBroadcastSecretBox
    }

    @Test
    fun add_sealsTheKeysBeforeTheWriteAndTheRowStillPlaysAfterARestart() = runTest {
        repo.add(row("a", endpoints = clearEndpoints(GROUP_KEY)))

        val stored = requireNotNull(dao.getById("a")?.broadcastEndpoints)
        assertNoClearSecret(stored)
        val relay = BroadcastExchangeEndpoints.playOrder(
            stored,
            URL
        ).filterIsInstance<BroadcastExchangeEndpoint.Relay>()
        assertEquals(GROUP_KEY, relay.single().groupKey)
    }

    @Test
    fun add_refusesAClearSecretItCouldNotSealAndWritesNothing() = runTest {
        box.sealOk = false

        assertThrows(BroadcastSecretStorageException::class.java) {
            runBlocking { repo.add(row("a", endpoints = clearEndpoints(GROUP_KEY))) }
        }

        assertNull(dao.getById("a"))
    }

    @Test
    fun add_writesAListWithNoSecretAsItIs() = runTest {
        val lan = BroadcastEndpointCodec.encode(listOf(BroadcastEndpointDto(url = URL)))

        repo.add(row("a", endpoints = lan))

        assertEquals(lan, dao.getById("a")?.broadcastEndpoints)
        assertEquals(0, box.seals)
    }

    @Test
    fun refreshBroadcastEndpoints_sealsTheNewListAndAFailureKeepsTheOldOne() = runTest {
        repo.add(row("a", endpoints = clearEndpoints(GROUP_KEY)))
        val before = requireNotNull(dao.getById("a")?.broadcastEndpoints)

        repo.refreshBroadcastEndpoints("a", clearEndpoints(OTHER_KEY))
        val refreshed = requireNotNull(dao.getById("a")?.broadcastEndpoints)
        assertNoClearSecret(refreshed)
        assertNotEquals(before, refreshed)

        box.sealOk = false
        assertThrows(BroadcastSecretStorageException::class.java) {
            runBlocking { repo.refreshBroadcastEndpoints("a", clearEndpoints(GROUP_KEY)) }
        }
        assertEquals(refreshed, dao.getById("a")?.broadcastEndpoints)
    }

    @Test
    fun migration_sealsEverySavedRowAndKeepsEverythingTheUserOwns() = runTest {
        dao.upsert(row("a", endpoints = clearEndpoints(GROUP_KEY), pinned = true, sortIndex = 5, title = "Mine"))
        dao.upsert(row("b", url = "http://192.168.1.2/x", endpoints = clearEndpoints(OTHER_KEY)))
        dao.upsert(row("c", url = "http://192.168.1.3/x", endpoints = null))

        val report = secrets.protectStored()

        assertEquals(BroadcastEndpointSecretsRepository.Migration(migrated = 2, raced = 0, failed = 0), report)
        val a = requireNotNull(dao.getById("a"))
        assertNoClearSecret(requireNotNull(a.broadcastEndpoints))
        assertNoClearSecret(requireNotNull(dao.getById("b")?.broadcastEndpoints))
        assertTrue(a.pinned)
        assertEquals(5, a.sortIndex)
        assertEquals("Mine", a.title)
        assertNull(dao.getById("c")?.broadcastEndpoints)
        val expected = BroadcastEndpointCodec.decode(clearEndpoints(GROUP_KEY), box)
        assertEquals(expected, BroadcastEndpointCodec.decode(a.broadcastEndpoints, box))
    }

    @Test
    fun migration_isRepeatableAndTheSecondRunHasNothingLeftToDo() = runTest {
        dao.upsert(row("a", endpoints = clearEndpoints(GROUP_KEY)))
        secrets.protectStored()
        val sealed = dao.getById("a")?.broadcastEndpoints

        val second = secrets.protectStored()

        assertEquals(BroadcastEndpointSecretsRepository.Migration(0, 0, 0), second)
        assertEquals(sealed, dao.getById("a")?.broadcastEndpoints)
    }

    @Test
    fun migration_aSealFailureLeavesTheRowUsableAndTheNextRunFinishesIt() = runTest {
        val clear = clearEndpoints(GROUP_KEY)
        dao.upsert(row("a", endpoints = clear))
        box.sealOk = false

        val failed = secrets.protectStored()

        assertEquals(1, failed.failed)
        assertEquals(clear, dao.getById("a")?.broadcastEndpoints)
        val playable = BroadcastExchangeEndpoints.playOrder(clear, URL)
        assertTrue(playable.any { it is BroadcastExchangeEndpoint.Relay })

        box.sealOk = true
        assertEquals(1, secrets.protectStored().migrated)
        assertNoClearSecret(requireNotNull(dao.getById("a")?.broadcastEndpoints))
    }

    @Test
    fun migration_neverOverwritesARowARefreshChangedMeanwhile() = runTest {
        dao.upsert(row("a", endpoints = clearEndpoints(GROUP_KEY)))
        val refreshed = requireNotNull(BroadcastEndpointCodec.encode(listOf(BroadcastEndpointDto(url = URL))))
        box.onSeal = { runBlocking { dao.updateBroadcastEndpoints("a", refreshed) } }

        val report = secrets.protectStored()

        assertEquals(1, report.raced)
        assertEquals(0, report.migrated)
        assertEquals(refreshed, dao.getById("a")?.broadcastEndpoints)
    }

    @Test
    fun migration_aRowWhoseKeyIsLostKeepsItsRowAndItsLanEndpoint() = runTest {
        dao.upsert(row("a", endpoints = clearEndpoints(GROUP_KEY)))
        secrets.protectStored()
        box.openOk = false

        val stored = requireNotNull(dao.getById("a")?.broadcastEndpoints)
        val playable = BroadcastExchangeEndpoints.playOrder(stored, URL)

        assertFalse(playable.any { it is BroadcastExchangeEndpoint.Relay })
        assertTrue(playable.any { it is BroadcastExchangeEndpoint.Lan })
        assertNotNull(dao.getById("a"))
    }

    private fun assertNoClearSecret(stored: String) {
        assertFalse(stored.contains(GROUP_KEY))
        assertFalse(stored.contains(OTHER_KEY))
        assertFalse(stored.contains(PIN))
        assertFalse(BroadcastEndpointCodec.holdsClearSecret(stored))
    }

    private fun clearEndpoints(key: String): String? = BroadcastEndpointCodec.encode(
        listOf(
            BroadcastEndpointDto(url = URL, transport = "HTTP"),
            BroadcastEndpointDto(url = RELAY_URL, transport = "RELAY", seal = BroadcastSealDto(key = key)),
            BroadcastEndpointDto(
                url = TUNNEL_URL,
                transport = "TUNNEL",
                inner = URL,
                seal = BroadcastSealDto(pin = PIN),
            ),
        ),
    )

    private fun row(
        id: String,
        url: String = URL,
        endpoints: String?,
        pinned: Boolean = false,
        sortIndex: Int = 0,
        title: String = "Broadcast",
    ) = StreamSourceEntity(
        id = id,
        url = url,
        title = title,
        mediaKind = "AUDIO",
        sourceOrigin = "MANUAL",
        sortIndex = sortIndex,
        pinned = pinned,
        addedAt = 1L,
        broadcastEndpoints = endpoints,
    )

    private companion object {
        const val URL = "http://192.168.1.97:8768/live-audio.aac"
        const val RELAY_URL = "https://exchange.example.org:8443/v2/b/Zm9vYmFyYmF6cXV4MTIzNA/stream"
        const val TUNNEL_URL = "fmsx://exchange.example.org:8443/b/Zm9vYmFyYmF6cXV4MTIzNA/http"
        const val GROUP_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val OTHER_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val PIN = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}
