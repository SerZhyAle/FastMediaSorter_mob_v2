package com.sza.fastmediasorter.wear.domain.tourist

enum class WearPressureTrend {
    RISING,
    STEADY,
    FALLING,
}

/** S4146: a trend needs an hour of samples; before that the tile says it is accumulating, never zero. */
sealed interface WearPressureTrendResult {

    data object Accumulating : WearPressureTrendResult

    data class Ready(
        val trend: WearPressureTrend,
        val deltaHpa: Float,
    ) : WearPressureTrendResult
}
