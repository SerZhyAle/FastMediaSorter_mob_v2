package com.sza.fastmediasorter.ui.player

import android.os.Bundle
import androidx.media3.common.Format
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber

/**
 * S0326: user-configurable 3D/VR detection behavior, read from [AppSettings] at detection time.
 *
 * [ALL_ENABLED] reproduces the pre-S0326 cascade (every source on, aspect-ratio heuristic on) and
 * is the default for callers that do not pass a config, so legacy behavior is preserved. Settings
 * derived via [from] default the aspect-ratio heuristic OFF (it is the main false-positive source).
 *
 * When the aspect-ratio heuristic is OFF and no other enabled source identifies the content, the
 * high-level detect entry points return [StereoMode.UNKNOWN] so the coordinator's global-default
 * slot can act. With the heuristic ON, the AR result (including a positive [StereoMode.MONO]) is
 * returned as the conclusive 2D determination, exactly as before.
 */
data class StereoDetectionConfig(
    val autoDetectEnabled: Boolean,
    val trustFilename: Boolean,
    val trustMetadata: Boolean,
    val trustAspectRatio: Boolean,
    val ambiguityBestGuess: Boolean,
) {
    companion object {
        /** Legacy all-sources-on configuration (aspect-ratio heuristic enabled). */
        val ALL_ENABLED = StereoDetectionConfig(
            autoDetectEnabled = true,
            trustFilename = true,
            trustMetadata = true,
            trustAspectRatio = true,
            ambiguityBestGuess = false,
        )

        /** Build from user settings. The aspect-ratio heuristic defaults OFF (false-positive source). */
        fun from(settings: AppSettings) = StereoDetectionConfig(
            autoDetectEnabled = settings.stereoAutoDetectEnabled,
            trustFilename = settings.stereoTrustFilename,
            trustMetadata = settings.stereoTrustMetadata,
            trustAspectRatio = settings.stereoTrustAspectRatio,
            ambiguityBestGuess = settings.stereoAmbiguityBestGuess,
        )
    }
}

/**
 * Detects the stereoscopic / spherical format of a piece of media from its track metadata
 * and/or filename.
 *
 * Detection strategy (highest-confidence first):
 *  1. MP4 Spatial Media boxes (`st3d` + `sv3d/proj/mshp`) when a readable local MP4 path exists.
 *  2. Filename tokens (see [detectFromFilename]) - explicit creator markers for both flat
 *     (SBS/OU/MONO) and spherical (360°/VR180/Cylinder) formats.
 *  3. GPano / Photo Sphere XMP metadata for local still images.
 *  4. Matroska StereoMode tag embedded in format extras (100% accurate when present;
 *     ~60% of real-world 3D MKV files carry this tag).
 *  5. Aspect ratio heuristic - reliable for SBS 3D content and for 360°/VR180 at
 *     characteristic AR values (2:1, 4:1, 1:1) with a resolution floor to reject
 *     low-res anamorphic false positives.
 *
 * Returns [StereoMode.UNKNOWN] only when all strategies are inconclusive.
 * Callers MUST map UNKNOWN to AUTO/MONO before storing to preferences or
 * passing to the renderer.
 */
class StereoDetector @javax.inject.Inject constructor() {

    private val mp4SpatialMetadataReader = Mp4SpatialMetadataReader()
    private var photoSphereReader: PhotoSphereMetadataReader = ExifPhotoSphereReader()

    internal constructor(photoSphereReader: PhotoSphereMetadataReader) : this() {
        this.photoSphereReader = photoSphereReader
    }

