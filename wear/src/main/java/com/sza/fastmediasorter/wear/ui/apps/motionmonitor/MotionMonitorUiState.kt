package com.sza.fastmediasorter.wear.ui.apps.motionmonitor

import com.sza.fastmediasorter.wear.domain.motion.WearSensorAvailability
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamId

/**
 * One stream as the screen needs it: the reading, and how well it is arriving.
 *
 * [ageMillis] is null while a stream has delivered nothing at all, which the screen must not render the
 * same as an event that arrived a moment ago (S2458 §5.4).
 */
data class MotionStreamRow(
    val id: WearSensorStreamId,
    val availability: WearSensorAvailability,
    val values: List<Float>,
    val eventCount: Int,
    val hertz: Double,
    val ageMillis: Long?
)

/**
 * The two groups the screen shows, kept apart because they answer to different permissions - the motion
 * group needs none at all, the activity group needs ACTIVITY_RECOGNITION and only exists in an edition
 * whose manifest declares it (S2458 ADR-2).
 */
data class MotionMonitorUiState(
    val motion: List<MotionStreamRow> = emptyList(),
    val activity: List<MotionStreamRow> = emptyList()
) {

    /**
     * True only when asking would change something: a refused grant. A stream missing its hardware, or
     * missing from this edition, cannot be fixed by a permission dialog, so the button stays away.
     */
    val canRequestPermission: Boolean
        get() = activity.any { it.availability == WearSensorAvailability.PermissionDenied }
}
