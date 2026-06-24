package com.sza.fastmediasorter.domain.model

/**
 * S0659: persisted policy controlling how the curated stream catalog refreshes.
 *
 * - [MANUAL]: nothing automatic; refresh only via the toolbar import action (legacy behavior).
 * - [ON_OPEN]: on screen open, offer a dismissible refresh suggestion, throttled by the last-refresh
 *   timestamp (default - "предлагать", never a silent download).
 * - [PERIODIC_WIFI]: opportunistic auto-refresh on open, WiFi-only, throttled to ~daily (no WorkManager
 *   periodic job in iteration 1 to honor the no-heavy-background constraint).
 */
enum class StreamsCatalogRefreshPolicy {
    MANUAL,
    ON_OPEN,
    PERIODIC_WIFI;

    companion object {
        val DEFAULT: StreamsCatalogRefreshPolicy = ON_OPEN

        fun fromName(name: String?): StreamsCatalogRefreshPolicy =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
