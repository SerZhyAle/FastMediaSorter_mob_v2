package com.sza.fastmediasorter.core.logging

import android.content.Context
import android.os.Build
import com.sza.fastmediasorter.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Names the diagnostic archive and writes its environment summary (DIAGNOSTIC-REPORT section 8 A, rule 2).
 *
 * The summary holds platform and build facts only. The count-only invariant keeps every user-authored
 * name, address and history record out of it, so no field here is read from settings or storage.
 */
object DiagnosticArchive {

    const val ENVIRONMENT_ENTRY = "environment.txt"

    /** The fixed name every build before the pattern name wrote; a cache copy of it is deleted on export. */
    const val LEGACY_FILE_NAME = "fastmediasorter_logs.zip"

    private const val PRODUCT = "FastMediaSorter"
    private const val NAME_PREFIX = "$PRODUCT-logs-"
    private const val NAME_SUFFIX = ".zip"
    private const val NAME_STAMP_PATTERN = "yyyyMMdd-HHmmss"
    private const val UTC_STAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private const val UTC_ZONE_ID = "UTC"
    private const val SCHEMA_VERSION = 1
    private const val UNKNOWN = "unknown"
    private val unsafeNameChars = Regex("[^A-Za-z0-9._-]")

    /** `<Product>-logs-<version>-yyyyMMdd-HHmmss.zip`; the version is cut to characters safe in a file name. */
    fun fileName(versionName: String = BuildConfig.VERSION_NAME, now: Date = Date()): String {
        val stamp = SimpleDateFormat(NAME_STAMP_PATTERN, Locale.US).format(now)
        val version = versionName.replace(unsafeNameChars, "_")
        return "$NAME_PREFIX$version-$stamp$NAME_SUFFIX"
    }

    /** True for a name written by [fileName], so a stale archive in the cache can be found and removed. */
    fun isArchiveName(name: String): Boolean = name.startsWith(NAME_PREFIX) && name.endsWith(NAME_SUFFIX)

    /** `KEY=value` lines, greppable, ending with a newline; `schema_version` is the contract's version key. */
    fun environmentSummary(context: Context, now: Date = Date()): String {
        val utc = SimpleDateFormat(UTC_STAMP_PATTERN, Locale.US).apply { timeZone = TimeZone.getTimeZone(UTC_ZONE_ID) }
        val density = String.format(Locale.US, "%.2f", context.resources.displayMetrics.density)
        val fields = listOf(
            "schema_version" to SCHEMA_VERSION.toString(),
            "app_id" to BuildConfig.APPLICATION_ID,
            "app_version" to BuildConfig.VERSION_NAME,
            "app_build" to BuildConfig.VERSION_CODE.toString(),
            "edition" to BuildConfig.FLAVOR.ifEmpty { UNKNOWN },
            "build_type" to BuildConfig.BUILD_TYPE,
            "os" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "arch" to (Build.SUPPORTED_ABIS.firstOrNull() ?: UNKNOWN),
            "dpi_scale" to density,
            "locale" to Locale.getDefault().toLanguageTag(),
            "generated_utc" to utc.format(now)
        )
        return fields.joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=$value" }
    }
}
