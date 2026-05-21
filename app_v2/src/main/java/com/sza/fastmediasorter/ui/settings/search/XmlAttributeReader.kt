package com.sza.fastmediasorter.ui.settings.search

import android.content.res.XmlResourceParser

/**
 * Thin wrapper around `XmlPullParser` attribute reads tailored to the settings layout scan.
 *
 * Two namespaces are used by the project's layout files:
 * - `android:*` attributes → `ANDROID_NS`
 * - `app:*` attributes (custom view styleables, e.g. `str_title`, `csh_title`) → `APP_NS`
 */
object XmlAttributeReader {

    const val ANDROID_NS: String = "http://schemas.android.com/apk/res/android"
    const val APP_NS: String = "http://schemas.android.com/apk/res-auto"

    /**
     * Returns the resource id behind `@string/...`, `@dimen/...`, etc. references for
     * the named attribute, or `null` if the attribute is missing or its value is not a
     * resource reference (literal strings give 0 from `getAttributeResourceValue`).
     */
    fun attrResourceValue(parser: XmlResourceParser, namespace: String?, name: String): Int? {
        val value = parser.getAttributeResourceValue(namespace, name, 0)
        return if (value == 0) null else value
    }

    /**
     * Returns the literal string value of the named attribute (for inline `android:text="Foo"`
     * cases), or `null` if missing OR if the value is a `@string/...` reference. Callers
     * should try `attrResourceValue` first and fall back to this only when the reference
     * lookup returned null.
     */
    fun attrStringValue(parser: XmlResourceParser, namespace: String?, name: String): String? {
        val raw = parser.getAttributeValue(namespace, name) ?: return null
        if (raw.startsWith("@")) return null
        return raw.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns the view's `android:id` resource id (`@+id/foo` form), or `null` if the
     * element has no id attribute at all.
     */
    fun attrId(parser: XmlResourceParser): Int? {
        val idResource = parser.getAttributeResourceValue(ANDROID_NS, "id", 0)
        return if (idResource == 0) null else idResource
    }
}
