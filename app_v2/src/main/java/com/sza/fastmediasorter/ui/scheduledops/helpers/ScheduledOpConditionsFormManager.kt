package com.sza.fastmediasorter.ui.scheduledops.helpers

import android.widget.EditText
import com.sza.fastmediasorter.databinding.IncludeScheduledOpFileConditionsBinding
import com.sza.fastmediasorter.domain.model.ScheduledFileConditions
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * S4075: reads and fills the optional file-condition fields of the scheduled-operation editor.
 *
 * Ages are entered as days plus hours and stored as whole hours; sizes are entered in megabytes and stored
 * in bytes. A blank field, and a zero where zero would mean "everything" or "nothing", is no condition.
 */
class ScheduledOpConditionsFormManager(private val b: IncludeScheduledOpFileConditionsBinding) {

    fun fill(conditions: ScheduledFileConditions) {
        b.etNameMask.setText(conditions.nameMask.orEmpty())
        fillAge(conditions.maxAgeHours, b.etMaxAgeDays, b.etMaxAgeHours)
        fillAge(conditions.minAgeHours, b.etMinAgeDays, b.etMinAgeHours)
        b.etMaxSizeMb.setText(formatMegabytes(conditions.maxSizeBytes))
        b.etMinSizeMb.setText(formatMegabytes(conditions.minSizeBytes))
    }

    fun read(): ScheduledFileConditions = ScheduledFileConditions(
        nameMask = b.etNameMask.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        minAgeHours = readAgeHours(b.etMinAgeDays, b.etMinAgeHours),
        maxAgeHours = readAgeHours(b.etMaxAgeDays, b.etMaxAgeHours),
        minSizeBytes = readBytes(b.etMinSizeMb, keepZero = true),
        maxSizeBytes = readBytes(b.etMaxSizeMb, keepZero = false),
    )

    private fun fillAge(totalHours: Int?, days: EditText, hours: EditText) {
        if (totalHours == null) {
            days.setText("")
            hours.setText("")
            return
        }
        val wholeDays = totalHours / HOURS_PER_DAY
        val restHours = totalHours % HOURS_PER_DAY
        days.setText(if (wholeDays > 0) wholeDays.toString() else "")
        hours.setText(if (restHours > 0) restHours.toString() else "")
    }

    private fun readAgeHours(days: EditText, hours: EditText): Int? {
        val d = days.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val h = hours.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val total = d * HOURS_PER_DAY + h
        return total.takeIf { it > 0 }
    }

    /** "Larger than 0 MB" is a real condition - it skips empty files - while "smaller than 0" matches nothing. */
    private fun readBytes(field: EditText, keepZero: Boolean): Long? {
        val mb = field.text?.toString()?.trim()?.replace(',', '.')?.toBigDecimalOrNull() ?: return null
        val bytes = mb.multiply(BYTES_PER_MB).setScale(0, RoundingMode.HALF_UP).toLong()
        return bytes.takeIf { it > 0 || (keepZero && it == 0L) }
    }

    private fun formatMegabytes(bytes: Long?): String =
        bytes?.let {
            BigDecimal(it).divide(BYTES_PER_MB, MB_DECIMALS, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
        }.orEmpty()

    private companion object {
        const val HOURS_PER_DAY = 24
        const val MB_DECIMALS = 3
        val BYTES_PER_MB: BigDecimal = BigDecimal.valueOf(1_048_576L)
    }
}