    companion object {
        // Flat SBS: width / height ≈ 32:9 (3.555…). ±5% tolerance around the ideal value.
        private const val SBS_AR_MIN = 3.2f
        private const val SBS_AR_MAX = 3.8f

        // Spherical AR centres with tight tolerances to avoid colliding with flat SBS (3.2-3.8).
        // Equirect 360° mono: AR 2:1. Equirect 360° SBS: 2 × 2:1 spheres laid out side-by-side
        // → AR 4:1. Equirect 360° OU: 2 × 2:1 spheres stacked → AR 1:1.
        private const val EQUIRECT_AR_TOL = 0.05f
        private const val EQUIRECT_360_MONO_AR = 2.0f
        private const val EQUIRECT_360_SBS_AR = 4.0f
        private const val EQUIRECT_360_OU_AR = 1.0f

        // Resolution floors for AR heuristic - prevents 320×160 anamorphic noise from being
        // auto-detected as 360° content.
        private const val SPHERICAL_MIN_WIDTH = 2048
        private const val SPHERICAL_SBS_MIN_WIDTH = 4096
        private const val SPHERICAL_OU_MIN_WIDTH = 3840

        // SBS half (anamorphic / squeezed): normal AR but unusually tall. Kept for documentation;
        // detection of SBS_HALF via AR alone is unreliable, so we rely on filename tokens.
        @Suppress("unused")
        private const val SBS_HALF_MIN_HEIGHT = 1800

        // Full Over-Under stereo: two 16:9 frames stacked vertically → 16:18 = 8:9 ≈ 0.8889.
        // Real-world masters: 1280×1440 (720p), 1920×2160 (1080p), 3840×4320 (4K).
        // Narrow tolerance ±0.02 keeps the window clear of common portrait ratios (4:5 = 0.8,
        // 1:1 = 1.0, 9:16 = 0.5625) - none of which are confusable with 8:9 at this width floor.
        private const val FLAT_OU_AR = 8f / 9f
        private const val FLAT_OU_AR_TOL = 0.02f
        private const val FLAT_OU_MIN_WIDTH = 1280

        // S1229: bands for the last-resort `ambiguityBestGuess` pass. Deliberately wider than the
        // conservative windows above - this runs only when every other source gave up and the user
        // opted into guessing - but they must still be *arithmetically possible* for stereo:
        //  - Full SBS is two frames side by side, so its aspect is 2x the source: 2.67 for 4:3,
        //    3.55 for 16:9. The widest ordinary cinema aspect is 2.39, so 2.5 separates them.
        //    The previous threshold was 1.6, which classified every 16:9 film (1.78) as SBS_FULL.
        //  - Full OU stacks two frames, so its aspect is half the source: 0.67 for 4:3, 0.89 for
        //    16:9, 1.20 for 2.39:1. Portrait phone video (9:16 = 0.5625) sits below the band.
        //    The previous rules were `<= 0.7` (which caught portrait video) plus `0.9..1.1` (which
        //    missed the most common 16:9 OU at 0.89 - the band had a hole exactly where the
        //    real-world value lives).
        private const val GUESS_SBS_AR_MIN = 2.5f
        private const val GUESS_OU_AR_MIN = 0.62f
        private const val GUESS_OU_AR_MAX = 1.25f
        private const val GUESS_MIN_WIDTH = 1024

        // S1249: the OU floor for an explicit user tap. Zero, i.e. no floor - the pre-S1229 rule on
        // this path was `aspect <= 0.7 -> OU` with nothing below it, and narrowing it was collateral
        // from tuning the passive band. Anything taller than GUESS_OU_AR_MAX is OU when the user
        // pointed at it: an OU-packed portrait frame lands at 0.28 (9:16 halved) and a square-eye
        // pair at 0.5, both of which a floor derived from landscape sources would reject.
        private const val GUESS_OU_AR_MIN_TAP = 0f

        // Matroska StereoMode values (EBML element 0x53B8)
        // https://www.matroska.org/technical/elements.html#StereoMode
        private const val MATROSKA_STEREO_MONO       = "0"
        private const val MATROSKA_STEREO_SBS_LEFT   = "1"   // left-eye first
        private const val MATROSKA_STEREO_OU_RIGHT   = "2"   // top-bottom, right-eye on top
        private const val MATROSKA_STEREO_OU_TOP     = "3"   // top-bottom, left-eye on top
        private const val MATROSKA_STEREO_SBS_RIGHT  = "11"  // right-eye first
        private const val MATROSKA_STEREO_MVC_LEFT   = "13"  // both eyes laced, left-first (MVC)
        private const val MATROSKA_STEREO_MVC_RIGHT  = "14"  // both eyes laced, right-first (MVC)

        // Shared-data key written by MatroskaExtractor in Media3 for the StereoMode EBML tag.
        // Access is best-effort because Media3 API exposure differs across versions/builds.
        private const val FORMAT_CUSTOM_DATA_KEY = "stereo_mode"

        private const val TAG = "StereoDetector"
    }

