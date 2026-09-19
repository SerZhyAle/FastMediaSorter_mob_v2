package com.sza.fastmediasorter.ui.common.widget.dimclock

/**
 * S3256: Pure timing logic provider for the dim clock overlay.
 *
 * Drives:
 * 1) Update cadence: 1 s if seconds are visible, 30 s otherwise (strategic §3.3).
 * 2) Burn-in shift offsets: periodic cycling to prevent OLED burn-in.
 * 3) Auto-fade opacity: drops to floor alpha after idle threshold, restored on user touch.
 *
 * Decoupled from Android views and SystemClock for clean unit-testing.
 */
class DimClockTicker(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private var lastActivityTimeMs: Long = clock()
    private var burnInStep: Int = 0

    /**
     * Resolves tick cadence in milliseconds based on seconds visibility.
     */
    fun getCadenceMs(secondsVisible: Boolean): Long =
        if (secondsVisible) CADENCE_SECONDS_MS else CADENCE_NORMAL_MS

    /**
     * Notifies ticker that user interaction occurred, resetting auto-fade timer.
     */
    fun onUserActivity(nowMs: Long = clock()) {
        lastActivityTimeMs = nowMs
    }

    /**
     * Computes current alpha transparency based on elapsed idle time.
     */
    fun computeAutoFadeAlpha(nowMs: Long = clock()): Float {
        val idleTimeMs = nowMs - lastActivityTimeMs
        return if (idleTimeMs >= FADE_DELAY_MS) FADE_FLOOR_ALPHA else FULL_ALPHA
    }

    /**
     * Advances burn-in position cycle and returns pair of (offsetX, offsetY) in DP.
     */
    fun nextBurnInOffsetDp(): Pair<Float, Float> {
        burnInStep = (burnInStep + 1) % BURN_IN_POSITIONS.size
        return BURN_IN_POSITIONS[burnInStep]
    }

    /**
     * Returns current burn-in offset without advancing cycle.
     */
    fun currentBurnInOffsetDp(): Pair<Float, Float> = BURN_IN_POSITIONS[burnInStep]

    companion object {
        const val CADENCE_SECONDS_MS: Long = 1_000L
        const val CADENCE_NORMAL_MS: Long = 30_000L

        const val BURN_IN_PERIOD_MS: Long = 60_000L
        const val BURN_IN_AMPLITUDE_DP: Float = 4.0f

        const val FADE_DELAY_MS: Long = 60_000L
        const val FADE_FLOOR_ALPHA: Float = 0.2f
        const val FULL_ALPHA: Float = 1.0f

        private val BURN_IN_POSITIONS: List<Pair<Float, Float>> = listOf(
            Pair(0.0f, 0.0f),
            Pair(BURN_IN_AMPLITUDE_DP, 0.0f),
            Pair(BURN_IN_AMPLITUDE_DP, BURN_IN_AMPLITUDE_DP),
            Pair(0.0f, BURN_IN_AMPLITUDE_DP),
            Pair(-BURN_IN_AMPLITUDE_DP, BURN_IN_AMPLITUDE_DP),
            Pair(-BURN_IN_AMPLITUDE_DP, 0.0f),
            Pair(-BURN_IN_AMPLITUDE_DP, -BURN_IN_AMPLITUDE_DP),
            Pair(0.0f, -BURN_IN_AMPLITUDE_DP)
        )
    }
}
