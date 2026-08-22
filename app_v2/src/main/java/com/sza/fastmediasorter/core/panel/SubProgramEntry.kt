package com.sza.fastmediasorter.core.panel

import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * One sub-program in the registry (strategic S1736 §5.1).
 *
 * This record states only what nothing else owns: whether a route key is a sub-program at all, in
 * what order it is shown, which surfaces it is eligible for, which widget it pairs with, and how it
 * is switched off.
 *
 * Deliberately absent, per ADR-1: label, icon and intent are read from
 * [InternalRouteCatalog.byKey] using [routeKey], and availability is read from
 * `ResolvePanelRouteAvailabilityUseCase`. Copying either into this record would recreate the second
 * copy this registry exists to remove.
 */
data class SubProgramEntry(

    /**
     * The canonical identity, always an `InternalRouteCatalog.KEY_*` constant.
     *
     * Route keys are already persisted on user devices inside launcher cell targets and
     * app-launch-panel tile targets, so they are the vocabulary rather than a new one (ADR-1).
     */
    val routeKey: String,

    /** Position in the one order every surface shows, taken from the programs menu (ADR-5). */
    val order: Int,

    /** Surfaces this entry declares itself fit for; the completeness test checks each one. */
    val surfaces: Set<SubProgramSurface>,

    /**
     * The paired `HomeWidgetCatalog` key, or null when no widget launches this program.
     *
     * Paired by launch point and never by key spelling (ADR-6): `stream_launch` opens one
     * configured stream while route `streams` opens the list, so they are not one program.
     */
    val widgetKey: String? = null,

    /** Turns the program off, for the surfaces that offer a remove action. */
    val disable: ((AppSettings) -> AppSettings)? = null,
)
