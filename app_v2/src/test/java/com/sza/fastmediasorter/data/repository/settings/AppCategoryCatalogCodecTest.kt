package com.sza.fastmediasorter.data.repository.settings

import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryCatalogCodecTest {

    @Test
    fun `a catalog survives a round trip`() {
        val catalog = AppCategoryCatalog(
            categories = listOf(
                AppCategory(id = "system:0", name = null, systemCategory = 0),
                AppCategory(id = "user:work", name = "Work", systemCategory = null),
            ),
            order = listOf("user:work", "system:0"),
            assignments = mapOf("com.slack" to setOf("user:work", "system:0")),
            seededPackages = setOf("com.slack", "com.game"),
        )

        assertEquals(catalog, AppCategoryCatalogCodec.decode(AppCategoryCatalogCodec.encode(catalog)))
    }

    @Test
    fun `null and blank input decode to an empty catalog`() {
        assertEquals(AppCategoryCatalog.EMPTY, AppCategoryCatalogCodec.decode(null))
        assertEquals(AppCategoryCatalog.EMPTY, AppCategoryCatalogCodec.decode("  "))
    }

    @Test
    fun `malformed input decodes to an empty catalog`() {
        assertEquals(AppCategoryCatalog.EMPTY, AppCategoryCatalogCodec.decode("{not json"))
    }

    @Test
    fun `ids naming no category are dropped`() {
        val json = """
            {"version":1,"categories":[{"id":"user:a","name":"A"}],
             "order":["user:missing","user:a"],
             "assignments":{"one.pkg":["user:missing"],"two.pkg":["user:a","user:missing"]},
             "seededPackages":["one.pkg"]}
        """.trimIndent()

        val decoded = AppCategoryCatalogCodec.decode(json)

        assertEquals(listOf("user:a"), decoded.order)
        assertEquals(mapOf("two.pkg" to setOf("user:a")), decoded.assignments)
        assertEquals(emptySet<String>(), decoded.categoryIdsOf("one.pkg"))
    }
}
