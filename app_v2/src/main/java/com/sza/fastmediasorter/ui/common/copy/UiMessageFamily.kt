package com.sza.fastmediasorter.ui.common.copy

/**
 * Semantic family of a user-facing message produced by S0118 flows.
 *
 * Each family maps to a distinct copy formula:
 * - [ERROR]: human explanation + optional corrective next step.
 * - [EMPTY_STATE]: absence explanation + invitation to act.
 * - [PROGRESS]: short calm status, no bureaucratic wording.
 * - [SUCCESS]: brief result confirmation, no formal "successfully completed" pattern.
 * - [CONFIRMATION]: direct question for destructive or irreversible actions.
 */
enum class UiMessageFamily {
    ERROR,
    EMPTY_STATE,
    PROGRESS,
    SUCCESS,
    CONFIRMATION,
}
