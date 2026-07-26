package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendResourcesToWatchUseCaseTest {

    private lateinit var resourceRepository: FakeResourceRepository
    private lateinit var credentialsRepository: FakeNetworkCredentialsRepository
    private lateinit var wearableRepository: FakeWearableDataLayerRepository
    private lateinit var useCase: SendResourcesToWatchUseCase

    @Before
    fun setup() {
        resourceRepository = FakeResourceRepository()
        credentialsRepository = FakeNetworkCredentialsRepository()
        wearableRepository = FakeWearableDataLayerRepository()
        useCase = SendResourcesToWatchUseCase(resourceRepository, credentialsRepository, wearableRepository)
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

private class FakeWearableDataLayerRepository : WearableDataLayerRepository {
    var connectedNodes: List<WearNode> = emptyList()
    val putCalls = mutableListOf<PutCall>()

    override suspend fun getConnectedNodes(): List<WearNode> = connectedNodes

    override suspend fun putDataItem(path: String, payload: ByteArray) {
        putCalls += PutCall(path, payload)
    }

    override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) = Unit

    // Required by the interface but not exercised in these tests.
    override suspend fun putEnvelopeDataItem(path: String, envelope: com.sza.fastmediasorter.domain.model.WearEventEnvelope) = Unit
}

private data class PutCall(
    val path: String,
    val payload: ByteArray
)

private class FakeNetworkCredentialsRepository : NetworkCredentialsRepository {
    val byCredentialId = mutableMapOf<String, NetworkCredentialsEntity>()

    override suspend fun insert(credentials: NetworkCredentialsEntity): Long = 0
    override suspend fun getById(id: Long): NetworkCredentialsEntity? = byCredentialId.values.firstOrNull { it.id == id }
    override suspend fun getByCredentialId(credentialId: String): NetworkCredentialsEntity? = byCredentialId[credentialId]
    override suspend fun getByTypeServerAndPort(type: String, server: String, port: Int): NetworkCredentialsEntity? = null
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
    override suspend fun swapResourceDisplayOrders(resource1: MediaResource, resource2: MediaResource) = Unit
    override suspend fun updateResourcesDisplayOrder(resources: List<MediaResource>) = Unit
    override suspend fun deleteResource(resourceId: Long) = Unit
    override suspend fun deleteResourceIfHidden(resourceId: Long) = Unit
    override suspend fun deleteAllResources() = Unit
    override suspend fun testConnection(resource: MediaResource): Result<String> = Result.success("ok")
    override suspend fun updateIcon(resourceId: Long, iconId: String?) = Unit
    override suspend fun updateLastViewedFile(resourceId: Long, path: String?) = Unit
    override suspend fun updateLastScrollPosition(resourceId: Long, position: Int) = Unit
    override suspend fun backfillMissingIcons(resolveIcon: (path: String, profileName: String, typeName: String) -> String?): Int = 0
}