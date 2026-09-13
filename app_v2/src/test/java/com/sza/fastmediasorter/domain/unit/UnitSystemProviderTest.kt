package com.sza.fastmediasorter.domain.unit

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ObserveUnitSystemUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2795: the caching is a requirement rather than an optimisation - the seam sits on the draw path of
 * lists and a ticking clock - so a regression that turns each read back into a storage read has to
 * fail here instead of only showing as jank on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnitSystemProviderTest {

    private val settings = MutableStateFlow(AppSettings())

    @Test
    fun `starts at the default before the first emission is collected`() = runTest {
        val provider = newProvider()
        assertEquals(UnitSystem.DEFAULT, provider.value)
    }

    @Test
    fun `follows a later emission`() = runTest {
        val provider = newProvider()
        settings.value = AppSettings(unitSystem = UnitSystem.IMPERIAL)
        runCurrent()
        assertEquals(UnitSystem.IMPERIAL, provider.value)
    }

    @Test
    fun `repeated reads do not need a new collection`() = runTest {
        val provider = newProvider()
        settings.value = AppSettings(unitSystem = UnitSystem.IMPERIAL)
        runCurrent()
        assertEquals(provider.value, provider.value)
        assertEquals(UnitSystem.IMPERIAL, provider.current.value)
    }

    // backgroundScope, not the test scope: the provider shares the setting eagerly and for the life of
    // the application, so a job on the test body's own scope never completes and every case times out.
    // S3066: on the default StandardTestDispatcher the eager collector is queued as background work,
    // which advanceUntilIdle() never runs, so the unconfined dispatcher subscribes it at construction
    // the way Eagerly does on the application's real dispatcher.
    private fun TestScope.newProvider(): UnitSystemProvider {
        val repository = mockk<SettingsRepository>()
        every { repository.getSettings() } returns settings
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        return UnitSystemProvider(ObserveUnitSystemUseCase(repository), scope)
    }
}
