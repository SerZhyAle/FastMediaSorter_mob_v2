package com.sza.fastmediasorter.wear

import com.google.gson.JsonParser
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

/**
 * S3358: the declared pre-release walk, read back so a flavor test can hold it to the catalogs.
 *
 * `scripts/devtest/wear-prerelease-screens.json` says which Home section or Apps program each walked
 * entry depends on and which flavors it is walkable in. Nothing checked that the second claim follows
 * from the first: S3178 put twelve of those rows behind a `WearRestrictedCapabilities` answer the store
 * build returns false for, the list went on declaring them, and the next standard sweep spent eighteen
 * `unreachable` verdicts on screens that were never in the artifact - measured 2026-09-20 on
 * emulator-5556 against wear-standard-release 2.60.9202.109.
 *
 * Here rather than in `assert-wear-walk-contract.ps1` because the answer has to come from the catalogs
 * RUNNING, with the flavor's own capabilities bound. A PowerShell re-reading of `HomeSectionCatalog`
 * would be a second implementation of the rule it is checking, and a second implementation is what the
 * screen list already was.
 *
 * In the shared test set with a flavor-scoped caller on each side: the subject is the answer a flavor
 * gives, and only `wear/src/testStandard` and `wear/src/testNoLegal` can name their own binding.
 */
object WearWalkContract {

    /**
     * One walked entry, reduced to the fields that decide its scope.
     *
     * [flavors] is null when the entry declares none, which means every flavor - the state of the
     * settings block and of the programs no capability gates.
     */
    data class Entry(
        val id: String,
        val homeSection: HomeSectionId?,
        val wearApp: WearAppId?,
        val flavors: List<String>?
    ) {
        fun isWalkableIn(flavor: String): Boolean = flavors == null || flavors.contains(flavor)
    }

    /**
     * Every entry of `screens[]`, in declaration order.
     *
     * Fails rather than returns empty when the file is missing or carries no entry: a reader that
     * quietly finds nothing turns every assertion built on it into a green statement about nothing,
     * which is the same silence this whole check exists to end.
     */
    fun entries(): List<Entry> {
        val file = File(repoRoot(), SCREEN_LIST)
        assertTrue("the declared walk is not at $SCREEN_LIST", file.isFile)
        val screens = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonArray("screens")
        assertTrue("$SCREEN_LIST declares no walked entry", screens.size() > 0)
        return screens.map { element ->
            val entry = element.asJsonObject
            Entry(
                id = entry.get("id").asString,
                homeSection = entry.get("homeSection")?.asString?.let { HomeSectionId.valueOf(it) },
                wearApp = entry.get("wearApp")?.asString?.let { WearAppId.valueOf(it) },
                flavors = entry.getAsJsonArray("flavors")?.map { it.asString }
            )
        }
    }

    /**
     * The declared scope against what the two catalogs emit in this flavor, in both directions.
     *
     * An entry is walkable here exactly when the row it depends on is drawn here. The reverse half is
     * what catches the original fault: a row that stops being drawn while the entry still claims it.
     *
     * [sections] and [apps] are passed in rather than computed here because the capabilities that
     * produce them live in the flavor source sets, which this file cannot see - and constructing one
     * would prove nothing about which implementation the build binds.
     */
    fun assertScopeMatchesCatalogs(
        flavor: String,
        sections: List<HomeSectionId>,
        apps: List<WearAppId>
    ) {
        val entries = entries()
        val bound = entries.filter { it.homeSection != null || it.wearApp != null }
        assertTrue("no walked entry names a catalog row, so this check decides nothing", bound.isNotEmpty())
        bound.forEach { entry ->
            val walkable = entry.isWalkableIn(flavor)
            entry.homeSection?.let { section ->
                assertEquals(
                    "${entry.id}: the walk declares it ${scopeWord(walkable)} in $flavor, " +
                        "HomeSectionCatalog ${drawnWord(sections.contains(section))} $section there",
                    sections.contains(section),
                    walkable
                )
            }
            entry.wearApp?.let { app ->
                assertEquals(
                    "${entry.id}: the walk declares it ${scopeWord(walkable)} in $flavor, " +
                        "WearAppCatalog ${drawnWord(apps.contains(app))} $app there",
                    apps.contains(app),
                    walkable
                )
            }
        }
    }

    private fun scopeWord(walkable: Boolean) = if (walkable) "walkable" else "withheld"

    private fun drawnWord(drawn: Boolean) = if (drawn) "draws" else "does not draw"

    /**
     * The checkout root, found by the file that marks it. A unit test runs with the module directory as
     * its working directory, and hard-coding one level up breaks the moment the module moves.
     */
    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = requireNotNull(dir.parentFile) { "no settings.gradle.kts above ${dir.absolutePath}" }
        }
        return dir
    }

    private const val SCREEN_LIST = "scripts/devtest/wear-prerelease-screens.json"
}
