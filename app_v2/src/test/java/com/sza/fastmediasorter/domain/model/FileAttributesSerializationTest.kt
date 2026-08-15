package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * S1660: FileAttributes travels inside the cached file list as MediaFile.attributes, so a blob written
 * by one release is read back by the next one. Its field names are therefore a wire format, and these
 * tests are what makes that format break loudly instead of silently: an R8 rename or a refactor that
 * drops the annotation turns a cached row into default attributes, which reads as "nothing is hidden or
 * read-only any more" rather than as an error.
 */
class FileAttributesSerializationTest {

    private val gson = Gson()

    @Test
    fun `every persisted field pins its wire name`() {
        // Static fields are skipped for the same reason Gson skips them: they are not instance state.
        // The Compose compiler puts a `$stable` static on every class it sees, and it is not persisted.
        val unpinned = FileAttributes::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .filter { it.getAnnotation(SerializedName::class.java) == null }
            .map { it.name }

        assertEquals("fields without @SerializedName", emptyList<String>(), unpinned)
    }

    @Test
    fun `serialized form uses the documented keys`() {
        val json = JsonParser.parseString(gson.toJson(FileAttributes(readOnly = true, hidden = true)))
            .asJsonObject

        assertEquals(setOf("readOnly", "hidden"), json.keySet())
        assertTrue(json["readOnly"].asBoolean)
        assertTrue(json["hidden"].asBoolean)
    }

    @Test
    fun `round trip preserves both flags`() {
        val original = FileAttributes(readOnly = true, hidden = false)

        val restored = gson.fromJson(gson.toJson(original), FileAttributes::class.java)

        assertEquals(original, restored)
    }

    @Test
    fun `a blob written in the current format still parses`() {
        // Written by hand rather than by toJson on purpose: a literal is what a previous release left
        // in the cache, so it keeps proving compatibility even if the writer changes.
        val stored = """{"readOnly":true,"hidden":true}"""

        val restored = gson.fromJson(stored, FileAttributes::class.java)

        assertEquals(FileAttributes(readOnly = true, hidden = true), restored)
    }

    @Test
    fun `an absent field falls back to its default rather than failing`() {
        val restored = gson.fromJson("""{"readOnly":true}""", FileAttributes::class.java)

        assertEquals(FileAttributes(readOnly = true, hidden = false), restored)
    }
}
