package com.sza.fastmediasorter.ui.cameracapture.helpers

import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile

/**
 * S1658: the capture set each lens keeps for itself - the active profile plus the manual values -
 * so switching optics moves to that lens's own set instead of resetting everything to NORMAL.
 *
 * Keyed by [com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry.id], the identity the
 * session already tracks across rebinds; no second notion of "which lens" is introduced.
 *
 * Holds no Android types on purpose: the encoding is a persistence contract read back after a
 * restart, and it is cheaper to pin by plain unit test than to discover a codec defect on a device.
 * The flat text form is deliberate too - a stored set is five scalars per lens, which a full
 * serializer would not make more readable.
 */
class CameraLensSettingsMemory {

    private val entries = mutableMapOf<String, LensSettings>()

    data class LensSettings(
        val profile: PhotoProfile,
        val whiteBalanceMode: Int?,
        val manualIso: Int?,
        val manualShutterNs: Long?,
        val exposureCompensationIndex: Int,
    )

    /** A lens id carrying a separator cannot round-trip through [encode], so it is never stored. */
    fun remember(lensId: String, settings: LensSettings) {
        if (lensId.isBlank() || lensId.contains(RECORD_SEPARATOR) || lensId.contains(FIELD_SEPARATOR)) return
        entries[lensId] = settings
    }

    fun recall(lensId: String): LensSettings? = entries[lensId]

    /**
     * Drops every set saved for a lens the current enumeration does not offer - a lens can leave with
     * an external camera, and a set kept for it would be restored onto whatever later takes its id.
     */
    fun retainOnly(lensIds: Set<String>) {
        entries.keys.retainAll(lensIds)
    }

    fun encode(): String = entries.entries.joinToString(RECORD_SEPARATOR.toString()) { (lensId, settings) ->
        listOf(
            lensId,
            settings.profile.name,
            settings.whiteBalanceMode?.toString().orEmpty(),
            settings.manualIso?.toString().orEmpty(),
            settings.manualShutterNs?.toString().orEmpty(),
            settings.exposureCompensationIndex.toString(),
        ).joinToString(FIELD_SEPARATOR.toString())
    }

    companion object {

        private const val RECORD_SEPARATOR = ';'
        private const val FIELD_SEPARATOR = ','
        private const val FIELD_COUNT = 6

        fun decode(raw: String): CameraLensSettingsMemory {
            val memory = CameraLensSettingsMemory()
            raw.split(RECORD_SEPARATOR)
                .filter { it.isNotBlank() }
                .forEach { record ->
                    decodeRecord(record)?.let { (lensId, settings) -> memory.remember(lensId, settings) }
                }
            return memory
        }

        /**
         * A record whose field count or profile name is unreadable is dropped rather than thrown on:
         * one bad entry must not cost every other lens its remembered set.
         */
        private fun decodeRecord(record: String): Pair<String, LensSettings>? {
            val parts = record.split(FIELD_SEPARATOR)
            val profile = parts.getOrNull(1)?.let { name -> PhotoProfile.entries.firstOrNull { it.name == name } }
            if (parts.size != FIELD_COUNT || parts[0].isBlank() || profile == null) return null
            return parts[0] to LensSettings(
                profile = profile,
                whiteBalanceMode = parts[2].toIntOrNull(),
                manualIso = parts[3].toIntOrNull(),
                manualShutterNs = parts[4].toLongOrNull(),
                exposureCompensationIndex = parts[5].toIntOrNull() ?: 0,
            )
        }
    }
}
