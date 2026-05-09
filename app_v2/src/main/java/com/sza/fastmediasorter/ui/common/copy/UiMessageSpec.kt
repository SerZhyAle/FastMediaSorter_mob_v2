package com.sza.fastmediasorter.ui.common.copy

import timber.log.Timber

/**
 * S0118: Payload shape for a single user-facing message produced by the friendly copy contract.
 *
 * Invariants (enforced in [validate]):
 * - [shortMessage] must be non-blank.
 * - At most one [nextStep] per spec (S0118 ADR-3).
 *
 * This class carries the payload only; rendering is delegated to [UiMessageProjector].
 *
 * @param family     Semantic family driving the copy formula at the rendering layer.
 * @param shortMessage  Primary user-facing text — short enough for a Snackbar or Toast.
 * @param detailedMessage Optional longer explanation shown in a dialog or details surface.
 * @param nextStep   Single contextual follow-up action, or null when none applies.
 */
data class UiMessageSpec(
    val family: UiMessageFamily,
    val shortMessage: String,
    val detailedMessage: String? = null,
    val nextStep: UiNextStep? = null,
) {
    init {
        validate()
    }

    private fun validate() {
        require(shortMessage.isNotBlank()) {
            "UiMessageSpec: shortMessage must not be blank (family=$family)"
        }
        // nextStep is single by type: sealed interface already prevents multiple steps
        // in one call site, but log for diagnostics when detailedMessage is huge.
        if (detailedMessage != null && detailedMessage.length > 2_000) {
            Timber.w("UiMessageSpec: detailedMessage is very long (%d chars) — consider truncating at the render layer", detailedMessage.length)
        }
    }

    companion object {
        /** Convenience builder for simple error messages without next steps. */
        fun error(shortMessage: String, detailedMessage: String? = null): UiMessageSpec =
            UiMessageSpec(
                family = UiMessageFamily.ERROR,
                shortMessage = shortMessage,
                detailedMessage = detailedMessage,
            )

        /** Convenience builder for success feedback. */
        fun success(shortMessage: String): UiMessageSpec =
            UiMessageSpec(
                family = UiMessageFamily.SUCCESS,
                shortMessage = shortMessage,
            )

        /** Convenience builder for progress status messages. */
        fun progress(shortMessage: String): UiMessageSpec =
            UiMessageSpec(
                family = UiMessageFamily.PROGRESS,
                shortMessage = shortMessage,
            )

        /** Convenience builder for empty-state prompts with an optional action link. */
        fun emptyState(shortMessage: String, nextStep: UiNextStep? = null): UiMessageSpec =
            UiMessageSpec(
                family = UiMessageFamily.EMPTY_STATE,
                shortMessage = shortMessage,
                nextStep = nextStep,
            )
    }
}
