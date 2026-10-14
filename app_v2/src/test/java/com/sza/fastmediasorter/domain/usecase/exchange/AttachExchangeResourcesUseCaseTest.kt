package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.data.remote.exchange.ExchangeResourcePinStore
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeDirectoryRepository
import com.sza.fastmediasorter.domain.model.AttachmentPin
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeAttachmentState
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryListing
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryRules
import com.sza.fastmediasorter.domain.model.ExchangeDirectorySource
import com.sza.fastmediasorter.domain.model.ExchangeListedResource
import com.sza.fastmediasorter.domain.model.ExchangePinAttempt
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.SftpTunnelAddress
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import com.sza.fastmediasorter.testing.fakes.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachExchangeResourcesUseCaseTest {

    private val entry = DeviceExchangeAccountEntry(
        host = "exchange.example",
        port = 7443,
        login = "user",
        deviceId = "me",
        pinnedCertificate = null,
    )
    private val resources = FakeResourceRepository()
    private val credentials = mockk<SmbOperationsUseCase>()
    private val accounts = mockk<DeviceExchangeAccountRepository>()
    private val pins = MemoryPins()
    private val refusals = MutableStateFlow<Set<ExchangeBinding>>(emptySet())
    private val verifyPin = mockk<VerifyExchangePinUseCase>(relaxed = true)
    private val useCase = AttachExchangeResourcesUseCase(
        directory = mockk<ExchangeDirectoryRepository>(),
        accounts = accounts,
        resources = resources,
        addResource = AddResourceUseCase(resources),
        credentials = credentials,
        settings = FakeSettingsRepository(),
        capabilities = mockk<CapabilityAvailability>(relaxed = true),
        pinStore = pins,
        verifyPin = verifyPin,
    )

    init {
        every { verifyPin.refusals } returns refusals
        every { accounts.entries } returns MutableStateFlow(listOf(entry))
        coEvery { credentials.saveSftpCredentials(any(), any(), any(), any(), any()) } returns
            Result.success(CREDENTIALS)
    }

    @Test
    fun `attach creates an SFTP resource bound to the entry and the resourceId`() = runTest {
        val listing = listingOf(record("r1", hosts = listOf("192.168.1.5", "10.0.0.5"), shareId = SHARE_ID))

        val result = useCase.attach(listing.devices.single().resources)

        assertEquals(1, result.attached)
        val made = resources.resources.single()
        assertEquals(ResourceType.SFTP, made.type)
        assertEquals("sftp://192.168.1.5:2222/Photos", made.path)
        assertEquals(entry.identity, made.exchangeAccount)
        assertEquals("r1", made.exchangeResourceId)
        assertEquals(
            listOf(
                HostPort("10.0.0.5", PORT),
                HostPort(SftpTunnelAddress.host(SHARE_ID, entry.host), entry.port),
            ),
            made.altAccessPaths,
        )
    }

    @Test
    fun `attaching the same binding twice keeps one resource`() = runTest {
        val items = listingOf(record("r1")).devices.single().resources
        useCase.attach(items)

        val again = useCase.attach(items)

        assertEquals(0, again.attached)
        assertEquals(1, again.alreadyAttached)
        assertEquals(1, resources.resources.size)
    }

    @Test
    fun `a later record's access replaces the kept one`() = runTest {
        useCase.attach(listingOf(record("r1")).devices.single().resources)

        useCase.sync(listingOf(record("r1", hosts = listOf("192.168.1.77"))))

        assertEquals("sftp://192.168.1.77:2222/Photos", resources.resources.single().path)
    }

    @Test
    fun `offline, unpublished and revoked records never delete the bound source`() = runTest {
        useCase.attach(listingOf(record("r1")).devices.single().resources)

        val offline = useCase.sync(listingOf(record("r1", presence = SftpRendezvousDevice.PRESENCE_OFFLINE)))
        val unpublished = useCase.sync(listingOf())
        val revoked = useCase.sync(
            ExchangeDirectoryRules.listing(
                listOf(ExchangeDirectorySource(entry.identity, emptyList(), emptyList(), emptyList())),
                null,
                setOf(entry.deviceId),
                emptySet(),
            )
        )
        val unread = useCase.sync(
            ExchangeDirectoryRules.listing(
                listOf(ExchangeDirectorySource.unreachable(entry.identity)),
                null,
                emptySet(),
                emptySet(),
            )
        )

        assertEquals(ExchangeAttachmentState.UNAVAILABLE, offline.attachments.single().state)
        assertEquals(ExchangeAttachmentState.NO_LONGER_PUBLISHED, unpublished.attachments.single().state)
        assertEquals(ExchangeAttachmentState.NO_LONGER_PUBLISHED, revoked.attachments.single().state)
        assertEquals(ExchangeAttachmentState.UNAVAILABLE, unread.attachments.single().state)
        assertTrue(resources.deletedResourceIds.isEmpty())
        assertEquals(1, resources.resources.size)
    }

    @Test
    fun `a record without access or under a PIN is not attached`() = runTest {
        val noAccess = ExchangeListedResource(entry.identity, record("r1").copy(descriptor = null), true)
        val locked = ExchangeListedResource(entry.identity, record("r2").copy(pinProtected = true), true)

        val result = useCase.attach(listOf(noAccess, locked))

        assertEquals(0, result.attached)
        assertEquals(2, result.failed)
        assertTrue(resources.resources.isEmpty())
    }

    @Test
    fun `a protected item waits for its PIN and is never attached by attach`() = runTest {
        val result = useCase.attach(listOf(protectedItem("r9")))

        assertEquals(0, result.attached)
        assertEquals(0, result.failed)
        assertEquals(listOf(ExchangeBinding(entry.identity, "r9")), result.needPin)
        assertTrue(resources.resources.isEmpty())
    }

    @Test
    fun `an accepted PIN is kept and only then is the protected resource attached`() = runTest {
        coEvery { verifyPin(any(), "4711") } returns ExchangePinAttempt.ACCEPTED

        val outcome = useCase.attachProtected(protectedItem("r9"), "4711")

        assertEquals(ExchangePinAttempt.ACCEPTED, outcome)
        assertEquals("4711", pins.values[ExchangeBinding(entry.identity, "r9")])
        assertEquals("r9", resources.resources.single().exchangeResourceId)
        verify { verifyPin.accepted(ExchangeBinding(entry.identity, "r9")) }
    }

    @Test
    fun `a refused or unreachable proof attaches nothing and keeps nothing`() = runTest {
        coEvery { verifyPin(any(), any()) } returns ExchangePinAttempt.REFUSED
        assertEquals(ExchangePinAttempt.REFUSED, useCase.attachProtected(protectedItem("r9"), "0000"))

        coEvery { verifyPin(any(), any()) } returns ExchangePinAttempt.UNREACHABLE
        assertEquals(ExchangePinAttempt.UNREACHABLE, useCase.attachProtected(protectedItem("r9"), "0000"))

        assertTrue(resources.resources.isEmpty())
        assertTrue(pins.values.isEmpty())
    }

    @Test
    fun `a record that carries no usable access cannot be proven`() = runTest {
        val withheld = ExchangeListedResource(entry.identity, protectedRecord("r9").copy(descriptor = null), true)

        assertEquals(ExchangePinAttempt.FAILED, useCase.attachProtected(withheld, "4711"))
        assertEquals(ExchangePinAttempt.UNREACHABLE, useCase.renewPin(withheld, "4711"))
    }

    @Test
    fun `an attached resource shows its PIN as saved, refused or needed`() = runTest {
        coEvery { verifyPin(any(), any()) } returns ExchangePinAttempt.ACCEPTED
        val item = protectedItem("r9")
        useCase.attachProtected(item, "4711")
        val listing = listingOf(protectedRecord("r9"))
        val binding = ExchangeBinding(entry.identity, "r9")

        assertEquals(AttachmentPin.SAVED, useCase.sync(listing).attachments.single().pin)

        refusals.value = setOf(binding)
        assertEquals(AttachmentPin.NEEDED, useCase.sync(listing).attachments.single().pin)

        refusals.value = emptySet()
        pins.values.clear()
        assertEquals(AttachmentPin.NEEDED, useCase.sync(listing).attachments.single().pin)
    }

    @Test
    fun `a renewed PIN replaces the stored one only when the owner accepts it`() = runTest {
        val item = protectedItem("r9")
        val binding = ExchangeBinding(entry.identity, "r9")
        pins.values[binding] = "old"

        coEvery { verifyPin(any(), "wrong") } returns ExchangePinAttempt.REFUSED
        assertEquals(ExchangePinAttempt.REFUSED, useCase.renewPin(item, "wrong"))
        assertEquals("old", pins.values[binding])

        coEvery { verifyPin(any(), "new") } returns ExchangePinAttempt.ACCEPTED
        assertEquals(ExchangePinAttempt.ACCEPTED, useCase.renewPin(item, "new"))
        assertEquals("new", pins.values[binding])
        verify { verifyPin.accepted(binding) }
    }

    @Test
    fun `a PIN is forgotten when its resource is no longer published and the resource stays`() = runTest {
        coEvery { verifyPin(any(), any()) } returns ExchangePinAttempt.ACCEPTED
        useCase.attachProtected(protectedItem("r9"), "4711")

        useCase.sync(listingOf())

        assertTrue(pins.values.isEmpty())
        assertEquals(1, resources.resources.size)
    }

    private fun protectedRecord(id: String) =
        record(id).copy(pinProtected = true, protectionScheme = SftpRendezvousResource.PROTECTION_PIN_V1)

    private fun protectedItem(id: String) = ExchangeListedResource(entry.identity, protectedRecord(id), true)

    private class MemoryPins : ExchangeResourcePinStore {
        val values = mutableMapOf<ExchangeBinding, String>()

        override suspend fun pin(binding: ExchangeBinding): String? = values[binding]

        override suspend fun save(binding: ExchangeBinding, pin: String): Boolean {
            values[binding] = pin
            return true
        }

        override suspend fun forget(binding: ExchangeBinding) {
            values.remove(binding)
        }

        override suspend fun move(from: String, to: String, resourceIds: Collection<String>) = Unit
    }

    private fun listingOf(vararg records: SftpRendezvousResource): ExchangeDirectoryListing =
        ExchangeDirectoryRules.listing(
            listOf(ExchangeDirectorySource(entry.identity, emptyList(), records.toList(), emptyList())),
            null,
            setOf(entry.deviceId),
            emptySet(),
        )

    private fun record(
        id: String,
        hosts: List<String> = listOf("192.168.1.5"),
        shareId: String? = null,
        presence: String = SftpRendezvousDevice.PRESENCE_ONLINE,
    ) = SftpRendezvousResource(
        resourceId = id,
        deviceId = "pc",
        kind = SftpRendezvousResource.KIND_SFTP_SHARE,
        name = "Photos on PC",
        descriptor = SftpPairingPayload(hosts, PORT, "fms", "secret", FINGERPRINT).encode(),
        updatedAtMs = 0L,
        writtenAtMs = 0L,
        ttlSeconds = 0L,
        shareId = shareId,
        root = "Photos",
        presence = presence,
    )

    private companion object {
        const val PORT = 2222
        const val CREDENTIALS = "cred-1"
        const val SHARE_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val FINGERPRINT = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}
