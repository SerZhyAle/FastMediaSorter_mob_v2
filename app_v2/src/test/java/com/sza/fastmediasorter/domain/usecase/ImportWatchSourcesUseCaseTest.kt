package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.domain.model.WearSourceTombstonePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.testing.fakes.FakeWearResourceIdAliasStore
import com.sza.fastmediasorter.testing.fakes.FakeWearResourceTombstoneStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportWatchSourcesUseCaseTest {

    private val credentialsRepository = mockk<NetworkCredentialsRepository>(relaxed = true)
    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private lateinit var stampStore: FakeStampStore
    private lateinit var tombstoneStore: FakeWearResourceTombstoneStore
    private lateinit var aliasStore: FakeWearResourceIdAliasStore
    private lateinit var useCase: ImportWatchSourcesUseCase

    private fun source(
        type: String = "SMB",
        server: String = "host",
        port: Int = 445,
        id: String = "src",
        name: String = "My Share",
        lastEditedAt: Long? = null,
    ) = WearNetworkSourcePayload(
        id = id,
        type = type,
        name = name,
        server = server,
        port = port,
        username = "user",
        password = "pw",
        basePath = "/base",
        lastEditedAt = lastEditedAt,
    )

    @Before
    fun setup() {
        // Bypass Android Keystore in NetworkCredentialsEntity.create.
        mockkObject(CryptoHelper)
        every { CryptoHelper.encrypt(any()) } returns "enc"
        stampStore = FakeStampStore()
        tombstoneStore = FakeWearResourceTombstoneStore()
        aliasStore = FakeWearResourceIdAliasStore()
        useCase = ImportWatchSourcesUseCase(
            resourceRepository,
            credentialsRepository,
            Gson(),
            stampStore,
            tombstoneStore,
            aliasStore
        )
    }

    @After
    fun tearDown() {
        unmockkObject(CryptoHelper)
    }

    @Test
    fun `existing credential is skipped and not inserted`() = runTest {
        coEvery {
            credentialsRepository.getByTypeServerAndPort("SMB", "host", 445)
        } returns NetworkCredentialsEntity(
            credentialId = "x", type = "SMB", server = "host", port = 445,
            username = "user", encryptedPassword = "enc"
        )

        val result = useCase(WearSourcesExportPayload(listOf(source()), "Watch"))

        assertTrue(result.isSuccess)
        assertEquals(ImportWatchResult(added = 0, skipped = 1), result.getOrThrow())
        coVerify(exactly = 0) { credentialsRepository.insert(any()) }
        coVerify(exactly = 0) { resourceRepository.addResource(any()) }
    }

    @Test
    fun `new source inserts credential and resource`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns null

        val result = useCase(WearSourcesExportPayload(listOf(source()), "Watch"))

        assertEquals(ImportWatchResult(added = 1, skipped = 0), result.getOrThrow())
        coVerify(exactly = 1) { credentialsRepository.insert(any()) }
        coVerify(exactly = 1) { resourceRepository.addResource(any()) }
    }

    @Test
    fun `unknown type falls back to SMB resource type`() = runTest {
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns null
        val resourceSlot = slot<MediaResource>()
        coEvery { resourceRepository.addResource(capture(resourceSlot)) } returns 1L

        useCase(WearSourcesExportPayload(listOf(source(type = "BOGUS")), "Watch")).getOrThrow()

        assertEquals(
            com.sza.fastmediasorter.domain.model.ResourceType.SMB,
            resourceSlot.captured.type
        )
    }

    @Test
    fun `empty payload yields zero counts`() = runTest {
        val result = useCase(WearSourcesExportPayload(emptyList(), "Watch"))
        assertEquals(ImportWatchResult(added = 0, skipped = 0), result.getOrThrow())
    }

    // S2502: this leg used to skip on any match. It now ranks the two edits and can replace.

    @Test
    fun `a watch record matched by resource id and stamped later updates the phone resource`() = runTest {
        givenPhoneResource(id = 7L, name = "phone name", stamp = SENT_AT - 9_000L)

        val result = useCase(
            exportPayload(source(id = "7", name = "watch name", lastEditedAt = SENT_AT - 1_000L)),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.getOrThrow().updated)
        assertEquals(0, result.getOrThrow().skipped)
        coVerify(exactly = 1) { resourceRepository.updateResource(match { it.name == "watch name" }) }
    }

    @Test
    fun `a watch record stamped earlier leaves the phone resource untouched`() = runTest {
        givenPhoneResource(id = 7L, name = "phone name", stamp = SENT_AT)

        val result = useCase(
            exportPayload(source(id = "7", name = "watch name", lastEditedAt = SENT_AT - 9_000L)),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(0, result.getOrThrow().updated)
        assertEquals(1, result.getOrThrow().skipped)
        coVerify(exactly = 0) { resourceRepository.updateResource(any()) }
    }

    @Test
    fun `a record whose id matches nothing still matches by its address tuple`() = runTest {
        givenPhoneResource(id = 7L, name = "phone name", stamp = SENT_AT - 9_000L)
        coEvery {
            credentialsRepository.getByTypeServerAndPort("SMB", "host", 445)
        } returns storedCredentials()

        val result = useCase(
            exportPayload(source(id = "not-a-number", name = "watch name", lastEditedAt = SENT_AT)),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.getOrThrow().updated)
    }

    @Test
    fun `a payload with no sentAt merges without applying a skew`() = runTest {
        givenPhoneResource(id = 7L, name = "phone name", stamp = 1_000L)

        val result = useCase(
            WearSourcesExportPayload(listOf(source(id = "7", lastEditedAt = 2_000L)), "Watch"),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.getOrThrow().updated)
        assertEquals(2_000L, stampStore.stamps["7"])
    }

    @Test
    fun `an incoming deletion removes the phone resource and keeps its tombstone`() = runTest {
        givenPhoneResource(id = 7L, name = "phone name", stamp = SENT_AT - 9_000L)

        useCase(
            exportPayload(tombstones = listOf(tombstone("7", SENT_AT))),
            receivedAtEpochMillis = RECEIVED_AT
        ).getOrThrow()

        coVerify(exactly = 1) { resourceRepository.deleteResource(7L) }
        // Stored in the phone's own time base, so the next exchange needs no second skew measurement.
        assertEquals(listOf(RECEIVED_AT), tombstoneStore.read().map { it.deletedAt })
    }

    @Test
    fun `a deletion older than the local edit leaves the resource alone`() = runTest {
        givenPhoneResource(id = 7L, name = "phone name", stamp = SENT_AT + 5_000L)

        useCase(
            exportPayload(tombstones = listOf(tombstone("7", SENT_AT))),
            receivedAtEpochMillis = RECEIVED_AT
        ).getOrThrow()

        coVerify(exactly = 0) { resourceRepository.deleteResource(any()) }
        assertTrue(tombstoneStore.read().isEmpty())
    }

    @Test
    fun `a newer incoming edit cancels an older tombstone and brings the resource back`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns null
        tombstoneStore.record(tombstone("7", SENT_AT - 5_000L))

        val result = useCase(
            exportPayload(source(id = "7", lastEditedAt = SENT_AT)),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.getOrThrow().added)
        assertTrue(tombstoneStore.read().isEmpty())
    }

    @Test
    fun `an incoming record older than the tombstone stays deleted`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        tombstoneStore.record(tombstone("7", SENT_AT + 5_000L))

        val result = useCase(
            exportPayload(source(id = "7", lastEditedAt = SENT_AT)),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(0, result.getOrThrow().added)
        assertEquals(1, result.getOrThrow().skipped)
        assertEquals(listOf("7"), tombstoneStore.read().map { it.id })
    }

    @Test
    fun `a payload carrying no tombstone collection deletes nothing`() = runTest {
        givenPhoneResource(id = 7L, name = "phone name", stamp = SENT_AT - 9_000L)

        val result = useCase(
            exportPayload(source(id = "7", name = "watch name", lastEditedAt = SENT_AT)),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.getOrThrow().updated)
        coVerify(exactly = 0) { resourceRepository.deleteResource(any()) }
    }

    @Test
    fun `a watch-created source is aliased to the phone id it was stored under`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        coEvery { credentialsRepository.getByTypeServerAndPort(any(), any(), any()) } returns null
        coEvery { resourceRepository.addResource(any()) } returns 42L

        useCase(
            exportPayload(source(id = WATCH_ID, lastEditedAt = SENT_AT)),
            receivedAtEpochMillis = RECEIVED_AT
        ).getOrThrow()

        assertEquals(42L, aliasStore.aliases[WATCH_ID])
    }

    @Test
    fun `a tombstone naming a watch id deletes the phone resource it was aliased to`() = runTest {
        givenPhoneResource(id = 42L, name = "from the watch", stamp = SENT_AT - 9_000L)
        aliasStore.record(WATCH_ID, 42L)

        useCase(
            exportPayload(tombstones = listOf(tombstone(WATCH_ID, SENT_AT))),
            receivedAtEpochMillis = RECEIVED_AT
        ).getOrThrow()

        coVerify(exactly = 1) { resourceRepository.deleteResource(42L) }
        // The row is gone, so the alias can only mislead a later exchange.
        assertTrue(aliasStore.aliases.isEmpty())
    }

    @Test
    fun `an aliased deletion older than the local edit leaves the resource alone`() = runTest {
        givenPhoneResource(id = 42L, name = "from the watch", stamp = SENT_AT + 5_000L)
        aliasStore.record(WATCH_ID, 42L)

        useCase(
            exportPayload(tombstones = listOf(tombstone(WATCH_ID, SENT_AT))),
            receivedAtEpochMillis = RECEIVED_AT
        ).getOrThrow()

        coVerify(exactly = 0) { resourceRepository.deleteResource(any()) }
        assertEquals(42L, aliasStore.aliases[WATCH_ID])
    }

    @Test
    fun `a tombstone naming an unknown foreign id deletes nothing`() = runTest {
        givenPhoneResource(id = 42L, name = "from the watch", stamp = SENT_AT - 9_000L)

        useCase(
            exportPayload(tombstones = listOf(tombstone(WATCH_ID, SENT_AT))),
            receivedAtEpochMillis = RECEIVED_AT
        ).getOrThrow()

        coVerify(exactly = 0) { resourceRepository.deleteResource(any()) }
    }

    private fun tombstone(id: String, deletedAt: Long) =
        WearSourceTombstonePayload(id = id, deletedAt = deletedAt)

    private fun givenPhoneResource(id: Long, name: String, stamp: Long) {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            MediaResource(
                id = id,
                name = name,
                path = "/base",
                type = com.sza.fastmediasorter.domain.model.ResourceType.SMB,
                credentialsId = "cred-$id"
            )
        )
        coEvery { credentialsRepository.getByCredentialId("cred-$id") } returns storedCredentials()
        stampStore.stamps[id.toString()] = stamp
    }

    private fun storedCredentials() = NetworkCredentialsEntity(
        id = 3L,
        credentialId = "cred-7",
        type = "SMB",
        server = "host",
        port = 445,
        username = "user",
        encryptedPassword = "enc"
    )

    private fun exportPayload(
        vararg items: WearNetworkSourcePayload,
        tombstones: List<WearSourceTombstonePayload> = emptyList()
    ) = WearSourcesExportPayload(items.toList(), "Watch", sentAt = SENT_AT, tombstones = tombstones)
}

// S2502: fixed times, so the skew the exchange measures is a known number.
private const val SKEW = 700L

// S2507 phase 04: the shape NetworkSource.id defaults to on the watch - never a phone row id.
private const val WATCH_ID = "6f1c8b2e-0f4a-4f1e-9a3d-2c7b5e8d1a04"
private val SENT_AT = System.currentTimeMillis() - 60_000L
private val RECEIVED_AT = SENT_AT + SKEW

private class FakeStampStore : com.sza.fastmediasorter.data.repository.wear.WearResourceStampStore {

    val stamps = mutableMapOf<String, Long>()

    override fun readStamps(): Map<String, Long> = stamps.toMap()

    override fun stampEdit(resourceId: String) {
        stamps[resourceId] = System.currentTimeMillis()
    }

    override fun writeStamp(resourceId: String, atEpochMillis: Long) {
        stamps[resourceId] = atEpochMillis
    }

    override fun forget(resourceId: String) {
        stamps.remove(resourceId)
    }
}
