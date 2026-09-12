package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderPage
import com.sza.fastmediasorter.wear.domain.repository.WearLocalFolderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkFolderRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2694: the lazy hold on the network repository is the mitigation for dragging the protocol clients
 * into the local walk's object graph. A mitigation nothing asserts is only an intention - injecting
 * the repository eagerly would compile, pass every other test, and quietly undo it.
 */
class WearFolderLevelRepositoryImplTest {

    private val emptyPage = WearFolderPage(entries = emptyList(), nextOffset = null)

    private class RecordingLocal : WearLocalFolderRepository {
        var calls = 0
        override suspend fun listLevel(address: WearFolderAddress, offset: Int): Result<WearFolderPage> {
            calls++
            return Result.success(WearFolderPage(emptyList(), null))
        }
    }

    private class RecordingNetwork : WearNetworkFolderRepository {
        var calls = 0
        override suspend fun listLevel(
            address: WearFolderAddress.NetworkLevel,
            offset: Int
        ): Result<WearFolderPage> {
            calls++
            return Result.success(WearFolderPage(emptyList(), null))
        }
    }

    /** Counts construction, which is what `dagger.Lazy` is here to postpone. */
    private class CountingLazy(private val value: RecordingNetwork) : dagger.Lazy<WearNetworkFolderRepository> {
        var constructed = false
        override fun get(): WearNetworkFolderRepository {
            constructed = true
            return value
        }
    }

    @Test
    fun `a network level reaches the network repository only`() = runTest {
        val local = RecordingLocal()
        val network = RecordingNetwork()
        val lazy = CountingLazy(network)
        val dispatcher = WearFolderLevelRepositoryImpl(local, lazy)

        dispatcher.listLevel(WearFolderAddress.NetworkLevel("src", "photos"), 0)

        assertEquals(1, network.calls)
        assertEquals(0, local.calls)
    }

    @Test
    fun `every local variant reaches the local repository only`() = runTest {
        val local = RecordingLocal()
        val network = RecordingNetwork()
        val dispatcher = WearFolderLevelRepositoryImpl(local, CountingLazy(network))

        dispatcher.listLevel(WearFolderAddress.Root, 0)
        dispatcher.listLevel(WearFolderAddress.AppOwned("/data/media"), 0)
        dispatcher.listLevel(WearFolderAddress.MediaStoreFolder("DCIM/"), 0)

        assertEquals(3, local.calls)
        assertEquals(0, network.calls)
    }

    @Test
    fun `a purely local walk never constructs the network repository`() = runTest {
        val lazy = CountingLazy(RecordingNetwork())
        val dispatcher = WearFolderLevelRepositoryImpl(RecordingLocal(), lazy)

        dispatcher.listLevel(WearFolderAddress.Root, 0)
        dispatcher.listLevel(WearFolderAddress.AppOwned("/data/media"), 0)

        assertFalse("the network repository was constructed for a local walk", lazy.constructed)
    }

    @Test
    fun `the network repository is constructed once a network level is listed`() = runTest {
        val lazy = CountingLazy(RecordingNetwork())
        val dispatcher = WearFolderLevelRepositoryImpl(RecordingLocal(), lazy)

        val result = dispatcher.listLevel(WearFolderAddress.NetworkLevel("src", ""), 0)

        assertTrue(lazy.constructed)
        assertEquals(emptyPage, result.getOrNull())
    }
}
