package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.ImportResult
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearEndpoint
import com.sza.fastmediasorter.wear.domain.model.WearEndpointPayload
import com.sza.fastmediasorter.wear.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.wear.domain.model.WearSourceTombstonePayload
import com.sza.fastmediasorter.wear.domain.model.WearSyncPayload
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// S2502: fixed times, so the skew the exchange measures is a known number rather than whatever the
// wall clock did between two statements. SKEW is what a phone running behind this watch produces.
private const val SKEW = 700L
private val SENT_AT = System.currentTimeMillis() - 60_000L
private val RECEIVED_AT = SENT_AT + SKEW
private const val MERGE_ID = "res-1"

class ImportNetworkSourcesUseCaseTest {

    private lateinit var fakeRepository: FakeNetworkSourceRepository
    private lateinit var useCase: ImportNetworkSourcesUseCase

    @Before
    fun setup() {
        fakeRepository = FakeNetworkSourceRepository()
        useCase = ImportNetworkSourcesUseCase(fakeRepository, mockk(relaxed = true))
    }

    @Test
    fun `stale payload returns zero counts`() = runTest {
        val stalePayload = WearSyncPayload(
            sentAt = System.currentTimeMillis() - (25 * 60 * 60 * 1000L), // 25h ago
            phoneName = "Phone",
            sources = listOf(makePayloadItem("id-1", "SMB"))
        )

        val result = useCase(stalePayload)

        assertEquals(ImportResult(added = 0, updated = 0, skipped = 0), result)
        assertEquals(0, fakeRepository.upsertCallCount)
    }

    @Test
    fun `new sources are added`() = runTest {
        val payload = freshPayload(listOf(
            makePayloadItem("new-1", "SMB"),
            makePayloadItem("new-2", "FTP")
        ))

        val result = useCase(payload)

        assertEquals(2, result.added)
        assertEquals(0, result.updated)
        assertEquals(0, result.skipped)
        assertEquals(2, fakeRepository.upsertCallCount)
    }

