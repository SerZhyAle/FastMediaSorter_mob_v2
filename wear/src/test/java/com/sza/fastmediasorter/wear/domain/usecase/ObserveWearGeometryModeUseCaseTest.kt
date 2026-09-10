package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.capability.WearGeometryDefaults
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2773: the fallback from the stored choice to the build variant's starting view.
 *
 * Strategic 6 item 3 puts the unit test here rather than on the shape functions: those are
 * `@Composable` and read `LocalConfiguration`, so they cannot be handed a screen outside an
 * instrumented run and are proved by measuring ink against the glass on the watch. An override that
 * never overrides, by contrast, is invisible on that measurement - it looks exactly like a user who
 * has not touched the setting.
 */
class ObserveWearGeometryModeUseCaseTest {

    @Test
    fun `nothing stored takes the store starting view`() = runTest {
        val useCase = useCase(stored = null, startingMode = WearGeometryMode.STORE)

        assertEquals(WearGeometryMode.STORE, useCase().first())
    }

    @Test
    fun `nothing stored takes the original starting view`() = runTest {
        val useCase = useCase(stored = null, startingMode = WearGeometryMode.ORIGINAL)

        assertEquals(WearGeometryMode.ORIGINAL, useCase().first())
    }

    @Test
    fun `a stored store view overrides an original starting view`() = runTest {
        val useCase = useCase(
            stored = WearGeometryMode.STORE,
            startingMode = WearGeometryMode.ORIGINAL
        )

        assertEquals(WearGeometryMode.STORE, useCase().first())
    }

    @Test
    fun `a stored original view overrides a store starting view`() = runTest {
        val useCase = useCase(
            stored = WearGeometryMode.ORIGINAL,
            startingMode = WearGeometryMode.STORE
        )

        assertEquals(WearGeometryMode.ORIGINAL, useCase().first())
    }

    private fun useCase(
        stored: WearGeometryMode?,
        startingMode: WearGeometryMode
    ): ObserveWearGeometryModeUseCase {
        val preferences = mockk<WearPreferencesRepository>()
        val storedFlow: Flow<WearGeometryMode?> = flowOf(stored)
        every { preferences.storedGeometryMode } returns storedFlow
        return ObserveWearGeometryModeUseCase(
            preferencesRepository = preferences,
            geometryDefaults = FakeGeometryDefaults(startingMode)
        )
    }

    private class FakeGeometryDefaults(
        override val startingMode: WearGeometryMode
    ) : WearGeometryDefaults {
        override val offersModeSwitch: Boolean = true
    }
}
