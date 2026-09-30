package com.sza.fastmediasorter.ui.icon

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * S3070: an icon id is a machine key. On an `ar`/`bn`/`ur` device the default locale renders `%02d`
 * in native digits, so every id built from it missed the registry - the icon grid came up empty and
 * `randomIdFor` crashed on the empty list. These tests pin the id shape to the locale of the device.
 */
class ResourceIconRegistryLocaleTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    /** Without this the three tests below would pass on a JVM that renders `%02d` in ASCII anyway. */
    @Test
    fun `the chosen locale really does render digits natively`() {
        assertEquals("٠٥", "%02d".format(5))
    }

    @Test
    fun `idsFor returns the full set under a native-digit locale`() {
        ResourceIconSet.values().forEach { set ->
            val ids = ResourceIconRegistry.idsFor(set)
            val retired = ResourceIconRegistry.retiredDuplicates.count {
                it.startsWith(String.format(Locale.ROOT, "ico-%02d-", set.setId))
            }
            assertEquals("set ${set.name}", set.countInSet - retired, ids.size)
            assertTrue("set ${set.name}", ids.all { ResourceIconRegistry.isValid(it) })
        }
    }

    @Test
    fun `firstIdFor stays ASCII under a native-digit locale`() {
        assertEquals("ico-01-001", ResourceIconRegistry.firstIdFor(ResourceIconSet.MUSIC))
        assertEquals("ico-05-001", ResourceIconRegistry.firstIdFor(ResourceIconSet.OTHER))
    }

    @Test
    fun `randomIdFor yields a known id under a native-digit locale`() {
        val id = ResourceIconRegistry.randomIdFor(ResourceIconSet.OTHER)
        assertTrue(id, ResourceIconRegistry.isValid(id))
    }
}
