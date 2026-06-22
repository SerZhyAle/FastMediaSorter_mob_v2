package com.sza.fastmediasorter.ui.settings.search

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import com.sza.fastmediasorter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `SettingsSearchSource` implementation that walks every settings-fragment layout listed in
 * `SettingsSearchLayoutCatalog` and emits one `RawSettingsSearchEntry` per discovered view
 * that has an `android:id` AND a recognized "kind" (toggle row, section header, button,
 * text input, spinner, or the S0567 compound rows `SettingsInputRow` / `SettingsDropdownRow` /
 * `SettingsSelectionRow`).
 *
 * Help-popup text (`str_helpTitle` / `str_helpMessage` / `csh_helpTitle` / `csh_helpMessage`)
 * is intentionally NOT extracted - see strategic spec §6.3.
 */
@Singleton
class LayoutSettingsSearchSource @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsSearchSource {

    override fun collect(): List<RawSettingsSearchEntry> {
        val all = mutableListOf<RawSettingsSearchEntry>()
        for (layoutResId in SettingsSearchLayoutCatalog.layoutResIds) {
            val layoutName = safeResourceName(layoutResId)
            try {
                val parser = context.resources.getXml(layoutResId)
                scan(parser, layoutResId, all)
            } catch (e: XmlPullParserException) {
                Timber.w(e, "Settings search: failed to parse layout $layoutName, skipping")
            } catch (e: IOException) {
                Timber.w(e, "Settings search: I/O error reading layout $layoutName, skipping")
            } catch (e: Resources.NotFoundException) {
                Timber.w(e, "Settings search: layout $layoutName not found, skipping")
            }
        }
        return all
    }

