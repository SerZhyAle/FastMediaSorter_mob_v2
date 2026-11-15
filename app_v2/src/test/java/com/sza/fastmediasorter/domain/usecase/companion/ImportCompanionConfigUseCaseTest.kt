package com.sza.fastmediasorter.domain.usecase.companion

import android.content.Context
import com.sza.fastmediasorter.data.companion.CompanionConfigException
import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.HostPort
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
import java.util.Base64

/**
 * S4032: the import must never create a resource that cannot log in, and a newer schemaVersion must reach
 * every caller as UNSUPPORTED_VERSION.
 *
 * S4643: FMSCFG 2.6 C2 and C3 - a missing or non-`SHA256:<base64>` fingerprint and a config without a
 * direct access path are refused whole, before any write.
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
        fingerprint: String = FINGERPRINT,
        virtualPath: String = "/Photos",
        label: String? = "Photos",
        schemaVersion: Int = 1,
        accessPaths: String = "{\"kind\":\"lan\",\"host\":\"$HOST\",\"port\":$PORT}"
    ): String {
        val labelField = label?.let { ",\"label\":\"$it\"" }.orEmpty()
        return "{\"schemaVersion\":$schemaVersion,\"resourceName\":\"Home PC\",\"protocol\":\"sftp\"," +
            "\"accessPaths\":[$accessPaths]," +
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

    private fun assertRefusedWithNothingWritten(
        result: Result<CompanionImportResult>,
        detail: String? = null,
        reason: CompanionConfigException.Reason = CompanionConfigException.Reason.INVALID_CONTENT,
    ) {
        assertEquals(reason, reasonOf(result))
        detail?.let { assertTrue(result.exceptionOrNull()?.message.orEmpty().contains(it)) }
        coVerify(exactly = 0) { smbOperations.saveSftpCredentials(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { addResource.addMultiple(any(), any()) }
    }

    @Test
    fun `absent or blank fingerprint is refused and nothing is written`() = runTest {
        listOf("", "   ").forEach { blank ->
            assertRefusedWithNothingWritten(useCase.importFromPayload(config(fingerprint = blank)))
        }
        val keyAbsent = config().replace("\"hostKeyFingerprintSha256\":\"$FINGERPRINT\",", "")
        assertRefusedWithNothingWritten(useCase.importFromPayload(keyAbsent))
    }

    @Test
    fun `only the exact SHA256 base64 form is accepted as a fingerprint`() = runTest {
        val bare = FINGERPRINT.removePrefix("SHA256:")
        val hex = Base64.getDecoder().decode("$bare=").joinToString("") { "%02x".format(it) }
        listOf(
            bare,
            hex,
            hex.chunked(2).joinToString(":"),
            "$FINGERPRINT=",
            FINGERPRINT.replaceFirst("SHA256:", "sha256:"),
            " $FINGERPRINT"
        ).forEach { form ->
            assertRefusedWithNothingWritten(useCase.importFromPayload(config(fingerprint = form)))
        }
    }

    @Test
    fun `canonical fingerprint is pinned on the created resource`() = runTest {
        assertTrue(useCase.importFromPayload(config()).isSuccess)

        assertEquals(FINGERPRINT, addedResources.captured.single().hostKeyFingerprint)
    }

    @Test
    fun `a config with only a tunnel path is refused as no usable access path`() = runTest {
        val tunnelOnly = "{\"kind\":\"rendezvousTunnel\",\"host\":\"relay.example.net\",\"port\":44022," +
            "\"shareId\":\"$SHARE_ID\"}"

        val result = useCase.importFromPayload(config(accessPaths = tunnelOnly))

        assertRefusedWithNothingWritten(result, "No usable access path", NO_PATH)
    }

    @Test
    fun `a config with only paths of an unknown kind is refused as no usable access path`() = runTest {
        val unknownOnly = "{\"kind\":\"satellite\",\"host\":\"$HOST\",\"port\":$PORT}," +
            "{\"host\":\"10.0.0.3\",\"port\":$PORT}"

        val result = useCase.importFromPayload(config(accessPaths = unknownOnly))

        assertRefusedWithNothingWritten(result, "No usable access path", NO_PATH)
    }

    @Test
    fun `the first direct path is the primary even when other kinds come before it`() = runTest {
        val paths = "{\"kind\":\"satellite\",\"host\":\"10.0.0.9\",\"port\":$PORT}," +
            "{\"kind\":\"portforward\",\"host\":\"203.0.113.7\",\"port\":$PORT}," +
            "{\"kind\":\"lan\",\"host\":\"$HOST\",\"port\":$PORT}"

        assertTrue(useCase.importFromPayload(config(accessPaths = paths)).isSuccess)

        val resource = addedResources.captured.single()
        assertTrue(resource.path.contains("203.0.113.7"))
        assertEquals(listOf(HostPort(HOST, PORT)), resource.altAccessPaths)
        coVerify(exactly = 0) { smbOperations.saveSftpCredentials("10.0.0.9", any(), any(), any(), any()) }
    }

    @Test
    fun `a path of an unknown kind after a direct one is not kept as an alternate`() = runTest {
        val paths = "{\"kind\":\"lan\",\"host\":\"$HOST\",\"port\":$PORT}," +
            "{\"kind\":\"satellite\",\"host\":\"10.0.0.9\",\"port\":$PORT}"

        assertTrue(useCase.importFromPayload(config(accessPaths = paths)).isSuccess)

        assertTrue(addedResources.captured.single().altAccessPaths.isEmpty())
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

    @Test
    fun `a rendezvous tunnel path becomes a tunnel alternate with its own credential row`() = runTest {
        val json = config(password = "secret").replace(
            "\"port\":$PORT}]",
            "\"port\":$PORT},{\"kind\":\"rendezvousTunnel\",\"host\":\"relay.example.net\",\"port\":44022," +
                "\"shareId\":\"$SHARE_ID\"},{\"kind\":\"rendezvousTunnel\",\"host\":\"relay.example.net\"," +
                "\"port\":44022}]",
        )

        assertTrue(useCase.importFromPayload(json).isSuccess)

        val tunnelHost = "$SHARE_ID@relay.example.net"
        assertEquals(listOf(HostPort(tunnelHost, 44022)), addedResources.captured.single().altAccessPaths)
        coVerify { smbOperations.saveSftpCredentials(tunnelHost, 44022, "fms", "secret", null) }
        coVerify(exactly = 0) { smbOperations.saveSftpCredentials("relay.example.net", any(), any(), any(), any()) }
    }

    private companion object {
        val NO_PATH = CompanionConfigException.Reason.NO_USABLE_ACCESS_PATH
        const val FINGERPRINT = "SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk"
        const val SHARE_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val HOST = "10.0.0.2"
        const val PORT = 2022
        const val CRED_ID = "cred-1"
    }
}
