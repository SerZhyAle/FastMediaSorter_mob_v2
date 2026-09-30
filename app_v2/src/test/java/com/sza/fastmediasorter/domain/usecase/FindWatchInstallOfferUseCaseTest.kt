package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.model.WatchInstallOffer
import com.sza.fastmediasorter.domain.repository.WatchFaceInstallRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindWatchInstallOfferUseCaseTest {

    private val repository = mockk<WatchFaceInstallRepository>()

    @Test
    fun `the offer reaches the caller unchanged`() = runTest {
        val offers = listOf(
            WatchInstallOffer(watchName = "Pixel Watch", watchAppInstalled = false),
            WatchInstallOffer(watchName = "Galaxy Watch", watchAppInstalled = true),
        )
        for (expected in offers) {
            coEvery { repository.findInstallOffer() } returns expected

            assertEquals(expected, FindWatchInstallOfferUseCase(repository)())
        }
    }

    @Test
    fun `no watch means no offer`() = runTest {
        coEvery { repository.findInstallOffer() } returns null

        assertNull(FindWatchInstallOfferUseCase(repository)())
    }

    @Test
    fun `opening the watch app listing passes every result through`() = runTest {
        val results = listOf(
            WatchFaceOpenResult.OpenedOnWatch("Pixel Watch"),
            WatchFaceOpenResult.NoWatch,
            WatchFaceOpenResult.WatchStoreUnavailable,
            WatchFaceOpenResult.Failed,
        )
        for (expected in results) {
            coEvery { repository.openWatchAppListingOnWatch() } returns expected

            assertEquals(expected, OpenWatchAppOnWatchUseCase(repository)())
        }
    }
}
