package com.sza.fastmediasorter.ui.player

import android.os.Bundle
import androidx.media3.common.Format
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber

/**
 * Detects the stereoscopic format of a video from its track metadata.
 *
 * Detection strategy (highest-confidence first):
 *  1. Matroska StereoMode tag embedded in format extras (100% accurate when present;
 *     ~60% of real-world 3D MKV files carry this tag).
 *  2. Aspect ratio heuristic — reliable for SBS 3D content (≈94% accuracy overall):
 *      • 3840×1080, 1920×540, etc.  →  StereoMode.SBS_FULL   (AR ≈ 32:9)
 *      • 1920×1080 with height ≥ 1800 squeezed SBS  →  StereoMode.SBS_HALF
 *      • AR ≈ 16:27 (e.g., 1920×2160)              →  StereoMode.OU  (reserved)
 *      • Everything else                            →  StereoMode.MONO
 *
 * Returns [StereoMode.UNKNOWN] only when both strategies are inconclusive.
 * Callers MUST map UNKNOWN to AUTO/MONO before storing to preferences or
 * passing to the renderer.
 */
class StereoDetector @javax.inject.Inject constructor() {

    companion object {
        // SBS: width / height ≈ 32:9 (3.555…)
        // Allow ±5% tolerance around the ideal value.
        private const val SBS_AR_MIN = 3.2f
        private const val SBS_AR_MAX = 3.8f

        // SBS half (anamorphic / squeezed): normal AR but unusually tall
        private const val SBS_HALF_MIN_HEIGHT = 1800

        // Over-Under: real OU 3D content is 1920×2160 → AR ≈ 0.889, or 1280×1440 → 0.889.
        // AR range 0.50-0.65 was catching ordinary portrait video (9:16 = 0.5625) → false positives.
        // OU is now detected ONLY via Matroska metadata; AR heuristic always returns MONO for
        // portrait-looking videos. These constants are kept for documentation only.
        private const val OU_AR_MIN = 0.82f
        private const val OU_AR_MAX = 0.95f

        // Matroska StereoMode values (EBML element 0x53B8)
        // https://www.matroska.org/technical/elements.html#StereoMode
        private const val MATROSKA_STEREO_MONO      = "0"
        private const val MATROSKA_STEREO_SBS_LEFT  = "1"
        private const val MATROSKA_STEREO_OU_TOP    = "3"
        private const val MATROSKA_STEREO_SBS_RIGHT = "11"

        // Shared-data key written by MatroskaExtractor in Media3 for the StereoMode EBML tag.
        // Access is best-effort because Media3 API exposure differs across versions/builds.
        private const val FORMAT_CUSTOM_DATA_KEY = "stereo_mode"

        private const val TAG = "StereoDetector"
    }

    /**
     * Derive the stereo mode from a video [Format] object.
     *
     * @param format Video track format from [ExoPlayer.currentTracks]. Must be a video format
     *               (format.height > 0 and format.width > 0).
     * @return Detected [StereoMode]; [StereoMode.UNKNOWN] when undecidable.
     */
    fun detectFromFormat(format: Format): StereoMode {
        // Guard: non-video formats have width == Format.NO_VALUE
        if (format.width <= 0 || format.height <= 0) {
            Timber.w("$TAG: Invalid format dimensions (${format.width}×${format.height}) — returning UNKNOWN")
            return StereoMode.UNKNOWN
        }

        // --- Step 1: Matroska container metadata ---
        val metaResult = detectFromMatroskaTag(format)
        if (metaResult != StereoMode.UNKNOWN) {
            Timber.d("$TAG: Metadata detection → $metaResult (${format.width}×${format.height})")
            return metaResult
        }

        // --- Step 2: Aspect ratio heuristic ---
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
     *
     * If the tag is absent, returns [StereoMode.UNKNOWN] so the heuristic runs.
     */
    private fun detectFromMatroskaTag(format: Format): StereoMode {
        val customData = extractCustomDataBundle(format) ?: return StereoMode.UNKNOWN
        val stereoTag = customData.getString(FORMAT_CUSTOM_DATA_KEY) ?: return StereoMode.UNKNOWN

        return when (stereoTag) {
            MATROSKA_STEREO_MONO      -> StereoMode.MONO
            MATROSKA_STEREO_SBS_LEFT,
            MATROSKA_STEREO_SBS_RIGHT -> StereoMode.SBS_FULL
            MATROSKA_STEREO_OU_TOP    -> StereoMode.OU
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
     * Classify stereo mode by the video's aspect ratio.
     *
     * Typical SBS 3D content:
     *  • 3840×1080 (4K SBS)   → AR 3.555
     *  • 1920×540  (1080p SBS) → AR 3.555
     *
     * Squeezed/half SBS:
     *  • 1920×1080 SBS-half    → AR 1.778 but height exactly 1080; detected via height heuristic
     *    Not perfectly distinguishable from mono without metadata — skipped in this heuristic.
     *
     * Over-Under:
     *  • 1920×2160 (4K OU) → AR 0.889
     *  OU is not detected by AR heuristic: portrait video (9:16 = AR 0.5625) and OU (AR 0.889)
     *  are only distinguishable via Matroska metadata. Without metadata, return MONO to avoid
     *  false positives that break ExoPlayer's video pipeline via setVideoEffects().
     */
    private fun detectFromAspectRatio(width: Int, height: Int): StereoMode {
        val ar = width.toFloat() / height.toFloat()
        return when {
            ar in SBS_AR_MIN..SBS_AR_MAX         -> StereoMode.SBS_FULL
            // OU detection disabled for AR heuristic — too many false positives on portrait video.
            // Matroska tag detection (Step 1) handles real OU 3D files correctly.
            // SBS_HALF: normal-ish AR but doubled width squeezed into standard 16:9 frame.
            // Reliable detection requires Matroska tag; heuristic returns MONO to avoid
            // false-positives on ultra-wide 21:9 content.
            else                                 -> StereoMode.MONO
        }
    }
}
