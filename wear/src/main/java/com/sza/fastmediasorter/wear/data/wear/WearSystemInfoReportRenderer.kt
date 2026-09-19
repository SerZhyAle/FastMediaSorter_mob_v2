package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Beyond this the text is cut: the Data Layer refuses a message near 100 KB outright. */
private const val REPORT_CHARACTER_LIMIT = 60_000

private const val TRUNCATION_MARKER = "[report truncated]"

private const val ENTRY_INDENT = "    "

/**
 * S3108: writes the finished report out as the flat text the phone stores.
 *
 * On the watch rather than on the phone, because every label here is a string resource of this
 * module: the phone can resolve none of them, and rendering there would print the report in whatever
 * language the phone is set to instead of the one the watch shows.
 *
 * The screen's own shape is kept - a section title, then one "label: value" per line - so a user who
 * has read the screen recognises the file.
 */
class WearSystemInfoReportRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun render(sections: List<WearSystemInfoSection>): String {
        val text = buildString {
            sections.forEach { section ->
                appendLine(context.getString(section.titleRes))
                appendSection(section)
                appendLine()
            }
        }
        return if (text.length <= REPORT_CHARACTER_LIMIT) {
            text
        } else {
            text.take(REPORT_CHARACTER_LIMIT) + System.lineSeparator() + TRUNCATION_MARKER
        }
    }

    private fun StringBuilder.appendSection(section: WearSystemInfoSection) {
        val emptyReasonRes = section.emptyReasonRes
        if (section.fields.isEmpty() && emptyReasonRes != null) {
            appendLine(context.getString(emptyReasonRes))
            return
        }
        section.fields.forEach { field -> appendField(field) }
    }

    private fun StringBuilder.appendField(field: WearSystemInfoField) {
        val label = context.getString(field.labelRes)
        when (val value = field.value) {
            is WearSystemInfoValue.Text -> appendLine("$label: ${value.text}")
            is WearSystemInfoValue.Label -> appendLine("$label: ${context.getString(value.res)}")
            // Written out whole rather than as the count the screen shows collapsed: the file is read
            // where there is room for it, and the count is the one thing a reader can recompute.
            is WearSystemInfoValue.Enumerated -> {
                appendLine("$label:")
                value.entries.forEach { entry -> appendLine("$ENTRY_INDENT$entry") }
            }
        }
    }
}
