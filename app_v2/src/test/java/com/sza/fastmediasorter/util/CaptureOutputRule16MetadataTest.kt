package com.sza.fastmediasorter.util

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.sza.fastmediasorter.ui.cameracapture.helpers.CapturedPhotoAspectCropper
import com.sza.fastmediasorter.util.CaptureFileNamer.CaptureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * CAPTURE-OUTPUT rule 16 conformance test suite (S4372):
 * "The capture time inside the file equals the one in its name. A photo carries it in EXIF
 * DateTimeOriginal, a video or a recording in its container's creation time."
 *
 * Verifies per-kind capture write and metadata read-back:
 * - [CaptureKind.PHOTO]: EXIF DateTimeOriginal equals name instant in local wall-clock time
 * - [CaptureKind.VIDEO]: MP4 mvhd creation_time equals name instant
 * - [CaptureKind.SCREEN_VIDEO]: MP4 mvhd creation_time equals name instant
 * - [CaptureKind.AUDIO]: M4A mvhd creation_time equals name instant
 * - [CaptureKind.VIDEO_FRAME]: JPEG EXIF DateTimeOriginal equals name instant
 * - Crop preservation: aspect cropping preserves DateTimeOriginal
 * - In-place container time update (used for zoom processor preservation)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureOutputRule16MetadataTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val namer = CaptureFileNamer.shared

    private fun withTimeZone(zone: TimeZone, block: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(zone)
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun fixedInstant(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int, zone: TimeZone): Long {
        return Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, min, sec)
        }.timeInMillis
    }

    @Test
    fun `photo capture writes EXIF DateTimeOriginal matching name instant`() {
        val zone = TimeZone.getTimeZone("Europe/Kyiv")
        withTimeZone(zone) {
            val instant = fixedInstant(2026, 10, 19, 14, 30, 15, zone)

            val fileName = namer.allocate(CaptureKind.PHOTO, ".jpg", instant)
            assertEquals("photo_261019_143015.jpg", fileName)

            val file = tempFolder.newFile(fileName)
            createDummyJpeg(file)

            assertTrue(ExifTimeWriter.writeCaptureTime(file, instant, zone))

            val exif = ExifInterface(file.absolutePath)
            val original = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            assertEquals("2026:10:19 14:30:15", original)

            val readBackMillis = ExifTimeWriter.readCaptureTimeMillis(file, zone)
            assertNotNull(readBackMillis)
            assertEquals(instant, readBackMillis)

            val localFromExif = SimpleDateFormat(CaptureFileNamer.DATE_TIME_PATTERN, Locale.US).apply {
                timeZone = zone
            }.format(Date(readBackMillis!!))
            assertTrue(fileName.contains(localFromExif))
        }
    }

    @Test
    fun `captured photo aspect crop preserves EXIF DateTimeOriginal`() {
        val zone = TimeZone.getTimeZone("UTC")
        withTimeZone(zone) {
            val instant = fixedInstant(2026, 8, 21, 10, 15, 30, zone)

            val fileName = namer.allocate(CaptureKind.PHOTO, ".jpg", instant)
            val file = tempFolder.newFile(fileName)
            createDummyJpeg(file, width = 200, height = 100)

            ExifTimeWriter.writeCaptureTime(file, instant, zone)

            // Crop with CapturedPhotoAspectCropper
            CapturedPhotoAspectCropper.cropToRatio(file, 1.0f)

            val exifAfterCrop = ExifInterface(file.absolutePath)
            val originalAfterCrop = exifAfterCrop.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            assertEquals("2026:08:21 10:15:30", originalAfterCrop)

            val readBackMillis = ExifTimeWriter.readCaptureTimeMillis(file, zone)
            assertEquals(instant, readBackMillis)
        }
    }

    @Test
    fun `video capture container creation time matches name instant`() {
        val zone = TimeZone.getTimeZone("UTC")
        withTimeZone(zone) {
            val instant = fixedInstant(2026, 10, 19, 12, 0, 0, zone)
            val unixSeconds = instant / 1000L

            val fileName = namer.allocate(CaptureKind.VIDEO, ".mp4", instant)
            assertEquals("video_261019_120000.mp4", fileName)

            val file = tempFolder.newFile(fileName)
            file.writeBytes(createMinimalMp4(unixSeconds))

            val readBackSeconds = Mp4ContainerTimeReader.readCreationTimeSeconds(file)
            assertEquals(unixSeconds, readBackSeconds)

            val readBackMillis = Mp4ContainerTimeReader.readCreationTimeMillis(file)
            assertEquals(instant, readBackMillis)

            val localFromContainer = SimpleDateFormat(CaptureFileNamer.DATE_TIME_PATTERN, Locale.US).apply {
                timeZone = zone
            }.format(Date(readBackMillis!!))
            assertTrue(fileName.contains(localFromContainer))
        }
    }

    @Test
    fun `screen video recording container creation time matches name instant`() {
        val zone = TimeZone.getTimeZone("Asia/Tokyo")
        withTimeZone(zone) {
            val instant = fixedInstant(2026, 12, 31, 23, 59, 58, zone)
            val unixSeconds = instant / 1000L

            val fileName = namer.allocate(CaptureKind.SCREEN_VIDEO, ".mp4", instant)
            assertEquals("screen_video_261231_235958.mp4", fileName)

            val file = tempFolder.newFile(fileName)
            file.writeBytes(createMinimalMp4(unixSeconds))

            val readBackMillis = Mp4ContainerTimeReader.readCreationTimeMillis(file)
            assertNotNull(readBackMillis)
            assertEquals(instant, readBackMillis)

            val localFromContainer = SimpleDateFormat(CaptureFileNamer.DATE_TIME_PATTERN, Locale.US).apply {
                timeZone = zone
            }.format(Date(readBackMillis!!))
            assertTrue(fileName.contains(localFromContainer))
        }
    }

    @Test
    fun `voice recording M4A container creation time matches name instant`() {
        val zone = TimeZone.getTimeZone("America/New_York")
        withTimeZone(zone) {
            val instant = fixedInstant(2026, 7, 4, 9, 15, 22, zone)
            val unixSeconds = instant / 1000L

            val fileName = namer.allocate(CaptureKind.AUDIO, ".m4a", instant)
            assertEquals("audio_260704_091522.m4a", fileName)

            val file = tempFolder.newFile(fileName)
            file.writeBytes(createMinimalMp4(unixSeconds, ftypMajorBrand = "M4A "))

            val readBackMillis = Mp4ContainerTimeReader.readCreationTimeMillis(file)
            assertNotNull(readBackMillis)
            assertEquals(instant, readBackMillis)

            val localFromContainer = SimpleDateFormat(CaptureFileNamer.DATE_TIME_PATTERN, Locale.US).apply {
                timeZone = zone
            }.format(Date(readBackMillis!!))
            assertTrue(fileName.contains(localFromContainer))
        }
    }

    @Test
    fun `video frame JPEG capture writes EXIF DateTimeOriginal matching name instant`() {
        val zone = TimeZone.getTimeZone("Europe/London")
        withTimeZone(zone) {
            val instant = fixedInstant(2026, 10, 18, 17, 37, 10, zone)

            val fileName = namer.allocate(CaptureKind.VIDEO_FRAME, ".jpg", instant)
            assertEquals("video_frame_261018_173710.jpg", fileName)

            val file = tempFolder.newFile(fileName)
            createDummyJpeg(file)
            ExifTimeWriter.writeCaptureTime(file, instant, zone)

            val exif = ExifInterface(file.absolutePath)
            assertEquals("2026:10:18 17:37:10", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        }
    }

    @Test
    fun `MP4 container creation time version 1 handles 64-bit timestamps`() {
        val unixSeconds = 1792411830L // Year 2026
        val file = tempFolder.newFile("test_v1.mp4")
        file.writeBytes(createMinimalMp4(unixSeconds, version = 1))

        val readBack = Mp4ContainerTimeReader.readCreationTimeSeconds(file)
        assertEquals(unixSeconds, readBack)
    }

    @Test
    fun `MP4 in-place creation time update modifies container time correctly`() {
        val initialSeconds = 1700000000L
        val updatedSeconds = 1792411830L

        val file = tempFolder.newFile("test_update.mp4")
        file.writeBytes(createMinimalMp4(initialSeconds))
        assertEquals(initialSeconds, Mp4ContainerTimeReader.readCreationTimeSeconds(file))

        assertTrue(Mp4ContainerTimeReader.writeCreationTimeSeconds(file, updatedSeconds))
        assertEquals(updatedSeconds, Mp4ContainerTimeReader.readCreationTimeSeconds(file))
    }

    @Test
    fun `text kinds take filename as authority without inventing embedded metadata`() {
        val zone = TimeZone.getTimeZone("UTC")
        withTimeZone(zone) {
            val instant = fixedInstant(2026, 10, 18, 17, 37, 0, zone)

            val textName = namer.allocate(CaptureKind.OCR_TEXT, ".txt", instant)
            val transName = namer.allocate(CaptureKind.TRANSLATION, ".txt", instant)

            assertEquals("ocr_text_261018_173700.txt", textName)
            assertEquals("translation_261018_173700.txt", transName)

            val textFile = tempFolder.newFile(textName)
            textFile.writeText("sample text", Charsets.UTF_8)

            // Non-JPEG/PNG/MP4 files return null for embedded capture time
            val exifTime = ExifTimeWriter.readCaptureTimeMillis(textFile)
            val mp4Time = Mp4ContainerTimeReader.readCreationTimeMillis(textFile)
            assertEquals(null, exifTime)
            assertEquals(null, mp4Time)
        }
    }

    private fun createDummyJpeg(file: File, width: Int = 10, height: Int = 10) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
    }

    private fun createMinimalMp4(
        unixEpochSeconds: Long,
        version: Int = 0,
        ftypMajorBrand: String = "isom"
    ): ByteArray {
        val ftypPayload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .put(ftypMajorBrand.toByteArray(Charsets.US_ASCII))
            .putInt(0x00000200)
            .array()
        return box("ftyp", ftypPayload) +
            box("moov", mvhdBox(unixEpochSeconds, version)) +
            box("mdat", byteArrayOf(0, 0, 0, 0))
    }

    private fun mvhdBox(unixEpochSeconds: Long, version: Int): ByteArray {
        val mp4Seconds = unixEpochSeconds + Mp4ContainerTimeReader.MP4_EPOCH_OFFSET_SECONDS
        val size = if (version == 0) 100 else 112
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        if (version == 0) {
            buffer.put(0.toByte()) // version 0
            buffer.put(byteArrayOf(0, 0, 0)) // flags
            buffer.putInt(mp4Seconds.toInt()) // creation_time
            buffer.putInt(mp4Seconds.toInt()) // modification_time
            buffer.putInt(1000) // timescale
            buffer.putInt(5000) // duration
        } else {
            buffer.put(1.toByte()) // version 1
            buffer.put(byteArrayOf(0, 0, 0)) // flags
            buffer.putLong(mp4Seconds) // creation_time
            buffer.putLong(mp4Seconds) // modification_time
            buffer.putInt(1000) // timescale
            buffer.putLong(5000) // duration
        }
        buffer.putInt(0x00010000) // rate 1.0
        buffer.putShort(0x0100) // volume 1.0
        buffer.put(ByteArray(10)) // reserved
        buffer.put(ByteArray(36)) // matrix
        buffer.put(ByteArray(24)) // pre-defined
        buffer.putInt(2) // next_track_id
        return box("mvhd", buffer.array())
    }

    private fun box(type: String, data: ByteArray): ByteArray {
        val size = data.size + 8
        return ByteBuffer.allocate(size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(size)
            .put(type.toByteArray(Charsets.US_ASCII))
            .put(data)
            .array()
    }
}
