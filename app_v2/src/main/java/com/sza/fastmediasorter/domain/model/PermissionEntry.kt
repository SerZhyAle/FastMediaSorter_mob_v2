package com.sza.fastmediasorter.domain.model

enum class PermissionGroup {
    STORAGE, NETWORK, MICROPHONE, NOTIFICATION, CAMERA, LOCATION, SYSTEM, CONTACTS
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
    val group: PermissionGroup,
    val optional: Boolean,
    val minSdk: Int = 0,
    val maxSdk: Int = Int.MAX_VALUE,
    val buildGates: Set<String> = emptySet(),
    val grantKind: PermissionGrantKind = PermissionGrantKind.RUNTIME_DIALOG,
    /**
     * Onboarding asks for this permission even in a build whose gates keep it out of the full
     * Settings list. A welcome-only relaxation, declared here rather than hand-written into the
     * onboarding query, so the composition difference stays in the registry with every other rule.
     * POST_NOTIFICATIONS is the case it exists for: builds without persistent audio playback hide it
     * from Settings, but onboarding always asks for it.
     */
    val shownInWelcomeDespiteGates: Boolean = false,
    /**
     * Per-task sentences appended to [descriptionRes] when the explanation is shown during that task.
     * Declared here rather than at the call site: one permission is needed for several different jobs,
     * and the part that differs between them is still a property of the permission, not of the screen
     * that happens to be asking.
     */
    val taskAddenda: Map<PermissionTask, Int> = emptyMap(),
    /**
     * The longer explanation shown while asking for the permission during work. Separate from
     * [descriptionRes] because the two answer different questions at different sizes: the row needs a
     * label the user scans in a list, a blocking dialog needs the paragraph that makes the request
     * make sense. Null means the row's description is good enough for both.
     */
    val rationaleRes: Int? = null,
)

/**
 * A job during which a permission has to be explained. Only jobs that actually need their own sentence
 * appear here - a task with nothing to add asks for the plain description and needs no value.
 */
enum class PermissionTask {
    FOLDER_PICKING,
    LOCAL_RESOURCE_CREATION,
    QR_PAIRING,
    SCREEN_RECORDING,
    SCHEDULED_OPERATIONS,
    CONTACT_CELL_PINNING,
}

/**
 * What to show the user when a permission has to be explained: the registry's own title and
 * description, plus the addendum for the task in hand when the entry declares one.
 */
data class PermissionRationale(
    val titleRes: Int,
    /**
     * The row's own short line. A toast gets this one: `docs/COMMUNICATION_POLICY.md` §2.1 allows a
     * toast one short thought, and [detailRes] is a paragraph by design.
     */
    val summaryRes: Int,
    /** The paragraph a blocking dialog shows; falls back to [summaryRes] when the entry has none. */
    val detailRes: Int,
    val taskAddendumRes: Int? = null,
)

data class PermissionGroupHeader(
    val group: PermissionGroup,
    val titleRes: Int,
)
