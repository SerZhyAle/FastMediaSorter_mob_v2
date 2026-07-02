package com.sza.fastmediasorter.docs

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.core.panel.ResourceTypeIconMap
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.di.ShareTargetModule
import com.sza.fastmediasorter.ui.player.helpers.CommandPanelLayoutPlanner.PlayerCommand
import com.sza.fastmediasorter.ui.settings.search.XmlAttributeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.lang.reflect.Modifier

/**
 * One record of `docs/icons/icon-inventory.json` (S0815 Phase 1).
 *
 * [public] is `false` for icons compiled into shared `src/main` but only runtime-visible in
 * noLegal (the VR set, [IconInventoryExportTest.VR_DRAWABLE]); everything else is `true`.
 */
data class IconInventoryEntry(
    val surface: String,
    val key: String,
    val feature: String,
    val drawable: String,
    val assetFormat: String,
    val public: Boolean
)

/**
 * Deterministic serializer for the icon inventory: entries sorted by `surface` then `key` (then
 * `feature`/`drawable` as stable tiebreakers), fixed key order per object, 2-space indent, trailing
 * newline. Hand-rolled rather than `org.json` because `JSONObject` does not guarantee key order,
 * which would make the committed file's byte-diffs meaningless for the freshness gate (mirrors
 * `SettingsManifestSerializer`).
 */
object IconInventorySerializer {

