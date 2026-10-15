package com.sza.fastmediasorter.ui.exchange

import com.sza.fastmediasorter.domain.model.AttachmentPin
import com.sza.fastmediasorter.domain.model.ExchangeAttachResult
import com.sza.fastmediasorter.domain.model.ExchangeAttachment
import com.sza.fastmediasorter.domain.model.ExchangeAttachmentState
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryListing
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryRules
import com.sza.fastmediasorter.domain.model.ExchangeDirectorySource
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryView
import com.sza.fastmediasorter.domain.model.ExchangePinAttempt
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.usecase.exchange.AttachExchangeResourcesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeDirectoryViewModelTest {

    private val useCase = mockk<AttachExchangeResourcesUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun record(id: String, name: String, protectedPin: Boolean = true) = SftpRendezvousResource(
        resourceId = id,
        deviceId = "pc",
        kind = SftpRendezvousResource.KIND_SFTP_SHARE,
        name = name,
        descriptor = "FMSSFTP2:synthetic",
        updatedAtMs = 0L,
        writtenAtMs = 0L,
        ttlSeconds = 0L,
        root = "/$name",
        presence = SftpRendezvousDevice.PRESENCE_ONLINE,
        pinProtected = protectedPin,
        protectionScheme = if (protectedPin) SftpRendezvousResource.PROTECTION_PIN_V1 else null,
    )

    private fun attachment(id: String, pin: AttachmentPin) = ExchangeAttachment(
        localId = 1L,
        name = "Private",
        binding = ExchangeBinding(SERVER, id),
        state = ExchangeAttachmentState.AVAILABLE,
        pin = pin,
    )

    private fun listingOf(vararg records: SftpRendezvousResource): ExchangeDirectoryListing =
        ExchangeDirectoryRules.listing(
            listOf(ExchangeDirectorySource(SERVER, emptyList(), records.toList(), emptyList())),
            null,
            emptySet(),
            emptySet(),
        )

    private fun viewOf(
        listing: ExchangeDirectoryListing,
        attachments: List<ExchangeAttachment> = emptyList(),
    ) = ExchangeDirectoryView(listing, attachments)

    private fun viewModel(view: ExchangeDirectoryView): ExchangeDirectoryViewModel {
        coEvery { useCase.observe() } returns flowOf(view)
        coEvery { useCase.sync(any()) } returns view
        return ExchangeDirectoryViewModel(useCase, mockk(relaxed = true)).also { it.open() }
    }

    private fun ExchangeDirectoryViewModel.resourceRows() =
        (state.value as ExchangeDirectoryState.Loaded).rows.filterIsInstance<ExchangeDirectoryRow.Resource>()

    private fun asking(vm: ExchangeDirectoryViewModel) = vm.pinPrompt.value as ExchangePinPrompt.Asking

    @Test
    fun `a protected resource is marked like any other and counts as attachable`() = runTest {
        val vm = viewModel(viewOf(listingOf(record("r1", "Private"))))

        val row = vm.resourceRows().single()

        assertTrue(row.checkable)
        assertTrue((vm.state.value as ExchangeDirectoryState.Loaded).anyAttachable)
    }

    @Test
    fun `attach all asks for each PIN in list order and a skipped one stays unattached`() = runTest {
        val listing = listingOf(record("r1", "First"), record("r2", "Second"))
        val vm = viewModel(viewOf(listing))
        coEvery { useCase.attach(any()) } returns ExchangeAttachResult(
            attached = 0,
            alreadyAttached = 0,
            failed = 0,
            needPin = listOf(ExchangeBinding(SERVER, "r1"), ExchangeBinding(SERVER, "r2")),
        )
        coEvery { useCase.attachProtected(any(), "4711") } returns ExchangePinAttempt.ACCEPTED

        vm.attachAll()
        assertEquals("First", asking(vm).resourceName)

        vm.submitPin("4711")
        assertEquals("Second", asking(vm).resourceName)

        vm.skipPin()
        assertEquals(ExchangePinPrompt.None, vm.pinPrompt.value)
        coVerify(exactly = 1) { useCase.attachProtected(any(), any()) }
    }

    @Test
    fun `a refused PIN keeps the prompt open in its refused phase`() = runTest {
        val vm = viewModel(viewOf(listingOf(record("r1", "Private"))))
        coEvery { useCase.attach(any()) } returns
            ExchangeAttachResult(0, 0, 0, listOf(ExchangeBinding(SERVER, "r1")))
        coEvery { useCase.attachProtected(any(), "0000") } returns ExchangePinAttempt.REFUSED
        coEvery { useCase.attachProtected(any(), "4711") } returns ExchangePinAttempt.UNREACHABLE

        vm.attachAll()
        vm.submitPin("0000")
        assertEquals(ExchangePinPhase.REFUSED, asking(vm).phase)

        vm.submitPin("4711")
        assertEquals(ExchangePinPhase.UNREACHABLE, asking(vm).phase)
        assertEquals("Private", asking(vm).resourceName)
    }

    @Test
    fun `an empty PIN is not presented`() = runTest {
        val vm = viewModel(viewOf(listingOf(record("r1", "Private"))))
        coEvery { useCase.attach(any()) } returns
            ExchangeAttachResult(0, 0, 0, listOf(ExchangeBinding(SERVER, "r1")))

        vm.attachAll()
        vm.submitPin("")

        assertEquals(ExchangePinPhase.IDLE, asking(vm).phase)
        coVerify(exactly = 0) { useCase.attachProtected(any(), any()) }
    }

    @Test
    fun `an attached resource that needs its PIN opens a renewing prompt on a tap`() = runTest {
        val vm = viewModel(viewOf(listingOf(record("r1", "Private")), listOf(attachment("r1", AttachmentPin.NEEDED))))
        coEvery { useCase.renewPin(any(), "4711") } returns ExchangePinAttempt.ACCEPTED

        val row = vm.resourceRows().single()
        assertTrue(row.asksPin)
        vm.askPin(row)

        assertTrue(asking(vm).renewing)
        vm.submitPin("4711")

        coVerify(exactly = 1) { useCase.renewPin(any(), "4711") }
        assertEquals(ExchangePinPrompt.None, vm.pinPrompt.value)
    }

    @Test
    fun `a tap on an attached resource that holds its PIN opens nothing`() = runTest {
        val vm = viewModel(viewOf(listingOf(record("r1", "Private")), listOf(attachment("r1", AttachmentPin.SAVED))))

        vm.askPin(vm.resourceRows().single())

        assertEquals(ExchangePinPrompt.None, vm.pinPrompt.value)
    }

    private companion object {
        const val SERVER = "exchange.example.org:8443/alice"
    }
}
