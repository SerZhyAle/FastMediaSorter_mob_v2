package com.sza.fastmediasorter.ui.player.render

enum class TransitionType {
    INSTANT_SWAP,
    FALLBACK_FADE
}

data class TransitionPolicy(
    val type: TransitionType = TransitionType.INSTANT_SWAP,
    val durationMs: Long = 0L,
    val fallbackDurationMs: Long = 120L,
    val allowFallbackOnError: Boolean = true
) {
    companion object {
        val Default = TransitionPolicy()
    }
}