    fun serialize(entries: List<IconInventoryEntry>): String {
        val sorted = entries.distinct()
            .sortedWith(compareBy({ it.surface }, { it.key }, { it.feature }, { it.drawable }))
        val sb = StringBuilder()
        sb.append("[\n")
        sorted.forEachIndexed { index, e ->
            sb.append("  {\n")
            sb.append("    \"surface\": ").append(quote(e.surface)).append(",\n")
            sb.append("    \"key\": ").append(quote(e.key)).append(",\n")
            sb.append("    \"feature\": ").append(quote(e.feature)).append(",\n")
            sb.append("    \"drawable\": ").append(quote(e.drawable)).append(",\n")
            sb.append("    \"assetFormat\": ").append(quote(e.assetFormat)).append(",\n")
            sb.append("    \"public\": ").append(e.public).append("\n")
            sb.append("  }")
            sb.append(if (index < sorted.lastIndex) ",\n" else "\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}

/**
 * Generates and verifies `docs/icons/icon-inventory.json` (S0815 Phase 1).
 *
 * The inventory is scanned from the SAME registries the app ships - `InternalRouteCatalog`,
 * `OsShortcutCatalog`, `ResourceTypeIconMap`, the `ShareTargetModule` providers, `PlayerCommand`,
 * and the compiled `fragment_settings_*` layouts - so it cannot silently drift from the shipped UI.
 * Phase 2 (SVG export) consumes the committed JSON.
 *
 * Verify mode (default): fails if the committed file is missing or stale. Generate mode
 * (`-Dicon.inventory.generate=true`): (re)writes it. Mirrors `SettingsManifestExportTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconInventoryExportTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val repoRoot: File = findRepoRoot()

    // Framework drawable ids are frozen across SDK levels, so reflecting android.R.drawable once
    // gives a stable int->name map without depending on Robolectric's resource tables.
    private val frameworkDrawableNames: Map<Int, String> = buildFrameworkDrawableNames()

    private fun buildInventory(): List<IconInventoryEntry> {
        val out = mutableListOf<IconInventoryEntry>()
        collectProgramNav(out)
        collectSendTo(out)
        collectPlayerCommands(out)
        collectSettings(out)
        return out
    }

    // Program navigation merges three registries into one surface (S0663): internal feature routes,
    // curated OS shortcuts, and the resource-type tiles.
    private fun collectProgramNav(out: MutableList<IconInventoryEntry>) {
        InternalRouteCatalog.all().forEach { route ->
            out += iconEntry(SURFACE_PROGRAM_NAV, route.key, stringName(route.labelRes), route.iconRes)
        }
        OsShortcutCatalog.all().forEach { target ->
            out += iconEntry(SURFACE_PROGRAM_NAV, target.key, stringName(target.labelRes), target.iconRes)
        }
        ResourceTypeIconMap.entries.forEach { (type, iconRes) ->
            out += iconEntry(SURFACE_PROGRAM_NAV, type.name, type.name, iconRes)
        }
    }

    private fun collectSendTo(out: MutableList<IconInventoryEntry>) {
        shareTargets().forEach { target ->
            val iconRes = target.iconRes ?: return@forEach
            out += iconEntry(SURFACE_SEND_TO, target.id, stringName(target.titleRes), iconRes)
        }
    }

    private fun collectPlayerCommands(out: MutableList<IconInventoryEntry>) {
        PlayerCommand.entries.forEach { command ->
            val iconRes = command.iconResId
            if (iconRes == 0) return@forEach // 0 = icon set dynamically; no static drawable to inventory
            out += iconEntry(SURFACE_PLAYER_COMMAND, command.name, stringName(command.titleResId), iconRes)
        }
    }

    // Section headers (csh_icon) and toggle/selection rows (str_icon/ssr_icon) live in the compiled
    // fragment_settings_* layouts. getXml resolves the portrait variant under Robolectric's default
    // orientation, so layout-land (a mirror) is intentionally not scanned.
    private fun collectSettings(out: MutableList<IconInventoryEntry>) {
        for (layoutId in settingsLayoutIds()) {
            val parser = context.resources.getXml(layoutId)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) readSettingsElement(parser, out)
                event = parser.next()
            }
        }
    }

    private fun readSettingsElement(parser: XmlResourceParser, out: MutableList<IconInventoryEntry>) {
        val viewId = XmlAttributeReader.attrId(parser)
        addIconPair(parser, viewId, "csh_icon", "csh_title", SURFACE_SETTINGS_HEADER, out)
        addIconPair(parser, viewId, "str_icon", "str_title", SURFACE_SETTINGS_ROW, out)
        addIconPair(parser, viewId, "ssr_icon", "ssr_title", SURFACE_SETTINGS_ROW, out)
    }

    private fun addIconPair(
        parser: XmlResourceParser,
        viewId: Int?,
        iconAttr: String,
        titleAttr: String,
        surface: String,
        out: MutableList<IconInventoryEntry>
    ) {
        val iconRes = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, iconAttr) ?: return
        val titleRes = XmlAttributeReader.attrResourceValue(parser, XmlAttributeReader.APP_NS, titleAttr)
        val feature = titleRes?.let { stringName(it) }
            ?: viewId?.let { resourceEntryName(it) }
            ?: iconAttr
        val key = viewId?.let { resourceEntryName(it) } ?: feature
        out += iconEntry(surface, key, feature, iconRes)
    }

    private fun iconEntry(surface: String, key: String, feature: String, iconRes: Int): IconInventoryEntry {
        val framework = isFrameworkResource(iconRes)
        val drawable = drawableName(iconRes, framework)
        val assetFormat = if (framework) ASSET_FRAMEWORK else appAssetFormat(drawable)
        return IconInventoryEntry(
            surface = surface,
            key = key,
            feature = feature,
            drawable = drawable,
            assetFormat = assetFormat,
            public = drawable != VR_DRAWABLE
        )
    }

    // Package id sits in the high byte of the resource id: 0x01 = android framework, 0x7f = app.
    private fun isFrameworkResource(id: Int): Boolean =
        (id ushr RESOURCE_PACKAGE_ID_SHIFT) == ANDROID_PACKAGE_ID

    private fun drawableName(id: Int, framework: Boolean): String {
        val frameworkName = if (framework) frameworkDrawableNames[id] else null
        return frameworkName ?: resourceEntryName(id)
    }

    private fun appAssetFormat(name: String): String = when {
        resFileExists("$name.xml") -> ASSET_VECTOR
        RASTER_EXTENSIONS.any { resFileExists("$name.$it") } -> ASSET_RASTER
        // The in-scope surfaces are all-vector; an app drawable not found on disk is still a vector
        // (defensive default, not reached by the current registries).
        else -> ASSET_VECTOR
    }

    private fun resFileExists(fileName: String): Boolean {
        val buckets = File(repoRoot, "app_v2/src/main/res")
            .listFiles { f -> f.isDirectory && f.name.startsWith("drawable") }
            ?: return false
        return buckets.any { File(it, fileName).exists() }
    }

    private fun stringName(resId: Int): String = resourceEntryName(resId)

    private fun resourceEntryName(id: Int): String = try {
        context.resources.getResourceEntryName(id)
    } catch (_: Resources.NotFoundException) {
        "res_0x${Integer.toHexString(id)}"
    }

    // The Send-to receivers are zero-arg @Provides funcs on ShareTargetModule.Companion, callable
    // without a Hilt graph. Reflecting them (vs hard-coding 10 names) keeps the scan drift-proof if
    // a receiver is added.
    private fun shareTargets(): List<ShareTarget> {
        val companion = ShareTargetModule.Companion
        return companion::class.java.declaredMethods
            .filter { it.parameterCount == 0 && it.returnType == ShareTarget::class.java }
            .map { method ->
                method.isAccessible = true
                method.invoke(companion) as ShareTarget
            }
    }

    private fun settingsLayoutIds(): List<Int> =
        R.layout::class.java.fields
            .filter { it.name.startsWith(SETTINGS_LAYOUT_PREFIX) }
            .map { it.getInt(null) }

    private fun buildFrameworkDrawableNames(): Map<Int, String> {
        val intType = Int::class.javaPrimitiveType
        return android.R.drawable::class.java.fields
            .filter { Modifier.isStatic(it.modifiers) && it.type == intType }
            .associate { it.getInt(null) to it.name }
    }

    // Gradle runs unit tests with the module dir as working dir; walk up to the repo root.
    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        return dir
    }

    private fun inventoryFile(): File = File(repoRoot, "docs/icons/icon-inventory.json")

    @Test
    fun `inventory scan yields entries`() {
        assertTrue("Icon scan produced no inventory entries", buildInventory().isNotEmpty())
    }

    @Test
    fun `committed icon inventory is fresh`() {
        val expected = IconInventorySerializer.serialize(buildInventory())
        val file = inventoryFile()
        if (System.getProperty(GENERATE_FLAG) == "true") {
            file.parentFile?.mkdirs()
            file.writeText(expected, Charsets.UTF_8)
            return
        }
        assertTrue(
            "docs/icons/icon-inventory.json missing - run with -D$GENERATE_FLAG=true",
            file.exists()
        )
        assertEquals(
            "docs/icons/icon-inventory.json is stale - regenerate with -D$GENERATE_FLAG=true",
            expected,
            file.readText(Charsets.UTF_8)
        )
    }

    private companion object {
        const val GENERATE_FLAG = "icon.inventory.generate"
        const val SETTINGS_LAYOUT_PREFIX = "fragment_settings_"

        // noLegal-only VR glyph: compiled into src/main but runtime-visible only when SUPPORT_VR_PLAYER.
        const val VR_DRAWABLE = "ic_vr_headset"

        const val RESOURCE_PACKAGE_ID_SHIFT = 24
        const val ANDROID_PACKAGE_ID = 0x01

        const val SURFACE_PROGRAM_NAV = "program-nav"
        const val SURFACE_SEND_TO = "send-to"
        const val SURFACE_PLAYER_COMMAND = "player-command"
        const val SURFACE_SETTINGS_HEADER = "settings-header"
        const val SURFACE_SETTINGS_ROW = "settings-row"

        const val ASSET_VECTOR = "vector"
        const val ASSET_RASTER = "raster"
        const val ASSET_FRAMEWORK = "framework"

        val RASTER_EXTENSIONS = listOf("png", "webp", "jpg", "jpeg")
    }
}
