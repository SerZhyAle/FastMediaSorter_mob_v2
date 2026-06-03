package com.sza.fastmediasorter.domain.model

/**
 * Observable lifecycle of translation-model prewarming (S0334).
 *
 * Prewarming ensures the ML Kit model(s) for the selected target language are present on device
 * before the user reaches the OCR / translation hot path, so the in-line download dialog does not
 * appear at capture time. Pure domain type - no Android or ML Kit dependencies.
 */
sealed interface TranslationModelPrewarmStatus {

    /** No prewarm requested yet, or nothing to do. */
    data object Idle : TranslationModelPrewarmStatus

    /** Required model(s) for [targetCode] are being downloaded. */
    data class Downloading(val targetCode: String) : TranslationModelPrewarmStatus

    /** All required model(s) for [targetCode] are present on device. */
    data class Ready(val targetCode: String) : TranslationModelPrewarmStatus

    /** Prewarm for [targetCode] failed (e.g. no network); a manual retry is offered. */
    data class Failed(val targetCode: String) : TranslationModelPrewarmStatus
}
