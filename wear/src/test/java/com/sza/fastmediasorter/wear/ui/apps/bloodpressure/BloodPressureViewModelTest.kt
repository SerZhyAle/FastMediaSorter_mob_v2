package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import com.sza.fastmediasorter.wear.domain.bloodpressure.EstimateBloodPressureUseCase
import com.sza.fastmediasorter.wear.domain.bloodpressure.ExtractPulseWaveFeaturesUseCase
import com.sza.fastmediasorter.wear.domain.bloodpressure.ImportArchivedCalibrationsUseCase
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveFeatures
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgCapture
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BloodPressureViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val history = FakeHistoryRepository()
    private val extract = ExtractPulseWaveFeaturesUseCase()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `one pair says how many are missing and never captures`() = runTest(dispatcher) {
        val source = FakePpgDataSource(PpgCapture.Captured(recordedStillWindow()))
        val viewModel = viewModel(source, FakeCalibrationRepository(listOf(pair(1L, HR_LOW, SYSTOLIC_LOW))))

        advanceUntilIdle()

        assertEquals(BloodPressureEstimatePhase.NotCalibrated(1, 2), viewModel.state.value.phase)
        assertEquals(0, source.captures)
    }

    @Test
    fun `two pairs and the recorded window give an estimate saved as an estimate`() = runTest(dispatcher) {
        val pairs = listOf(pair(1L, HR_LOW, SYSTOLIC_LOW), pair(2L, HR_HIGH, SYSTOLIC_HIGH))
        val viewModel =
            viewModel(FakePpgDataSource(PpgCapture.Captured(recordedStillWindow())), FakeCalibrationRepository(pairs))

        advanceUntilIdle()

        val phase = viewModel.state.value.phase
        assertTrue("expected an estimate, got $phase", phase is BloodPressureEstimatePhase.Estimated)
        assertEquals(BloodPressureSource.ESTIMATE, history.saved.single().third)
        assertTrue(viewModel.state.value.canMeasure)
    }

    @Test
    fun `a refused permission is named and can be retried`() = runTest(dispatcher) {
        val refusal = PpgCapture.Unavailable(BodySensorUnavailableReason.PERMISSION_DENIED)
        val pairs = listOf(pair(1L, HR_LOW, SYSTOLIC_LOW), pair(2L, HR_HIGH, SYSTOLIC_HIGH))
        val viewModel = viewModel(FakePpgDataSource(refusal), FakeCalibrationRepository(pairs))

        advanceUntilIdle()

        assertEquals(
            BloodPressureEstimatePhase.Unavailable(BodySensorUnavailableReason.PERMISSION_DENIED),
            viewModel.state.value.phase
        )
        assertTrue(viewModel.state.value.canMeasure)
    }

    private fun viewModel(source: FakePpgDataSource, calibrations: FakeCalibrationRepository) = BloodPressureViewModel(
        historyRepository = history,
        calibrationRepository = calibrations,
        ppgDataSource = source,
        extractFeatures = extract,
        estimate = EstimateBloodPressureUseCase(),
        importArchivedCalibrations = ImportArchivedCalibrationsUseCase(FakePpgWindowRepository(), calibrations, extract)
    )

    private fun pair(id: Long, heartRate: Double, systolic: Int) = BloodPressureCalibration(
        id = id,
        systolic = systolic,
        diastolic = DIASTOLIC,
        timestampMillis = id,
        windowFile = null,
        features = PulseWaveFeatures(heartRate, UPSTROKE, WIDTH, PERFUSION, BEATS)
    )

    private companion object {
        const val HR_LOW = 65.0
        const val HR_HIGH = 80.0
        const val SYSTOLIC_LOW = 140
        const val SYSTOLIC_HIGH = 160
        const val DIASTOLIC = 95
        const val UPSTROKE = 0.35
        const val WIDTH = 0.52
        const val PERFUSION = 0.0006
        const val BEATS = 30
    }
}
