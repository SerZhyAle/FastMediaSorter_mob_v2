package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.sza.fastmediasorter.wear.data.exchange.WearFolderIdentity
import com.sza.fastmediasorter.wear.data.exchange.WearResourcePinStore
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearServerClipboardRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S4227 ADR-1 and S4230 ADR-1: the watch's own receivers follow the phone's list and never enter it, and a resource
 * the watch cannot write to is not among them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearSendToReceiverCatalogRepositoryTest {

    private val phoneList = MutableStateFlow(listOf(WearSendToReceiverEntry(id = "com.mail/.Compose", title = "Mail")))
    private val eligible = MutableStateFlow(false)
    private val networkSources = MutableStateFlow<List<NetworkSource>>(emptyList())
    private val phone = mockk<WearSendToReceiversRepository> { every { observe() } returns phoneList }
    private val clipboard = mockk<WearServerClipboardRepository> { every { hasEligibleEntry } returns eligible }
    private val sources = mockk<NetworkSourceRepository> { every { observeSources() } returns networkSources }
    private val pins = mockk<WearResourcePinStore> {
        every { changes } returns kotlinx.coroutines.flow.emptyFlow()
        coEvery { pin(any()) } returns null
    }
    private val context = mockk<Context> { every { getString(any()) } returns "Clipboard on the server" }

    private fun TestScope.catalog() = WearSendToReceiverCatalogRepository(
        phone,
        clipboard,
        sources,
        pins,
        context,
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    @Test
    fun `no eligible entry shows the phone list alone`() = runTest {
        assertEquals(listOf("com.mail/.Compose"), catalog().observe().value.map { it.id })
    }

    @Test
    fun `an eligible entry adds the receiver after the phone receivers`() = runTest {
        val catalog = catalog()
        eligible.value = true

        assertEquals(
            listOf("com.mail/.Compose", WearSendToReceiverCatalogRepository.SERVER_CLIPBOARD_RECEIVER_ID),
            catalog.observe().value.map { it.id },
        )
    }

    @Test
    fun `a phone list replacement keeps the watch receiver`() = runTest {
        val catalog = catalog()
        eligible.value = true
        phoneList.value = emptyList()

        val ids = catalog.observe().value.map { it.id }

        assertEquals(listOf(WearSendToReceiverCatalogRepository.SERVER_CLIPBOARD_RECEIVER_ID), ids)
        assertEquals(false, catalog.observe().value.single().batchCapable)
    }

    @Test
    fun `a writable resource is listed while the phone is away`() = runTest {
        val catalog = catalog()
        phoneList.value = emptyList()
        networkSources.value = listOf(source("nas", "NAS"))

        val entry = catalog.observe().value.single()

        assertEquals(WearSendToReceiverCatalogRepository.RESOURCE_RECEIVER_PREFIX + "nas", entry.id)
        assertEquals("NAS", entry.title)
        assertEquals(true, entry.servedOnWatch)
        assertEquals(true, entry.batchCapable)
    }

    @Test
    fun `a read-only resource is not listed`() = runTest {
        val catalog = catalog()
        networkSources.value = listOf(source("nas", "NAS", readOnly = true))

        assertEquals(listOf("com.mail/.Compose"), catalog.observe().value.map { it.id })
    }

    @Test
    fun `a protected resource is listed only once its PIN is held`() = runTest {
        val catalog = catalog()
        val protected = source("vault", "Vault", pinProtected = true, fingerprint = "SHA256:abc")
        networkSources.value = listOf(protected)
        assertEquals(listOf("com.mail/.Compose"), catalog.observe().value.map { it.id })

        coEvery { pins.pin(WearFolderIdentity.of(protected)!!) } returns "1234"
        networkSources.value = listOf(protected.copy(name = "Vault "))

        assertEquals(
            listOf("com.mail/.Compose", WearSendToReceiverCatalogRepository.RESOURCE_RECEIVER_PREFIX + "vault"),
            catalog.observe().value.map { it.id },
        )
    }

    @Test
    fun `the order is phone then clipboard then resources`() = runTest {
        val catalog = catalog()
        eligible.value = true
        networkSources.value = listOf(source("nas", "NAS"))

        assertEquals(
            listOf(
                "com.mail/.Compose",
                WearSendToReceiverCatalogRepository.SERVER_CLIPBOARD_RECEIVER_ID,
                WearSendToReceiverCatalogRepository.RESOURCE_RECEIVER_PREFIX + "nas",
            ),
            catalog.observe().value.map { it.id },
        )
    }

    @Test
    fun `a receiver id round trips to its source id`() {
        val id = WearSendToReceiverCatalogRepository.RESOURCE_RECEIVER_PREFIX + "abc-1"

        assertEquals("abc-1", WearSendToReceiverCatalogRepository.sourceIdOf(id))
        assertEquals(null, WearSendToReceiverCatalogRepository.sourceIdOf("com.mail/.Compose"))
        assertEquals(null, WearSendToReceiverCatalogRepository.sourceIdOf("watch:resource:"))
    }

    private fun source(
        id: String,
        name: String,
        readOnly: Boolean = false,
        pinProtected: Boolean = false,
        fingerprint: String? = null,
    ) = NetworkSource(
        id = id,
        type = NetworkSourceType.SFTP,
        name = name,
        server = "192.168.1.5",
        username = "u",
        password = "p",
        readOnly = readOnly,
        pinProtected = pinProtected,
        hostKeyFingerprint = fingerprint,
    )
}
