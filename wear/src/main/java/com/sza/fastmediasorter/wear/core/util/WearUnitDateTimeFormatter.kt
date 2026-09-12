package com.sza.fastmediasorter.wear.core.util

import com.sza.fastmediasorter.wear.domain.model.UnitSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * S2795: the one place a watch surface turns an instant into a clock time or a date.
 *
 * The device's own 12/24-hour switch is deliberately not an input: the measurement system the owner
 * chose on the phone and this watch stored under `WearAppearancePreferences.unitSystem` decides the
 * clock length and the field order, so metric means a 24-hour clock and a year-first date even on a
 * watch whose system setting says otherwise.
 *
 * The patterns are literal rather than skeletons handed to `DateFormat.getBestDateTimePattern`. That
 * call looks like the locale-aware way to do this, but it takes the field ORDER from the locale and
 * only the field SET from the skeleton, so a metric skeleton still renders "09.03.2026" in Russian -
 * the exact divergence this ticket removes. Nothing in these patterns needs translating; the locale
 * still selects the digits and the AM/PM marker text.
 *
 * The four pattern strings are byte-identical to `UnitScale` in `app_v2`
 * (`domain/model/UnitScale.kt`). The two modules share no source, so this duplication is the seam
 * where they could drift silently: change one side and change the other in the same edit.
 *
 * A formatter is built per call rather than cached: `SimpleDateFormat` is not thread-safe, and a
 * history list formatting rows off the main thread while a ticking clock formats on it is exactly the
 * sharing that corrupts output.
 */
@Singleton
class WearUnitDateTimeFormatter @Inject constructor() {

    fun formatTime(
        epochMillis: Long,
        system: UnitSystem,
        withSeconds: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String = render(epochMillis, timePattern(system, withSeconds), locale)

    fun formatDate(
        epochMillis: Long,
        system: UnitSystem,
        locale: Locale = Locale.getDefault(),
    ): String = render(epochMillis, datePattern(system), locale)

    fun formatDateTime(
        epochMillis: Long,
        system: UnitSystem,
        locale: Locale = Locale.getDefault(),
    ): String = render(
        epochMillis,
        datePattern(system) + ' ' + timePattern(system, withSeconds = false),
        locale,
    )

    private fun render(epochMillis: Long, pattern: String, locale: Locale): String {
        Timber.d("S2795: watch renders with pattern=%s", pattern)
        return SimpleDateFormat(pattern, locale).format(Date(epochMillis))
    }

    private fun datePattern(system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> PATTERN_DATE_METRIC
        UnitSystem.IMPERIAL -> PATTERN_DATE_IMPERIAL
    }

    /**
     * Seconds go after the minutes rather than at the end: the imperial pattern ends in the AM/PM
     * marker, so appending would read "9:30 PM:00".
     */
    private fun timePattern(system: UnitSystem, withSeconds: Boolean): String {
        val base = when (system) {
            UnitSystem.METRIC -> PATTERN_TIME_METRIC
            UnitSystem.IMPERIAL -> PATTERN_TIME_IMPERIAL
        }
        return if (withSeconds) base.replace(":mm", ":mm:ss") else base
    }

    private companion object {

        /** Year first, 24-hour clock. Mirrors `UnitScale.PATTERN_*_METRIC`. */
        const val PATTERN_DATE_METRIC = "yyyy-MM-dd"
        const val PATTERN_TIME_METRIC = "HH:mm"

        /** Month first, 12-hour clock with a marker. Mirrors `UnitScale.PATTERN_*_IMPERIAL`. */
        const val PATTERN_DATE_IMPERIAL = "MM/dd/yyyy"
        const val PATTERN_TIME_IMPERIAL = "h:mm a"
    }
}
