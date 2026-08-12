package com.sza.fastmediasorter.data.networkmonitor

import android.content.Context
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssCoordinate
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * S1433: pins the opt-in gate on GNSS track recording.
 *
 * This is the one behaviour in the ticket that the Play Data Safety form makes a claim about on the product's
 * behalf, so it is tested off-device rather than left to a device pass: strategic §11 criterion 10 requires
 * the setting, the form and the privacy policy to say the same thing, and a regression here would make the
 * form false rather than make a screen wrong.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GnssTrackRecorderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val settings = MutableStateFlow(AppSettings())
    private val settingsRepository = mockk<SettingsRepository> {
        every { getSettings() } returns settings
    }

    private fun recorder(): GnssTrackRecorder {
        val context = mockk<Context>(relaxed = true) {
            every { filesDir } returns temporaryFolder.root
        }
        return GnssTrackRecorder(
            context = context,
            settingsRepository = settingsRepository,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `no point is recorded while the setting is off`() = runTest {
        settings.value = AppSettings(recordGnssTrack = false)
        val coordinates = MutableSharedFlow<GnssCoordinate>(extraBufferCapacity = COORDINATE_BUFFER)
        val statuses = mutableListOf<GnssTrackStatus>()

        val collection = launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder().record(coordinates).toList(statuses)
        }
        coordinates.emit(coordinate(millis = 1L))
        coordinates.emit(coordinate(millis = 2L))
        collection.cancel()

        assertTrue(statuses.none { status -> status.recording })
        assertEquals(0, statuses.sumOf { status -> status.pointCount })
        // The absence of the directory is the assertion: an empty file would still be a track file the owner
        // never asked for, and would still have to be explained in the Data Safety answer.
        assertFalse(File(temporaryFolder.root, TRACK_DIRECTORY).exists())
    }

    @Test
    fun `turning the setting off mid-session stops the recording`() = runTest {
        settings.value = AppSettings(recordGnssTrack = true)
        val coordinates = MutableSharedFlow<GnssCoordinate>(extraBufferCapacity = COORDINATE_BUFFER)
        val statuses = mutableListOf<GnssTrackStatus>()

        val collection = launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder().record(coordinates).toList(statuses)
        }
        coordinates.emit(coordinate(millis = 1L))
        coordinates.emit(coordinate(millis = 2L))
        val recordedBeforeStop = statuses.last().pointCount
        val trackFile = statuses.last().trackFile

        settings.value = AppSettings(recordGnssTrack = false)
        coordinates.emit(coordinate(millis = 3L))
        collection.cancel()

        assertEquals(TWO_POINTS, recordedBeforeStop)
        assertFalse(statuses.last().recording)
        assertEquals(TWO_POINTS, statuses.maxOf { status -> status.pointCount })
        assertEquals(HEADER_AND_TWO_POINTS, requireNotNull(trackFile).readLines().size)
    }

    @Test
    fun `cancelling the collection releases the recorder`() = runTest {
        settings.value = AppSettings(recordGnssTrack = true)
        val coordinates = MutableSharedFlow<GnssCoordinate>(extraBufferCapacity = COORDINATE_BUFFER)
        val statuses = mutableListOf<GnssTrackStatus>()

        val collection = launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder().record(coordinates).toList(statuses)
        }
        coordinates.emit(coordinate(millis = 1L))
        val trackFile = requireNotNull(statuses.last().trackFile)
        collection.cancel()

        coordinates.emit(coordinate(millis = 2L))
        coordinates.emit(coordinate(millis = 3L))

        assertEquals(1, statuses.last().pointCount)
        // Read back rather than trusting the counter: the point of the assertion is that the file was flushed
        // and closed by cancellation, not merely that the flow stopped emitting.
        assertEquals(HEADER_AND_ONE_POINT, trackFile.readLines().size)
    }

    private fun coordinate(millis: Long) = GnssCoordinate(
        latitudeDegrees = LATITUDE,
        longitudeDegrees = LONGITUDE,
        accuracyMeters = ACCURACY_METERS,
        fixedAtMillis = millis,
    )

    private companion object {

        const val TRACK_DIRECTORY = "gnss-tracks"
        const val COORDINATE_BUFFER = 8
        const val TWO_POINTS = 2
        const val HEADER_AND_ONE_POINT = 2
        const val HEADER_AND_TWO_POINTS = 3
        const val LATITUDE = 50.45
        const val LONGITUDE = 30.52
        const val ACCURACY_METERS = 5.0f
    }
}
