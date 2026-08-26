package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2004: what the watch sends when it asks the phone to show a file, and what comes back.
 *
 * Mirrored verbatim in the phone module's copy of this file - the two are hand-kept in step, exactly as
 * the eleven paths before them.
 *
 * Field names are pinned rather than left to the module keep rule: the wire name is the whole contract
 * between two modules that share no code, and it must not depend on which module's shrinker config
 * happens to cover this package.
 */
data class WearOpenOnPhoneRequest(
    /**
     * The token the phone's own browse protocol issued for this item.
     *
     * The watch never invents an address: it echoes what the phone said, so the phone resolves it with
     * the same use case that listed it and no second addressing scheme exists to drift.
     */
    @SerializedName("token") val token: String,

    /** The name the notification is titled by, so the phone need not resolve the token to show it. */
    @SerializedName("displayName") val displayName: String
)

/** S2004: what the phone did with the request. */
enum class WearOpenOnPhoneOutcome {

    /** The phone was in the foreground and opened the file there and then. */
    SHOWN,

    /** The phone was not in the foreground, so it posted a notification the user can tap. */
    NOTIFIED,

    /**
     * The phone could neither show nor notify - notifications are off for the app.
     *
     * Distinct from a lost link on purpose: the watch must be able to say why nothing happened, and
     * "the phone refused" and "the phone never answered" call for different words (strategic §11
     * criterion 9).
     */
    REFUSED_NO_NOTIFICATION,

    /** The token names nothing the phone can still open. */
    NOT_FOUND
}

/** S2004: the phone's answer to one [WearOpenOnPhoneRequest]. */
data class WearOpenOnPhoneAck(
    @SerializedName("token") val token: String,
    @SerializedName("outcome") val outcome: WearOpenOnPhoneOutcome
)
