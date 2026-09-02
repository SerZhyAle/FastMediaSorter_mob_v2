package com.sza.fastmediasorter.wear.core.util

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import com.sza.fastmediasorter.wear.R
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.io.IOException
import java.util.Locale

/**
 * S2054: the wear interface languages, read from `res/xml/locales_config.xml` rather than restated in code.
 *
 * Mirrors the phone's `UiLanguageCatalog` (S1190) for the same reason its KDoc records: before that
 * existed the set lived in four independent places and any one of them alone pinned the app back to three
 * languages. The watch had re-created exactly that - a three-tag Kotlin literal beside a declaration file
 * nothing in the app read - so a phone switched to German pushed `de` and got a silent refusal while the
 * German strings sat in the APK.
 *
 * Unlike the phone's catalog this one needs no separate initialization step: every caller on the watch
 * already holds a Context at the point it resolves a language, so the declaration is parsed on first use
 * and there is no "read before initialized" state to fall back from.
 */
object WearLanguageCatalog {

    /** Tag used when the declaration cannot be read - matches the base `values/` resources. */
    const val DEFAULT_TAG = "en"

    @Volatile
    private var cachedTags: List<String>? = null

    /** The declared language tags, in declaration order. Parsed once per process. */
    fun supportedTags(context: Context): List<String> =
        cachedTags ?: synchronized(this) {
            cachedTags ?: parseLocalesConfig(context).also { cachedTags = it }
        }

    /** The declared tag whose language subtag matches [languageTag], or null when undeclared. */
    fun resolveTag(context: Context, languageTag: String?): String? =
        resolveTagIn(supportedTags(context), languageTag)

    /**
     * Resolution over an explicit list, split out from [resolveTag] so the mapping from an incoming phone
     * tag to a declared tag is testable without a Context - the wear module has no Robolectric.
     *
     * Compares the language subtag, so an incoming `zh` resolves to the declared `zh-Hans`: the script is
     * pinned in the declaration because simplified and traditional are mutually unreadable.
     */
    fun resolveTagIn(declaredTags: List<String>, languageTag: String?): String? {
        val normalized = normalize(languageTag) ?: return null
        return declaredTags.firstOrNull { normalize(it) == normalized }
    }

    private fun normalize(languageTag: String?): String? =
        languageTag?.trim()?.lowercase(Locale.ROOT)?.substringBefore('-')?.takeIf { it.isNotEmpty() }

    /**
     * Seeds or clears the cached declaration. The only way a JVM unit test can exercise a caller that
     * resolves a language: `Context.getResources().getXml()` cannot be produced off-device, and a relaxed
     * mock of it returns a parser that never reaches END_DOCUMENT. Pass null to restore parsing.
     */
    internal fun overrideDeclarationForTest(tags: List<String>?) {
        cachedTags = tags
    }

    private fun parseLocalesConfig(context: Context): List<String> {
        val tags = mutableListOf<String>()
        var parser: XmlResourceParser? = null
        try {
            parser = context.resources.getXml(R.xml.locales_config)
            var events = 0
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                // A parser that never reports END_DOCUMENT would spin here forever, and this runs on the
                // startup path that applies the persisted language - a hang, not a degraded language.
                if (++events > MAX_PARSE_EVENTS) {
                    Timber.e("WearLanguageCatalog: locales_config exceeded $MAX_PARSE_EVENTS events, giving up")
                    return listOf(DEFAULT_TAG)
                }
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
     * A malformed or missing declaration must not take the watch down on a language read: English always
     * exists as the base resource set, so the interface stays usable while the defect stays visible.
     */
    private fun logParseFailure(cause: Throwable) {
        Timber.e(cause, "WearLanguageCatalog: failed to parse locales_config, falling back to $DEFAULT_TAG")
    }

    /** Generous ceiling on parser events: the thirteen declared locales cost well under a hundred. */
    private const val MAX_PARSE_EVENTS = 512

    private const val LOCALE_TAG = "locale"
    private const val NAME_ATTRIBUTE = "name"
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
}
