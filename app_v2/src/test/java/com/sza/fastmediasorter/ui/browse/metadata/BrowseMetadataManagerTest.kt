package com.sza.fastmediasorter.ui.browse.metadata

import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseMetadataManagerTest {

    private val updateResourceUseCase: UpdateResourceUseCase = mockk()

    @Test
    fun `cancellation is rethrown instead of being logged as a metadata failure`() = runTest {
        // S3005: navigating away mid-browse cancels this update, and the generic catch used to log
        // the JobCancellationException at error level ("Exception while updating resource metadata")
        // and answer null - a normal teardown shown as a failure, twice, in the same millisecond.
        coEvery { updateResourceUseCase(any()) } throws CancellationException("scope cancelled")
        val manager = BrowseMetadataManager(updateResourceUseCase, StandardTestDispatcher(testScheduler))

        var propagated = false
        try {
            manager.updateMetadata(createMediaResource(id = 1L), actualFileCount = 3)
        } catch (e: CancellationException) {
            propagated = true
        }

        assertTrue("CancellationException must be rethrown, not answered with null", propagated)
    }

    @Test
    fun `a real failure is still swallowed into a null result`() = runTest {
        coEvery { updateResourceUseCase(any()) } throws IllegalStateException("db closed")
        val manager = BrowseMetadataManager(updateResourceUseCase, StandardTestDispatcher(testScheduler))

        assertNull(manager.updateMetadata(createMediaResource(id = 1L), actualFileCount = 3))
    }
}
