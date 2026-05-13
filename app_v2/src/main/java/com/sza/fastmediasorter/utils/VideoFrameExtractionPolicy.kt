package com.sza.fastmediasorter.utils

/**
 * Policy constants for video frame extraction with black-frame detection.
 * Seek offsets and retry limits shared by both the network decoder and the background extractor.
 * S0178.
 */
object VideoFrameExtractionPolicy {

    /** Candidate seek positions in microseconds (5 s, 15 s, 30 s). ADR-3. */
    val SEEK_OFFSETS_US: LongArray = longArrayOf(5_000_000L, 15_000_000L, 30_000_000L)

    /**
     * Maximum retry attempts after the first extraction on the network path.
     * Total attempts = MAX_RETRIES_NETWORK + 1 = 3. Keeps total watchdog exposure ≤ 10 s. ADR-4.
     */
    const val MAX_RETRIES_NETWORK: Int = 2

    /**
     * Maximum retry attempts on the background / local path.
     * Total attempts = MAX_RETRIES_LOCAL + 1 = 4. No watchdog constraint on local files.
     */
    const val MAX_RETRIES_LOCAL: Int = 3

    /**
     * Mean luma threshold below which a frame is classified as dark.
     * Value ≈ 5.9% of full range (15/255). ADR-1.
     */
    const val DARKNESS_LUMA_THRESHOLD: Float = 15f / 255f

    /** Width/height to downscale a Bitmap to before computing mean luma. */
    const val DOWNSCALE_SIZE: Int = 64
}