    /**
     * Detect stereo mode from the media filename.
     *
     * Filename conventions recognised (case-insensitive, token-boundary aware):
     *
     * **Spherical / panoramic** (checked first - wins over flat markers when present).
     * Specific layout markers (`sbs`, `ou`/`tb`) are tested BEFORE the generic `stereo` token
     * (S1112) so `*_stereo_tb` does not collapse to SBS:
     *  - `cylinder`, `cylinder180`  → [StereoMode.CYLINDER_180]
     *  - `vr180`, `180x180`         → [StereoMode.VR180_FISHEYE_SBS]
     *  - `180` + `sbs`              → [StereoMode.EQUIRECT_180_SBS]
     *  - `180` + `ou`/`tb`          → [StereoMode.UNKNOWN] (no 180-OU mode; caller renders TOP_BOTTOM)
     *  - `180` + `stereo`            → [StereoMode.EQUIRECT_180_SBS]
     *  - `180` (alone)              → [StereoMode.EQUIRECT_180_MONO]
     *  - `360` + `sbs`              → [StereoMode.EQUIRECT_360_SBS]
     *  - `360` + `ou`/`tb`          → [StereoMode.EQUIRECT_360_OU]
     *  - `360` + `stereo`            → [StereoMode.EQUIRECT_360_SBS]
     *  - `360`, `equirect`          → [StereoMode.EQUIRECT_360_MONO]
     *  - `cubemap`                  → [StereoMode.UNKNOWN] (unsupported projection)
     *
     * **Flat stereo** (fallback when no spherical marker matches):
     *  - `_3dh`, `_sbs`, `_lr`, `_rl`, `FullSBS*` → [StereoMode.SBS_FULL]
     *  - `_3dv`, `_ou`, `_tb`, `_hou`, `_tab`    → [StereoMode.OU]
     *  - `_hsbs`, `_halfsbs`, `Half-SBS`, `half sbs` → [StereoMode.SBS_HALF]
     *
     * Should be called BEFORE [detectFromFormat] - explicit filename markers from the
     * content creator are more reliable than heuristics on MP4 track metadata.
     * Most commercial VR content is distributed as MP4 without Matroska StereoMode tags.
     *
     * @param filename Bare filename, URI, or full path - only the last segment and stem are used.
     * @return Detected [StereoMode]; [StereoMode.UNKNOWN] when no pattern matches.
     */
    fun detectFromFilename(filename: String): StereoMode {
        val stem = filename
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
            .lowercase()

        Timber.d("$TAG: detectFromFilename stem='$stem'")

        // Precompute token presence. `containsToken` requires a non-alphanumeric boundary,
        // so "DJI_0360" does NOT match "360" (the preceding "0" is alphanumeric), while
        // "vacation_360" does. `contains` is used for multi-token compounds like "180x180".
        val hasCylinder = containsToken(stem, "cylinder") || stem.contains("cylinder180")
        val hasVr180 = containsToken(stem, "vr180") || stem.contains("180x180")
        val has180 = containsToken(stem, "180") || stem.contains("equirect180") || hasVr180
        val has360 = containsToken(stem, "360") || stem.contains("equirect360") || containsToken(stem, "equirect")
        // `fullsbs` uses contains() - token boundary fails when digit follows (e.g. "FullSBS3D")
        val hasSbs = containsToken(stem, "sbs") || containsToken(stem, "3dh") || containsToken(stem, "lr")
                  || containsToken(stem, "rl") || stem.contains("fullsbs")
        // `hou` requires explicit token - `containsToken("hou","ou")` fails because "h" is alphanumeric boundary
        val hasOu = containsToken(stem, "ou") || containsToken(stem, "tb") || containsToken(stem, "3dv")
                 || containsToken(stem, "hou") || containsToken(stem, "tab")
        // `half-sbs` (with separator) is not caught by `halfsbs` - add compound check
        val hasHalfSbs = containsToken(stem, "hsbs") || stem.contains("halfsbs")
                      || (containsToken(stem, "half") && containsToken(stem, "sbs"))
        val hasCubemap = containsToken(stem, "cubemap")
        val hasStereo = containsToken(stem, "stereo")
        val hasMono   = containsToken(stem, "mono")

        return when {
            hasStereo && hasMono -> {
                Timber.w("$TAG: filename conflict stereo+mono - mono wins for stem='$stem'")
                logMatch("MONO", StereoMode.MONO)
            }
            // ─── Spherical / panoramic (priority over flat) ───
            hasCylinder -> logMatch("CYLINDER_180", StereoMode.CYLINDER_180)
            hasVr180    -> logMatch("VR180_FISHEYE_SBS", StereoMode.VR180_FISHEYE_SBS)
            // S1112: specific layout markers (sbs, ou/tb) MUST be tested BEFORE the generic
            // `stereo` token. Otherwise `*_stereo_tb` matches stereo -> SBS and top-bottom content
            // renders side-by-side (eyes cannot fuse). Mirrors the S0290 ordering fix that lived
            // only in the immersive legacy parser; regressed when S0771 routed the immersive
            // renderer through this detector.
            has180 && hasSbs -> logMatch("EQUIRECT_180_SBS", StereoMode.EQUIRECT_180_SBS)
            // 180 OU: no EQUIRECT_180_OU stereo mode exists. Return UNKNOWN so the layout-aware
            // immersive parser renders HEMISPHERE_180 + TOP_BOTTOM (pre-S0771 behaviour) instead
            // of the generic-stereo SBS mismatch below.
            has180 && hasOu -> {
                Timber.d("$TAG: 180+OU has no dedicated stereo mode -> UNKNOWN (caller is layout-aware)")
                StereoMode.UNKNOWN
            }
            has180 && hasStereo -> logMatch("EQUIRECT_180_SBS", StereoMode.EQUIRECT_180_SBS)
            // Plain 180 -> mono half-sphere.
            has180 -> logMatch("EQUIRECT_180_MONO", StereoMode.EQUIRECT_180_MONO)
            has360 && hasSbs -> logMatch("EQUIRECT_360_SBS", StereoMode.EQUIRECT_360_SBS)
            // S1112: OU/TB before generic `stereo` (see note above).
            has360 && hasOu  -> logMatch("EQUIRECT_360_OU",  StereoMode.EQUIRECT_360_OU)
            has360 && hasStereo -> logMatch("EQUIRECT_360_SBS", StereoMode.EQUIRECT_360_SBS)
            has360 -> logMatch("EQUIRECT_360_MONO", StereoMode.EQUIRECT_360_MONO)
            hasCubemap -> {
                Timber.d("$TAG: cubemap marker detected - unsupported projection, UNKNOWN")
                StereoMode.UNKNOWN
            }
            // ─── Flat stereo patterns ───
            hasOu       -> logMatch("OU", StereoMode.OU)
            hasHalfSbs  -> logMatch("SBS_HALF", StereoMode.SBS_HALF)
            hasSbs      -> logMatch("SBS_FULL", StereoMode.SBS_FULL)
            else -> StereoMode.UNKNOWN
        }
    }

