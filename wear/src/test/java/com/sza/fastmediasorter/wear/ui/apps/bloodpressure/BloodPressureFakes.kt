package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import com.sza.fastmediasorter.wear.data.bodysensor.PpgWindowCsv
import com.sza.fastmediasorter.wear.domain.bloodpressure.PulseWaveFeatures
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgCapture
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import com.sza.fastmediasorter.wear.domain.bodysensor.WearPpgDataSource
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSource
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureCalibrationRepository
import com.sza.fastmediasorter.wear.domain.repository.BloodPressureHistoryRepository
import com.sza.fastmediasorter.wear.domain.repository.PpgWindowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** S3113: the owner's recorded still window (research 03), shared by the screen tests. */
internal fun recordedStillWindow(): PpgWindow = PpgWindowCsv.parse(
    requireNotNull(object {}.javaClass.classLoader?.getResource("s3113/ppg_still.csv")) { "fixture missing" }
        .readText()
)

internal class FakePpgDataSource(
    private val terminal: PpgCapture,
    private val reason: BodySensorUnavailableReason? = null
) : WearPpgDataSource {
    var captures = 0
        private set

    override suspend fun unavailableReason(): BodySensorUnavailableReason? = reason

    override fun capture(durationMillis: Long): Flow<PpgCapture> {
        captures++
        return flowOf(PpgCapture.Capturing(0L, durationMillis), terminal)
    }
}

internal class FakeHistoryRepository : BloodPressureHistoryRepository {
    val saved = mutableListOf<Triple<Int, Int, BloodPressureSource>>()
    private val entries = MutableStateFlow<List<BloodPressureHistoryEntry>>(emptyList())

    override suspend fun save(systolic: Int, diastolic: Int, source: BloodPressureSource, pulse: Int?) {
        saved += Triple(systolic, diastolic, source)
    }

    override fun observeAll(): Flow<List<BloodPressureHistoryEntry>> = entries

    override suspend fun deleteAll() {
        saved.clear()
    }
}

internal class FakeCalibrationRepository(initial: List<BloodPressureCalibration> = emptyList()) :
    BloodPressureCalibrationRepository {
    val pairs = MutableStateFlow(initial)

    override suspend fun save(systolic: Int, diastolic: Int, features: PulseWaveFeatures, windowFile: String?) {
        val next = BloodPressureCalibration(
            id = pairs.value.size + 1L,
            systolic = systolic,
            diastolic = diastolic,
            timestampMillis = System.currentTimeMillis(),
            windowFile = windowFile,
            features = features
        )
        pairs.value = listOf(next) + pairs.value
    }

    override fun observeAll(): Flow<List<BloodPressureCalibration>> = pairs

    override suspend fun getAll(): List<BloodPressureCalibration> = pairs.value

    override suspend fun delete(id: Long) {
        pairs.value = pairs.value.filterNot { it.id == id }
    }
}

internal class FakePpgWindowRepository : PpgWindowRepository {
    val saved = mutableListOf<PpgWindow>()

    override suspend fun save(window: PpgWindow, label: String?): String {
        saved += window
        return "${window.startedAtMillis}.csv"
    }

    override suspend fun list(): List<String> = emptyList()

    override suspend fun load(fileName: String): PpgWindow? = null
}
