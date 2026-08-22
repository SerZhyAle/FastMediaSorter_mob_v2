package com.sza.fastmediasorter.ui.browse

/**
 * S1968: refuses a thumbnail decode whose target cannot be allocated.
 *
 * The browse thumbnail request ends in `.override(300, 300).centerCrop()`. `centerCrop` must COVER the
 * target, so it scales by the LARGER of the two per-axis factors - and for a source that is extremely
 * narrow relative to its height, covering 300 px of width forces an upscale whose height follows the
 * aspect ratio with nothing checking it on its own. A 300 x 1970400 target is ~2.4 GB at ARGB_8888, so
 * the allocation cannot succeed; that exact pair appeared 254 times in one pre-release sweep.
 *
 * The budget is on TOTAL PIXELS, deliberately not on the aspect ratio. An aspect rule would have to
 * guess which panoramas are legitimate; a pixel budget does not care why an image is shaped as it is
 * and refuses only what cannot be allocated. A corrupt header and an honest panorama therefore get the
 * same answer, which is the same placeholder either way.
 */
object ThumbnailDecodeBudget {

    /**
     * Largest decode this path will attempt, in pixels.
     *
     * 16 MP is ~64 MB at ARGB_8888 - far above any real thumbnail source, far below the point where
     * the allocation is hopeless. The number is a ceiling on absurdity, not a quality setting: an
     * ordinary photo, and a tall screenshot, sit orders of magnitude below it.
     */
    const val MAX_DECODE_PIXELS: Long = 16L * 1024 * 1024

    /**
     * The pixel count `centerCrop` would have to allocate to cover a [target] x [target] box.
     *
     * Returns 0 when either source dimension is unknown (a header that failed to parse reports 0 or
     * -1), because nothing can be concluded from it - the caller treats that as "no opinion" and lets
     * the ordinary decode path try, rather than refusing a file on missing metadata.
     */
    fun coverPixelsFor(sourceWidth: Int, sourceHeight: Int, target: Int): Long {
        if (sourceWidth <= 0 || sourceHeight <= 0 || target <= 0) return 0L
        // Cover scales by the larger factor so the smaller side still fills the box.
        val scale = maxOf(
            target.toDouble() / sourceWidth.toDouble(),
            target.toDouble() / sourceHeight.toDouble(),
        )
        val width = Math.round(sourceWidth * scale)
        val height = Math.round(sourceHeight * scale)
        return width * height
    }

    /** True when covering a [target] box from this source would exceed [MAX_DECODE_PIXELS]. */
    fun exceedsBudget(sourceWidth: Int, sourceHeight: Int, target: Int): Boolean {
        val pixels = coverPixelsFor(sourceWidth, sourceHeight, target)
        return pixels > MAX_DECODE_PIXELS
    }
}
