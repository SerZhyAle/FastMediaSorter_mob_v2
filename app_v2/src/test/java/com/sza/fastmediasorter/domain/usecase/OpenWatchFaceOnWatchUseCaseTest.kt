package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.repository.WatchFaceInstallRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenWatchFaceOnWatchUseCaseTest {

    private val repository = mockk<WatchFaceInstallRepository>()
    private val useCase = OpenWatchFaceOnWatchUseCase(repository)

    @Test
    fun `every repository result reaches the caller unchanged`() = runTest {
        val results = listOf(
            WatchFaceOpenResult.OpenedOnWatch("Pixel Watch"),
            WatchFaceOpenResult.NoWatch,
            WatchFaceOpenResult.WatchStoreUnavailable,
            WatchFaceOpenResult.Failed,
        )
        for (expected in results) {
            coEvery { repository.openListingOnWatch() } returns expected

            assertEquals(expected, useCase())
        }
    }
}
