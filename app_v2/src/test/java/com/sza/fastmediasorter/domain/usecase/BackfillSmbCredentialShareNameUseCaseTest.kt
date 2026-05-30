package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BackfillSmbCredentialShareNameUseCaseTest {

    private val credentialsRepository = mockk<NetworkCredentialsRepository>()
    private val resourceRepository = FakeResourceRepository()
    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()

    private lateinit var useCase: BackfillSmbCredentialShareNameUseCase

    @Before
    fun setup() {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs
        useCase = BackfillSmbCredentialShareNameUseCase(credentialsRepository, resourceRepository, context)
    }

    private fun smbCred(id: String, share: String? = null) = NetworkCredentialsEntity(
        credentialId = id,
        type = "SMB",
        server = "host",
        port = 445,
        username = "u",
        encryptedPassword = "x",
        shareName = share,
    )

    @Test
    fun `done flag short-circuits without touching repositories`() = runTest {
        every { prefs.getBoolean(any(), any()) } returns true

        val result = useCase()

        assertEquals(BackfillSmbCredentialShareNameUseCase.BackfillResult(0, 0, 0), result)
        coVerify(exactly = 0) { credentialsRepository.getAllCredentials() }
    }

    @Test
    fun `no empty-share SMB credentials sets done flag and reports zero`() = runTest {
        every { prefs.getBoolean(any(), any()) } returns false
        every { credentialsRepository.getAllCredentials() } returns
            flowOf(listOf(smbCred("c1", share = "already")))

        val result = useCase()

        assertEquals(0, result.scanned)
        verify { editor.putBoolean(any(), true) }
    }

    @Test
    fun `empty-share credential with parseable path is backfilled`() = runTest {
        every { prefs.getBoolean(any(), any()) } returns false
        every { credentialsRepository.getAllCredentials() } returns flowOf(listOf(smbCred("c1")))
        coEvery { credentialsRepository.update(any()) } just Runs
        resourceRepository.setResources(
            listOf(createMediaResource(id = 1L, type = ResourceType.SMB, path = "smb://host/myshare/dir", credentialsId = "c1"))
        )
        val updatedSlot = slot<NetworkCredentialsEntity>()
        coEvery { credentialsRepository.update(capture(updatedSlot)) } just Runs

        val result = useCase()

        assertEquals(1, result.updated)
        assertEquals(0, result.skipped)
        assertEquals("myshare", updatedSlot.captured.shareName)
        verify { editor.putBoolean(any(), true) }
    }

    @Test
    fun `credential without matching SMB resource is skipped`() = runTest {
        every { prefs.getBoolean(any(), any()) } returns false
        every { credentialsRepository.getAllCredentials() } returns flowOf(listOf(smbCred("orphan")))
        resourceRepository.setResources(emptyList())

        val result = useCase()

        assertEquals(1, result.scanned)
        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
        coVerify(exactly = 0) { credentialsRepository.update(any()) }
    }

    @Test
    fun `credential whose resource path lacks a share is skipped`() = runTest {
        every { prefs.getBoolean(any(), any()) } returns false
        every { credentialsRepository.getAllCredentials() } returns flowOf(listOf(smbCred("c1")))
        resourceRepository.setResources(
            // Path with server only, no share segment -> parseSmbPath returns null.
            listOf(createMediaResource(id = 1L, type = ResourceType.SMB, path = "smb://host", credentialsId = "c1"))
        )

        val result = useCase()

        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `repository exception returns sentinel scanned -1 and does not set done flag`() = runTest {
        every { prefs.getBoolean(any(), any()) } returns false
        every { credentialsRepository.getAllCredentials() } throws RuntimeException("db error")

        val result = useCase()

        assertEquals(-1, result.scanned)
        verify(exactly = 0) { editor.putBoolean(any(), true) }
    }
}
