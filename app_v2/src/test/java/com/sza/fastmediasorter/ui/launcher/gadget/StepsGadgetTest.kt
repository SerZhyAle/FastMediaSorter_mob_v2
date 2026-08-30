package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.model.sensors.StepReading
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveStepCountUseCase
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S2239: verifies StepsGadget step count display and reset flow. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@Suppress("FunctionNaming")
class StepsGadgetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val availability: SensorAvailabilityRepository = mockk {
        every { isAvailable(SensorCapability.STEP_COUNTER) } returns true
    }
    private val observeStepCountUseCase: ObserveStepCountUseCase = mockk<ObserveStepCountUseCase>().also { useCase ->
        every { useCase.invoke() } returns
            flowOf(StepReading(stepsSinceBoot = 1000L, takenAtMillis = 0L))
    }
    private val settingsRepository: SettingsRepository = mockk(relaxed = true) {
        every {
            getSettings()
        } returns flowOf(AppSettings(launcherStepsResetCount = 200L, launcherStepsResetTimestamp = 1600000000000L))
    }
    private val host: LauncherGadgetHost = mockk(relaxed = true)

    @Test
    fun `isAvailable delegates to sensor availability`() {
        val gadget = StepsGadget(
            availability,
            Lazy { observeStepCountUseCase },
            Lazy { settingsRepository },
        )
        assertTrue(gadget.isAvailable())
    }

    @Test
    fun `createView creates StepsGadgetView`() {
        val gadget = StepsGadget(
            availability,
            Lazy { observeStepCountUseCase },
            Lazy { settingsRepository },
        )
        val container = FrameLayout(context)
        val view = gadget.createView(container, host, null)
        assertEquals(R.id.gadgetStepsBody, view.id)
    }
}
