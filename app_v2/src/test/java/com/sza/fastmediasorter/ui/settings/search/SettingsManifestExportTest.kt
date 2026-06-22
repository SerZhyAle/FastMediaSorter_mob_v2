package com.sza.fastmediasorter.ui.settings.search

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * Generates and verifies `docs/settings/settings-manifest.json` (S0440).
 *
 * The manifest is produced by the SAME scan the app ships (`LayoutSettingsSearchSource` +
 * `SettingsSearchTabMapping`), so it cannot drift from the in-app settings index. Titles are
 * resolved per locale exactly as `LocalizedKeywordCollector` does (configuration-context per tag).
 * Only structure + trilingual titles are captured here; per-flavor availability is rendered later
 * from the availability modules, and human descriptions live in the annotations sidecar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsManifestExportTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val localizedResources: Map<String, Resources> =
        SupportedSearchLocales.tags.associateWith { tag ->
            val base = Configuration(context.resources.configuration)
            base.setLocales(LocaleList(Locale.forLanguageTag(tag)))
            context.createConfigurationContext(base).resources
        }

    private fun buildManifest(): List<SettingsManifestEntry> {
        val source = LayoutSettingsSearchSource(context)
        val entries = mutableListOf<SettingsManifestEntry>()
        for (raw in source.collect()) {
            val assignment = SettingsSearchTabMapping.assignmentFor(raw.layoutResId) ?: continue
            val titles = SupportedSearchLocales.tags.associateWith { tag ->
                resolveTitle(raw, localizedResources.getValue(tag))
            }
            if (titles.values.all { it.isBlank() }) continue
            entries += SettingsManifestEntry(
                key = resourceName(raw.viewId, "viewId_${raw.viewId}"),
                sectionId = assignment.sectionId,
                destination = assignment.destination.name,
                layout = resourceName(raw.layoutResId, "layout_${raw.layoutResId}"),
                kind = raw.kind.name,
                titleEn = titles.getValue("en"),
                titleRu = titles.getValue("ru"),
                titleUk = titles.getValue("uk")
            )
        }
        return entries
    }

    // Mirrors LocalizedKeywordCollector title precedence: title res, inline title, hint res, inline hint.
    private fun resolveTitle(raw: RawSettingsSearchEntry, res: Resources): String {
        raw.titleResId?.let { return safeString(res, it) }
        if (!raw.inlineTitle.isNullOrBlank()) return raw.inlineTitle
        raw.hintResId?.let { return safeString(res, it) }
        if (!raw.inlineHint.isNullOrBlank()) return raw.inlineHint
        return ""
    }

    private fun safeString(res: Resources, resId: Int): String = try {
        res.getString(resId).trim()
    } catch (_: Resources.NotFoundException) {
        ""
    }

    private fun resourceName(resId: Int, fallback: String): String = try {
        context.resources.getResourceEntryName(resId)
    } catch (_: Resources.NotFoundException) {
        fallback
    }

    // Gradle runs unit tests with the module dir as working dir; walk up to the repo root.
    private fun manifestFile(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        return File(dir, "docs/settings/settings-manifest.json")
    }

    @Test
    fun `manifest scan yields entries`() {
        assertTrue("Settings scan produced no manifest entries", buildManifest().isNotEmpty())
    }

    /**
     * Transient permission-prompt buttons are de-indexed at the source (S0604): they are GONE
     * whenever the permission is already granted, so indexing them yields dead search results.
     */
    @Test
    fun `transient permission-prompt buttons are not indexed`() {
        val keys = buildManifest().map { it.key }.toSet()
        assertFalse("btnNotificationPermission must be de-indexed (S0604)", "btnNotificationPermission" in keys)
        assertFalse("btnScheduledNotificationPermission must be de-indexed (S0604)", "btnScheduledNotificationPermission" in keys)
    }

    /**
     * Verify mode (default): fails if `docs/settings/settings-manifest.json` is missing or stale.
     * Generate mode (`-Dsettings.manifest.generate=true`): (re)writes the committed file instead.
     */
    @Test
    fun `committed manifest is fresh`() {
        val expected = SettingsManifestSerializer.serialize(buildManifest())
        val file = manifestFile()
        if (System.getProperty("settings.manifest.generate") == "true") {
            file.parentFile?.mkdirs()
            file.writeText(expected, Charsets.UTF_8)
            return
        }
        assertTrue(
            "docs/settings/settings-manifest.json missing - run with -Dsettings.manifest.generate=true",
            file.exists()
        )
        assertEquals(
            "docs/settings/settings-manifest.json is stale - regenerate with -Dsettings.manifest.generate=true",
            expected,
            file.readText(Charsets.UTF_8)
        )
    }
}
