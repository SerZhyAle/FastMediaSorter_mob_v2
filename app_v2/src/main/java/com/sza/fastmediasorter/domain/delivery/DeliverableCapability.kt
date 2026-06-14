package com.sza.fastmediasorter.domain.delivery

/**
 * Per-[DeliverableSet] availability state (S0386 strategic Pillar A).
 *
 * - [NOT_INSTALLED]: not bundled and not downloaded - needs delivery before use.
 * - [INSTALLED]: bundled in this build or already downloaded - usable.
 * - [DISABLED_BY_USER]: present but the user declined to enable it.
 */
enum class DeliverableCapability {
    NOT_INSTALLED,
    INSTALLED,
    DISABLED_BY_USER
}
