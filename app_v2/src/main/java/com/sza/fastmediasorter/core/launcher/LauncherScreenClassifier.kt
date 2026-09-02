package com.sza.fastmediasorter.core.launcher

/**
 * S2309: turns a screen configuration into the [LauncherScreenClass] the starter desktop composes on.
 *
 * The thresholds are strategic ADR-6. 600 is not a new number in this codebase - it is the
 * smallest-width value the device-profile detector already uses to tell a tablet from a phone.
 */
object LauncherScreenClassifier {

    private const val MIN_MEDIUM_SMALLEST_WIDTH_DP = 600
    private const val MIN_EXPANDED_SMALLEST_WIDTH_DP = 840

    private const val MIN_WIDE_RATIO = 1.5f
    private const val MAX_WIDE_RATIO = 1.9f

    /**
     * The two screen dimensions arrive in whatever orientation the device is currently in, so the
     * ratio is taken long-over-short: a rotation must not change the class (ADR-2).
     *
     * A non-positive dimension means the configuration has not been resolved yet; the smallest and
     * most conservative class is the safe answer, because it seeds a desktop that fits anywhere.
     */
    fun classify(smallestWidthDp: Int, screenWidthDp: Int, screenHeightDp: Int): LauncherScreenClass {
        if (screenWidthDp <= 0 || screenHeightDp <= 0) {
            return LauncherScreenClass(LauncherScreenClass.Size.COMPACT, LauncherScreenClass.Shape.BALANCED)
        }
        return LauncherScreenClass(
            size = sizeOf(smallestWidthDp),
            shape = shapeOf(screenWidthDp, screenHeightDp),
        )
    }

    private fun sizeOf(smallestWidthDp: Int): LauncherScreenClass.Size = when {
        smallestWidthDp >= MIN_EXPANDED_SMALLEST_WIDTH_DP -> LauncherScreenClass.Size.EXPANDED
        smallestWidthDp >= MIN_MEDIUM_SMALLEST_WIDTH_DP -> LauncherScreenClass.Size.MEDIUM
        else -> LauncherScreenClass.Size.COMPACT
    }

    private fun shapeOf(screenWidthDp: Int, screenHeightDp: Int): LauncherScreenClass.Shape {
        val ratio = maxOf(screenWidthDp, screenHeightDp).toFloat() / minOf(screenWidthDp, screenHeightDp)
        return when {
            ratio > MAX_WIDE_RATIO -> LauncherScreenClass.Shape.ELONGATED
            ratio >= MIN_WIDE_RATIO -> LauncherScreenClass.Shape.WIDE
            else -> LauncherScreenClass.Shape.BALANCED
        }
    }
}
