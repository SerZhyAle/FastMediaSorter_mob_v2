package com.sza.fastmediasorter.ui.dialog.helpers

import androidx.annotation.StringRes

/**
 * S1474: what one row of the "about this channel" window shows in place of a value.
 *
 * Three states, not one nullable string, because they mean different things to the reader and the
 * window is required to say which: nothing was ever stored, or the engine could not tell us. Collapsing
 * them would let an unmeasured value read as an empty one, which strategic §3.2 forbids.
 */
sealed interface StreamInfoValue {

    /** A value we have. */
    data class Text(val value: String) : StreamInfoValue

    /** The channel carries no such value - the row still shows, so the reader sees the field exists. */
    data object Empty : StreamInfoValue

    /** The playing engine did not supply it. Never rendered as zero and never filled from the catalog. */
    data object Unavailable : StreamInfoValue
}

/** One labelled line of the window. */
data class StreamInfoProperty(
    @StringRes val labelRes: Int,
    val value: StreamInfoValue,
)

/** One titled block of lines - the channel, what the catalog says, what the connection measured. */
data class StreamInfoGroup(
    @StringRes val titleRes: Int,
    val properties: List<StreamInfoProperty>,
)

/**
 * S1474: what the playing engine reported about the transmission, in the units it reported them.
 *
 * Every field is nullable and null means "the engine did not supply this", never zero: a stream that
 * genuinely carries no audio and a stream whose audio track has not been read yet are different answers,
 * and only the second one may be shown as unavailable (ADR-5).
 */
data class StreamMeasuredFormats(
    val videoMimeType: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val frameRate: Float? = null,
    val videoBitrate: Int? = null,
    val audioMimeType: String? = null,
    val audioChannelCount: Int? = null,
    val audioSampleRate: Int? = null,
    val audioBitrate: Int? = null,
    /**
     * Bits per second observed on this connection right now - a property of the link, not of the
     * channel, which is why it is labelled apart from the declared bitrates above.
     */
    val observedBitrate: Long? = null,
)

/**
 * S1474: the whole window, resolved.
 *
 * [NotInCatalog] is its own state rather than a readout with empty groups: a channel opened from a link
 * has no stored row at all, and rendering fourteen "empty" lines for it would claim the catalog holds
 * a record that it does not.
 */
sealed interface StreamInfoReadout {

    data class Resolved(val groups: List<StreamInfoGroup>) : StreamInfoReadout

    data object NotInCatalog : StreamInfoReadout
}
