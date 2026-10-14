package com.sza.fastmediasorter.domain.model.tourist

enum class PressureTrend {
    RISING,
    STEADY,
    FALLING,
}

/** S4145: a trend needs an hour of samples; before that the tile says it is accumulating, never zero. */
sealed interface PressureTrendResult {

    data object Accumulating : PressureTrendResult

    data class Ready(
        val trend: PressureTrend,
        val deltaHpa: Float,
    ) : PressureTrendResult
}
