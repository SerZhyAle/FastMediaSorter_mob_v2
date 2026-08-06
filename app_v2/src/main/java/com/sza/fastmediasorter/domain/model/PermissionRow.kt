package com.sza.fastmediasorter.domain.model

/**
 * One line of a permission list: a group heading, or a permission with the state it is currently in.
 *
 * S1436: it lives in the domain because both permission surfaces now receive their rows from
 * `BuildPermissionRowsUseCase` rather than assembling them - a use case that returned a type owned by
 * one screen's adapter would make the domain depend on the UI it serves.
 */
sealed class PermissionRow {
    data class Header(val header: PermissionGroupHeader) : PermissionRow()
    data class Entry(val entry: PermissionEntry, val status: PermissionStatus) : PermissionRow()
}
