package com.sza.fastmediasorter.ui.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * S3430: four ids drew the same picture as another id of their set, so the picker showed one icon
 * twice. The picker stops offering them, but a resource saved with one keeps its icon.
 */
class ResourceIconRegistryRetiredDuplicatesTest {

    private val survivorOf = mapOf(
        "ico-02-020" to "ico-02-007",
        "ico-03-016" to "ico-03-001",
        "ico-04-011" to "ico-04-003",
        "ico-04-020" to "ico-04-010",
    )

    @Test
    fun `the picker never offers a retired duplicate`() {
        val offered = ResourceIconSet.values().flatMap { ResourceIconRegistry.idsFor(it) }.toSet()
        survivorOf.keys.forEach { assertFalse(it, it in offered) }
    }

    @Test
    fun `a retired duplicate still resolves for a resource that saved it`() {
        survivorOf.keys.forEach { assertNotNull(it, ResourceIconRegistry.resolveDrawable(it)) }
    }

    @Test
    fun `the survivor of every retired duplicate is still offered`() {
        val offered = ResourceIconSet.values().flatMap { ResourceIconRegistry.idsFor(it) }.toSet()
        survivorOf.values.forEach { assertEquals(it, true, it in offered) }
    }
}
