package com.sza.fastmediasorter.core.share

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all declared [ShareTarget]s (S0452 foundation).
 *
 * Targets are contributed by Hilt multibinding (`@IntoSet ShareTarget`), so a new target ticket
 * (S0443-S0446) registers an entry without editing this class. Holds declaration only - default
 * and availability logic live in [ShareTargetAvailabilityResolver].
 */
@Singleton
class ShareTargetRegistry @Inject constructor(
    targets: Set<@JvmSuppressWildcards ShareTarget>,
) {
    private val byId: Map<String, ShareTarget> = targets.associateBy { it.id }

    /**
     * All registered targets in the canonical display order ([DISPLAY_ORDER]). This is the single
     * source of truth shared by every presentation - the «Send to..» menu and the settings toggle
     * group - so a receiver always sits in the same position regardless of how often it is used
     * (predictable finger placement, not usage-frequency reordering). Targets are grouped by kind
     * (messengers/social, email, Google services, device utilities); `system_share` is kept last as
     * the universal catch-all. An id absent from [DISPLAY_ORDER] sorts just before `system_share`.
     */
    fun all(): List<ShareTarget> = byId.values.sortedBy { rankOf(it.id) }

    /** @return the registered target for [id], or null when none is registered. */
    fun byId(id: String): ShareTarget? = byId[id]

    private fun rankOf(id: String): Int = when (id) {
        SYSTEM_SHARE_ID -> Int.MAX_VALUE
        else -> DISPLAY_ORDER.indexOf(id).let { if (it == -1) Int.MAX_VALUE - 1 else it }
    }

    companion object {
        private const val SYSTEM_SHARE_ID = "system_share"

        /**
         * Canonical receiver order, grouped by kind. Editing this list is the single point that
         * re-orders both the «Send to..» menu and the settings toggle group. `system_share` is not
         * listed here - it is always forced last as the universal catch-all.
         */
        private val DISPLAY_ORDER = listOf(
            // Messengers & social - the most-tapped personal-share apps, kept together.
            "telegram",
            "whatsapp",
            "instagram",
            // Email.
            "email",
            // Google services.
            "keep_text",
            "keep_drawing",
            "lens",
            // Device utilities.
            "open_in",
            "print",
        )
    }
}
