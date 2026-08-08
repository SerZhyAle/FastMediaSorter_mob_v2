package com.sza.fastmediasorter.ui.dialog.helpers

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1474: rows never disappear and a code never throws - the two behaviours a later refactor breaks
 * silently, because no build gate can see either.
 *
 * The resolver returns the resource id as text, so an assertion can name the exact key that was
 * chosen rather than a translated phrase that changes with the wording.
 */
class StreamPropertiesFormatterTest {

    private val formatter = StreamPropertiesFormatter(FakeResources)

    @Test
    fun `every fixed-choice code maps to its own word`() {
        val kinds = listOf("AUDIO" to R.string.stream_info_kind_audio, "VIDEO" to R.string.stream_info_kind_video)

        kinds.forEach { (code, expected) ->
            val group = formatter.channelGroup(entity(mediaKind = code))

            assertEquals(text(expected), group.properties.last().value)
        }
        assertEquals(
            text(R.string.stream_info_origin_catalog),
            formatter.catalogGroup(entity(sourceOrigin = "CATALOG"), lastPlayOutcome = null).properties.first().value,
        )
    }

    @Test
    fun `an unknown code falls back instead of throwing`() {
        val group = formatter.channelGroup(entity(mediaKind = "SOMETHING_NEW"))

        assertEquals(text(R.string.stream_info_state_empty), group.properties.last().value)
    }

    @Test
    fun `an entity with every optional field unset still produces every row`() {
        val group = formatter.catalogGroup(entity(), lastPlayOutcome = null)

        assertEquals(CATALOG_ROW_COUNT, group.properties.size)
        assertTrue(group.properties.any { it.value == StreamInfoValue.Empty })
    }

    @Test
    fun `formats with nothing measured still produce a full group of unavailable rows`() {
        val group = formatter.measuredGroup(StreamMeasuredFormats())

        assertEquals(MEASURED_ROW_COUNT, group.properties.size)
        assertTrue(group.properties.all { it.value == StreamInfoValue.Unavailable })
    }

    @Test
    fun `a measured value is reported as text rather than as unavailable`() {
        val group = formatter.measuredGroup(StreamMeasuredFormats(videoWidth = 1920, videoHeight = 1080))

        val pictureSize = group.properties[PICTURE_SIZE_ROW].value

        assertEquals(StreamInfoValue.Text("1920x1080"), pictureSize)
    }

    @Test
    fun `clipboard text carries one line per row with both label and value`() {
        val readout = formatter.readout(entity(), StreamMeasuredFormats(), lastPlayOutcome = null)

        val lines = formatter.asPlainText(readout).lines()

        val rowCount = readout.groups.sumOf { it.properties.size }
        assertEquals(rowCount + readout.groups.size, lines.size)
        assertTrue(lines.filter { it.contains(':') }.all { it.substringAfter(':').isNotBlank() })
    }

    private fun text(resId: Int) = StreamInfoValue.Text(resId.toString())

    private fun entity(
        mediaKind: String = "VIDEO",
        sourceOrigin: String = "MANUAL",
    ): StreamSourceEntity = StreamSourceEntity(
        id = "id",
        url = "http://example.invalid/live",
        title = "Channel",
        mediaKind = mediaKind,
        sourceOrigin = sourceOrigin,
        sortIndex = 0,
        addedAt = 0L,
    )

    private object FakeResources : StreamInfoResources {
        override fun string(resId: Int): String = resId.toString()
        override fun dateTime(epochMillis: Long): String = epochMillis.toString()
        override fun pictureSize(width: Int, height: Int): String = "${width}x$height"
        override fun frameRate(value: Float): String = value.toString()
        override fun bitrate(bitsPerSecond: Long): String = bitsPerSecond.toString()
        override fun sampleRate(hertz: Int): String = hertz.toString()
    }

    private companion object {
        const val CATALOG_ROW_COUNT = 10
        const val MEASURED_ROW_COUNT = 9
        const val PICTURE_SIZE_ROW = 1
    }
}
