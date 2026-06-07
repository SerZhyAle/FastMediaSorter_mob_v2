package com.sza.fastmediasorter.ui.settings.search

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
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
 * text input, or spinner).
 *
 * Help-popup text (`str_helpTitle` / `str_helpMessage` / `csh_helpTitle` / `csh_helpMessage`)
 * is intentionally NOT extracted — see strategic spec §6.3.
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
                val count = scan(parser, layoutResId, all)
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
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val viewId = XmlAttributeReader.attrId(parser)
                val kind = kindFromTag(parser.name)
                if (viewId != null && kind != null) {
                    val raw = buildEntry(parser, viewId, layoutResId, kind)
                    if (raw != null) {
                        out += raw
                        added++
                    }
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
            simple == "Spinner" || simple == "AutoCompleteTextView" ||
                simple == "MaterialAutoCompleteTextView" -> EntryKind.SPINNER
            simple == "EditText" || simple == "TextInputEditText" -> EntryKind.TEXT_INPUT
            simple == "MaterialButton" || simple == "Button" ||
                simple == "ImageButton" -> EntryKind.BUTTON
            else -> null
        }
    }

    private fun buildEntry(
        parser: XmlResourceParser,
        viewId: Int,
        layoutResId: Int,
        kind: EntryKind
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
                titleResId = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.ANDROID_NS, "hint")
                if (titleResId == null) {
                    inlineTitle = XmlAttributeReader.attrStringValue(parser, XmlAttributeReader.ANDROID_NS, "hint")
                }
                hintResId = titleResId
                inlineHint = inlineTitle
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
            inlineHint = inlineHint
        )
    }

    private fun safeResourceName(resId: Int): String = try {
        context.resources.getResourceEntryName(resId)
    } catch (_: Resources.NotFoundException) {
        "resId=$resId"
    }
}
