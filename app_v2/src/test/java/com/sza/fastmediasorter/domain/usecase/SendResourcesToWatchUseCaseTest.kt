package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.repository.WearResourceSelectionRepositoryImpl
import com.sza.fastmediasorter.data.repository.wear.WearResourceStampStore
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearSyncPayload
import com.sza.fastmediasorter.domain.networkmonitor.ReachableEndpointProvider
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.testing.fakes.FakeWearResourceTombstoneStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Robolectric supplies the SharedPreferences the selection repository is backed by; the use case
// reads that set on every call, so a test that sends anything has to state its selection first.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendResourcesToWatchUseCaseTest {

    private lateinit var resourceRepository: FakeResourceRepository
    private lateinit var credentialsRepository: FakeNetworkCredentialsRepository
    private lateinit var wearableRepository: FakeWearableDataLayerRepository
    private lateinit var selectionRepository: WearResourceSelectionRepositoryImpl
    private lateinit var endpointProvider: FakeReachableEndpointProvider
    private lateinit var stampStore: FakeWearResourceStampStore
    private lateinit var tombstoneStore: FakeWearResourceTombstoneStore
    private lateinit var useCase: SendResourcesToWatchUseCase

    @Before
    fun setup() {
        resourceRepository = FakeResourceRepository()
        credentialsRepository = FakeNetworkCredentialsRepository()
        wearableRepository = FakeWearableDataLayerRepository()
        selectionRepository = WearResourceSelectionRepositoryImpl(RuntimeEnvironment.getApplication())
        endpointProvider = FakeReachableEndpointProvider()
        stampStore = FakeWearResourceStampStore()
        tombstoneStore = FakeWearResourceTombstoneStore()
        useCase = SendResourcesToWatchUseCase(
            resourceRepository,
            credentialsRepository,
            wearableRepository,
            selectionRepository,
            endpointProvider,
            stampStore,
            tombstoneStore
        )
    }

    private fun select(vararg ids: Long) {
        selectionRepository.setSelectedIds(ids.toSet())
    }

    @Test
    fun `returns failure when no nodes connected`() = runTest {
        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No watch") == true)
    }

    @Test
    fun `sends all SMB FTP SFTP resources and skips other types`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(
            makeResource(id = 1, type = ResourceType.SMB, credId = "cred-1"),
            makeResource(id = 2, type = ResourceType.FTP, credId = "cred-2"),
            makeResource(id = 3, type = ResourceType.LOCAL, credId = null)
        )
        select(1L, 2L, 3L)
        credentialsRepository.byCredentialId["cred-1"] = makeCredentials("cred-1", "pass1")
        credentialsRepository.byCredentialId["cred-2"] = makeCredentials("cred-2", "pass2")

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().sent)
        assertEquals(0, result.getOrThrow().skipped)
        assertEquals(1, wearableRepository.putCalls.size)
    }

    @Test
    fun `skips resource with null credentialsId`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SMB, credId = null))
        select(1L)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().sent)
        assertEquals(1, result.getOrThrow().skipped)
        assertEquals(1, wearableRepository.putCalls.size)
    }

    @Test
    fun `skips resource when credentials not found`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SMB, credId = "missing-cred"))
        select(1L)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().sent)
        assertEquals(1, result.getOrThrow().skipped)
        assertEquals(1, wearableRepository.putCalls.size)
    }

    @Test
    fun `skips resource when password decryption failed`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SMB, credId = "cred-bad"))
        select(1L)
        credentialsRepository.byCredentialId["cred-bad"] = NetworkCredentialsEntity(
            credentialId = "cred-bad",
            type = "SMB",
            server = "192.168.1.1",
            port = 445,
            username = "user",
            encryptedPassword = "broken-ciphertext",
            domain = "",
            shareName = null,
            sshPrivateKey = null
        )

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().sent)
        assertEquals(1, result.getOrThrow().skipped)
    }

    @Test
    fun `sends valid payload as JSON bytes`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 5, type = ResourceType.SMB, credId = "cred-5"))
        select(5L)
        credentialsRepository.byCredentialId["cred-5"] = makeCredentials("cred-5", "secret")

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, wearableRepository.putCalls.size)
        val call = wearableRepository.putCalls.single()
        val json = call.payload.decodeToString()
        assertEquals("/fms/network_sources/push", call.path)
        assertTrue(json.contains("\"sources\""))
        assertTrue(json.contains("SMB"))
    }

    @Test
    fun `sends only the selected resources out of the whole registry`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = (1L..5L).map { id ->
            makeResource(id = id, type = ResourceType.SMB, credId = "cred-$id")
        }
        (1L..5L).forEach { id ->
            credentialsRepository.byCredentialId["cred-$id"] = makeCredentials("cred-$id", "pass$id")
        }
        select(2L, 4L)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().sent)
        val payload = Gson().fromJson(
            wearableRepository.putCalls.single().payload.decodeToString(),
            WearSyncPayload::class.java
        )
        assertEquals(listOf("2", "4"), payload.sources.map { it.id })
    }

    // S2502: the watch ranks incoming records by this stamp, so a resource that was never edited must
    // ship null rather than a zero, which would rank it as the oldest record that can exist.

    @Test
    fun `a stored edit stamp ships with its resource`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SMB, credId = "cred-1"))
        credentialsRepository.byCredentialId["cred-1"] = makeCredentials("cred-1", "pass1")
        select(1L)
        stampStore.writeStamp("1", 1_700_000_000_000L)

        useCase()

        val payload = Gson().fromJson(
            wearableRepository.putCalls.single().payload.decodeToString(),
            WearSyncPayload::class.java
        )
        assertEquals(1_700_000_000_000L, payload.sources.single().lastEditedAt)
    }

    @Test
    fun `a resource with no stored stamp ships null, not zero`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SMB, credId = "cred-1"))
        credentialsRepository.byCredentialId["cred-1"] = makeCredentials("cred-1", "pass1")
        select(1L)

        useCase()

        val payload = Gson().fromJson(
            wearableRepository.putCalls.single().payload.decodeToString(),
            WearSyncPayload::class.java
        )
        assertEquals(null, payload.sources.single().lastEditedAt)
    }

    @Test
    fun `empty selection sends nothing and never writes to the data layer`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SMB, credId = "cred-1"))
        credentialsRepository.byCredentialId["cred-1"] = makeCredentials("cred-1", "pass1")

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().sent)
        assertEquals(0, result.getOrThrow().skipped)
        assertTrue(wearableRepository.putCalls.isEmpty())
    }

    // S2488: the four cases below cover the endpoint substitution and both of its exclusions.

    @Test
    fun `SFTP resource sends the reachable endpoint instead of the credentials row`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SFTP, credId = "cred-1"))
        select(1L)
        credentialsRepository.byCredentialId["cred-1"] = makeCredentials("cred-1", "pass1")
        endpointProvider.result = listOf(HostPort("192.168.1.70", 61423), HostPort("192.168.1.1", 445))

        val result = useCase()

        assertTrue(result.isSuccess)
        val source = sentSources().single()
        assertEquals("192.168.1.70", source.server)
        assertEquals(61423, source.port)
    }

    @Test
    fun `SFTP resource sends the requested pair unchanged when it is the reachable one`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SFTP, credId = "cred-1"))
        select(1L)
        credentialsRepository.byCredentialId["cred-1"] = makeCredentials("cred-1", "pass1")
        endpointProvider.result = listOf(HostPort("192.168.1.1", 445))

        val result = useCase()

        assertTrue(result.isSuccess)
        val source = sentSources().single()
        assertEquals("192.168.1.1", source.server)
        assertEquals(445, source.port)
    }

    @Test
    fun `SMB resource sends the credentials row and never asks the provider`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(makeResource(id = 1, type = ResourceType.SMB, credId = "cred-1"))
        select(1L)
        credentialsRepository.byCredentialId["cred-1"] = makeCredentials("cred-1", "pass1")
        endpointProvider.result = listOf(HostPort("10.0.0.9", 2222))

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, endpointProvider.calls)
        val source = sentSources().single()
        assertEquals("192.168.1.1", source.server)
        assertEquals(445, source.port)
    }

    @Test
    fun `a throwing provider falls back to the credentials row and still sends the others`() = runTest {
        wearableRepository.connectedNodes = listOf(WearNode("node-1", "Pixel Watch"))
        resourceRepository.resources = listOf(
            makeResource(id = 1, type = ResourceType.SFTP, credId = "cred-1"),
            makeResource(id = 2, type = ResourceType.SMB, credId = "cred-2")
        )
        select(1L, 2L)
        credentialsRepository.byCredentialId["cred-1"] = makeCredentials("cred-1", "pass1")
        credentialsRepository.byCredentialId["cred-2"] = makeCredentials("cred-2", "pass2")
        endpointProvider.failure = IllegalStateException("resolver unavailable")

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().sent)
        val sftp = sentSources().first { it.type == "SFTP" }
        assertEquals("192.168.1.1", sftp.server)
        assertEquals(445, sftp.port)
    }

    private fun sentSources() = Gson().fromJson(
        wearableRepository.putCalls.single().payload.decodeToString(),
        WearSyncPayload::class.java
    ).sources

    private fun makeResource(id: Long, type: ResourceType, credId: String?) = MediaResource(
        id = id,
        name = "Resource $id",
        path = "/resource/$id",
        type = type,
        credentialsId = credId
    )

    private fun makeCredentials(credId: String, plainPass: String): NetworkCredentialsEntity {
        return NetworkCredentialsEntity(
            credentialId = credId,
            type = "SMB",
            server = "192.168.1.1",
            port = 445,
            username = "user",
            encryptedPassword = "",
            domain = "",
            shareName = null,
            sshPrivateKey = null
        )
    }
}

