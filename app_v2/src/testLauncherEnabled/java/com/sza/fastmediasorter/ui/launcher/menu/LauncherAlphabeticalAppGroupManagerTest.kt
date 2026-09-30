package com.sza.fastmediasorter.ui.launcher.menu

import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3752: letters outside the contiguous U+0410..U+042F range - Russian Ё and Ukrainian Є, І, Ї, Ґ - used
 * to fall into the '#' group. Lives in `src/testLauncherEnabled` because the manager ships only there.
 */
class LauncherAlphabeticalAppGroupManagerTest {

    private val manager = LauncherAlphabeticalAppGroupManager()

    private fun app(label: String) = InstalledApp(
        packageName = "pkg.$label",
        label = label,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        category = 0,
        isSystemApp = false,
        iconFile = null,
    )

    private fun letterKeys(vararg labels: String): List<String> = manager
        .groupApps(labels.map(::app), columns = 4, expandedKeys = emptySet(), previewRows = 1)
        .filterNot { it.isPreview }
        .map { it.key }

    @Test
    fun `Russian and Ukrainian letters outside the basic range get their own group`() {
        val keys = letterKeys("Ёлка", "Інтернет", "їжак", "Єдність", "ґанок")

        assertTrue("no app may land in '#': $keys", "#" !in keys)
        assertEquals(setOf("Ё", "І", "Ї", "Є", "Ґ"), keys.toSet())
    }

    @Test
    fun `Cyrillic groups follow dictionary order after Latin and symbols`() {
        val keys = letterKeys("Яндекс", "Ёлка", "Ґанок", "Гугл", "Єдність", "Ешка", "Іра", "Ира", "Zoom", "123")

        assertEquals(listOf("#", "Z", "Г", "Ґ", "Е", "Ё", "Є", "И", "І", "Я"), keys)
    }

    @Test
    fun `the adapter colour check recognises every group letter`() {
        "АБВГҐДЕЁЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ".forEach { letter ->
            assertTrue("$letter", LauncherAlphabeticalAppGroupManager.isCyrillicLetter(letter))
        }
        assertTrue(!LauncherAlphabeticalAppGroupManager.isCyrillicLetter('A'))
    }
}
