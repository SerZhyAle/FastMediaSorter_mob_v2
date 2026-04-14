package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.ImportResult
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.wear.domain.model.WearSyncPayload
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ImportNetworkSourcesUseCaseTest {

    private lateinit var fakeRepository: FakeNetworkSourceRepository
    private lateinit var useCase: ImportNetworkSourcesUseCase

    @Before
    fun setup() {
        fakeRepository = FakeNetworkSourceRepository()
        useCase = ImportNetworkSourcesUseCase(fakeRepository)
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

    // ----- helpers -----

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

private class FakeNetworkSourceRepository : NetworkSourceRepository {

    val existing = mutableListOf<NetworkSource>()
    var upsertCallCount = 0

    override suspend fun getAllSources(): List<NetworkSource> = existing.toList()

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

    override suspend fun deleteSource(sourceId: String) {
        existing.removeIf { it.id == sourceId }
    }

    override suspend fun getSourceById(sourceId: String): NetworkSource? =
        existing.firstOrNull { it.id == sourceId }

    override suspend fun testConnection(source: NetworkSource): Result<Boolean> =
        Result.success(true)
}
