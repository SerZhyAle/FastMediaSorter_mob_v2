package com.sza.fastmediasorter.core.launcher

/**
 * S2309: the second axis the starter desktop composes on, beside the device profile.
 *
 * Size and shape are separate values because they answer different questions: a 20:9 phone and a
 * 4:3 tablet can land in one size bucket while fitting very different numbers of grid rows
 * (strategic ADR-6). The profile decides what belongs on the device; this decides how much of it
 * reaches the first screen.
 */
data class LauncherScreenClass(
    val size: Size,
    val shape: Shape,
) {

    /** The device's smallest side, which is what stays constant across a rotation. */
    enum class Size {
        COMPACT,
        MEDIUM,
        EXPANDED,
    }

    /** The long side over the short side, so the value is the same in either orientation. */
    enum class Shape {
        BALANCED,
        WIDE,
        ELONGATED,
    }
}
