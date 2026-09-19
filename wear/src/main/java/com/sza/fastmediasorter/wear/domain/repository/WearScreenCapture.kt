package com.sza.fastmediasorter.wear.domain.repository

import java.io.File

/**
 * S3110: what one attempt to photograph this watch's own screen produced.
 *
 * [file] and [reason] are exclusive: a capture either left a PNG in the cache or names why it did
 * not, in the vocabulary the phone matches on.
 */
data class WearScreenCaptureResult(
    val file: File? = null,
    val reason: String? = null
)

/** S3110: photographs the app's own foreground window. Never throws for an expected condition. */
interface WearScreenCapture {

    suspend fun capture(): WearScreenCaptureResult
}