    /**
     * Read authoritative MP4 spatial metadata when a readable local MP4 path exists.
     * Unknown, missing, or unsupported boxes deliberately fall through to older heuristics.
     */
    fun detectFromMp4Path(path: String): StereoMode {
        if (!path.endsWith(".mp4", ignoreCase = true) && !path.contains(".mp4?", ignoreCase = true)) {
            return StereoMode.UNKNOWN
        }

        val detected = mp4SpatialMetadataReader.detectStereoMode(path)
        if (detected != StereoMode.UNKNOWN) {
            Timber.d("$TAG: MP4 spatial metadata detection → $detected")
        }
        return detected
    }

    /**
     * Full video detection path.
     * MP4 spatial metadata is authoritative, so it must run before filename or AR heuristics.
     */
    fun detectForVideo(
        path: String?,
        format: Format,
        config: StereoDetectionConfig = StereoDetectionConfig.ALL_ENABLED,
    ): StereoMode {
        if (!config.autoDetectEnabled) return StereoMode.MONO

        if (config.trustMetadata) {
            val mp4Result = path?.let { detectFromMp4Path(it) } ?: StereoMode.UNKNOWN
            if (mp4Result != StereoMode.UNKNOWN) {
                Timber.d("VR_AUDIT/12: detectForVideo result=%s source=mp4-spatial filename=%s", mp4Result, path)
                return mp4Result
            }
        }

        if (config.trustFilename) {
            val filenameResult = path?.let { detectFromFilename(it) } ?: StereoMode.UNKNOWN
            if (filenameResult != StereoMode.UNKNOWN) {
                Timber.d("VR_AUDIT/12: detectForVideo result=%s source=filename filename=%s", filenameResult, path)
                return filenameResult
            }
        }

        if (config.trustMetadata) {
            val matroskaResult = detectFromMatroskaTag(format)
            if (matroskaResult != StereoMode.UNKNOWN) {
                Timber.d("VR_AUDIT/12: detectForVideo result=%s source=matroska-tag filename=%s", matroskaResult, path)
                return matroskaResult
            }
        }

        // Aspect-ratio heuristic. When ON, its result (including a positive MONO) is conclusive.
        if (config.trustAspectRatio && format.width > 0 && format.height > 0) {
            val arResult = detectFromAspectRatio(format.width, format.height)
            Timber.d("VR_AUDIT/12: detectForVideo result=%s source=aspect-ratio filename=%s size=%dx%d",
                arResult, path, format.width, format.height)
            return arResult
        }

        // No enabled source identified the content. Apply ambiguity behavior.
        if (config.ambiguityBestGuess) {
            val guess = aggressiveDimensionGuess(format.width, format.height)
            Timber.d("S1229: best-guess %dx%d -> %s", format.width, format.height, guess)
            if (guess != StereoMode.MONO) {
                Timber.d("VR_AUDIT/12: detectForVideo result=%s source=ambiguity-best-guess filename=%s", guess, path)
                return guess
            }
        }
        return StereoMode.UNKNOWN
    }

