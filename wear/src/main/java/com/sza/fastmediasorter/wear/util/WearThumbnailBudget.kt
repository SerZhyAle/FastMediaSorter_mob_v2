package com.sza.fastmediasorter.wear.util

/**
 * Every limit the watch thumbnail path obeys, declared once.
 *
 * The head-read cap is the load-bearing number: a thumbnail is worth showing only while getting it
 * stays far cheaper than fetching the file. A camera JPEG stores its embedded preview inside the
 * metadata block at the head of the file, typically within the first few tens of kilobytes, so
 * 128 KB clears a normal preview with margin while keeping a folder of twenty photos in the low
 * megabytes rather than the high tens.
 */
object WearThumbnailBudget {

    /** Maximum bytes read from the head of a network file before the stream is closed. */
    const val MAX_HEAD_READ_BYTES: Int = 128 * 1024

    /** Longest edge, in pixels, of a decoded thumbnail. A watch cell never needs more. */
    const val MAX_THUMBNAIL_EDGE_PX: Int = 128

    /** How many decoded thumbnails stay in memory, so scrolling a list back re-reads nothing. */
    const val MAX_CACHED_THUMBNAILS: Int = 64

    /**
     * Longest Base64 thumbnail accepted from the phone. The phone encodes a cell-sized JPEG, which is
     * a few kilobytes; the head-read cap is reused as the ceiling so a phone-sent picture can never
     * cost more than the network preview path is allowed to.
     */
    const val MAX_PHONE_THUMBNAIL_BASE64_CHARS: Int = MAX_HEAD_READ_BYTES

    private const val SAMPLE_STEP = 2

    /**
     * The largest power of two that still leaves the longest edge at or above
     * [MAX_THUMBNAIL_EDGE_PX], so the sampled decode never holds more than about four times the
     * cell's pixels and the final downscale never has to enlarge.
     */
    fun sampleSizeFor(width: Int, height: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * SAMPLE_STEP) >= MAX_THUMBNAIL_EDGE_PX) {
            sample *= SAMPLE_STEP
        }
        return sample
    }
}
