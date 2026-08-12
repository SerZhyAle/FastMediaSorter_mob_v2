package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.data.networkmonitor.GnssTrackRecorder
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssCoordinate
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssTrackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1433: records the open section's coordinates, and reports what the recorder is doing.
 *
 * No `flowOn` here, unlike every other Monitor use case: the recorder is the one source that sets
 * `@IoDispatcher` itself, because it writes a file rather than reading a binder (Phase 06's handoff note).
 * Adding a second one would only hide which layer owns the thread.
 *
 * The recorder's own status type carries a `java.io.File`; it is mapped to a path here so the section never
 * receives an open handle to a file it has no business reading.
 */
class RecordGnssTrackUseCase @Inject constructor(
    private val recorder: GnssTrackRecorder,
) {

    operator fun invoke(coordinates: Flow<GnssCoordinate>): Flow<GnssTrackState> =
        recorder.record(coordinates).map { status ->
            GnssTrackState(
                recording = status.recording,
                pointCount = status.pointCount,
                trackFilePath = status.trackFile?.absolutePath,
            )
        }
}
