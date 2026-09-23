package com.sza.fastmediasorter.ui.common.widget

import com.sza.fastmediasorter.domain.model.sensors.CompassReading
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveCompassUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S3370: the cache the spark pair reads at tap time.
 *
 * A cached UNRELIABLE reading would paint compass colors without a working compass, and a cache
 * surviving [DimHeadingProvider.setActive]`(false)` would aim the next tap's sparks at a heading
 * read while the screen was last dimmed.
 */
class DimHeadingProviderTest {

    private val readings = MutableSharedFlow<CompassReading>(extraBufferCapacity = 8)

    private fun provider(scope: TestScope): DimHeadingProvider {
        val useCase = mockk<ObserveCompassUseCase>()
        every { useCase() } returns readings
        return DimHeadingProvider(useCase, scope)
    }

    @Test
    fun `cache is empty before the first confident reading`() = runTest {
        val provider = provider(TestScope(StandardTestDispatcher(testScheduler)))

        provider.setActive(true)
        advanceUntilIdle()

        assertNull(provider.current())
    }

    @Test
    fun `unreliable readings are dropped and the latest confident one wins`() = runTest {
        val provider = provider(TestScope(StandardTestDispatcher(testScheduler)))
        provider.setActive(true)
        advanceUntilIdle()

        readings.tryEmit(CompassReading(10f, SensorAccuracy.UNRELIABLE, null, 1L))
        advanceUntilIdle()
        assertNull(provider.current())

        readings.tryEmit(CompassReading(20f, SensorAccuracy.MEDIUM, null, 2L))
        advanceUntilIdle()
        assertEquals(20f, provider.current()?.azimuthDegrees)

        readings.tryEmit(CompassReading(30f, SensorAccuracy.HIGH, null, 3L))
        advanceUntilIdle()
        assertEquals(30f, provider.current()?.azimuthDegrees)
    }

    @Test
    fun `cache resets when the provider goes inactive`() = runTest {
        val provider = provider(TestScope(StandardTestDispatcher(testScheduler)))
        provider.setActive(true)
        advanceUntilIdle()
        readings.tryEmit(CompassReading(45f, SensorAccuracy.HIGH, null, 1L))
        advanceUntilIdle()

        provider.setActive(false)

        assertNull(provider.current())
    }
}
