package com.sza.fastmediasorter.domain.streams

import android.graphics.Bitmap

/** Adopts a decoded stream frame as the shared in-memory and persisted channel thumbnail. */
interface StreamFrameIngestor {
    suspend fun ingest(url: String, bitmap: Bitmap): Boolean
}