private class FakeWearResourceStampStore : WearResourceStampStore {

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

private class FakeReachableEndpointProvider : ReachableEndpointProvider {
    var result: List<HostPort> = emptyList()
    var failure: Throwable? = null
    var calls = 0

    override suspend fun orderedEndpoints(host: String, port: Int): List<HostPort> {
        calls++
        failure?.let { throw it }
        return result.ifEmpty { listOf(HostPort(host, port)) }
    }
}

private class FakeWearableDataLayerRepository : WearableDataLayerRepository {
    var connectedNodes: List<WearNode> = emptyList()
    val putCalls = mutableListOf<PutCall>()

    override suspend fun getConnectedNodes(): List<WearNode> = connectedNodes

    override suspend fun putDataItem(path: String, payload: ByteArray) {
        putCalls += PutCall(path, payload)
    }

    override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) = Unit

    // Required by the interface but not exercised in these tests.
    override suspend fun putEnvelopeDataItem(
        path: String,
        envelope: com.sza.fastmediasorter.domain.model.WearEventEnvelope
    ) = Unit
}

private data class PutCall(
    val path: String,
    val payload: ByteArray
)

private class FakeNetworkCredentialsRepository : NetworkCredentialsRepository {
    val byCredentialId = mutableMapOf<String, NetworkCredentialsEntity>()

