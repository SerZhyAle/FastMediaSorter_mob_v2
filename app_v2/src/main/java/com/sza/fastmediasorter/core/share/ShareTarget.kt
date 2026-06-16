package com.sza.fastmediasorter.core.share

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Domain description of one "send file to X" target (S0452 foundation).
 *
 * A target is registered once (Hilt `@IntoSet`) and is then surfaced as a settings toggle and
 * gated across every share menu. Concrete targets (Keep, Email, system Share, messengers) are
 * contributed by S0443-S0446 - this model carries no behaviour, only declaration.
 */
data class ShareTarget(
    /** Stable registry key; also the token persisted in `AppSettings.enabledShareTargets`. */
    val id: String,
    @get:StringRes val titleRes: Int,
    @get:DrawableRes val iconRes: Int? = null,
    /** Default on/off rule when the user has not explicitly toggled this target. */
    val defaultEnabled: ShareTargetDefault,
    /** Rule deciding whether the target is usable on this device right now. */
    val availability: ShareTargetAvailability,
    /** Candidate package ids for [ShareTargetAvailability.PACKAGE_INSTALLED]; empty otherwise. */
    val packages: List<String> = emptyList(),
)

/** Default-enabled rule, resolved against device capability (not [com.sza.fastmediasorter.data.model.DeviceProfileType]). */
enum class ShareTargetDefault {
    ALWAYS_ON,
    ALWAYS_OFF,
    ON_IF_GOOGLE,
    ON_IF_INTERNET,
}

/** Availability rule deciding whether the target's command may be shown. */
enum class ShareTargetAvailability {
    ALWAYS,
    PACKAGE_INSTALLED,
    REQUIRES_GOOGLE,
    REQUIRES_INTERNET,
}