    /**
     * Full still-image detection path.
     *
     * Filename tokens remain authoritative because they carry stereo-layout details (SBS/OU/180).
     * GPano XMP is then used as a local-file fallback so plain `IMG_1234.JPG` panoramas do not
     * collapse to flat MONO when the filename is silent.
     *
     * @param userInitiated When `true`, the caller explicitly signalled stereo intent (e.g. the
     *  user tapped the VR-toolbar icon on an image). If the conservative cascade returns
     *  `UNKNOWN`/`MONO`, an aggressive aspect-ratio heuristic biases toward `SBS_FULL` / `OU`
     *  (see [aggressiveDimensionGuess]). When `false` (default) behaviour is unchanged - the
     *  passive `displayImage()` path must never falsely classify ordinary photos as stereo.
     */
    fun detectForImage(
        path: String,
        width: Int? = null,
        height: Int? = null,
        userInitiated: Boolean = false,
        config: StereoDetectionConfig = StereoDetectionConfig.ALL_ENABLED,
    ): StereoMode {
        if (!config.autoDetectEnabled) return StereoMode.MONO

        val passive = detectForImagePassive(path, width, height, config)
        if (passive != StereoMode.UNKNOWN && passive != StereoMode.MONO) return passive

        // Best-guess path: explicit user tap (userInitiated) OR the ambiguity-best-guess setting.
        if (userInitiated || config.ambiguityBestGuess) {
            val aggressive = aggressiveDimensionGuess(width, height, userInitiated)
            if (userInitiated) {
                if (aggressive != StereoMode.MONO) {
                    Timber.d(
                        "VR_AUDIT/12: detectForImage result=%s source=user-initiated-tap filename=%s w=%s h=%s",
                        aggressive, path, width, height,
                    )
                }
                return aggressive
            }
            if (aggressive != StereoMode.MONO) {
                Timber.d("VR_AUDIT/12: detectForImage result=%s source=ambiguity-best-guess filename=%s", aggressive, path)
                return aggressive
            }
        }
        return passive
    }

