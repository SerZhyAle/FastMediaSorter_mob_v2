package com.sza.fastmediasorter.wear.domain.model

/** S4381: the kind of object "Open in.." hands over; the last-used memory is kept per kind (strategic ADR-4). */
enum class WearOpenInKind { TEXT, FILE }

/**
 * S4381: what "Open in.." hands to another application.
 *
 * Neither variant prints its content: a log line or a crash report carrying a clipboard text, a file name
 * or a content URI would leak what the wearer copied (strategic criterion 13).
 */
sealed interface WearOpenInObject {

    val kind: WearOpenInKind

    data class Text(val text: String) : WearOpenInObject {
        override val kind: WearOpenInKind
            get() = WearOpenInKind.TEXT

        override fun toString(): String = "WearOpenInObject.Text(length=${text.length})"
    }

    /** Exactly one of [contentUri] (a media row) and [path] (a file in app storage) is set. */
    data class File(
        val mimeType: String,
        val displayName: String,
        val contentUri: String? = null,
        val path: String? = null,
    ) : WearOpenInObject {
        override val kind: WearOpenInKind
            get() = WearOpenInKind.FILE

        override fun toString(): String = "WearOpenInObject.File(mimeType=$mimeType)"
    }
}

/** S4381: which intent a row starts; one package may handle several, so the row remembers its own. */
enum class WearOpenInAction {
    /** A web address or a link (mail, phone, message). */
    VIEW_LINK,

    /** A place, started as a `geo:` view. */
    VIEW_PLACE,

    /** Free text offered to an application that takes text. */
    SEND_TEXT,

    /** Free text searched in the default browser. */
    WEB_SEARCH,

    /** A file, started as a view with its type and a read grant. */
    VIEW_FILE,
}

/** S4381: the role a row plays in the list order (strategic ADR-4). */
enum class WearOpenInRole { LAST_USED, BROWSER, MAPS, OTHER }

/** S4381: one installed activity able to take the object, before the order is applied. */
data class WearOpenInCandidate(
    val packageName: String,
    val activityName: String,
    val label: String,
    val action: WearOpenInAction,
)

/** S4381: what the system answered for an object: the handlers, and which packages it starts by default. */
data class WearOpenInCandidates(
    val apps: List<WearOpenInCandidate>,
    val defaultBrowser: String?,
    val defaultMaps: String?,
)

/** S4381: one row of the list the wearer sees. */
data class WearOpenInTarget(
    val packageName: String,
    val activityName: String,
    val label: String,
    val action: WearOpenInAction,
    val role: WearOpenInRole,
)

/** S4381: how a launch ended; only [Launched] is remembered as the last choice. */
enum class WearOpenInLaunchResult { Launched, NotFound, Refused }

/** S4381: the longest text handed over as a string; above it the text goes as a file (research 03). */
const val WEAR_OPEN_IN_TEXT_LIMIT = 100_000
