package com.sza.fastmediasorter.golden

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3371 phase 06 step 06.5: golden proof that a route form already saved on a user's device still
 * resolves.
 *
 * WHAT "DEEP LINK" MEANS HERE. This app publishes no `fms://` URI scheme; its internal routes travel
 * as the namespaced strings two surfaces persist - a launcher desktop cell's `target`
 * ([LauncherCellCommand]) and an app-launch panel tile's `targetId`
 * ([AppLaunchPanelRouteTarget]) - and both end at [InternalRouteCatalog] by route key. Those strings
 * are the saved contract: a cell outlives the release that placed it, and a backup restores the
 * string verbatim. `golden/deeplinks/routes.txt` is the list, one form per line, with the resolution
 * each must still produce.
 *
 * WHERE THE OLDER FORMS COME FROM. Each is named by the production code that still reads it:
 * `fn:` survives because S1440 added `fns:` as a separate command rather than widening it;
 * `sec:everything_else` is the stored value S1744 kept while renaming the constant to `SECTION_MAIN`;
 * `sec:settings` is the S2735 persistence token a backup restores untouched; and `stream:<uuid>` is
 * the catalog-row payload that preceded S1832, which `Migration52To53` rewrote only where the row
 * still existed and the reader therefore still resolves.
 *
 * NOT COVERED, and why: the `route_<key>` id `AppShortcutsManager` gives a pinned shortcut is never
 * read back - the shortcut carries an Intent, not a parsed id - so there is no reader to hold to a
 * fixture. The `fmsbcast://import` intent link is a payload carrier, not a route, and is outside the
 * route catalog this step names.
 */
class DeepLinkGoldenTest {

    @Test
    fun `every stored form in the golden list resolves to its expected route`() {
        readRows().forEach { row ->
            assertEquals("${row.surface} | ${row.stored}", row.expected, resolve(row))
        }
    }

    @Test
    fun `every resolvable stored form re-encodes to exactly the string that was stored`() {
        readRows()
            .filterNot { it.expected == UNRESOLVABLE }
            .forEach { row ->
                val reEncoded = when (row.surface) {
                    SURFACE_CELL -> LauncherCellCommand.decode(row.stored)?.encode()
                    else -> AppLaunchPanelRouteTarget.decode(row.stored)?.encode()
                }
                assertEquals("${row.surface} | ${row.stored} did not survive a write-back", row.stored, reEncoded)
            }
    }

    @Test
    fun `every route key the golden list names is still in the internal route catalog`() {
        val keys = readRows()
            .map { it.expected }
            .filter { it.startsWith("$KIND_ROUTE:") || it.startsWith("$KIND_ROUTE_SECTION:") }
            .map { it.substringAfter(':').substringBefore(':') }
            .distinct()

        assertTrue("the golden list names no route at all", keys.isNotEmpty())
        keys.forEach { key ->
            assertNotNull(
                "InternalRouteCatalog no longer knows the saved route key '$key'",
                InternalRouteCatalog.byKey(key)
            )
        }
    }

    @Test
    fun `the other catalogs the golden list addresses still know their saved keys`() {
        readRows().map { it.expected }.forEach { expected ->
            when {
                expected.startsWith("$KIND_OS:") ->
                    assertNotNull(expected, OsShortcutCatalog.byKey(expected.substringAfter(':')))

                expected.startsWith("$KIND_ACTION:") ->
                    assertNotNull(expected, LauncherActionCatalog.byKey(expected.substringAfter(':')))
            }
        }
    }

    @Test
    fun `the current stream cell form is the identity this build derives from a channel address`() {
        // The fixture's post-S1832 payload is not a hand-written string: it is what
        // StreamChannelIdentity folds an https channel address into, which is what the cell stores.
        val identity = StreamChannelIdentity.of("https://cdn.example.org/live/ch1.m3u8")

        assertEquals("web://cdn.example.org/live/ch1.m3u8", identity)
        assertTrue(
            "the golden list must carry the identity form the current build writes",
            readRows().any { it.stored == "${LauncherCellCommand.PREFIX_STREAM}$identity" }
        )
    }

    private fun resolve(row: Row): String = when (row.surface) {
        SURFACE_CELL -> describeCell(LauncherCellCommand.decode(row.stored))
        else -> describePanel(AppLaunchPanelRouteTarget.decode(row.stored))
    }

    private fun describeCell(command: LauncherCellCommand?): String = when (command) {
        is LauncherCellCommand.Feature -> "$KIND_ROUTE:${command.routeKey}"
        is LauncherCellCommand.FeatureSection ->
            "$KIND_ROUTE_SECTION:${command.routeKey}:${command.sectionKey}"

        is LauncherCellCommand.Section -> "$KIND_SECTION:${command.sectionKey}"
        is LauncherCellCommand.Resource -> "$KIND_RESOURCE:${command.resourceId}:${command.mode.name}"
        is LauncherCellCommand.Stream -> "$KIND_STREAM:${command.identityKey}"
        is LauncherCellCommand.OsShortcut -> "$KIND_OS:${command.targetKey}"
        is LauncherCellCommand.LauncherAction -> "$KIND_ACTION:${command.actionKey}"
        is LauncherCellCommand.ScheduledOp -> "$KIND_SCHEDULED_OP:${command.operationId}"
        is LauncherCellCommand.FavoriteFile -> "$KIND_FAVORITE:${command.resourceId}:${command.filePath}"
        is LauncherCellCommand.App -> "$KIND_APP:${command.packageName}"
        else -> UNRESOLVABLE
    }

    private fun describePanel(target: AppLaunchPanelRouteTarget?): String = when (target) {
        is AppLaunchPanelRouteTarget.Feature -> "$KIND_ROUTE:${target.routeKey}"
        is AppLaunchPanelRouteTarget.FeatureSection ->
            "$KIND_ROUTE_SECTION:${target.routeKey}:${target.sectionKey}"

        is AppLaunchPanelRouteTarget.Resource -> "$KIND_RESOURCE:${target.resourceId}"
        is AppLaunchPanelRouteTarget.OsShortcut -> "$KIND_OS:${target.targetKey}"
        else -> UNRESOLVABLE
    }

    private fun readRows(): List<Row> {
        val path = "golden/deeplinks/routes.txt"
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Golden fixture $path is missing from the test resources"
        }.bufferedReader().use { it.readText() }

        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val columns = line.split(COLUMN_SEPARATOR)
                check(columns.size == COLUMN_COUNT) { "Malformed golden row: $line" }
                Row(columns[0].trim(), columns[1].trim(), columns[2].trim())
            }
            .toList()
    }

    private data class Row(val surface: String, val stored: String, val expected: String)

    private companion object {
        const val COLUMN_SEPARATOR = "|"
        const val COLUMN_COUNT = 3
        const val SURFACE_CELL = "cell"
        const val UNRESOLVABLE = "unresolvable"
        const val KIND_ROUTE = "route"
        const val KIND_ROUTE_SECTION = "route-section"
        const val KIND_SECTION = "section"
        const val KIND_RESOURCE = "resource"
        const val KIND_STREAM = "stream"
        const val KIND_OS = "os"
        const val KIND_ACTION = "action"
        const val KIND_SCHEDULED_OP = "scheduled-op"
        const val KIND_FAVORITE = "favorite"
        const val KIND_APP = "app"
    }
}
