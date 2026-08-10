package com.sza.fastmediasorter.domain.model.networkmonitor

/**
 * S1433: what the GNSS section can say about track recording right now.
 *
 * [trackFilePath] is a path and not a `File` on purpose: the recorder writes into `filesDir`, which no file
 * manager can open, so the section has to offer the file - but only the share use case ever opens it, and
 * handing a ViewModel a `java.io.File` would invite a screen to read the track itself.
 */
data class GnssTrackState(
    val recording: Boolean,
    val pointCount: Int,
    val trackFilePath: String?,
) {

    companion object {

        /** Not recording: the setting is off, or the storage refused the session file. */
        val IDLE = GnssTrackState(recording = false, pointCount = 0, trackFilePath = null)
    }
}
