package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocalResourceStrategyTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val strategy = LocalResourceStrategy()

    @Test
    fun `validate passes for a named path with media types`() {
        val result = strategy.validate(
            ResourceFormData(name = "Photos", path = "/sdcard/DCIM", supportedMediaTypes = setOf(MediaType.IMAGE)),
        )
        assertTrue(result.isValid)
        assertTrue(result.fieldErrors.isEmpty())
    }

    @Test
    fun `validate flags blank name path and empty media types`() {
        val result = strategy.validate(
            ResourceFormData(name = " ", path = "", supportedMediaTypes = emptySet(), allFiles = false),
        )
        assertFalse(result.isValid)
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.NAME])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.PATH])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.MEDIA_TYPES])
    }

    @Test
    fun `validate allows empty media types when allFiles is on`() {
        val result = strategy.validate(
            ResourceFormData(name = "All", path = "/sdcard", supportedMediaTypes = emptySet(), allFiles = true),
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `testConnection reports NOT_SUPPORTED for local resources`() = runTest {
        val result = strategy.testConnection(ResourceFormData(name = "x", path = "/x"))
        assertEquals(ResourceConnectionStatus.NOT_SUPPORTED, result.status)
    }

    @Test
    fun `normalizeBeforeSave trims name and path and clears network fields`() {
        val result = strategy.normalizeBeforeSave(
            ResourceFormData(
                name = "  Photos  ",
                path = "  /sdcard/DCIM  ",
                host = "ignored",
                username = "u",
                password = "p",
                port = 21,
            ),
        )
        assertEquals("Photos", result.name)
        assertEquals("/sdcard/DCIM", result.path)
        assertEquals("", result.host)
        assertEquals("", result.username)
        assertEquals("", result.password)
        assertEquals(null, result.port)
    }

    @Test
    fun `fieldSchema marks name and path required`() {
        val schema = strategy.fieldSchema().associateBy { it.key }
        assertTrue(schema.getValue(ResourceFieldKey.NAME).required)
        assertTrue(schema.getValue(ResourceFieldKey.PATH).required)
        assertFalse(schema.getValue(ResourceFieldKey.MEDIA_TYPES).required)
    }
}
