package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.WearNetworkSourcePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportWatchSourcesUseCaseTest {

    private val credentialsRepository = mockk<NetworkCredentialsRepository>(relaxed = true)
    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private lateinit var useCase: ImportWatchSourcesUseCase

    private fun source(
        type: String = "SMB",
        server: String = "host",
        port: Int = 445,
    ) = WearNetworkSourcePayload(
        id = "src",
        type = type,
        name = "My Share",
        server = server,
        port = port,
        username = "user",
        password = "pw",
        basePath = "/base",
    )

    @Before
    fun setup() {
        // Bypass Android Keystore in NetworkCredentialsEntity.create.
        mockkObject(CryptoHelper)
        every { CryptoHelper.encrypt(any()) } returns "enc"
        useCase = ImportWatchSourcesUseCase(resourceRepository, credentialsRepository, Gson())
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
}
