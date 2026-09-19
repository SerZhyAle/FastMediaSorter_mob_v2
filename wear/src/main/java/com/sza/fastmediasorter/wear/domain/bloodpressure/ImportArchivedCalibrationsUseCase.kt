package com.sza.fastmediasorter.wear.domain.bloodpressure

import com.sza.fastmediasorter.wear.domain.repository.BloodPressureCalibrationRepository
import com.sza.fastmediasorter.wear.domain.repository.PpgWindowRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S3113: turns cuff-labelled windows that no pair refers to yet into calibration pairs.
 *
 * The capture build that came before the calibration table archived each "Save" as
 * `<startedAt>_cuff-<systolic>-<diastolic>.csv`, and the owner's first real cuff reading lives in one
 * (research 03). A window under a cuff label is still judged by the quality rules, so the same build's
 * off-the-wrist test save is refused instead of becoming a false pair. Running it twice imports nothing
 * new, because an imported file becomes the `windowFile` of its pair.
 */
class ImportArchivedCalibrationsUseCase @Inject constructor(
    private val windows: PpgWindowRepository,
    private val calibrations: BloodPressureCalibrationRepository,
    private val extract: ExtractPulseWaveFeaturesUseCase
) {

    /** Returns how many pairs were imported. */
    suspend operator fun invoke(): Int {
        val referenced = calibrations.getAll().mapNotNull { it.windowFile }.toSet()
        val candidates = windows.list()
            .filterNot { it in referenced }
            .mapNotNull { name -> CUFF_LABEL.find(name)?.let { match -> name to match } }
        return candidates.count { (name, match) ->
            importOne(name, systolic = match.groupValues[1].toInt(), diastolic = match.groupValues[2].toInt())
        }
    }

    private suspend fun importOne(fileName: String, systolic: Int, diastolic: Int): Boolean {
        val analysis = windows.load(fileName)?.let { extract(it) }
        if (analysis is PulseWaveAnalysis.Accepted) {
            calibrations.save(systolic, diastolic, analysis.features, fileName)
        } else {
            Timber.i("Archived cuff window %s not imported: %s", fileName, analysis)
        }
        return analysis is PulseWaveAnalysis.Accepted
    }

    private companion object {
        val CUFF_LABEL = Regex("_cuff-(\\d{2,3})-(\\d{2,3})\\.csv$")
    }
}
