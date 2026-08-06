package com.sza.fastmediasorter.domain.model

enum class PermissionGroup {
    STORAGE, NETWORK, MICROPHONE, NOTIFICATION, CAMERA, LOCATION, SYSTEM, VR, CONTACTS
}

/**
 * NOT_YET_REQUESTED and PERMANENTLY_DENIED both correspond to the same platform answer - the system
 * reports no rationale in either case - and are told apart only by the request marker.
 *
 * ASKED_EACH_TIME is not a grant state at all: it belongs to a [PermissionGrantKind.PER_USE_CONSENT]
 * entry, where the system asks again on every use and nothing is stored to read back. It is a
 * distinct value rather than a DENIED so that the bulk "grant all" run, which selects on
 * NOT_YET_REQUESTED and DENIED, cannot pick up a permission it could never grant.
 */
enum class PermissionStatus {
    GRANTED, NOT_YET_REQUESTED, DENIED, PERMANENTLY_DENIED, NOT_APPLICABLE, ASKED_EACH_TIME
}

/**
 * How the system lets the user answer for a permission. The row follows from this, not from a list
 * of exceptions held by whichever screen happens to draw it.
 *
 * - [RUNTIME_DIALOG] - the ordinary request dialog; the grant happens in place and persists.
 * - [SYSTEM_SCREEN] - `requestPermissions()` drops it silently, so the only route is a dedicated
 *   system screen the app navigates to and returns from.
 * - [PER_USE_CONSENT] - there is no persisted grant at all: the system asks again on every use, so
 *   the row can report the situation but cannot offer to grant it in advance.
 */
enum class PermissionGrantKind {
    RUNTIME_DIALOG,
    SYSTEM_SCREEN,
    PER_USE_CONSENT,
}

data class PermissionEntry(
    val id: String,
    val manifestName: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val group: PermissionGroup,
    val optional: Boolean,
    val minSdk: Int = 0,
    val maxSdk: Int = Int.MAX_VALUE,
    val buildGates: Set<String> = emptySet(),
    val grantKind: PermissionGrantKind = PermissionGrantKind.RUNTIME_DIALOG,
)

data class PermissionGroupHeader(
    val group: PermissionGroup,
    val titleRes: Int,
)
