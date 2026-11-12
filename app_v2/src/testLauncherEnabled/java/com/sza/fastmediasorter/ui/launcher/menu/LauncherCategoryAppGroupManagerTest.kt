package com.sza.fastmediasorter.ui.launcher.menu

import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCategoryAppGroupManagerTest {
    private val manager = LauncherCategoryAppGroupManager()
    private val apps = listOf(
        InstalledApp("a", "A", 0, 0, 0, false, null),
        InstalledApp("b", "B", 0, 0, 0, false, null),
    )
    private val catalog = AppCategoryCatalog(
        listOf(AppCategory("user:one", "One", null)),
        emptyList(),
        mapOf("a" to setOf("user:one")),
        emptySet(),
    )

    private fun groups(expanded: Set<String>) = manager.groupApps(
        apps,
        catalog,
        expanded,
        null,
        { it.name.orEmpty() },
        "Other",
    )

    @Test
    fun `groups default to collapsed and retain category membership`() {
        val result = groups(emptySet())
        assertEquals(listOf("One", "Other"), result.map { it.title })
        assertEquals(listOf("a", "b"), result.map { it.apps.single().id })
        assertTrue(result.none { it.isExpanded })
    }

    @Test
    fun `only explicitly expanded categories open`() {
        val result = groups(setOf("category:user:one"))
        assertTrue(result.first().isExpanded)
        assertFalse(result.last().isExpanded)
    }
}
