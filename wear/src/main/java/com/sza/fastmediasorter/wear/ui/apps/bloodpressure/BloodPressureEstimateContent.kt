package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R

private val CONTENT_PADDING = 8.dp
private const val BADGE_ALPHA = 0.2f

/**
 * S3113: the estimate half of the blood-pressure screen - progress, the estimate with what it rests on,
 * or the sentence that replaces it.
 *
 * The value is announced as one TalkBack node that says "estimated" and how old the calibration is, so the
 * number is never read out on its own as if it were a measurement (strategic §3.2).
 */
@Composable
internal fun BloodPressureEstimateContent(phase: BloodPressureEstimatePhase) {
    when (phase) {
        is BloodPressureEstimatePhase.Idle -> Unit
        is BloodPressureEstimatePhase.Capturing -> MeasuringIndicator(phase.elapsedMillis, phase.totalMillis)
        is BloodPressureEstimatePhase.Estimated -> EstimatedContent(phase)
        is BloodPressureEstimatePhase.NotCalibrated -> StatusText(
            stringResource(R.string.blood_pressure_not_calibrated, phase.pairCount, phase.requiredPairs)
        )
        is BloodPressureEstimatePhase.Rejected -> StatusText(stringResource(rejectionText(phase.reason)))
        is BloodPressureEstimatePhase.Unavailable -> StatusText(stringResource(captureReasonText(phase.reason)))
    }
}

@Composable
private fun EstimatedContent(phase: BloodPressureEstimatePhase.Estimated) {
    val pulse = stringResource(R.string.blood_pressure_pulse, phase.pulse)
    val description = stringResource(
        R.string.blood_pressure_estimate_description,
        phase.systolic,
        phase.diastolic,
        phase.pairCount,
        phase.newestPairAgeDays.toInt()
    ) + ". " + pulse
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CONTENT_PADDING)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.blood_pressure_estimate_label),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.blood_pressure_reading_format, phase.systolic, phase.diastolic),
            style = MaterialTheme.typography.display3.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colors.onSurface
        )
        Text(
            text = pulse,
            style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colors.onSurface
        )
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(phase.category.color.copy(alpha = BADGE_ALPHA))
                .padding(horizontal = 12.dp, vertical = 3.dp)
        ) {
            Text(
                text = stringResource(phase.category.labelRes),
                style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                color = phase.category.color
            )
        }
        StatusText(
            stringResource(R.string.blood_pressure_estimate_basis, phase.pairCount, phase.newestPairAgeDays.toInt())
        )
        if (phase.errorSystolic != null && phase.errorDiastolic != null) {
            StatusText(
                stringResource(R.string.blood_pressure_estimate_error, phase.errorSystolic, phase.errorDiastolic)
            )
        }
    }
}

@Composable
internal fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CONTENT_PADDING, vertical = 2.dp),
        textAlign = TextAlign.Center
    )
}
