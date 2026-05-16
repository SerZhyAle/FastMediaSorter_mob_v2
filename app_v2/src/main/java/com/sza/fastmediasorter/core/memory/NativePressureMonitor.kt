package com.sza.fastmediasorter.core.memory

import android.os.Debug
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Samples native heap free size with a short TTL to avoid hot-path syscall storms.
 * Threshold 30 MB is the S0207 Phase 04 starting hypothesis and is calibrated later.
 */
@Singleton
class NativePressureMonitor @Inject constructor() {

    @Volatile
    private var lastSampleAtMs: Long = 0L

    @Volatile
    private var lastFreeMb: Long = Long.MAX_VALUE

    fun isUnderPressure(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSampleAtMs > SAMPLE_VALIDITY_MS) {
            lastFreeMb = Debug.getNativeHeapFreeSize() / BYTES_PER_MB
            lastSampleAtMs = now
        }
        return lastFreeMb < THRESHOLD_LOW_MB
    }

    fun lastFreeNativeMb(): Long = lastFreeMb

    private companion object {
        const val THRESHOLD_LOW_MB = 30L
        const val SAMPLE_VALIDITY_MS = 200L
        const val BYTES_PER_MB = 1024L * 1024L
    }
}