    private fun scan(
        parser: XmlResourceParser,
        layoutResId: Int,
        out: MutableList<RawSettingsSearchEntry>
    ): Int {
        var added = 0
        // Ancestor view-ids of the element being visited, outermost first. One entry is pushed per
        // START_TAG (NO_ID for id-less elements) and popped per END_TAG so the stack depth tracks
        // the XML nesting; a row's ancestors are the real ids already on the stack when it opens.
        val ancestors = ArrayDeque<Int>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val viewId = XmlAttributeReader.attrId(parser)
                    // Transient permission-prompt buttons are not navigable settings: they are GONE
                    // whenever the permission is already granted (the common case), so indexing them
                    // yields dead search results. De-index at the source (S0604).
                    val kind = if (viewId != null && viewId in TRANSIENT_ACTION_BUTTON_IDS) {
                        null
                    } else {
                        kindFromTag(parser.name) ?: pickerKindForId(viewId)
                    }
                    if (viewId != null && kind != null) {
                        val raw = buildEntry(parser, viewId, layoutResId, kind, ancestors.filter { it != NO_ID })
                        if (raw != null) {
                            out += raw
                            added++
                        }
                    }
                    ancestors.addLast(viewId ?: NO_ID)
                }
                XmlPullParser.END_TAG -> {
                    if (ancestors.isNotEmpty()) ancestors.removeLast()
                }
            }
            event = parser.next()
        }
        return added
    }

    private fun kindFromTag(tag: String?): EntryKind? {
        if (tag == null) return null
        val simple = tag.substringAfterLast('.')
        return when {
            simple == "SettingsToggleRow" -> EntryKind.TOGGLE_ROW
            simple == "CollapsibleSectionHeader" -> EntryKind.SECTION_HEADER
            simple == "Spinner" || simple == "AppCompatSpinner" ||
                simple == "AutoCompleteTextView" ||
                simple == "MaterialAutoCompleteTextView" -> EntryKind.SPINNER
            simple == "EditText" || simple == "TextInputEditText" -> EntryKind.TEXT_INPUT
            // S0567 compound input row carries its label in app:sir_title (not android:hint),
            // so it gets its own kind; otherwise its settings are invisible to search (S0597).
            simple == "SettingsInputRow" -> EntryKind.INPUT_ROW
            // S0567 compound dropdown/selection rows carry their labels in app:sdr_title /
            // app:ssr_title; same rationale as INPUT_ROW above (S0598).
            simple == "SettingsDropdownRow" -> EntryKind.DROPDOWN_ROW
            simple == "SettingsSelectionRow" -> EntryKind.SELECTION_ROW
            simple == "MaterialButton" || simple == "Button" ||
                simple == "ImageButton" -> EntryKind.BUTTON
            else -> null
        }
    }

    /**
     * Recognizes the two translation language pickers, which are plain `TextView`s (not a spinner
     * widget) yet behave as dropdowns. Matching the bare `TextView` tag in [kindFromTag] would
     * flood the index with every labelled/static TextView, so these are allow-listed by id and
     * mapped to [EntryKind.SPINNER]; their label is resolved from `android:contentDescription`
     * (see [buildEntry]). Without this they are silently non-indexed and undiscoverable (S0603).
     */
    private fun pickerKindForId(viewId: Int?): EntryKind? =
        if (viewId != null && viewId in TEXTVIEW_PICKER_IDS) EntryKind.SPINNER else null

    private fun buildEntry(
        parser: XmlResourceParser,
        viewId: Int,
        layoutResId: Int,
        kind: EntryKind,
        ancestorIds: List<Int>
    ): RawSettingsSearchEntry? {
        var titleResId: Int? = null
        var subtitleResId: Int? = null
        var hintResId: Int? = null
        var inlineTitle: String? = null
        var inlineSubtitle: String? = null
        var inlineHint: String? = null

        when (kind) {
            EntryKind.TOGGLE_ROW -> {
                titleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, "str_title")
                if (titleResId == null) {
                    inlineTitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.APP_NS, "str_title")
                }
                subtitleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, "str_subtitle")
                if (subtitleResId == null) {
                    inlineSubtitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.APP_NS, "str_subtitle")
                }
            }
            EntryKind.SECTION_HEADER -> {
                titleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, "csh_title")
                if (titleResId == null) {
                    inlineTitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.APP_NS, "csh_title")
                }
            }
            EntryKind.BUTTON -> {
                titleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.ANDROID_NS, "text")
                if (titleResId == null) {
                    inlineTitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.ANDROID_NS, "text")
                }
            }
            EntryKind.TEXT_INPUT, EntryKind.SPINNER -> {
                hintResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.ANDROID_NS, "hint")
                inlineHint = if (hintResId == null) {
                    XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.ANDROID_NS, "hint")
                } else {
                    null
                }
                titleResId = hintResId
                inlineTitle = inlineHint
                // Hint-less spinners (OCR engine/model/font pickers, S0288) and the TextView-based
                // translation language pickers label themselves via android:contentDescription;
                // fall back to it so these rows are searchable (S0603). The hint slot stays the real
                // (here absent) hint - only the title borrows the content description.
                if (titleResId == null && inlineTitle == null) {
                    titleResId = XmlAttributeReader.attrResourceValue(
                        parser, XmlAttributeReader.ANDROID_NS, "contentDescription"
                    )
                    if (titleResId == null) {
                        inlineTitle = XmlAttributeReader.attrStringValue(
                            parser, XmlAttributeReader.ANDROID_NS, "contentDescription"
                        )
                    }
                }
            }
            EntryKind.INPUT_ROW -> {
                titleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, "sir_title")
                if (titleResId == null) {
                    inlineTitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.APP_NS, "sir_title")
                }
                hintResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, "sir_hint")
                if (hintResId == null) {
                    inlineHint = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.APP_NS, "sir_hint")
                }
            }
            EntryKind.DROPDOWN_ROW -> {
                titleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, "sdr_title")
                if (titleResId == null) {
                    inlineTitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.APP_NS, "sdr_title")
                }
            }
            EntryKind.SELECTION_ROW -> {
                titleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, "ssr_title")
                if (titleResId == null) {
                    inlineTitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.APP_NS, "ssr_title")
                }
                subtitleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, "ssr_subtitle")
                if (subtitleResId == null) {
                    inlineSubtitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.APP_NS, "ssr_subtitle")
                }
            }
        }

        if (titleResId == null && inlineTitle == null &&
            subtitleResId == null && inlineSubtitle == null &&
            hintResId == null && inlineHint == null
        ) {
            return null
        }

        return RawSettingsSearchEntry(
            viewId = viewId,
            layoutResId = layoutResId,
            kind = kind,
            titleResId = titleResId,
            subtitleResId = subtitleResId,
            hintResId = hintResId,
            inlineTitle = inlineTitle,
            inlineSubtitle = inlineSubtitle,
            inlineHint = inlineHint,
            ancestorIds = ancestorIds
        )
    }

    private fun safeResourceName(resId: Int): String = try {
        context.resources.getResourceEntryName(resId)
    } catch (_: Resources.NotFoundException) {
        "resId=$resId"
    }

    private companion object {
        // Sentinel pushed for elements without an android:id; resource ids are always positive.
        const val NO_ID = -1

        // TextView-based language pickers that act as dropdowns but are not a spinner widget,
        // so they are not matched by tag (S0603). Allow-listed by id to avoid indexing every TextView.
        val TEXTVIEW_PICKER_IDS: Set<Int> = setOf(
            R.id.spinnerTranslationSourceLanguage,
            R.id.spinnerTranslationTargetLanguage
        )

        // Transient permission-prompt action buttons gated purely by mutable runtime state (a
        // permission grant +/- a toggle), not by flavor/DI/device capability. They are hidden the
        // vast majority of the time, so search must not index them as if they were settings (S0604).
        val TRANSIENT_ACTION_BUTTON_IDS: Set<Int> = setOf(
            R.id.btnNotificationPermission,
            R.id.btnScheduledNotificationPermission
        )
    }
}
