package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room coverage for the hand-written transactional logic of [ResourceDao]:
 * the FTS-sync inserts/updates/deletes, display-order swap/batch reorder, and the
 * S0200 needs-sign-in conditional updates. Pure Room-generated accessors are exercised
 * only as far as needed to observe those transactions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResourceDaoTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.resourceDao()

    private fun resource(
        name: String = "R",
        path: String = "/root",
        type: ResourceType = ResourceType.LOCAL,
        cloudProvider: CloudProvider? = null,
        credentialsId: String? = null,
        displayOrder: Int = 0,
    ) = ResourceEntity(
        name = name,
        path = path,
        type = type,
        cloudProvider = cloudProvider,
        credentialsId = credentialsId,
        displayOrder = displayOrder,
    )

    @Test
    fun `insert returns an id and populates the FTS index`() = runTest {
        val id = dao.insert(resource(name = "Holiday Photos", path = "/dcim/holiday"))
        assertTrue(id > 0)

        val hits = dao.searchResourcesFts("Holiday")
        assertEquals(listOf(id), hits.map { it.id })
    }

    @Test
    fun `update keeps the FTS index in sync`() = runTest {
        val id = dao.insert(resource(name = "OldName"))
        dao.update(dao.getResourceByIdSync(id)!!.copy(name = "BrandNew"))

        assertTrue(dao.searchResourcesFts("OldName").isEmpty())
        assertEquals(listOf(id), dao.searchResourcesFts("BrandNew").map { it.id })
    }

    @Test
    fun `deleteByIdWithFts removes the row and its FTS entry`() = runTest {
        val id = dao.insert(resource(name = "Doomed"))
        dao.deleteByIdWithFts(id)

        assertNull(dao.getResourceByIdSync(id))
        assertTrue(dao.searchResourcesFts("Doomed").isEmpty())
    }

    @Test
    fun `swapDisplayOrders exchanges the two orders`() = runTest {
        val a = dao.insert(resource(name = "A", path = "/a", displayOrder = 1))
        val b = dao.insert(resource(name = "B", path = "/b", displayOrder = 2))

        dao.swapDisplayOrders(a, 1, b, 2)

        assertEquals(2, dao.getResourceByIdSync(a)?.displayOrder)
        assertEquals(1, dao.getResourceByIdSync(b)?.displayOrder)
    }

    @Test
    fun `updateAllDisplayOrders applies every pair`() = runTest {
        val a = dao.insert(resource(name = "A", path = "/a", displayOrder = 0))
        val b = dao.insert(resource(name = "B", path = "/b", displayOrder = 0))
        val c = dao.insert(resource(name = "C", path = "/c", displayOrder = 0))

        dao.updateAllDisplayOrders(listOf(a to 5, b to 6, c to 7))

        assertEquals(5, dao.getResourceByIdSync(a)?.displayOrder)
        assertEquals(6, dao.getResourceByIdSync(b)?.displayOrder)
        assertEquals(7, dao.getResourceByIdSync(c)?.displayOrder)
    }

    @Test
    fun `markAllDriveNeedsSignIn touches only Drive resources`() = runTest {
        val drive = dao.insert(
            resource(name = "Drive", path = "/d", type = ResourceType.CLOUD, cloudProvider = CloudProvider.GOOGLE_DRIVE)
        )
        val local = dao.insert(resource(name = "Local", path = "/l", type = ResourceType.LOCAL))

        dao.markAllDriveNeedsSignIn(true)

        assertTrue(dao.getResourceByIdSync(drive)!!.needsSignIn)
        assertEquals(false, dao.getResourceByIdSync(local)!!.needsSignIn)
    }

    @Test
    fun `needs-sign-in updates are scoped per credentials`() = runTest {
        val primary = dao.insert(
            resource(name = "P", path = "/p", type = ResourceType.CLOUD, cloudProvider = CloudProvider.GOOGLE_DRIVE, credentialsId = "alice@x")
        )
        val secondary = dao.insert(
            resource(name = "S", path = "/s", type = ResourceType.CLOUD, cloudProvider = CloudProvider.GOOGLE_DRIVE, credentialsId = "bob@x")
        )

        dao.markDriveNeedsSignInForCredentials("alice@x", true)
        assertTrue(dao.getResourceByIdSync(primary)!!.needsSignIn)
        assertEquals(false, dao.getResourceByIdSync(secondary)!!.needsSignIn)

        dao.clearNeedsSignInForCredentials("alice@x")
        assertEquals(false, dao.getResourceByIdSync(primary)!!.needsSignIn)
    }

    @Test
    fun `getDestinations returns only destination rows ordered`() = runTest {
        dao.insert(resource(name = "Plain", path = "/p"))
        val destId = dao.insert(resource(name = "Dest", path = "/d").copy(isDestination = true, destinationOrder = 0))

        val destinations = dao.getDestinations().first()
        assertEquals(listOf(destId), destinations.map { it.id })
    }
}