    override suspend fun insert(credentials: NetworkCredentialsEntity): Long = 0
    override suspend fun getById(
        id: Long
    ): NetworkCredentialsEntity? = byCredentialId.values.firstOrNull { it.id == id }
    override suspend fun getByCredentialId(
        credentialId: String
    ): NetworkCredentialsEntity? = byCredentialId[credentialId]
    override suspend fun getByTypeServerAndPort(
        type: String,
        server: String,
        port: Int
    ): NetworkCredentialsEntity? = null
    override suspend fun getByServerAndShare(server: String, shareName: String): NetworkCredentialsEntity? = null
    override suspend fun getCredentialsByHost(host: String): NetworkCredentialsEntity? = null
    override suspend fun getByTypeAndAccountId(type: String, accountId: String): NetworkCredentialsEntity? = null
    override suspend fun update(credentials: NetworkCredentialsEntity) = Unit
    override suspend fun delete(credentials: NetworkCredentialsEntity) = Unit
    override fun getAllCredentials(): Flow<List<NetworkCredentialsEntity>> = emptyFlow()
    override suspend fun getOrphanedCredentials(): List<NetworkCredentialsEntity> = emptyList()
    override suspend fun getManualShareNamesForServer(server: String, port: Int): List<String> = emptyList()
    override suspend fun addManualShareName(server: String, port: Int, shareName: String) = Unit
}

private class FakeResourceRepository : ResourceRepository {
    var resources: List<MediaResource> = emptyList()

    override fun getAllResources(): Flow<List<MediaResource>> = emptyFlow()
    override suspend fun getAllResourcesSync(): List<MediaResource> = resources
    override suspend fun getResourceById(id: Long): MediaResource? = resources.firstOrNull { it.id == id }
    override suspend fun getLocalResourceByPath(path: String): MediaResource? =
        resources.firstOrNull { it.type == ResourceType.LOCAL && it.path == path }
    override fun getResourcesByType(type: ResourceType): Flow<List<MediaResource>> = emptyFlow()
    override fun getDestinations(): Flow<List<MediaResource>> = emptyFlow()
    override suspend fun getFilteredResources(
        filterByType: Set<ResourceType>?,
        filterByMediaType: Set<com.sza.fastmediasorter.domain.model.MediaType>?,
        filterByName: String?,
        sortMode: com.sza.fastmediasorter.domain.model.SortMode
    ): List<MediaResource> = resources
    override suspend fun addResource(resource: MediaResource): Long = 0
    override suspend fun updateResource(resource: MediaResource) = Unit
    override suspend fun updateResourceAddress(resourceId: Long, newPath: String) = Unit
    override suspend fun swapResourceDisplayOrders(resource1: MediaResource, resource2: MediaResource) = Unit
    override suspend fun updateResourcesDisplayOrder(resources: List<MediaResource>) = Unit
    override suspend fun deleteResource(resourceId: Long) = Unit
    override suspend fun deleteResourceIfHidden(resourceId: Long) = Unit
    override suspend fun deleteAllResources() = Unit
    override suspend fun testConnection(resource: MediaResource): Result<String> = Result.success("ok")
    override suspend fun updateIcon(resourceId: Long, iconId: String?) = Unit
    override suspend fun updateLastViewedFile(resourceId: Long, path: String?) = Unit
    override suspend fun updateLastScrollPosition(resourceId: Long, position: Int) = Unit
    override suspend fun backfillMissingIcons(
        resolveIcon: (path: String, profileName: String, typeName: String) -> String?
    ): Int = 0
}
