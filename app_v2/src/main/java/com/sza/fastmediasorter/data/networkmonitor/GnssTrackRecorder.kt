package com.sza.fastmediasorter.data.networkmonitor

import android.content.Context
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssCoordinate
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1433: what the GNSS section can say about track recording right now.
 *
 * [trackFile] is the session's file, so the section can offer it to its owner - a track written where no file
 * manager can reach it and no screen offers it would be recorded for nobody.
 */
data class GnssTrackStatus(
    val recording: Boolean,
    val pointCount: Int,
    val trackFile: File?,
) {

    companion object {

        /** Not recording: the setting is off, or the file could not be opened. */
        val IDLE = GnssTrackStatus(recording = false, pointCount = 0, trackFile = null)
    }
}

/**
 * S1433: writes the GNSS track to the device while the owner has asked for it and the section is open.
 *
 * Two independent gates, and both are structural rather than remembered. The setting is read live and drives
 * a `flatMapLatest`, so switching it off cancels the session mid-flight instead of being noticed at the next
 * point; and the whole recorder is a cold flow, so closing the section ends it. There is no start method to
 * call from anywhere else, which is what keeps strategic §3.2's "only while the screen is open" from being a
 * convention somebody has to honour.
 *
 * The track never leaves the device: this class holds no HTTP client and no repository, and its only sink is
 * a file under `filesDir`.
 */
@Singleton
class GnssTrackRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Records [coordinates] for as long as this flow is collected and the setting stays on.
     *
     * Emits the current status rather than nothing, so a section can show a point count and offer the file
     * without reaching into the file system itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun record(coordinates: Flow<GnssCoordinate>): Flow<GnssTrackStatus> =
        settingsRepository.getSettings()
            .map { settings -> settings.recordGnssTrack }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) {
                    session(coordinates)
                } else {
                    flowOf(GnssTrackStatus.IDLE)
                }
            }
            .flowOn(ioDispatcher)

    private fun session(coordinates: Flow<GnssCoordinate>): Flow<GnssTrackStatus> = flow {
        val writer = openWriter()
        if (writer == null) {
            emit(GnssTrackStatus.IDLE)
        } else {
            writer.use { open ->
                var recorded = 0
                emit(GnssTrackStatus(recording = true, pointCount = 0, trackFile = open.file))
                coordinates.collect { point ->
                    if (open.append(point)) {
                        recorded++
                        emit(GnssTrackStatus(recording = true, pointCount = recorded, trackFile = open.file))
                    }
                }
            }
        }
    }

    /**
     * Opens this session's file, or null when the storage refuses.
     *
     * A refusal downgrades to "not recording" rather than throwing: the GNSS section is a diagnostic screen
     * and full storage is not a reason to take the satellite view down with it.
     */
    private fun openWriter(): TrackWriter? = try {
        val directory = trackDirectory()
        directory.mkdirs()
        prune(directory)
        val file = File(directory, fileName())
        val sink = file.bufferedWriter()
        sink.appendLine(HEADER_LINE)
        sink.flush()
        TrackWriter(file, sink)
    } catch (io: IOException) {
        Timber.w(io, "GNSS track file could not be opened; recording stays off")
        null
    } catch (security: SecurityException) {
        Timber.w(security, "GNSS track directory refused; recording stays off")
        null
    }

    /**
     * Drops the oldest sessions past [MAX_TRACK_FILES].
     *
     * Nothing else ever deletes them: `filesDir` is private to the app, so no file manager and no cleaner
     * reaches it, and a screen left open on a long drive would otherwise leave a file behind every time.
     */
    private fun prune(directory: File) {
        val existing = directory.listFiles { file -> file.isFile && file.name.endsWith(FILE_EXTENSION) }
            ?: return
        existing.sortedByDescending { file -> file.lastModified() }
            .drop(MAX_TRACK_FILES - 1)
            .forEach { stale -> stale.delete() }
    }

    private fun trackDirectory(): File = File(context.filesDir, TRACK_DIRECTORY)

    private fun fileName(): String =
        TRACK_PREFIX + SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US).format(Date()) + FILE_EXTENSION

    private companion object {

        const val TRACK_DIRECTORY = "gnss-tracks"
        const val TRACK_PREFIX = "track-"
        const val FILE_EXTENSION = ".csv"
        const val FILE_STAMP_PATTERN = "yyyyMMdd-HHmmss"
        const val HEADER_LINE = "fixed_at_millis,latitude,longitude,accuracy_meters"

        /** How many past sessions stay on the device. */
        const val MAX_TRACK_FILES = 20
    }
}

/**
 * S1433: one open track file.
 *
 * Flushes per point rather than on close, because the session ends by cancellation far more often than by
 * completion - the screen closes - and a buffer dropped at that moment would lose the tail of the drive.
 */
private class TrackWriter(
    val file: File,
    private val sink: BufferedWriter,
) : Closeable {

    fun append(point: GnssCoordinate): Boolean = try {
        sink.appendLine(line(point))
        sink.flush()
        true
    } catch (io: IOException) {
        Timber.w(io, "GNSS track point could not be written")
        false
    }

    override fun close() {
        try {
            sink.close()
        } catch (io: IOException) {
            Timber.w(io, "GNSS track file could not be closed cleanly")
        }
    }

    private fun line(point: GnssCoordinate): String = listOf(
        point.fixedAtMillis.toString(),
        point.latitudeDegrees.toString(),
        point.longitudeDegrees.toString(),
        point.accuracyMeters?.toString().orEmpty(),
    ).joinToString(separator = ",")
}