    /**
     * Conservative still-image cascade. Extracted so the user-initiated overload can compose it.
     * Each source is gated by its [config] trust flag; when the aspect-ratio heuristic is OFF and no
     * other enabled source matches, the result is [StereoMode.UNKNOWN] (coordinator default applies).
     */
    private fun detectForImagePassive(
        path: String,
        width: Int?,
        height: Int?,
        config: StereoDetectionConfig,
    ): StereoMode {
        if (config.trustFilename) {
            val filenameResult = detectFromFilename(path)
            if (filenameResult != StereoMode.UNKNOWN) {
                Timber.d("VR_AUDIT/12: detectForImage result=%s source=filename filename=%s", filenameResult, path)
                return filenameResult
            }
        }

        val dimensionResult = if (config.trustAspectRatio && width != null && height != null) {
            detectFromDimensions(width, height)
        } else {
            StereoMode.UNKNOWN
        }

        if (config.trustMetadata) {
            val photoSphere = photoSphereReader.read(path)
            if (photoSphere?.isEquirectangular == true) {
                val r = when {
                    photoSphere.is180Projection() -> StereoMode.EQUIRECT_180_MONO
                    dimensionResult.isSpherical() -> dimensionResult
                    else -> StereoMode.EQUIRECT_360_MONO
                }
                Timber.d("VR_AUDIT/12: detectForImage result=%s source=photo-sphere-xmp filename=%s", r, path)
                return r
            }
        }

        Timber.d("VR_AUDIT/12: detectForImage result=%s source=dimensions filename=%s w=%s h=%s",
            dimensionResult, path, width, height)
        return dimensionResult
    }

    /**
     * Aggressive aspect-ratio heuristic, reached when every conservative source declined AND the
     * caller opted into guessing - either an explicit VR-toolbar tap on an image (`userInitiated`)
     * or the `ambiguityBestGuess` setting. Returns `MONO` for ordinary ratios so a regular film or
     * DSLR JPG does not get false-3D-claimed.
     *
     * The bands are the arithmetic of packed stereo, not tuned guesses - see [GUESS_SBS_AR_MIN] and
     * [GUESS_OU_AR_MIN] for the derivation:
     *
     * - aspect ≥ [GUESS_SBS_AR_MIN] and width ≥ [GUESS_MIN_WIDTH] → SBS_FULL
     * - aspect in [GUESS_OU_AR_MIN]..[GUESS_OU_AR_MAX] and width ≥ [GUESS_MIN_WIDTH] → OU
     * - everything else (including null dimensions) → MONO
     *
     * S1249: [userInitiated] drops the OU band's lower bound, because the two callers have opposite
     * priors and one set of thresholds cannot serve both. The passive caller is guessing about an
     * arbitrary library, where a false positive plays an ordinary film as 3D. An explicit tap on
     * *this* image is ~95% stereo intent, so a false negative there discards an instruction the user
     * gave, while a false positive costs one tap to undo. The upper bound stays shared - it is what
     * keeps an ordinary 4:3 DSLR frame (1.33) MONO on either path.
     */
    private fun aggressiveDimensionGuess(
        width: Int?,
        height: Int?,
        userInitiated: Boolean = false,
    ): StereoMode {
        if (width == null || height == null || width <= 0 || height <= 0) return StereoMode.MONO
        val aspect = width.toFloat() / height.toFloat()
        val ouMin = if (userInitiated) GUESS_OU_AR_MIN_TAP else GUESS_OU_AR_MIN
        return when {
            aspect >= GUESS_SBS_AR_MIN && width >= GUESS_MIN_WIDTH -> StereoMode.SBS_FULL
            aspect in ouMin..GUESS_OU_AR_MAX && width >= GUESS_MIN_WIDTH -> StereoMode.OU
            else -> StereoMode.MONO
        }
    }

