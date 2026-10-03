package com.sza.fastmediasorter.domain.usecase.companion

import android.content.Context
import com.sza.fastmediasorter.data.companion.CompanionConfigException
import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.AddMultipleResult
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S4032: the import must never create a resource that cannot log in, and a newer schemaVersion must reach
 * every caller as UNSUPPORTED_VERSION.
 */
class ImportCompanionConfigUseCaseTest {

    private val parser = CompanionConfigParser()
    private val smbOperations: SmbOperationsUseCase = mockk()
    private val addResource: AddResourceUseCase = mockk()
    private val credentials: NetworkCredentialsRepository = mockk()
    private val addedResources = slot<List<MediaResource>>()
    private lateinit var useCase: ImportCompanionConfigUseCase

    @Before
    fun setUp() {
        useCase = ImportCompanionConfigUseCase(
            context = mockk<Context>(relaxed = true),
            parser = parser,
            smbOperationsUseCase = smbOperations,
            addResourceUseCase = addResource,
            credentialsRepository = credentials,
            ioDispatcher = Dispatchers.Unconfined
        )
        coEvery { credentials.getByTypeServerAndPort(any(), any(), any()) } returns null
        coEvery { smbOperations.saveSftpCredentials(any(), any(), any(), any(), any()) } returns Result.success(CRED_ID)
        coEvery { addResource.addMultiple(capture(addedResources), any()) } answers {
            val names = addedResources.captured.map { it.name }
            Result.success(
                AddMultipleResult(
                    addedCount = names.size,
                    destinationsFull = false,
                    skippedDestinations = 0,
                    addedNames = names
                )
            )
        }
    }

    private fun config(
        password: String = "pw",
        fingerprint: String = "",
        virtualPath: String = "/Photos",
        label: String? = "Photos",
        schemaVersion: Int = 1
    ): String {
        val labelField = label?.let { ",\"label\":\"$it\"" }.orEmpty()
        return "{\"schemaVersion\":$schemaVersion,\"resourceName\":\"Home PC\",\"protocol\":\"sftp\"," +
            "\"accessPaths\":[{\"kind\":\"lan\",\"host\":\"$HOST\",\"port\":$PORT}]," +
            "\"username\":\"fms\",\"password\":\"$password\",\"hostKeyFingerprintSha256\":\"$fingerprint\"," +
            "\"roots\":[{\"virtualPath\":\"$virtualPath\"$labelField}]}"
    }

    private fun storedCredential(encryptedPassword: String, sshKey: String? = null) = NetworkCredentialsEntity(
        credentialId = CRED_ID,
        type = "SFTP",
        server = HOST,
        port = PORT,
        username = "fms",
        encryptedPassword = encryptedPassword,
        sshPrivateKey = sshKey
    )

    private fun reasonOf(result: Result<CompanionImportResult>): CompanionConfigException.Reason? =
        (result.exceptionOrNull() as? CompanionConfigException)?.reason

    @Test
    fun `empty password with nothing stored asks for it and writes nothing`() = runTest {
        val result = useCase.importFromPayload(config(password = ""))

        assertEquals(CompanionConfigException.Reason.PASSWORD_REQUIRED, reasonOf(result))
        assertNotNull((result.exceptionOrNull() as CompanionConfigException).config)
        coVerify(exactly = 0) { smbOperations.saveSftpCredentials(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { addResource.addMultiple(any(), any()) }
    }

    @Test
    fun `whitespace-only password counts as empty`() = runTest {
        val result = useCase.importFromPayload(config(password = "   "))

        assertEquals(CompanionConfigException.Reason.PASSWORD_REQUIRED, reasonOf(result))
    }

    @Test
    fun `stored credential with an empty password still asks`() = runTest {
        coEvery { credentials.getByTypeServerAndPort("SFTP", HOST, PORT) } returns storedCredential("")

        val result = useCase.importFromPayload(config(password = ""))

        assertEquals(CompanionConfigException.Reason.PASSWORD_REQUIRED, reasonOf(result))
    }

    @Test
    fun `empty password imports when a password is already stored for the server`() = runTest {
        coEvery { credentials.getByTypeServerAndPort("SFTP", HOST, PORT) } returns storedCredential("cipher")

        val result = useCase.importFromPayload(config(password = ""))

        assertTrue(result.isSuccess)
        coVerify { smbOperations.saveSftpCredentials(HOST, PORT, "fms", "", null) }
    }

    @Test
    fun `empty password imports when an ssh key is stored for the server`() = runTest {
        coEvery { credentials.getByTypeServerAndPort("SFTP", HOST, PORT) } returns
            storedCredential("", sshKey = "key")

        assertTrue(useCase.importFromPayload(config(password = "")).isSuccess)
    }

    @Test
    fun `explicit password imports with that password`() = runTest {
        val result = useCase.importFromPayload(config(password = "secret"))

        assertTrue(result.isSuccess)
        assertEquals(listOf("Photos"), result.getOrThrow().addedNames)
        coVerify { smbOperations.saveSftpCredentials(HOST, PORT, "fms", "secret", null) }
    }

    @Test
    fun `password supplied after the prompt completes the import`() = runTest {
        val asked = useCase.importFromPayload(config(password = ""))
        val pending = requireNotNull((asked.exceptionOrNull() as CompanionConfigException).config)

        val result = useCase.import(pending.copy(password = "typed"), matchExistingByPath = true)

        assertTrue(result.isSuccess)
        coVerify { smbOperations.saveSftpCredentials(HOST, PORT, "fms", "typed", null) }
    }

    @Test
    fun `malformed fingerprint is rejected before any write`() = runTest {
        val result = useCase.importFromPayload(config(fingerprint = "not-a-fingerprint"))

        assertEquals(CompanionConfigException.Reason.INVALID_CONTENT, reasonOf(result))
        coVerify(exactly = 0) { smbOperations.saveSftpCredentials(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `newer schemaVersion surfaces as unsupported version`() = runTest {
        val result = useCase.importFromPayload(config(schemaVersion = 3))

        assertEquals(CompanionConfigException.Reason.UNSUPPORTED_VERSION, reasonOf(result))
    }

    @Test
    fun `whole-share root without a label is named after the config`() = runTest {
        val result = useCase.importFromPayload(config(virtualPath = "/", label = null))

        assertEquals(listOf("Home PC"), result.getOrThrow().addedNames)
    }

    private companion object {
        const val HOST = "10.0.0.2"
        const val PORT = 2022
        const val CRED_ID = "cred-1"
    }
}
