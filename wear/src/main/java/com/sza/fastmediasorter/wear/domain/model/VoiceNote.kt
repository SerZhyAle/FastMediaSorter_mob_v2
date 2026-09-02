package com.sza.fastmediasorter.wear.domain.model

/**
 * S1862: one voice note recorded on the watch.
 *
 * The note survives every transfer outcome (ADR-3) - speech cannot be recorded again, so the file
 * becomes a note first and is only then offered to the transport.
 */
data class VoiceNote(
    val id: Long,
    val fileName: String,
    val absolutePath: String,
    val createdAtMillis: Long,
    val durationMillis: Long,
    val sizeBytes: Long,
    val deliveryState: VoiceNoteDeliveryState
)

/**
 * Delivery is a field of the note, not something derived on the fly: section 7 requires a waiting
 * note to be visible in the list as its own state rather than as an internal flag.
 */
enum class VoiceNoteDeliveryState {

    /** Manual policy - the note waits for an explicit "Send" and is not queued for anything. */
    LOCAL_ONLY,

    /** Automatic policy with the phone out of reach - the note leaves when the link returns. */
    PENDING,

    /** The phone acknowledged the transfer. The note itself stays until deleted by hand. */
    SENT,

    /** The transfer failed for a reason that retrying on reconnect will not fix by itself. */
    FAILED;

    companion object {

        /** Room stores the name, so an unknown name (a downgrade, a corrupt row) must not throw. */
        fun fromNameOrDefault(name: String?, default: VoiceNoteDeliveryState = LOCAL_ONLY): VoiceNoteDeliveryState =
            entries.firstOrNull { it.name == name } ?: default
    }
}