    /**
     * Derive the stereo mode from a video [Format] object.
     *
     * @param format Video track format from ExoPlayer's `currentTracks`. Must be a video format
     *               (format.height > 0 and format.width > 0).
     * @return Detected [StereoMode]; [StereoMode.UNKNOWN] when undecidable.
     */
    fun detectFromFormat(format: Format): StereoMode {
        if (format.width <= 0 || format.height <= 0) {
            Timber.w("$TAG: Invalid format dimensions (${format.width}×${format.height}) - returning UNKNOWN")
            return StereoMode.UNKNOWN
        }

        // --- Step 1: Matroska container metadata (flat stereo only; spherical requires filename or MP4 spatial boxes) ---
        val metaResult = detectFromMatroskaTag(format)
        if (metaResult != StereoMode.UNKNOWN) {
            Timber.d("$TAG: Metadata detection → $metaResult (${format.width}×${format.height})")
            return metaResult
        }

        // --- Step 2: Aspect ratio heuristic (flat + spherical) ---
        val arResult = detectFromAspectRatio(format.width, format.height)
        Timber.d("$TAG: AR heuristic (${format.width}×${format.height}, AR=${format.width.toFloat() / format.height}) → $arResult")
        return arResult
    }

    /**
     * Convenience overload using explicit dimensions.
     * Useful when the full [Format] object is not available.
     */
    fun detectFromDimensions(width: Int, height: Int): StereoMode {
        if (width <= 0 || height <= 0) return StereoMode.UNKNOWN
        return detectFromAspectRatio(width, height)
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Attempt to read the Matroska StereoMode EBML tag from format extras.
     *
     * Media3 API exposure differs across versions: some builds surface a Bundle-like
     * object via a custom-data getter, others do not expose it at compile time.
     * We therefore use reflection and fall back to UNKNOWN when the data is absent.
     */
    private fun detectFromMatroskaTag(format: Format): StereoMode {
        val customData = extractCustomDataBundle(format) ?: return StereoMode.UNKNOWN
        val stereoTag = customData.getString(FORMAT_CUSTOM_DATA_KEY) ?: return StereoMode.UNKNOWN

        return when (stereoTag) {
            MATROSKA_STEREO_MONO                      -> StereoMode.MONO
            MATROSKA_STEREO_SBS_LEFT,
            MATROSKA_STEREO_SBS_RIGHT                 -> StereoMode.SBS_FULL
            MATROSKA_STEREO_OU_RIGHT,
            MATROSKA_STEREO_OU_TOP                    -> StereoMode.OU
            MATROSKA_STEREO_MVC_LEFT,
            MATROSKA_STEREO_MVC_RIGHT                 -> StereoMode.SBS_HALF
            else -> {
                Timber.w("$TAG: Unknown Matroska StereoMode tag value: '$stereoTag'")
                StereoMode.UNKNOWN
            }
        }
    }

    private fun extractCustomDataBundle(format: Format): Bundle? {
        return try {
            val getter = format.javaClass.methods.firstOrNull { method ->
                method.parameterCount == 0 && (method.name == "getCustomData" || method.name == "customData")
            } ?: return null
            getter.invoke(format) as? Bundle
        } catch (e: Exception) {
            Timber.v(e, "$TAG: customData not exposed on this Media3 build")
            null
        }
    }

    /**
     * Word-boundary token match: [token] must not be preceded or followed by an
     * alphanumeric character. Prevents false positives such as "sbs" matching "absorbs"
     * or "360" matching camera sequence numbers like "DJI_0360".
     *
     * Input [text] is expected to already be lowercase.
     */
    private fun containsToken(text: String, token: String): Boolean {
        val pattern = Regex("(?<![a-z0-9])${Regex.escape(token)}(?![a-z0-9])")
        return pattern.containsMatchIn(text)
    }

    /**
     * Classify stereo mode by the video's aspect ratio.
     *
     * Priority: spherical AR checks run first with narrow tolerance + resolution floor,
     * then fall back to flat SBS (wider range), finally MONO.
     *
     * Flat SBS:
     *  - 3840×1080, 1920×540 (AR 3.555, ±SBS tolerance) → [StereoMode.SBS_FULL]
     *
     * Spherical candidates (narrow windows, high-res only):
     *  - AR ≈ 4.0 + width ≥ 4096 → [StereoMode.EQUIRECT_360_SBS] (stacked stereo spheres)
     *  - AR ≈ 2.0 + width ≥ 2048 → [StereoMode.EQUIRECT_360_MONO] (classic 360° mono)
     *  - AR ≈ 1.0 + width ≥ 3840 → [StereoMode.EQUIRECT_360_OU] (top-bottom stereo spheres)
     *
     * Flat OU (Full Over-Under stacking):
     *  - AR ≈ 8:9 (0.8889) + width ≥ [FLAT_OU_MIN_WIDTH] → [StereoMode.OU]
     *    Covers 1280×1440 (720p), 1920×2160 (1080p), 3840×4320 (4K).
     *    The ±0.02 window is clear of common portrait ratios (4:5=0.8, 1:1=1.0, 9:16=0.5625).
     *
     * Half-OU (1920×1080 squeezed) carries normal 16:9 AR and is indistinguishable from regular
     * landscape - rely on [detectFromFilename] or Matroska tag for that case.
     */
    private fun detectFromAspectRatio(width: Int, height: Int): StereoMode {
        val ar = width.toFloat() / height.toFloat()
        return when {
            // Spherical - narrow windows + resolution floor
            isNear(ar, EQUIRECT_360_SBS_AR, EQUIRECT_AR_TOL) && width >= SPHERICAL_SBS_MIN_WIDTH ->
                StereoMode.EQUIRECT_360_SBS
            isNear(ar, EQUIRECT_360_MONO_AR, EQUIRECT_AR_TOL) && width >= SPHERICAL_MIN_WIDTH ->
                StereoMode.EQUIRECT_360_MONO
            // 3840x3840 is a common full-sphere OU master, so the floor is lower than 4:1 SBS.
            isNear(ar, EQUIRECT_360_OU_AR, EQUIRECT_AR_TOL) && width >= SPHERICAL_OU_MIN_WIDTH ->
                StereoMode.EQUIRECT_360_OU
            // Flat SBS (existing behaviour)
            ar in SBS_AR_MIN..SBS_AR_MAX -> StereoMode.SBS_FULL
            // Flat OU - 8:9 (Full Over-Under stacking). Real-world: 1280×1440 / 1920×2160 / 3840×4320.
            isNear(ar, FLAT_OU_AR, FLAT_OU_AR_TOL) && width >= FLAT_OU_MIN_WIDTH ->
                StereoMode.OU
            // Everything else - mono. Half-OU (16:9 squeezed) not detected by AR alone.
            else -> StereoMode.MONO
        }
    }

    private fun isNear(value: Float, target: Float, tolerance: Float): Boolean =
        value >= target - tolerance && value <= target + tolerance

    private fun logMatch(label: String, mode: StereoMode): StereoMode {
        Timber.d("$TAG: filename match → $label")
        return mode
    }
}
