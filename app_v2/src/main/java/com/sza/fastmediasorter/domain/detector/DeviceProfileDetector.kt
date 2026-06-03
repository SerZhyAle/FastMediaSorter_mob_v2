package com.sza.fastmediasorter.domain.detector

import com.sza.fastmediasorter.data.model.DetectionConfidence
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.model.DetectorSignal

/**
 * Auto-detector for device profile based on Android platform signals.
 * Analyzes features, UI mode, screen size, manufacturer to recommend a profile.
 * Returns confidence level and list of signals that fired.
 */
interface DeviceProfileDetector {

    /**
     * Run detection logic synchronously and return DetectionResult.
     * Does not persist the result - caller is responsible for saving if desired.
     */
    suspend fun detectProfile(): DetectionResult

    /**
     * Get the primary Android signal responsible for a given profile type.
     * Used for documentation/audit of detection rationale.
     */
    fun getSignalForProfile(type: DeviceProfileType): Signal?

    /**
     * Detection result: selected profile, confidence level, and signals that fired.
     */
    data class DetectionResult(
        val profile: DeviceProfileType,
        val confidence: DetectionConfidence,
        val signals: List<String>  // Signal names that contributed to this result
    )

    /**
     * A single detector signal.
     */
    data class Signal(
        val name: String,
        val value: Any?  // Signal value or presence indicator
    )
}
