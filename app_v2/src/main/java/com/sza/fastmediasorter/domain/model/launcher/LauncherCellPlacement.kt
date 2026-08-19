package com.sza.fastmediasorter.domain.model.launcher

/**
 * S1772: what happened to a cell the user asked to place, and why.
 *
 * Replaces a bare nullable id. A refusal that carries no reason cannot be explained to the user, and
 * explaining it is half of what this ticket is - the silent non-placement it fixes was a `null` nobody
 * read (ADR-2).
 */
sealed interface LauncherCellPlacement {

    data class Placed(val id: Long) : LauncherCellPlacement

    /**
     * The footprint is wider than the grid itself, so no amount of pushing rows down can seat it.
     * The one genuinely unfixable case, and the only one the user has to be told about.
     */
    data object TooWide : LauncherCellPlacement

    /**
     * A structural cell was asked to displace another one - a second section header on a row that
     * already carries one. Pushing here would duplicate a header rather than make room for content.
     */
    data object Refused : LauncherCellPlacement

    val idOrNull: Long?
        get() = (this as? Placed)?.id
}
