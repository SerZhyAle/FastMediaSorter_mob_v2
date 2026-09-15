package com.sza.fastmediasorter.broadcast

/**
 * One lens a phone video broadcast can open.
 *
 * [id] is the capture screen's lens identity - `logical` or `logical/physical` - because Camera2 opens
 * the logical camera and only then narrows it to a physical sub-lens, so the service needs both halves.
 */
data class BroadcastLensOption(
    val id: String,
    val facing: String,
    val zoomMultiplier: Float,
) {
    companion object {
        const val FACING_BACK = "back"
        const val FACING_FRONT = "front"
        const val FACING_EXTERNAL = "external"

        private const val PHYSICAL_SEPARATOR = '/'

        fun logicalIdOf(lensId: String): String = lensId.substringBefore(PHYSICAL_SEPARATOR)

        fun physicalIdOf(lensId: String): String? =
            lensId.substringAfter(PHYSICAL_SEPARATOR, missingDelimiterValue = "").ifEmpty { null }
    }
}

/** The lenses on offer and the one a fresh session opens when the user picks nothing. */
data class BroadcastLensChoice(
    val options: List<BroadcastLensOption>,
    val initialLensId: String?,
) {
    companion object {
        val EMPTY = BroadcastLensChoice(emptyList(), null)
    }
}
