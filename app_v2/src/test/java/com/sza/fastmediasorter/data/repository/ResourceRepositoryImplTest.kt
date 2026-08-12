package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.StorageVolumeRepository
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for the entity<->domain mapping of [ResourceRepositoryImpl]: the
 * supportedMediaTypes flag bitmask both ways, the SMB backslash normalisation,
 * the ResourceProfile invalid-value fallback, credential accountId enrichment, and
 * destinationOrder null->-1. DAO and credential repo are faked; SmbOperationsUseCase
 * is unused by these paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResourceRepositoryImplTest {

    private lateinit var dao: ResourceDao
    private lateinit var credentialsRepository: NetworkCredentialsRepository
    private lateinit var smbOperations: SmbOperationsUseCase
    private lateinit var storageVolumeRepository: StorageVolumeRepository
    private lateinit var repo: ResourceRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        credentialsRepository = mockk(relaxed = true)
        smbOperations = mockk(relaxed = true)
        storageVolumeRepository = mockk(relaxed = true)
        coEvery { credentialsRepository.getAllCredentials() } returns flowOf(emptyList())
        // S1378: these paths bind no resource to a volume, so the availability pass short-circuits
        // and never reaches the registry - relaxed mock is enough.
        coEvery { storageVolumeRepository.getVolumes() } returns emptyList()
        repo = ResourceRepositoryImpl(dao, credentialsRepository, smbOperations, storageVolumeRepository)
    }

    private fun entity(
        path: String = "/root",
        type: ResourceType = ResourceType.LOCAL,
        flags: Int = 0b0001,
        profile: String = "NONE",
        credentialsId: String? = null,
    ) = ResourceEntity(
        id = 1L,
        name = "R",
        path = path,
        type = type,
        credentialsId = credentialsId,
        supportedMediaTypesFlags = flags,
        profile = profile,
    )

    @Test
    fun `toDomain decodes the full media-type flag bitmask`() = runTest {
        coEvery { dao.getResourceByIdSync(1L) } returns entity(flags = 0b11111111)

        val types = repo.getResourceById(1L)!!.supportedMediaTypes
        assertEquals(
            setOf(
                MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF,
                MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT,
            ),
            types,
        )
    }

    @Test
    fun `toDomain decodes a partial bitmask`() = runTest {
        coEvery { dao.getResourceByIdSync(1L) } returns entity(flags = 0b0010 or 0b1000)
        assertEquals(setOf(MediaType.VIDEO, MediaType.GIF), repo.getResourceById(1L)!!.supportedMediaTypes)
    }

    @Test
    fun `toDomain normalises backslashes for SMB paths only`() = runTest {
        coEvery { dao.getResourceByIdSync(1L) } returns entity(path = "\\\\server\\share\\dir", type = ResourceType.SMB)
        assertEquals("//server/share/dir", repo.getResourceById(1L)!!.path)

        coEvery { dao.getResourceByIdSync(2L) } returns entity(path = "C:\\local\\dir", type = ResourceType.LOCAL).copy(id = 2L)
        assertEquals("C:\\local\\dir", repo.getResourceById(2L)!!.path)
    }

    @Test
    fun `toDomain falls back to NONE for an unknown profile`() = runTest {
        coEvery { dao.getResourceByIdSync(1L) } returns entity(profile = "NOT_A_PROFILE")
        assertEquals(ResourceProfile.NONE, repo.getResourceById(1L)!!.profile)
    }

    @Test
    fun `toDomain enriches accountId from the matching credential`() = runTest {
        coEvery { dao.getResourceByIdSync(1L) } returns entity(credentialsId = "cred-1")
        coEvery { credentialsRepository.getByCredentialId("cred-1") } returns
            NetworkCredentialsEntity(
                credentialId = "cred-1",
                type = "SMB",
                server = "h",
                port = 445,
                username = "u",
                encryptedPassword = "",
                accountId = "user@x",
            )

        assertEquals("user@x", repo.getResourceById(1L)!!.accountId)
    }

    @Test
    fun `getResourceById returns null when dao has no row`() = runTest {
        coEvery { dao.getResourceByIdSync(9L) } returns null
        assertNull(repo.getResourceById(9L))
    }

    @Test
    fun `getAllResourcesSync maps each entity`() = runTest {
        coEvery { dao.getAllResourcesSync() } returns listOf(entity(flags = 0b0001), entity(flags = 0b0010).copy(id = 2L))

        val all = repo.getAllResourcesSync()
        assertEquals(2, all.size)
        assertTrue(MediaType.IMAGE in all[0].supportedMediaTypes)
        assertTrue(MediaType.VIDEO in all[1].supportedMediaTypes)
    }

    @Test
    fun `addResource encodes supportedMediaTypes back into the flag bitmask`() = runTest {
        val captured = slot<ResourceEntity>()
        coEvery { dao.insert(capture(captured)) } returns 5L

        val id = repo.addResource(
            createMediaResource(
                supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.AUDIO, MediaType.OFFICE_DOCUMENT),
            )
        )

        assertEquals(5L, id)
        assertEquals(0b0001 or 0b0100 or 0b10000000, captured.captured.supportedMediaTypesFlags)
    }

    @Test
    fun `toEntity maps null destinationOrder to -1`() = runTest {
        val captured = slot<ResourceEntity>()
        coEvery { dao.insert(capture(captured)) } returns 1L

        repo.addResource(createMediaResource(destinationOrder = null))

        assertEquals(-1, captured.captured.destinationOrder)
    }

    @Test
    fun `toEntity persists profile name`() = runTest {
        val captured = slot<ResourceEntity>()
        coEvery { dao.insert(capture(captured)) } returns 1L

        repo.addResource(createMediaResource(profile = ResourceProfile.NONE))

        assertEquals("NONE", captured.captured.profile)
    }

    @Test
    fun `getAllResourcesSync marks a resource on an unmounted volume unavailable`() = runTest {
        coEvery { dao.getAllResourcesSync() } returns listOf(
            entity().copy(id = 1L, storageVolumeId = "CARD-UUID"),
            entity().copy(id = 2L, storageVolumeId = null),
        )
        coEvery { storageVolumeRepository.getVolumes() } returns listOf(volume("CARD-UUID", mounted = false))

        val all = repo.getAllResourcesSync()

        assertEquals(false, all.single { it.id == 1L }.isAvailable)
        assertTrue("an unbound resource keeps its stored flag", all.single { it.id == 2L }.isAvailable)
    }

    @Test
    fun `getAllResourcesSync leaves a resource on a mounted volume available`() = runTest {
        coEvery { dao.getAllResourcesSync() } returns listOf(entity().copy(storageVolumeId = "CARD-UUID"))
        coEvery { storageVolumeRepository.getVolumes() } returns listOf(volume("CARD-UUID", mounted = true))

        assertTrue(repo.getAllResourcesSync().single().isAvailable)
    }

    /**
     * S1378 regression: `isAvailable` is derived on load for a volume-bound resource, and every caller
     * edits an unrelated field on a `copy()` of that loaded object. If the derived false were written
     * back, ejecting the card once would mark the resource dead forever - reinserting it sets nothing
     * back. The stored flag must win on the write path.
     */
    @Test
    fun `updateResource never persists the derived unavailability of a volume-bound resource`() = runTest {
        val stored = entity().copy(storageVolumeId = "CARD-UUID", isAvailable = true)
        coEvery { dao.getResourceByIdSync(1L) } returns stored
        val captured = slot<ResourceEntity>()
        coEvery { dao.update(capture(captured)) } returns Unit

        // What a caller holds after loading while the card is out, then editing an unrelated field.
        repo.updateResource(
            createMediaResource(id = 1L, isAvailable = false)
                .copy(storageVolumeId = "CARD-UUID", name = "renamed")
        )

        assertEquals("renamed", captured.captured.name)
        assertTrue("stored availability must survive the write", captured.captured.isAvailable)
    }

    @Test
    fun `updateResource still authors availability for an unbound resource`() = runTest {
        val captured = slot<ResourceEntity>()
        coEvery { dao.update(capture(captured)) } returns Unit

        repo.updateResource(createMediaResource(id = 1L, isAvailable = false).copy(storageVolumeId = null))

        assertEquals(false, captured.captured.isAvailable)
    }

    private fun volume(id: String, mounted: Boolean) = StorageVolumeInfo(
        id = id,
        displayName = id,
        isRemovable = true,
        isMounted = mounted,
        mountPath = if (mounted) "/storage/$id" else null,
        totalBytes = if (mounted) 1_000L else 0L,
        availableBytes = if (mounted) 500L else 0L,
    )
}
