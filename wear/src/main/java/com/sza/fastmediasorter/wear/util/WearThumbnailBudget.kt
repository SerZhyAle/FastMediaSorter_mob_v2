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
}
