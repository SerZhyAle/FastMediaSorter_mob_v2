package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.calibration

import com.sza.fastmediasorter.wear.domain.bloodpressure.ExtractPulseWaveFeaturesUseCase
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveRejection
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgCapture
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.FakeCalibrationRepository
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.FakePpgDataSource
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.FakePpgWindowRepository
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.recordedStillWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BloodPressureCalibrationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val calibrations = FakeCalibrationRepository()
    private val windows = FakePpgWindowRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an accepted window is archived before save and the cuff values are stored with it`() = runTest(dispatcher) {
        val viewModel = viewModel(PpgCapture.Captured(recordedStillWindow()))

        advanceUntilIdle()
        assertEquals("archived on completion, not on save", 1, windows.saved.size)
        assertTrue(viewModel.state.value.canSave)

        viewModel.onSystolicChanged(CUFF_SYSTOLIC.toString())
        viewModel.onDiastolicChanged(CUFF_DIASTOLIC.toString())
        viewModel.save()
        advanceUntilIdle()

        val stored = calibrations.pairs.value.single()
        assertEquals(CUFF_SYSTOLIC, stored.systolic)
        assertEquals(CUFF_DIASTOLIC, stored.diastolic)
        assertTrue(viewModel.state.value.justSaved)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `a flat window is refused and offers no save`() = runTest(dispatcher) {
        val still = recordedStillWindow()
        val flat = still.copy(ppg = still.ppg.map { it.copy(channels = it.channels.map { FLAT_COUNT }) })
        val viewModel = viewModel(PpgCapture.Captured(flat))

        advanceUntilIdle()

        assertEquals(CalibrationWindowState.Rejected(PulseWaveRejection.WEAK_SIGNAL), viewModel.state.value.window)
        assertFalse(viewModel.state.value.canSave)
        assertTrue(viewModel.state.value.canRecord)
        assertTrue(windows.saved.isEmpty())
    }

    private fun viewModel(terminal: PpgCapture) = BloodPressureCalibrationViewModel(
        calibrationRepository = calibrations,
        ppgDataSource = FakePpgDataSource(terminal),
        ppgWindowRepository = windows,
        extractFeatures = ExtractPulseWaveFeaturesUseCase()
    )

    private companion object {
        const val CUFF_SYSTOLIC = 150
        const val CUFF_DIASTOLIC = 98
        const val FLAT_COUNT = 1_985_000f
    }
}
