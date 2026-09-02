package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2004: what the watch sends when it asks this phone to show a file, and what goes back.
 *
 * Mirrored verbatim in the watch module's copy of this file - the two are hand-kept in step, exactly as
 * the eleven paths before them.
 *
 * Field names are pinned because this crosses a process and a version boundary: the two modules share
 * no code, so the wire name is the whole contract, and R8 renaming a field here would make the watch's
 * request unreadable rather than merely unknown.
 */
data class WearOpenOnPhoneRequest(
    /**
     * The token this phone's own browse protocol issued for the item.
     *
     * The watch echoes what it was told rather than inventing an address, so the same use case that
     * listed the item resolves it and no second addressing scheme exists to drift.
     */
    @SerializedName("token") val token: String,

    /** The name the notification is titled by, so the token need not be resolved just to show it. */
    @SerializedName("displayName") val displayName: String
)

/** S2004: what this phone did with the request. */
enum class WearOpenOnPhoneOutcome {

    /** The app was in the foreground and opened the file there and then. */
    SHOWN,

    /** The app was not in the foreground, so a notification was posted for the user to tap. */
    NOTIFIED,

    /**
     * Neither shown nor notified - notifications are off for the app.
     *
     * Distinct from a lost link on purpose: the watch must be able to say why nothing happened, and
     * "the phone refused" and "the phone never answered" call for different words (strategic §11
     * criterion 9).
     */
    REFUSED_NO_NOTIFICATION,

    /** The token names nothing this phone can still open. */
    NOT_FOUND
}

/** S2004: this phone's answer to one [WearOpenOnPhoneRequest]. */
data class WearOpenOnPhoneAck(
    @SerializedName("token") val token: String,
    @SerializedName("outcome") val outcome: WearOpenOnPhoneOutcome
)
