package com.sza.fastmediasorter.ui.player

import androidx.media3.common.C
import androidx.media3.common.Format
import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for [StereoDetector].
 *
 * Tests cover:
 * 1. Aspect-ratio heuristic - flat SBS / mono and spherical (360° mono/SBS/OU) dimensions
 * 2. Container stereo tag detection - [C.StereoMode] values mapped to flat StereoMode
 * 3. Metadata priority over aspect ratio
 * 4. Edge cases - invalid dimensions, borderline AR values
 * 5. False-positive guard - common ultra-wide mono content
 * 6. Filename token detection - flat + spherical (360°/VR180/Cylinder) patterns
 *
 * S2892 dropped the Robolectric runner S2876 had added: the tag cases needed a real [android.os.Bundle]
 * only while the detector read the tag out of custom data. `Format.stereoMode` is a plain int, so
 * these run on the stubbed android.jar again.
 */
class StereoDetectorTest {

    private lateinit var detector: StereoDetector

    @Before
    fun setUp() {
        detector = StereoDetector()
    }

    // ── Aspect ratio heuristic - flat SBS detection ───────────────────────

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

    // ── Aspect ratio heuristic - mono detection ──────────────────────────

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
        // AR ≈ 4.99 - outside narrow EQUIRECT_360_SBS window (3.95..4.05) and above flat SBS max
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(4096, 820))
    }

    @Test
    fun `detectFromDimensions returns MONO for narrow 1280x1280`() {
        // AR = 1.0 but width 1280 is below SPHERICAL_SBS_MIN_WIDTH (3840) → not EQUIRECT_360_OU
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1280, 1280))
    }

    // ── Aspect ratio heuristic - spherical detection ─────────────────────

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
        // AR 1.778 - must not match any spherical AR window
        val result = detector.detectFromDimensions(3840, 2160)
        assertEquals(StereoMode.MONO, result)
    }

    // ── Aspect ratio heuristic - flat OU detection (Full-OU 8:9 stacking) ─

    @Test
    fun `detectFromDimensions returns OU for 1920x2160 (Full-OU 1080p)`() {
        // AR = 8:9 = 0.8889. Real-world: 3D-Blu-ray Full-OU 1080p masters.
        assertEquals(StereoMode.OU, detector.detectFromDimensions(1920, 2160))
    }

    @Test
    fun `detectFromDimensions returns OU for 1280x1440 (Full-OU 720p)`() {
        // AR = 8:9 = 0.8889. Smallest legitimate Full-OU master (at width floor).
        assertEquals(StereoMode.OU, detector.detectFromDimensions(1280, 1440))
    }

    @Test
    fun `detectFromDimensions returns OU for 3840x4320 (Full-OU 4K)`() {
        // AR = 8:9 = 0.8889. 4K Full-OU master.
        assertEquals(StereoMode.OU, detector.detectFromDimensions(3840, 4320))
    }

    @Test
    fun `detectFromDimensions returns MONO for 1080x1350 (4 to 5 IG portrait)`() {
        // AR = 0.8 - outside the flat-OU ±0.02 window around 0.8889 → must not false-positive.
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1080, 1350))
    }

    @Test
    fun `detectFromDimensions returns MONO for 1080x1920 (9 to 16 phone portrait)`() {
        // AR = 0.5625 - far outside flat-OU window → must not false-positive.
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1080, 1920))
    }

    @Test
    fun `detectFromDimensions returns MONO for 800x900 (8 to 9 but below width floor)`() {
        // AR = 0.8889 matches flat-OU window but width 800 < FLAT_OU_MIN_WIDTH (1280) → MONO.
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(800, 900))
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

    // ── Container stereo tag - mono ──────────────────────────────────────

    @Test
    fun `detectFromFormat returns MONO when container tag is STEREO_MODE_MONO`() {
        val format = buildFormatWithStereoMode(C.STEREO_MODE_MONO, 3840, 1080) // AR would suggest SBS_FULL
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    // ── Container stereo tag - SBS ───────────────────────────────────────

    @Test
    fun `detectFromFormat returns SBS_FULL when container tag is STEREO_MODE_LEFT_RIGHT`() {
        val format = buildFormatWithStereoMode(C.STEREO_MODE_LEFT_RIGHT, 1920, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    // ── Container stereo tag - OU ────────────────────────────────────────

    @Test
    fun `detectFromFormat returns OU when container tag is STEREO_MODE_TOP_BOTTOM`() {
        val format = buildFormatWithStereoMode(C.STEREO_MODE_TOP_BOTTOM, 1920, 1080)
        assertEquals(StereoMode.OU, detector.detectFromFormat(format))
    }

    /**
     * S2892: Media3's constants are not the raw EBML numbers. `MatroskaExtractor` maps raw EBML 1
     * (SBS) to [C.STEREO_MODE_LEFT_RIGHT] = 2 and raw EBML 3 (OU) to [C.STEREO_MODE_TOP_BOTTOM] = 1,
     * so the two layouts swap numbers on the way in. A detector that compared against raw tag values
     * would pass every other test in this file and still render SBS content as OU on real files.
     * These two assertions pin the values themselves, not just the labels.
     */
    @Test
    fun `container tag values follow Media3 numbering, not raw EBML numbering`() {
        assertEquals(2, C.STEREO_MODE_LEFT_RIGHT)
        assertEquals(1, C.STEREO_MODE_TOP_BOTTOM)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(buildFormatWithStereoMode(2, 1920, 1080)))
        assertEquals(StereoMode.OU, detector.detectFromFormat(buildFormatWithStereoMode(1, 1920, 1080)))
    }

    // ── Container stereo tag priority over AR heuristic ──────────────────

    @Test
    fun `container mono tag overrides SBS aspect ratio`() {
        val format = buildFormatWithStereoMode(C.STEREO_MODE_MONO, 3840, 1080)
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    @Test
    fun `container SBS tag overrides mono aspect ratio`() {
        val format = buildFormatWithStereoMode(C.STEREO_MODE_LEFT_RIGHT, 1920, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    // ── Missing / unrenderable tag - fall back to AR ─────────────────────

    @Test
    fun `Format carries NO_VALUE when no stereo mode was set`() {
        assertEquals(Format.NO_VALUE, buildFormatWithoutTag(1920, 1080).stereoMode)
    }

    @Test
    fun `detectFromFormat falls back to AR when tag absent`() {
        val format = buildFormatWithoutTag(3840, 1080)
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFormat(format))
    }

    @Test
    fun `detectFromFormat falls back to AR for STEREO_MODE_STEREO_MESH`() {
        // Mesh projection has no renderer mode - UNKNOWN from the tag, then AR → MONO for 1920x1080.
        val format = buildFormatWithStereoMode(C.STEREO_MODE_STEREO_MESH, 1920, 1080)
        assertEquals(StereoMode.MONO, detector.detectFromFormat(format))
    }

    @Test
    fun `detectFromFormat falls back to AR for interleaved MV-HEVC tags`() {
        val leftPrimary = buildFormatWithStereoMode(C.STEREO_MODE_INTERLEAVED_LEFT_PRIMARY, 1920, 1080)
        val rightPrimary = buildFormatWithStereoMode(C.STEREO_MODE_INTERLEAVED_RIGHT_PRIMARY, 1920, 1080)
        assertEquals(StereoMode.MONO, detector.detectFromFormat(leftPrimary))
        assertEquals(StereoMode.MONO, detector.detectFromFormat(rightPrimary))
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
        // 2.39:1 (Scope) - must not be SBS
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(2560, 1072))
        // 2.35:1 (CinemaScope)
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1920, 816))
        // 1.85:1 (Flat)
        assertEquals(StereoMode.MONO, detector.detectFromDimensions(1920, 1038))
    }

    // ── Filename - flat patterns ──────────────────────────────────────────

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
    fun `detectFromFilename SBS_HALF for Half-SBS marker`() {
        // Blu-ray 3D rips: "Half-SBS" with separator between tokens
        assertEquals(StereoMode.SBS_HALF, detector.detectFromFilename("Ghostbusters.2016.Half-SBS.mkv"))
        assertEquals(StereoMode.SBS_HALF, detector.detectFromFilename("movie.half_sbs.mkv"))
        assertEquals(StereoMode.SBS_HALF, detector.detectFromFilename("movie half sbs.mkv"))
    }

    @Test
    fun `detectFromFilename SBS_FULL for FullSBS marker`() {
        // "FullSBS3D" - token boundary fails on digit after "sbs", handled via contains()
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFilename("Blade Runner (1080p24fpsH264FullSBS3D).mkv"))
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFilename("movie_fullsbs.mkv"))
    }

    @Test
    fun `detectFromFilename SBS_FULL for RL reversed-SBS marker`() {
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFilename("movie_rl.mp4"))
        assertEquals(StereoMode.SBS_FULL, detector.detectFromFilename("scene-rl-stereo.mkv"))
    }

    @Test
    fun `detectFromFilename OU for explicit ou marker`() {
        assertEquals(StereoMode.OU, detector.detectFromFilename("movie_ou.mp4"))
        assertEquals(StereoMode.OU, detector.detectFromFilename("movie_tb.mkv"))
        assertEquals(StereoMode.OU, detector.detectFromFilename("movie_3dv.mp4"))
    }

    @Test
    fun `detectFromFilename OU for hOU marker`() {
        // Real-world 3D Blu-ray rips use "3D-hOU" (half Over-Under, anamorphic)
        assertEquals(StereoMode.OU, detector.detectFromFilename("BlackWidow(2021)3D-hOU(Ash61)iTunes.mkv"))
        assertEquals(StereoMode.OU, detector.detectFromFilename("DeadpoolAndWolverine(2024)3D-hOU(Ash61).mkv"))
        assertEquals(StereoMode.OU, detector.detectFromFilename("movie_hou.mkv"))
    }

    @Test
    fun `detectFromFilename OU for TAB marker`() {
        // TAB = Top-And-Bottom, used by Meta Quest Store and YouTube VR
        assertEquals(StereoMode.OU, detector.detectFromFilename("movie_tab.mp4"))
        assertEquals(StereoMode.OU, detector.detectFromFilename("scene-TAB-stereo.mkv"))
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

    // ── Filename - spherical patterns ─────────────────────────────────────

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
    fun `detectFromFilename EQUIRECT_360_OU when 360 stereo and tb both present (S1112)`() {
        // Regression: the specific `tb`/`ou` marker must win over the generic `stereo` token.
        // Before S1112, `has360 && hasStereo` was tested first and collapsed TB content to SBS,
        // so `*_stereo_tb` rendered side-by-side (eyes could not fuse).
        assertEquals(StereoMode.EQUIRECT_360_OU, detector.detectFromFilename("diagnostic_360_stereo_tb.jpg"))
        assertEquals(StereoMode.EQUIRECT_360_OU, detector.detectFromFilename("video_360_stereo_tb.mp4"))
    }

    @Test
    fun `detectFromFilename EQUIRECT_360_SBS still wins for 360 stereo sbs (S1112 guard)`() {
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromFilename("diagnostic_360_stereo_sbs.jpg"))
    }

    @Test
    fun `detectFromFilename UNKNOWN for 180 stereo tb so caller renders TOP_BOTTOM (S1112)`() {
        // No EQUIRECT_180_OU stereo mode exists; UNKNOWN lets the layout-aware immersive parser
        // render HEMISPHERE_180 + TOP_BOTTOM instead of the generic-stereo SBS mismatch.
        assertEquals(StereoMode.UNKNOWN, detector.detectFromFilename("diagnostic_180_stereo_tb.jpg"))
        assertEquals(StereoMode.UNKNOWN, detector.detectFromFilename("video_180_stereo_tb.mp4"))
    }

    @Test
    fun `detectFromFilename EQUIRECT_180_SBS still wins for 180 stereo sbs (S1112 guard)`() {
        assertEquals(StereoMode.EQUIRECT_180_SBS, detector.detectFromFilename("diagnostic_180_stereo_sbs.jpg"))
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
        // Format carries TOP_BOTTOM + equirect (st3d mode 1 + equi). Filename says 360_sbs (SBS),
        // but the container metadata is authoritative and wins with EQUIRECT_360_OU.
        val format = buildFormatWithMp4Spatial(C.STEREO_MODE_TOP_BOTTOM, "equi", 1920, 1080)

        assertEquals(StereoMode.EQUIRECT_360_OU, detector.detectForVideo("sample_360_sbs.mp4", format))
    }

    @Test
    fun `detectForVideo reads st3d mode 3 as VR180_FISHEYE_SBS`() {
        val format = buildFormatWithMp4Spatial(C.STEREO_MODE_STEREO_MESH, "equi", 7168, 3584)
        assertEquals(StereoMode.VR180_FISHEYE_SBS, detector.detectForVideo("sample_vr180.mp4", format))
    }

    @Test
    fun `detectForVideo falls back to filename when st3d mode 4 is discarded by media3`() {
        // st3d mode 4 (right-left reversed SBS) is discarded by media3, so stereoMode stays
        // NO_VALUE even though projectionData carries equi. detectFromMp4Format returns UNKNOWN
        // and the filename source compensates with EQUIRECT_360_SBS.
        val format = Format.Builder()
            .setWidth(7680)
            .setHeight(1920)
            .setProjectionData(buildProjectionData("equi"))
            .build()
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectForVideo("sample_360_sbs.mp4", format))
    }

    // ── S2893: detectFromMp4Format (Format.stereoMode + Format.projectionData) ─

    @Test
    fun `detectFromMp4Format returns EQUIRECT_360_SBS for equirect SBS`() {
        val format = buildFormatWithMp4Spatial(C.STEREO_MODE_LEFT_RIGHT, "equi", 7680, 1920)
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromMp4Format(format))
    }

    @Test
    fun `detectFromMp4Format returns EQUIRECT_360_OU for equirect OU`() {
        val format = buildFormatWithMp4Spatial(C.STEREO_MODE_TOP_BOTTOM, "equi", 3840, 3840)
        assertEquals(StereoMode.EQUIRECT_360_OU, detector.detectFromMp4Format(format))
    }

    @Test
    fun `detectFromMp4Format returns EQUIRECT_360_MONO for equirect mono`() {
        val format = buildFormatWithMp4Spatial(C.STEREO_MODE_MONO, "equi", 4096, 2048)
        assertEquals(StereoMode.EQUIRECT_360_MONO, detector.detectFromMp4Format(format))
    }

    @Test
    fun `detectFromMp4Format returns VR180_FISHEYE_SBS for equirect mesh layout`() {
        val format = buildFormatWithMp4Spatial(C.STEREO_MODE_STEREO_MESH, "equi", 7168, 3584)
        assertEquals(StereoMode.VR180_FISHEYE_SBS, detector.detectFromMp4Format(format))
    }

    @Test
    fun `detectFromMp4Format returns UNKNOWN for cubemap projection`() {
        val format = buildFormatWithMp4Spatial(C.STEREO_MODE_LEFT_RIGHT, "cbmp", 4096, 2048)
        assertEquals(StereoMode.UNKNOWN, detector.detectFromMp4Format(format))
    }

    @Test
    fun `detectFromMp4Format returns UNKNOWN when projectionData is null`() {
        val format = buildFormatWithStereoMode(C.STEREO_MODE_LEFT_RIGHT, 7680, 1920)
        assertEquals(StereoMode.UNKNOWN, detector.detectFromMp4Format(format))
    }

    @Test
    fun `detectFromMp4Format returns UNKNOWN when stereoMode is NO_VALUE`() {
        val format = Format.Builder()
            .setWidth(7680)
            .setHeight(1920)
            .setProjectionData(buildProjectionData("equi"))
            .build()
        assertEquals(StereoMode.UNKNOWN, detector.detectFromMp4Format(format))
    }

    @Test
    fun `detectFromFilename is case-insensitive`() {
        assertEquals(StereoMode.EQUIRECT_360_SBS, detector.detectFromFilename("VACATION_360_SBS.MP4"))
        assertEquals(StereoMode.VR180_FISHEYE_SBS, detector.detectFromFilename("CLIP_VR180.MP4"))
    }

    // ── S1229: ambiguity best-guess bands ────────────────────────────────

    @Test
    fun `best guess leaves an ordinary 16x9 film undecided rather than SBS_FULL`() {
        // The S1229 bug in one case: 1920x1080 is 1.78, the old threshold was 1.6, so every
        // ordinary film played as side-by-side stereo.
        assertEquals(
            StereoMode.UNKNOWN,
            detector.detectForVideo("plain-film.mkv", buildFormatWithoutTag(1920, 1080), bestGuessOnly),
        )
    }

    @Test
    fun `best guess still resolves true full SBS`() {
        assertEquals(
            StereoMode.SBS_FULL,
            detector.detectForVideo("packed.mkv", buildFormatWithoutTag(3840, 1080), bestGuessOnly),
        )
    }

    @Test
    fun `best guess resolves full OU of a 16x9 source - the case the old band missed`() {
        // 1920x2160 is 0.89. The old rules were `<= 0.7` or `0.9..1.1`, so the most common real
        // OU value fell in the hole between them.
        assertEquals(
            StereoMode.OU,
            detector.detectForVideo("stacked.mkv", buildFormatWithoutTag(1920, 2160), bestGuessOnly),
        )
    }

    @Test
    fun `best guess leaves portrait phone video undecided`() {
        // 1080x1920 is 0.5625. The old `aspect <= 0.7` rule claimed it as over-under.
        assertEquals(
            StereoMode.UNKNOWN,
            detector.detectForVideo("phone.mp4", buildFormatWithoutTag(1080, 1920), bestGuessOnly),
        )
    }

    @Test
    fun `best guess leaves the widest ordinary cinema aspect undecided`() {
        // 2560x1080 is 2.37. This is the case the 2.5 threshold was chosen to sit above: the
        // widest normal cinema framing must stay below the "two frames side by side" band.
        assertEquals(
            StereoMode.UNKNOWN,
            detector.detectForVideo("scope.mkv", buildFormatWithoutTag(2560, 1080), bestGuessOnly),
        )
    }

    @Test
    fun `best guess ignores content below the minimum width`() {
        // 640x720 is 0.89 - inside the OU band by aspect alone, but too small to be packed stereo.
        assertEquals(
            StereoMode.UNKNOWN,
            detector.detectForVideo("thumb.mkv", buildFormatWithoutTag(640, 720), bestGuessOnly),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Isolates the last-resort guess: every conservative source is off, so `detectForVideo` cannot
     * return before reaching `aggressiveDimensionGuess`. With `trustAspectRatio` ON the strict
     * heuristic answers first and the guess never runs.
     */
    private val bestGuessOnly = StereoDetectionConfig(
        autoDetectEnabled = true,
        trustFilename = false,
        trustMetadata = false,
        trustAspectRatio = false,
        ambiguityBestGuess = true,
    )

    /**
     * Build a minimal [Format] carrying the container stereo tag Media3 parsed, expressed in
     * [C.StereoMode] values. `setStereoMode` is a stable public setter, so this needs no reflection
     * and no version guard - unlike the custom-data route it replaced (S2892).
     */
    private fun buildFormatWithStereoMode(stereoMode: Int, width: Int, height: Int): Format {
        return Format.Builder()
            .setWidth(width)
            .setHeight(height)
            .setStereoMode(stereoMode)
            .build()
    }

    private fun buildFormatWithoutTag(width: Int, height: Int): Format {
        return Format.Builder()
            .setWidth(width)
            .setHeight(height)
            .build()
    }

    private fun buildFormatWithMp4Spatial(stereoMode: Int, projectionBox: String, width: Int, height: Int): Format {
        return Format.Builder()
            .setWidth(width)
            .setHeight(height)
            .setStereoMode(stereoMode)
            .setProjectionData(buildProjectionData(projectionBox))
            .build()
    }

    /**
     * S2893: build a `proj` box matching what media3 copies into [Format.projectionData].
     * Structure: [size][type="proj"][prhd full-box][projection full-box]. The `prhd` child
     * carries zero pose; the projection child (`equi`/`cbmp`) carries zero bounds/layout.
     */
    private fun buildProjectionData(projectionBox: String): ByteArray {
        val prhd = box("prhd", fullBoxPayload(ByteArray(12)))
        val projChild = box(
            projectionBox,
            fullBoxPayload(
                if (projectionBox == "equi") ByteArray(16) else ByteArray(8)
            )
        )
        return box("proj", prhd + projChild)
    }

    private fun fullBoxPayload(payload: ByteArray): ByteArray = byteArrayOf(0, 0, 0, 0) + payload

    private fun box(type: String, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(8 + payload.size)
        buffer.put(type.toByteArray(Charsets.US_ASCII))
        buffer.put(payload)
        return buffer.array()
    }
}
