package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.google.android.gms.wearable.Node
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendResourcesToWatchUseCaseTest {

    private val resourceRepository: ResourceRepository = mockk()
    private val credentialsRepository: NetworkCredentialsRepository = mockk()
    private val wearableRepository: WearableDataLayerRepository = mockk()
    private lateinit var useCase: SendResourcesToWatchUseCase

    private val mockNode: Node = mockk {
        every { id } returns "node-1"
        every { displayName } returns "Pixel Watch"
        every { isNearby } returns true
    }

    @Before
    fun setup() {
        useCase = SendResourcesToWatchUseCase(resourceRepository, credentialsRepository, wearableRepository)
    }

    @Test
    fun `returns failure when no nodes connected`() = runTest {
        coEvery { wearableRepository.getConnectedNodes() } returns emptyList()

        val result = useCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No watch") == true)
    }

    @Test
    fun `sends all SMB FTP SFTP resources and skips other types`() = runTest {
        val smbResource = makeResource(id = 1, type = ResourceType.SMB, credId = "cred-1")
        val ftpResource = makeResource(id = 2, type = ResourceType.FTP, credId = "cred-2")
        val localResource = makeResource(id = 3, type = ResourceType.LOCAL, credId = null)

        coEvery { wearableRepository.getConnectedNodes() } returns listOf(mockNode)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(smbResource, ftpResource, localResource)
        coEvery { credentialsRepository.getByCredentialId("cred-1") } returns makeCredentials("cred-1", "pass1")
        coEvery { credentialsRepository.getByCredentialId("cred-2") } returns makeCredentials("cred-2", "pass2")
        coEvery { wearableRepository.putDataItem(any(), any()) } returns Unit

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().sent)
        assertEquals(0, result.getOrThrow().skipped)
        coVerify(exactly = 1) { wearableRepository.putDataItem(any(), any()) }
    }

    @Test
    fun `skips resource with null credentialsId`() = runTest {
        val resource = makeResource(id = 1, type = ResourceType.SMB, credId = null)

        coEvery { wearableRepository.getConnectedNodes() } returns listOf(mockNode)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(resource)
        coEvery { wearableRepository.putDataItem(any(), any()) } returns Unit

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().sent)
        assertEquals(1, result.getOrThrow().skipped)
    }

    @Test
    fun `skips resource when credentials not found`() = runTest {
        val resource = makeResource(id = 1, type = ResourceType.SMB, credId = "missing-cred")

        coEvery { wearableRepository.getConnectedNodes() } returns listOf(mockNode)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(resource)
        coEvery { credentialsRepository.getByCredentialId("missing-cred") } returns null
        coEvery { wearableRepository.putDataItem(any(), any()) } returns Unit

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().sent)
        assertEquals(1, result.getOrThrow().skipped)
    }

    @Test
    fun `skips resource when password decryption failed`() = runTest {
        val resource = makeResource(id = 1, type = ResourceType.SMB, credId = "cred-bad")
        // password decryption failure: password="" but encryptedPassword non-empty
        val entity: NetworkCredentialsEntity = mockk {
            every { password } returns ""
            every { encryptedPassword } returns "corrupted-ciphertext"
            every { server } returns "192.168.1.1"
            every { port } returns 445
            every { username } returns "user"
            every { domain } returns ""
            every { shareName } returns null
            every { decryptedSshPrivateKey } returns null
        }

        coEvery { wearableRepository.getConnectedNodes() } returns listOf(mockNode)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(resource)
        coEvery { credentialsRepository.getByCredentialId("cred-bad") } returns entity
        coEvery { wearableRepository.putDataItem(any(), any()) } returns Unit

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().sent)
        assertEquals(1, result.getOrThrow().skipped)
    }

    @Test
    fun `sends valid payload as JSON bytes`() = runTest {
        val resource = makeResource(id = 5, type = ResourceType.SMB, credId = "cred-5")

        coEvery { wearableRepository.getConnectedNodes() } returns listOf(mockNode)
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(resource)
        coEvery { credentialsRepository.getByCredentialId("cred-5") } returns makeCredentials("cred-5", "secret")
        coEvery { wearableRepository.putDataItem(any(), any()) } returns Unit

        useCase()

        coVerify {
            wearableRepository.putDataItem(
                eq("/fms/network_sources/push"),
                match { bytes ->
                    val json = bytes.decodeToString()
                    json.contains("\"sources\"") && json.contains("SMB")
                }
            )
        }
    }

    // ----- helpers -----

    private fun makeResource(id: Long, type: ResourceType, credId: String?) = MediaResource(
        id = id,
        name = "Resource $id",
        path = "/resource/$id",
        type = type,
        credentialsId = credId
    )

    private fun makeCredentials(credId: String, plainPass: String): NetworkCredentialsEntity {
        val entity: NetworkCredentialsEntity = mockk {
            every { this@mockk.password } returns plainPass
            every { encryptedPassword } returns "enc_$plainPass"
            every { server } returns "192.168.1.1"
            every { port } returns 445
            every { username } returns "user"
            every { domain } returns ""
            every { shareName } returns null
            every { decryptedSshPrivateKey } returns null
        }
        return entity
    }
}
