package com.sza.fastmediasorter.core.panel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S4544: a sub-program with an off-switch must be guarded at its destination or exempt with a reason,
 * so a new switched program cannot ship with a pinned shortcut that ignores its switch.
 */
class ProgramScreenGuardCatalogCompletenessTest {

    private val guardedKeys: Set<String> =
        (ProgramScreenGuardCatalog.screens.values + ProgramScreenGuardCatalog.launchers.values).toSet()

    @Test
    fun `every switched sub-program is guarded or exempt with a reason`() {
        val uncovered = SubProgramCatalog.all()
            .filter { it.disable != null }
            .map { it.routeKey }
            .filterNot { key ->
                key in guardedKeys || !ProgramScreenGuardCatalog.exemptions[key].isNullOrBlank()
            }
        assertTrue("switched sub-programs with no guard and no exemption: $uncovered", uncovered.isEmpty())
    }

    @Test
    fun `no route is both guarded and exempt`() {
        val both = guardedKeys.intersect(ProgramScreenGuardCatalog.exemptions.keys)
        assertTrue("guarded and exempt at once: $both", both.isEmpty())
    }

    // A launcher acts inside onCreate, before the activity callbacks could stop it, so the admission call
    // has to be in its own source; a listed launcher without one would be guarded on paper only.
    @Test
    fun `every guarded launcher calls the guard in its own source`() {
        val silent = ProgramScreenGuardCatalog.launchers.keys.filterNot { launcher ->
            val path = "src/main/java/" + launcher.name.replace('.', '/') + ".kt"
            File(path).readText().contains("programScreenGuard.admit(")
        }
        assertTrue("guarded launchers that never call the guard: ${silent.map { it.simpleName }}", silent.isEmpty())
    }
}
