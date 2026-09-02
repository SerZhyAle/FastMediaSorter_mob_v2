package com.sza.fastmediasorter.ui.launcher.gadget

import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * S1906: the zone list the world-clock picker offers, and the caption a placed world-clock cell shows.
 *
 * Both live here so the picker and the cell name the same zone the same way - a row read as
 * "Kyiv - Europe (UTC+03:00)" and then placed as something else would look like the wrong pick.
 *
 * The set of zones is the platform's, never a list of our own (strategic §2 non-goals): a shipped city
 * table goes stale on the next zone-database update, and the platform's is the one the clock itself
 * resolves against.
 */
object LauncherTimeZoneCatalog {

    /**
     * The stored param is only ever a zone id, and an id the platform no longer knows must not throw
     * on the home screen - `ZoneId.of` on an unknown id is an exception, not a null.
     */
    fun zoneOrNull(param: String?): ZoneId? {
        val id = param?.trim().orEmpty()
        return if (id.isEmpty() || id !in ZoneId.getAvailableZoneIds()) null else ZoneId.of(id)
    }

    /**
     * Sorted by current offset, then by label: the zones a user hesitates between are the neighbouring
     * ones, and offset order puts them next to each other where an alphabetical list scatters them.
     */
    fun options(): List<Option> {
        val now = Instant.now()
        return ZoneId.getAvailableZoneIds()
            .map { ZoneId.of(it) }
            .map { zone -> ZoneRow(offsetSeconds(zone, now), pickerLabel(zone, now), zone.id) }
            .sortedWith(compareBy({ it.offsetSeconds }, { it.label }))
            .map { Option(id = it.id, label = it.label) }
    }

    /**
     * What the placed cell says under the time: the city, plus how far ahead or behind local time it
     * is. A zone matching the device's own offset says only the city - "+0" under a clock that reads
     * the same as the phone's is noise.
     */
    fun caption(zone: ZoneId, now: Instant = Instant.now()): String {
        val delta = offsetSeconds(zone, now) - offsetSeconds(ZoneId.systemDefault(), now)
        val city = cityOf(zone)
        return if (delta == 0) city else "$city (${signedDelta(delta)})"
    }

    /** The segment after the last slash, underscores spaced out: `Europe/Kyiv` reads as `Kyiv`. */
    private fun cityOf(zone: ZoneId): String =
        zone.id.substringAfterLast(ZONE_SEPARATOR).replace(UNDERSCORE, ' ')

    private fun pickerLabel(zone: ZoneId, now: Instant): String {
        val offset = "$UTC_PREFIX${signedOffset(offsetSeconds(zone, now))}"
        val region = zone.id.substringBeforeLast(ZONE_SEPARATOR, missingDelimiterValue = "")
        // A zone with no region - "UTC", "CET", "EST5EDT" - is its own name; appending an empty region
        // would leave a dangling dash in the row the user reads.
        return if (region.isEmpty()) {
            "${cityOf(zone)} ($offset)"
        } else {
            "${cityOf(zone)} - ${region.replace(UNDERSCORE, ' ')} ($offset)"
        }
    }

    private fun offsetSeconds(zone: ZoneId, now: Instant): Int =
        zone.rules.getOffset(now).totalSeconds

    /** `+03:00` / `-05:30` - the fixed shape a UTC offset is written in. */
    private fun signedOffset(seconds: Int): String {
        val sign = if (seconds < 0) '-' else '+'
        val absolute = kotlin.math.abs(seconds)
        val hours = absolute / SECONDS_PER_HOUR
        val minutes = absolute % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        return String.format(Locale.US, OFFSET_FORMAT, sign, hours, minutes)
    }

    /** `+2` / `-5:30` - the difference from local time, kept short enough for a 2x1 cell. */
    private fun signedDelta(seconds: Int): String {
        val sign = if (seconds < 0) '-' else '+'
        val absolute = kotlin.math.abs(seconds)
        val hours = absolute / SECONDS_PER_HOUR
        val minutes = absolute % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        return if (minutes == 0) {
            "$sign$hours"
        } else {
            String.format(Locale.US, DELTA_FORMAT, sign, hours, minutes)
        }
    }

    private data class ZoneRow(val offsetSeconds: Int, val label: String, val id: String)

    private const val ZONE_SEPARATOR = '/'
    private const val UNDERSCORE = '_'
    private const val UTC_PREFIX = "UTC"
    private const val OFFSET_FORMAT = "%s%02d:%02d"
    private const val DELTA_FORMAT = "%s%d:%02d"
    private const val SECONDS_PER_HOUR = 3600
    private const val SECONDS_PER_MINUTE = 60
}
