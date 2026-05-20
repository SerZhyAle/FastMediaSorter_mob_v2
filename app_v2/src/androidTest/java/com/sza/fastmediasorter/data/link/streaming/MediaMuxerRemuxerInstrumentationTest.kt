package com.sza.fastmediasorter.data.link.streaming

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * S0116 Phase 03 step 9: instrumentation suite for [MediaMuxerRemuxer].
 *
 * Robolectric does not shadow `MediaMuxer` / `MediaExtractor` - these need real
 * platform native code, hence `@RunWith(AndroidJUnit4)` + a connected device or
 * emulator (gradle task `connectedStandardDebugAndroidTest`).
 *
 * Fixtures live in `androidTest/assets/s0116_fixtures/` and are PLACEHOLDERS by
 * default (see fixture README). When the fixture is empty/absent the test skips
 * via `assumeTrue` rather than failing - replace the placeholders with real
 * media bytes to exercise the cases.
 */
@RunWith(AndroidJUnit4::class)
class MediaMuxerRemuxerInstrumentationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val remuxer = MediaMuxerRemuxer()

    @Test
    fun `avc_aac fixture remuxes to MP4`() {
        val segment = copyFixture("tiny_avc_aac.ts")
        assumeTrue("Fixture is a placeholder - populate per assets/s0116_fixtures/README.md", isRealFixture(segment))
        val output = tempFolder.newFile("out_avc_aac.mp4")
        val bundle = SegmentBundle(
            manifestFile = segment,
            segmentFiles = listOf(segment),
            videoMime = null,
            audioMime = null,
        )
        val result = remuxer.remux(bundle, output)
        assertTrue("expected Success, got $result", result is RemuxResult.Success)
    }

    @Test
    fun `opus fixture reports MuxFailed with codec hint`() {
        val segment = copyFixture("tiny_opus.webm")
        assumeTrue("Fixture is a placeholder - populate per assets/s0116_fixtures/README.md", isRealFixture(segment))
        val output = tempFolder.newFile("out_opus.mp4")
        val bundle = SegmentBundle(
            manifestFile = segment,
            segmentFiles = listOf(segment),
            videoMime = null,
            audioMime = null,
        )
        val result = remuxer.remux(bundle, output)
        assertTrue(
            "expected MuxFailed, got $result",
            result is RemuxResult.MuxFailed && result.codec.contains("opus", ignoreCase = true),
        )
    }

    @Test
    fun `corrupted segment returns MuxFailed extractor_failed`() {
        val corrupted = tempFolder.newFile("corrupted.ts").apply { writeBytes(ByteArray(64) { 0xFF.toByte() }) }
        val output = tempFolder.newFile("out_corrupted.mp4")
        val bundle = SegmentBundle(
            manifestFile = corrupted,
            segmentFiles = listOf(corrupted),
            videoMime = null,
            audioMime = null,
        )
        val result = remuxer.remux(bundle, output)
        assertTrue(
            "expected MuxFailed, got $result",
            result is RemuxResult.MuxFailed,
        )
    }

    private fun copyFixture(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val dest = tempFolder.newFile(name)
        ctx.assets.open("s0116_fixtures/$name").use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        return dest
    }

    /** Placeholder fixtures contain the literal `PLACEHOLDER` ASCII marker. */
    private fun isRealFixture(file: File): Boolean {
        if (!file.exists() || file.length() < 32) return false
        val head = file.readBytes().take(16).map { it.toInt().toChar() }.joinToString("")
        return !head.startsWith("PLACEHOLDER")
    }
}
