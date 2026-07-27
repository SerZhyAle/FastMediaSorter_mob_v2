package com.sza.fastmediasorter.core.util

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import com.sza.fastmediasorter.R
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.io.IOException
import java.util.Locale

/**
 * S1190: the interface languages, read from `res/xml/locales_config.xml` rather than restated in code.
 *
 * That file is the one declaration Android itself already needs, so parsing it here keeps the app, the
 * system language picker and the localization scripts on a single list - adding a language is a data
 * edit. Before this existed the set lived in four independent places and any one of them alone pinned
 * the app back to three languages.
 */
object UiLanguageCatalog {

    /** Tag used when the requested language is not supported - matches the base `values/` resources. */
    const val DEFAULT_TAG = "en"

    @Volatile
    private var cachedTags: List<String>? = null

    /**
     * Parses the declaration once. Called from [LocaleHelper.applyLocale], which runs in the
     * application's `attachBaseContext` - the earliest point the app resolves a language at all - so
     * every later caller sees a populated catalog without carrying a Context of its own.
     */
    fun ensureInitialized(context: Context) {
        if (cachedTags != null) return
        synchronized(this) {
            if (cachedTags == null) {
                cachedTags = parseLocalesConfig(context)
            }
        }
    }

    /**
     * The declared language tags, in declaration order.
     *
     * Falls back to English alone if something resolved a language before the application attached its
     * base context. That is not a supported state - it is logged rather than silently accepted, because
     * a shrunken list here is exactly the failure this class exists to prevent.
     */
    val supportedTags: List<String>
        get() = cachedTags ?: run {
            Timber.w("UiLanguageCatalog: read before initialization, falling back to $DEFAULT_TAG")
            listOf(DEFAULT_TAG)
        }

    /** True when [languageTag] is declared. Compares the language subtag, so `zh-Hans` matches `zh`. */
    fun isSupported(languageTag: String?): Boolean {
        val normalized = normalize(languageTag) ?: return false
        return supportedTags.any { normalize(it) == normalized }
    }

    /** The declared tag whose language subtag matches [languageTag], or null when unsupported. */
    fun resolveTag(languageTag: String?): String? {
        val normalized = normalize(languageTag) ?: return null
        return supportedTags.firstOrNull { normalize(it) == normalized }
    }

    /** The language's own name for its row in the picker - "Deutsch", not "German". */
    fun displayName(languageTag: String): String {
        val locale = Locale.forLanguageTag(languageTag)
        val name = locale.getDisplayLanguage(locale)
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    private fun normalize(languageTag: String?): String? =
        languageTag?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.takeIf { it.isNotEmpty() }

    private fun parseLocalesConfig(context: Context): List<String> {
        val tags = mutableListOf<String>()
        var parser: XmlResourceParser? = null
        try {
            parser = context.resources.getXml(R.xml.locales_config)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == LOCALE_TAG) {
                    parser.getAttributeValue(ANDROID_NAMESPACE, NAME_ATTRIBUTE)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { tags += it }
                }
            }
        } catch (e: XmlPullParserException) {
            logParseFailure(e)
        } catch (e: IOException) {
            logParseFailure(e)
        } catch (e: Resources.NotFoundException) {
            logParseFailure(e)
        } finally {
            parser?.close()
        }
        return tags.ifEmpty { listOf(DEFAULT_TAG) }
    }

    /**
     * A malformed or missing declaration must not take the app down on a language read: English always
     * exists as the base resource set, so the interface stays usable while the defect stays visible.
     */
    private fun logParseFailure(cause: Throwable) {
        Timber.e(cause, "UiLanguageCatalog: failed to parse locales_config, falling back to $DEFAULT_TAG")
    }

    private const val LOCALE_TAG = "locale"
    private const val NAME_ATTRIBUTE = "name"
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
}
