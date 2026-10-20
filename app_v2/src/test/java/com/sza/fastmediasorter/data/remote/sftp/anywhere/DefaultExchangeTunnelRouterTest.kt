package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.data.repository.settings.DeviceExchangeAccountStore
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SftpTunnelAddress
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultExchangeTunnelRouterTest {

    private fun createRouter(
        resources: List<ResourceEntity> = emptyList(),
        entries: List<DeviceExchangeAccountEntry> = emptyList(),
    ): DefaultExchangeTunnelRouter {
        val resourceDao = mockk<ResourceDao>(relaxed = true)
        val accountStore = mockk<DeviceExchangeAccountStore>(relaxed = true)
        coEvery { resourceDao.getAllResourcesSync() } returns resources
        every { resourceDao.getAllResources() } returns flowOf(resources)
        coEvery { accountStore.snapshot() } returns entries
        every { accountStore.entries } returns flowOf(entries)

        val testDispatcher = UnconfinedTestDispatcher()
        return DefaultExchangeTunnelRouter(
            resourceDao = resourceDao,
            accountStore = accountStore,
            applicationScope = CoroutineScope(testDispatcher),
        )
    }

    @Test
    fun `attached own-account resource routes to OwnAccount`() {
        val entry = DeviceExchangeAccountEntry(
            host = "exchange.test.local",
            port = 443,
            login = "alice",
            deviceId = "dev-1",
            pinnedCertificate = "SHA256:pin1",
            pinnedKey = "SHA256:key1",
        )
        val resource = ResourceEntity(
            id = 10L,
            name = "My Share",
            path = "sftp://sftp.test.local/media",
            type = ResourceType.SFTP,
            exchangeAccount = entry.identity,
            exchangeResourceId = VALID_SHARE_ID_1,
        )

        val router = createRouter(resources = listOf(resource), entries = listOf(entry))
        val tunnelHost = SftpTunnelAddress.host(VALID_SHARE_ID_1, "exchange.test.local")
        val route = router.route(tunnelHost, 22)

        assertTrue(route is ExchangeTunnelRoute.OwnAccount)
        val own = route as ExchangeTunnelRoute.OwnAccount
        assertEquals(entry, own.entry)
        assertEquals(VALID_SHARE_ID_1, own.resourceId)
    }

    @Test
    fun `no resource match but host has pinned certificate routes to Outsider with pinned cert`() {
        val entry = DeviceExchangeAccountEntry(
            host = "exchange.test.local",
            port = 443,
            login = "bob",
            deviceId = "dev-2",
            pinnedCertificate = "SHA256:pin2",
            pinnedKey = "SHA256:key2",
        )

        val router = createRouter(resources = emptyList(), entries = listOf(entry))
        val tunnelHost = SftpTunnelAddress.host(VALID_SHARE_ID_2, "exchange.test.local")
        val route = router.route(tunnelHost, 22)

        assertTrue(route is ExchangeTunnelRoute.Outsider)
        val outsider = route as ExchangeTunnelRoute.Outsider
        assertEquals(VALID_SHARE_ID_2, outsider.shareId)
        assertEquals(ExchangeTrust.Verified("SHA256:pin2", "SHA256:key2"), outsider.trust)
    }

    @Test
    fun `unknown exchange host routes to Outsider with platform trust`() {
        val router = createRouter(resources = emptyList(), entries = emptyList())
        val tunnelHost = SftpTunnelAddress.host(VALID_SHARE_ID_1, "other.exchange.org")
        val route = router.route(tunnelHost, 22)

        assertTrue(route is ExchangeTunnelRoute.Outsider)
        val outsider = route as ExchangeTunnelRoute.Outsider
        assertEquals(VALID_SHARE_ID_1, outsider.shareId)
        assertEquals(ExchangeTrust.Verified(pinned = null), outsider.trust)
    }

    @Test
    fun `non-tunnel host returns null`() {
        val router = createRouter()
        assertNull(router.route("192.168.1.50", 22))
    }

    private companion object {
        const val VALID_SHARE_ID_1 = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val VALID_SHARE_ID_2 = "AQIDBAUGBwgJCgsMDQ4PEA"
    }
}
