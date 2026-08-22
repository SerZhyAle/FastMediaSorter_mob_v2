package com.sza.fastmediasorter.ui.launcher.signal

/**
 * One thing this app is doing right now, as the launcher status strip shows it.
 *
 * Deliberately carries no navigation target and no Android view type. Only the producer of a signal knows which
 * screen the signal belongs to, so the tap target is resolved by [LauncherSignalSource.open] instead of by
 * a `when` over [LauncherSignalKind] inside the strip - otherwise every source added later would have to
 * extend that `when` as well as declare itself (S1421 ADR-4).
 */
data class LauncherSignal(
    /**
     * Stable while the underlying activity lasts, so a refresh that returns the same work re-uses the same
     * chip instead of rebuilding the row and dropping focus mid-D-pad.
     */
    val id: String,
    val kind: LauncherSignalKind,
    val icon: LauncherSignalIcon,
    val label: String,
    val detail: String? = null,
)

/**
 * Declaration order is the strip's left-to-right order - [LauncherSignalRegistry] sorts by ordinal - so a
 * new value's position is a product decision, not a cosmetic one.
 */
enum class LauncherSignalKind {
    PLAYBACK,
    FILE_TRANSFER,
    BACKGROUND_WORK,

    /**
     * Last by the owner's decision of 2026-08-17: another application's notifications must never push this
     * app's own playback or file transfer out of a row that has run out of room (S1465).
     */
    FOREIGN_NOTIFICATION,
}
