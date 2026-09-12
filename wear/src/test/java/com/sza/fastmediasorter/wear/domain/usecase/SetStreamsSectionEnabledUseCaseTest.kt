package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2511: the switch and the tile it invalidates, checked together.
 *
 * A device pass found the sections tile still offering Streams long after the switch was off, because the
 * preference write was the whole of the action and tile content is pulled rather than pushed. Persisting
 * without asking for a redraw is exactly the defect, so the two assertions belong in one test.
 */
class SetStreamsSectionEnabledUseCaseTest {

    @Test
    fun `switching the section off persists and invalidates the sections tile`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val refresh = mockk<RequestWearTileRefreshUseCase>(relaxed = true)

        SetStreamsSectionEnabledUseCase(repository, refresh)(false)

        // The fake's flow is a snapshot taken at construction; what a write moves is the field.
        assertFalse(repository.streamsSectionEnabledValue)
        verify { refresh(WearTileKind.SECTIONS) }
    }

    @Test
    fun `switching the section on invalidates the tile as well`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val refresh = mockk<RequestWearTileRefreshUseCase>(relaxed = true)
        val useCase = SetStreamsSectionEnabledUseCase(repository, refresh)

        useCase(false)
        useCase(true)

        assertTrue(repository.streamsSectionEnabledValue)
        verify(exactly = 2) { refresh(WearTileKind.SECTIONS) }
    }
}
