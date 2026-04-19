package com.sza.fastmediasorter.ui.player

import android.os.Bundle
import androidx.media3.common.Format
import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for [StereoDetector].
 *
 * Tests cover:
 * 1. Aspect-ratio heuristic — flat SBS / mono and spherical (360° mono/SBS/OU) dimensions
 * 2. Matroska metadata detection — tag values mapped to flat StereoMode
 * 3. Metadata priority over aspect ratio
 * 4. Edge cases — invalid dimensions, borderline AR values
 * 5. False-positive guard — common ultra-wide mono content
 * 6. Filename token detection — flat + spherical (360°/VR180/Cylinder) patterns
 */
class StereoDetectorTest {

    private lateinit var detector: StereoDetector

    @Before
    fun setUp() {
        detector = StereoDetector()
    }

    // ── Aspect ratio heuristic — flat SBS detection ───────────────────────

    @Test
    fun `detectFromDimensions returns SBS_FULL for 3840x1080`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(3840, 1080))
    }

    @Test
    fun `detectFromDimensions returns SBS_FULL for 1920x540`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(1920, 540))
    }

    @Test
    fun `detectFromDimensions returns SBS_FULL for 2560x720`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(2560, 720))
    }

    // ── Aspect ratio heuristic — mono detection ──────────────────────────

    @Test
    fun `detectFromDimensions returns MONO for 1920x1080`() {
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1920, 1080))
    }

    @Test
    fun `detectFromDimensions returns MONO for 1280x720`() {
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1280, 720))
    }

    @Test
    fun `detectFromDimensions returns MONO for 3840x2160 (4K)`() {
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(3840, 2160))
    }

    @Test
    fun `detectFromDimensions returns MONO for ultrawide 4096x820`() {
        // AR ≈ 4.99 — outside narrow EQUIRECT_360_SBS window (3.95..4.05) and above flat SBS max
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(4096, 820))
    }

    @Test
    fun `detectFromDimensions returns MONO for narrow 1280x1280`() {
        // AR = 1.0 but width 1280 is below SPHERICAL_SBS_MIN_WIDTH (3840) → not EQUIRECT_360_OU
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1280, 1280))
    }

    // ── Aspect ratio heuristic — spherical detection ─────────────────────

    @Test
    fun `detectFromDimensions returns EQUIRECT_360_MONO for 4096x2048`() {
        assertEquals(StereoMode.EQUIRECT_360_MONO, detector.detectFromDimensions(4096, 2048))
    }

    @Test
    fun `detectFromDimensions returns EQUIRECT_360_MONO for 7680x3840`() {
        assertEquals(StereoMode.EQUIRECT_360_MONO, detector.detectFromDimensions(7680, 3840))
    }

    @Test
    fun `detectFromDimensions returns MONO when 2-to-1 AR but width below spherical floor`() {
        // AR 2:1 but width 1024 < SPHERICAL_MIN_WIDTH (2048) → NOT spherical
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1024, 512))
    }

    @Test
    fun `detectFromDimensions returns EQUIRECT_360_SBS for 7680x1920`() {
        // AR 4:1 stacked SBS 360° content
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromDimensions(7680, 1920))
    }

    @Test
    fun `detectFromDimensions returns EQUIRECT_360_SBS for 8192x2048`() {
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromDimensions(8192, 2048))
    }

    @Test
    fun `detectFromDimensions returns EQUIRECT_360_OU for 3840x3840`() {
        // AR 1:1 stacked OU 360° content
        assertEquals(StereoMode.EQUIRECT_360_OU, detector.detectFromDimensions(3840, 3840))
    }

    @Test
    fun `4K mono at 3840x2160 is not detected as any spherical mode`() {
        // AR 1.778 — must not match any spherical AR window
        val result = detector.detectFromDimensions(3840, 2160)
        assertEquals(StereoMode.MONO, result)
    }

    // ── Aspect ratio edge cases ───────────────────────────────────────────

    @Test
    fun `detectFromDimensions returns UNKNOWN for zero dimensions`() {
        assertEquals(StereoMode.UNKNOWN, detector.detectFromDimensions(0, 1080))
        assertEquals(StereoMode.UNKNOWN, detector.detectFromDimensions(1920, 0))
        assertEquals(StereoMode.UNKNOWN, detector.detectFromDimensions(0, 0))
    }

    @Test
    fun `detectFromDimensions returns UNKNOWN for negative dimensions`() {
        assertEquals(StereoMode.UNKNOWN, detector.detectFromDimensions(-1920, 1080))
    }

    @Test
    fun `detectFromDimensions returns SBS_FULL at SBS_AR_MIN boundary 3_20`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(3840, 1200))
    }

    @Test
    fun `detectFromDimensions returns SBS_FULL at SBS_AR_MAX boundary 3_80`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromDimensions(3800, 1000))
    }

    // ── Matroska metadata — mono tag ─────────────────────────────────────

    @Test
    fun `detectFromFormat returns MONO when Matroska tag is 0 (mono)`() {
        val format = buildFormatWithTag("0", 3840, 1080) // AR would suggest SBS_FULL
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    // ── Matroska metadata — SBS tags ─────────────────────────────────────

    @Test
    fun `detectFromFormat returns SBS_FULL when Matroska tag is 1 (SBS left-first)`() {
        val format = buildFormatWithTag("1", 1920, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    @Test
    fun `detectFromFormat returns SBS_FULL when Matroska tag is 11 (SBS right-first)`() {
        val format = buildFormatWithTag("11", 1920, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    // ── Matroska metadata — OU tag ───────────────────────────────────────

    @Test
    fun `detectFromFormat returns OU when Matroska tag is 3 (OU top-first)`() {
        val format = buildFormatWithTag("3", 1920, 1080)
        assertEquals(StereoMode.OU, detector.detectFromFormat(format))
    }

    // ── Matroska metadata priority over AR heuristic ─────────────────────

    @Test
    fun `Matroska mono tag overrides SBS aspect ratio`() {
        val format = buildFormatWithTag("0", 3840, 1080)
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    @Test
    fun `Matroska SBS tag overrides mono aspect ratio`() {
        val format = buildFormatWithTag("1", 1920, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    // ── Missing / unknown tag — fall back to AR ───────────────────────────

    @Test
    fun `detectFromFormat falls back to AR when tag absent`() {
        val format = buildFormatWithoutTag(3840, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    @Test
    fun `detectFromFormat returns MONO for unrecognised tag value`() {
        val format = buildFormatWithTag("99", 1920, 1080)
        // Unknown tag → UNKNOWN from metadata path → fall back to AR → MONO for 1920x1080
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    @Test
    fun `detectFromFormat detects spherical via AR when tag absent`() {
        // 4096x2048 is AR 2:1 → EQUIRECT_360_MONO via heuristic
        val format = buildFormatWithoutTag(4096, 2048)
        assertEquals(StereoMode.EQUIRECT_360_MONO, detector.detectFromFormat(format))
    }

    // ── False-positive rate guard ─────────────────────────────────────────

    @Test
    fun `common cinema aspect ratios are not detected as 3D`() {
        // 2.39:1 (Scope) — must not be SBS
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(2560, 1072))
        // 2.35:1 (CinemaScope)
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1920, 816))
        // 1.85:1 (Flat)
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1920, 1038))
    }

    // ── Filename — flat patterns ──────────────────────────────────────────

    @Test
    fun `detectFromFilename SBS_FULL for explicit sbs marker`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFilename("movie_3dh.mp4"))
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFilename("movie_sbs.mkv"))
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFilename("clip_lr.mp4"))
    }

    @Test
    fun `detectFromFilename SBS_HALF for hsbs marker`() {
        assertEquals(StereoMode.SBS_HALF, detector.detectFromFilename("movie_hsbs.mkv"))
        assertEquals(StereoMode.SBS_HALF, detector.detectFromFilename("movie_halfsbs.mkv"))
    }

    @Test
    fun `detectFromFilename OU for explicit ou marker`() {
        assertEquals(StereoMode.OU, detector.detectFromFilename("movie_ou.mp4"))
        assertEquals(StereoMode.OU, detector.detectFromFilename("movie_tb.mkv"))
        assertEquals(StereoMode.OU, detector.detectFromFilename("movie_3dv.mp4"))
    }

    @Test
    fun `detectFromFilename UNKNOWN for plain filename`() {
        assertEquals(StereoMode.UNKNOWN, detector.detectFromFilename("vacation.mp4"))
        assertEquals(StereoMode.UNKNOWN, detector.detectFromFilename("sunset_clip.mkv"))
    }

    @Test
    fun `detectFromFilename does not false-match sbs substring inside word`() {
        // "absorbs" contains "sbs" but must not be detected (word-boundary check)
        assertEquals(StereoMode.UNKNOWN, detector.detectFromFilename("absorbs_clip.mp4"))
    }

    // ── Filename — spherical patterns ─────────────────────────────────────

    @Test
    fun `detectFromFilename EQUIRECT_360_MONO for bare 360 marker`() {
        assertEquals(StereoMode.EQUIRECT_360_MONO, detector.detectFromFilename("vacation_360.mp4"))
        assertEquals(StereoMode.EQUIRECT_360_MONO, detector.detectFromFilename("sunset_360_clip.mp4"))
    }

    @Test
    fun `detectFromFilename EQUIRECT_360_MONO for equirect marker`() {
        assertEquals(StereoMode.EQUIRECT_360_MONO, detector.detectFromFilename("beach_equirect.mp4"))
    }

    @Test
    fun `detectFromFilename EQUIRECT_360_SBS when 360 and sbs both present`() {
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromFilename("vacation_360_sbs.mp4"))
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromFilename("trip_sbs_360.mp4"))
    }

    @Test
    fun `detectFromFilename EQUIRECT_360_OU when 360 and ou or tb present`() {
        assertEquals(StereoMode.EQUIRECT_360_OU, detector.detectFromFilename("vacation_360_ou.mp4"))
        assertEquals(StereoMode.EQUIRECT_360_OU, detector.detectFromFilename("clip_tb_360.mp4"))
    }

    @Test
    fun `detectFromFilename VR180_FISHEYE_SBS for vr180 or 180x180 markers`() {
        assertEquals(StereoMode.VR180_FISHEYE_SBS, detector.detectFromFilename("movie_vr180.mp4"))
        assertEquals(StereoMode.VR180_FISHEYE_SBS, detector.detectFromFilename("beach_180x180.mp4"))
    }

    @Test
    fun `detectFromFilename EQUIRECT_180_SBS when 180 and sbs both present`() {
        assertEquals(StereoMode.EQUIRECT_180_SBS, detector.detectFromFilename("vacation_180_sbs.mp4"))
    }

    @Test
    fun `detectFromFilename EQUIRECT_180_MONO for plain 180 marker`() {
        assertEquals(StereoMode.EQUIRECT_180_MONO, detector.detectFromFilename("clip_180.mp4"))
        assertEquals(StereoMode.EQUIRECT_180_MONO, detector.detectFromFilename("sunset_equirect180.mp4"))
    }

    @Test
    fun `detectFromFilename CYLINDER_180 for cylinder marker`() {
        assertEquals(StereoMode.CYLINDER_180, detector.detectFromFilename("panorama_cylinder.mp4"))
        assertEquals(StereoMode.CYLINDER_180, detector.detectFromFilename("city_cylinder180.mp4"))
    }

    @Test
    fun `detectFromFilename UNKNOWN for cubemap (unsupported projection)`() {
        assertEquals(StereoMode.UNKNOWN, detector.detectFromFilename("scene_cubemap.mp4"))
    }

    @Test
    fun `detectFromFilename does not match 360 inside camera sequence DJI_0360`() {
        // DJI/GoPro camera sequence numbers embed digits; word-boundary check must reject
        assertEquals(StereoMode.UNKNOWN, detector.detectFromFilename("DJI_0360.mp4"))
    }

    @Test
    fun `detectFromFilename spherical wins over flat when both present`() {
        // A file with both "sbs" and "360" must yield EQUIRECT_360_SBS, not flat SBS_FULL
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromFilename("content_sbs_360.mp4"))
    }

    @Test
    fun `detectForVideo prefers MP4 spatial metadata over filename heuristic`() {
        val file = createMp4WithSpatialMetadata(
            filenameSuffix = "_360_sbs",
            st3dLayout = 1,
            projectionBox = "equi"
        )
        val format = buildFormatWithoutTag(1920, 1080)

        assertEquals(StereoMode.EQUIRECT_360_OU, detector.detectForVideo(file.absolutePath, format))
    }

    @Test
    fun `detectFromFilename is case-insensitive`() {
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromFilename("VACATION_360_SBS.MP4"))
        assertEquals(StereoMode.VR180_FISHEYE_SBS, detector.detectFromFilename("CLIP_VR180.MP4"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Build a minimal [Format] with a [Bundle] carrying a fake Matroska stereo tag.
     *
     * Media3 API exposure differs across versions, so this helper attaches the Bundle
     * reflectively and skips metadata-specific tests if the current build does not expose
     * a compatible setter on [Format.Builder].
     */
    private fun buildFormatWithTag(tagValue: String, width: Int, height: Int): Format {
        val bundle = Bundle().apply { putString("stereo_mode", tagValue) }
        val builder = Format.Builder()
            .setWidth(width)
            .setHeight(height)

        val setter = builder.javaClass.methods.firstOrNull { method ->
            method.parameterCount == 1 && method.name == "setCustomData"
        }
        assumeTrue("Media3 build does not expose Format.Builder.setCustomData", setter != null)
        setter!!.invoke(builder, bundle)
        return builder.build()
    }

    private fun buildFormatWithoutTag(width: Int, height: Int): Format {
        return Format.Builder()
            .setWidth(width)
            .setHeight(height)
            .build()
    }

    private fun createMp4WithSpatialMetadata(
        filenameSuffix: String = "",
        st3dLayout: Int,
        projectionBox: String,
    ): File {
        val tempDir = createTempDirectory(prefix = "stereo-detector-").toFile()
        tempDir.deleteOnExit()
        val file = File(tempDir, "sample${filenameSuffix}.mp4")
        file.deleteOnExit()
        file.writeBytes(spatialMp4Bytes(st3dLayout, projectionBox))
        return file
    }

    private fun spatialMp4Bytes(st3dLayout: Int, projectionBox: String): ByteArray {
        return box("ftyp", byteArrayOf(0, 0, 0, 0)) +
            box(
                "moov",
                box(
                    "trak",
                    box(
                        "mdia",
                        box(
                            "minf",
                            box(
                                "stbl",
                                stsdBox(
                                    visualSampleEntry(
                                        "hvc1",
                                        box("st3d", fullBoxPayload(byteArrayOf(st3dLayout.toByte()))) +
                                            box(
                                                "sv3d",
                                                box(
                                                    "proj",
                                                    box(
                                                        "mshp",
                                                        box(projectionBox, byteArrayOf(0, 0, 0, 0))
                                                    )
                                                )
                                            )
                                    )
                                )
                            )
                        )
                    )
                )
            )
    }

    private fun fullBoxPayload(payload: ByteArray): ByteArray = byteArrayOf(0, 0, 0, 0) + payload

    private fun stsdBox(entry: ByteArray): ByteArray = box(
        "stsd",
        byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1) + entry
    )

    private fun visualSampleEntry(type: String, childBoxes: ByteArray): ByteArray = box(
        type,
        ByteArray(78) + childBoxes
    )

    private fun box(type: String, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(8 + payload.size)
        buffer.put(type.toByteArray(Charsets.US_ASCII))
        buffer.put(payload)
        return buffer.array()
    }
}
