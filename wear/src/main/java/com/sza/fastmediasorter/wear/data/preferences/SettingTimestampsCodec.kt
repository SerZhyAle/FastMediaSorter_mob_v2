package com.sza.fastmediasorter.wear.data.preferences

// Neither separator can occur inside a record: a key is a WearSettingsRegistry field name, which is a
// Kotlin identifier, and a value is decimal digits. The map is store-private and never leaves the
// watch, so it needs no library format - the same reasoning already keeps the last-used-resource
// history in this package on a joined string rather than on a serializer.
private const val RECORD_SEPARATOR = ";"
private const val PAIR_SEPARATOR = "="
private const val PAIR_PART_COUNT = 2

/**
 * S2093: encodes the per-field edit times of the watch settings into one DataStore value.
 *
 * One key holding a map rather than a key per setting, so a registry entry added later needs neither a
 * new DataStore key nor a migration.
 */
object SettingTimestampsCodec {

    fun encode(stamps: Map<String, Long>): String = stamps.entries
        .joinToString(RECORD_SEPARATOR) { "${it.key}$PAIR_SEPARATOR${it.value}" }

    /**
     * Decodes what [encode] wrote. A record that cannot be read is dropped rather than reported: the
     * value it stamped is still stored, and a merge with no stamp for a field degrades to "the other
     * side's value wins", which is the behaviour that predates this ticket.
     */
    fun decode(stored: String?): Map<String, Long> {
        if (stored.isNullOrEmpty()) return emptyMap()
        return stored.split(RECORD_SEPARATOR)
            .mapNotNull { record ->
                val parts = record.split(PAIR_SEPARATOR)
                val millis = if (parts.size == PAIR_PART_COUNT) parts[1].toLongOrNull() else null
                if (millis == null || parts[0].isEmpty()) null else parts[0] to millis
            }
            .toMap()
    }
}
