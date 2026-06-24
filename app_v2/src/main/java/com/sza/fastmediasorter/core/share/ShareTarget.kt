package com.sza.fastmediasorter.core.share

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.domain.model.MediaType

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
    /**
     * Media types this target can receive. Empty = applies to any type (additive-safe default that
     * preserves un-gated behaviour for targets that do not restrict by content). Non-empty = the
     * target is offered only when the current file's [MediaType] is a member (S0459 ADR-3).
     */
    val applicableTypes: Set<MediaType> = emptySet(),
    /**
     * Whether this receiver accepts a whole multi-file selection. `false` (default) = single-file
     * receiver: on a multi-selection it applies to the first file only, and the menu shows the
     * "applies to first file" hint (S0459 ADR-4). Single source of truth for batch capability,
     * consumed by both menu presentations (bottom sheet + overflow submenu).
     */
    val batchCapable: Boolean = false,
    /**
     * Whether this receiver consumes the file bytes (a real local Uri or a readable [ShareableContent.mediaFile]).
     * `true` (default) = a remote source must be downloaded to a local cache copy before the receiver runs
     * (S0493). `false` = the receiver works from non-file payload (e.g. plain `content.text`) and never needs
     * materialization. Gates the on-demand download in the «Send to..» dispatch layer.
     */
    val requiresLocalFile: Boolean = true,
    /**
     * Whether this receiver can send a plain-text payload ([ShareableContent.text]) with no file
     * attachment. `false` (default) = file-only receiver. `true` = the handler can dispatch a text-only
     * payload (e.g. a stream link or note text). Used to filter the «Send to..» menu for text-only
     * content (uris empty, no sourcePath) so file-only receivers are not offered as dead entries (S0631).
     */
    val textCapable: Boolean = false,
    /**
     * Brief description shown as a subtitle under the toggle label in the settings group (S0463).
     * Null targets render no subtitle when available (only "Not installed" when unavailable).
     */
    @get:StringRes val subtitleRes: Int? = null,
    /**
     * Body text for the help TooltipDialog shown by the (?) button in the settings group (S0463).
     * The dialog title is taken from [titleRes]. Null = no help button rendered.
     */
    @get:StringRes val helpMessageRes: Int? = null,
)

/**
 * @return whether this target is offered for [type]. A target with no [ShareTarget.applicableTypes]
 * restriction applies to every media type; otherwise only to its declared members (S0459 ADR-3).
 */
fun ShareTarget.appliesTo(type: MediaType): Boolean =
    applicableTypes.isEmpty() || type in applicableTypes

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
