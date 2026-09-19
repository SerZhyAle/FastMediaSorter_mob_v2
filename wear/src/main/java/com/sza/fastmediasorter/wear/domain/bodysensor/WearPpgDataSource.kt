package com.sza.fastmediasorter.wear.domain.bodysensor

import kotlinx.coroutines.flow.Flow

/**
 * S3113: raw pulse-wave windows for the blood-pressure estimate, and the reason when there cannot be one.
 *
 * Repeats the shape of [WearBodySensorDataSource] on purpose (strategic ADR-2): one implementation per
 * flavor source set with a binding beside each, `standard` withholding the capability and `noLegal`
 * reading the sensor. A second pulse-wave source replaces the first by binding alone, without touching
 * signal processing or the screen.
 */
interface WearPpgDataSource {

    /** Why a window could not start right now, or null when it could. Asked without starting one. */
    suspend fun unavailableReason(): BodySensorUnavailableReason?

    /**
     * One capture window of [durationMillis], as a COLD flow: nothing is registered until somebody
     * collects, and every sensor is released when the window completes or the collector leaves.
     *
     * Emits [PpgCapture.Capturing] while recording, then exactly one terminal [PpgCapture.Captured] or
     * [PpgCapture.Unavailable], after which the flow completes.
     */
    fun capture(durationMillis: Long): Flow<PpgCapture>
}