    @Test
    fun `existing sources are updated`() = runTest {
        fakeRepository.existing.add(makeSource("existing-1", NetworkSourceType.SMB))

        val payload = freshPayload(listOf(makePayloadItem("existing-1", "SMB")))

        val result = useCase(payload)

        assertEquals(0, result.added)
        assertEquals(1, result.updated)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `unknown type is skipped`() = runTest {
        val payload = freshPayload(listOf(
            makePayloadItem("id-ok", "SMB"),
            makePayloadItem("id-bad", "WEBDAV") // unknown
        ))

        val result = useCase(payload)

        assertEquals(1, result.added)
        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `mixed add update skip in single payload`() = runTest {
        fakeRepository.existing.add(makeSource("upd-1", NetworkSourceType.SFTP))

        val payload = freshPayload(listOf(
            makePayloadItem("new-1", "SMB"),
            makePayloadItem("upd-1", "SFTP"),
            makePayloadItem("bad-1", "UNKNOWN")
        ))

        val result = useCase(payload)

        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `all three source types are accepted`() = runTest {
        val payload = freshPayload(listOf(
            makePayloadItem("a", "SMB"),
            makePayloadItem("b", "FTP"),
            makePayloadItem("c", "SFTP")
        ))

        val result = useCase(payload)

        assertEquals(3, result.added)
        assertEquals(0, result.skipped)
    }

    // S2488: the endpoint group survives the import, and one malformed entry costs only itself.

    @Test
    fun `two endpoints are stored in the order they arrived`() = runTest {
        val payload = freshPayload(listOf(
            makePayloadItem("sftp-1", "SFTP").copy(
                endpoints = listOf(
                    WearEndpointPayload("192.168.1.70", 61423),
                    WearEndpointPayload("192.168.1.100", 22)
                )
            )
        ))

        useCase(payload)

        assertEquals(
            listOf(WearEndpoint("192.168.1.70", 61423), WearEndpoint("192.168.1.100", 22)),
            fakeRepository.existing.single().endpoints
        )
    }

    @Test
    fun `a payload carrying no endpoints stores null`() = runTest {
        useCase(freshPayload(listOf(makePayloadItem("sftp-1", "SFTP"))))

        assertEquals(null, fakeRepository.existing.single().endpoints)
    }

    @Test
    fun `a blank host or an out-of-range port is dropped and the source still imports`() = runTest {
        val payload = freshPayload(listOf(
            makePayloadItem("sftp-1", "SFTP").copy(
                endpoints = listOf(
                    WearEndpointPayload("", 22),
                    WearEndpointPayload("192.168.1.70", 0),
                    WearEndpointPayload("192.168.1.70", 70000)
                )
            )
        ))

        val result = useCase(payload)

        assertEquals(1, result.added)
        assertEquals(null, fakeRepository.existing.single().endpoints)
    }

    @Test
    fun `two items differing only in basePath import as two sources`() = runTest {
        val payload = freshPayload(listOf(
            makePayloadItem("sftp-1", "SFTP").copy(basePath = "/music"),
            makePayloadItem("sftp-2", "SFTP").copy(basePath = "/photos")
        ))

        val result = useCase(payload)

        assertEquals(2, result.added)
        assertEquals(2, fakeRepository.existing.size)
    }

    // S2502: the later edit wins, per whole record, with the clock offset measured by the exchange.

    @Test
    fun `an incoming record stamped later than the stored one is written`() = runTest {
        fakeRepository.existing.add(
            storedSource(name = "watch name", lastEditedAt = SENT_AT - 9_000L)
        )

        val result = useCase(
            stampedPayload(name = "phone name", lastEditedAt = SENT_AT - 1_000L),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.updated)
        assertEquals(0, result.skipped)
        assertEquals("phone name", fakeRepository.existing.single().name)
    }

    @Test
    fun `an incoming record stamped earlier leaves the stored one alone`() = runTest {
        fakeRepository.existing.add(
            storedSource(name = "watch name", lastEditedAt = SENT_AT)
        )

        val result = useCase(
            stampedPayload(name = "phone name", lastEditedAt = SENT_AT - 9_000L),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
        assertEquals("watch name", fakeRepository.existing.single().name)
        assertEquals(0, fakeRepository.upsertCallCount)
    }

    @Test
    fun `a batch carrying no stamps at all overwrites, as it did before this ticket`() = runTest {
        fakeRepository.existing.add(
            storedSource(name = "watch name", lastEditedAt = SENT_AT)
        )

        val result = useCase(
            stampedPayload(name = "phone name", lastEditedAt = null),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.updated)
        assertEquals("phone name", fakeRepository.existing.single().name)
    }

    @Test
    fun `a record with no stored match is added whatever its stamp`() = runTest {
        val result = useCase(
            stampedPayload(name = "phone name", lastEditedAt = 1L),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.added)
        assertEquals(1, fakeRepository.existing.size)
    }

    @Test
    fun `the applied record keeps the incoming stamp corrected into this watch's time base`() = runTest {
        val result = useCase(
            stampedPayload(name = "phone name", lastEditedAt = SENT_AT - 4_000L),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.added)
        assertEquals(SENT_AT - 4_000L + SKEW, fakeRepository.existing.single().lastEditedAt)
    }

    // S2507: deletion travels as its own record, and ranks against edits by the same corrected clock.

    @Test
    fun `an incoming deletion removes the source and keeps its tombstone`() = runTest {
        fakeRepository.existing.add(storedSource(name = "watch name", lastEditedAt = SENT_AT - 9_000L))

        useCase(deletionPayload(SENT_AT), receivedAtEpochMillis = RECEIVED_AT)

        assertEquals(emptyList<String>(), fakeRepository.existing.map { it.id })
        // Stored in this watch's own base, so a later exchange needs no second skew measurement.
        assertEquals(listOf(SENT_AT + SKEW), fakeRepository.tombstones.map { it.deletedAt })
    }

    @Test
    fun `a deletion older than the local edit leaves the source alone`() = runTest {
        fakeRepository.existing.add(storedSource(name = "watch name", lastEditedAt = SENT_AT + 5_000L))

        useCase(deletionPayload(SENT_AT), receivedAtEpochMillis = RECEIVED_AT)

        assertEquals(listOf(MERGE_ID), fakeRepository.existing.map { it.id })
        assertEquals(emptyList<String>(), fakeRepository.tombstones.map { it.id })
    }

    @Test
    fun `a repeated exchange does not resurrect the deleted source`() = runTest {
        fakeRepository.existing.add(storedSource(name = "watch name", lastEditedAt = SENT_AT - 9_000L))
        useCase(deletionPayload(SENT_AT), receivedAtEpochMillis = RECEIVED_AT)

        // The phone still holds the record it deleted and sends it again in the next batch.
        val result = useCase(
            stampedPayload(name = "phone name", lastEditedAt = SENT_AT - 9_000L),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(0, result.added)
        assertEquals(1, result.skipped)
        assertEquals(emptyList<String>(), fakeRepository.existing.map { it.id })
    }

    @Test
    fun `a newer incoming edit cancels the tombstone and brings the source back`() = runTest {
        fakeRepository.recordTombstone(WearSourceTombstonePayload(MERGE_ID, SENT_AT - 5_000L))

        val result = useCase(
            stampedPayload(name = "phone name", lastEditedAt = SENT_AT),
            receivedAtEpochMillis = RECEIVED_AT
        )

        assertEquals(1, result.added)
        assertEquals(emptyList<String>(), fakeRepository.tombstones.map { it.id })
    }

    @Test
    fun `a one-sided source with no tombstone is still added`() = runTest {
        val result = useCase(freshPayload(listOf(makePayloadItem("only-on-phone", "SMB"))))

        assertEquals(1, result.added)
        assertEquals(listOf("only-on-phone"), fakeRepository.existing.map { it.id })
    }

    // ----- helpers -----

    private fun deletionPayload(deletedAt: Long) = WearSyncPayload(
        sentAt = SENT_AT,
        phoneName = "TestPhone",
        sources = emptyList(),
        tombstones = listOf(WearSourceTombstonePayload(MERGE_ID, deletedAt))
    )

    private fun storedSource(name: String, lastEditedAt: Long?) =
        makeSource(MERGE_ID, NetworkSourceType.SMB).copy(name = name, lastEditedAt = lastEditedAt)

    private fun stampedPayload(name: String, lastEditedAt: Long?) = WearSyncPayload(
        sentAt = SENT_AT,
        phoneName = "TestPhone",
        sources = listOf(
            makePayloadItem(MERGE_ID, "SMB").copy(name = name, lastEditedAt = lastEditedAt)
        )
    )

    private fun freshPayload(items: List<WearNetworkSourcePayload>) = WearSyncPayload(
        sentAt = System.currentTimeMillis() - 1000,
        phoneName = "TestPhone",
        sources = items
    )

    private fun makePayloadItem(id: String, type: String) = WearNetworkSourcePayload(
        id = id,
        type = type,
        name = "Source $id",
        server = "192.168.1.1",
        port = 445,
        username = "user",
        password = "pass"
    )

    private fun makeSource(id: String, type: NetworkSourceType) = NetworkSource(
        id = id,
        type = type,
        name = "Source $id",
        server = "192.168.1.1",
        username = "user",
        password = "pass"
    )
}

@Suppress("EmptyFunctionBlock", "UnusedParameter")
private class FakeNetworkSourceRepository : NetworkSourceRepository {

    val existing = mutableListOf<NetworkSource>()
    val tombstones = mutableListOf<WearSourceTombstonePayload>()
    var upsertCallCount = 0

    override suspend fun getAllSources(): List<NetworkSource> = existing.toList()

    override fun observeSources(): Flow<List<NetworkSource>> = flowOf(existing.toList())

    override suspend fun upsertSource(source: NetworkSource) {
        upsertCallCount++
        val idx = existing.indexOfFirst { it.id == source.id }
        if (idx >= 0) existing[idx] = source else existing.add(source)
    }

    override suspend fun addSource(source: NetworkSource) {
        existing.add(source)
    }

    override suspend fun updateSource(source: NetworkSource) {
        val idx = existing.indexOfFirst { it.id == source.id }
        if (idx >= 0) existing[idx] = source
    }

    override suspend fun deleteSource(id: String) {
        existing.removeIf { it.id == id }
    }

    override suspend fun deleteSourceWithTombstone(id: String, deletedAt: Long) {
        recordTombstone(WearSourceTombstonePayload(id = id, deletedAt = deletedAt))
        deleteSource(id)
    }

    override suspend fun getTombstones(): List<WearSourceTombstonePayload> = tombstones.toList()

    override suspend fun recordTombstone(tombstone: WearSourceTombstonePayload) {
        tombstones.removeIf { it.id == tombstone.id }
        tombstones.add(tombstone)
    }

    override suspend fun removeTombstone(id: String) {
        tombstones.removeIf { it.id == id }
    }

    override suspend fun getSourceById(id: String): NetworkSource? =
        existing.firstOrNull { it.id == id }

    override suspend fun testConnection(source: NetworkSource): Result<Boolean> =
        Result.success(true)
}
