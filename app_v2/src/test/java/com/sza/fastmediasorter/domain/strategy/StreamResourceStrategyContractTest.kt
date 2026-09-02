package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2041: pins which fields the stream schema exposes. Without this the schema can drift back to a
 * folder's shape - scanning switches, media types, a destination flag - with nothing failing.
 */
class StreamResourceStrategyContractTest : ResourceEditorContractTestBase() {

    private lateinit var strategy: StreamResourceStrategy

    @Before
    fun setUp() {
        strategy = StreamResourceStrategy()
    }

    // --- Field schema: what a stream has ---

    @Test
    fun `fieldSchema - NAME is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.NAME)
    }

    @Test
    fun `fieldSchema - PATH is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.PATH)
    }

    @Test
    fun `fieldSchema - PATH is rendered`() {
        assertVisible(strategy.fieldSchema(), ResourceFieldKey.PATH)
    }

    @Test
    fun `fieldSchema - COMMENT is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.COMMENT)
    }

    @Test
    fun `fieldSchema - ACCESS_PIN is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.ACCESS_PIN)
    }

    @Test
    fun `fieldSchema - the editor renders exactly name, path, comment and pin`() {
        val visibleKeys = strategy.fieldSchema().filter { it.visible }.map { it.key }.toSet()
        assertEquals(
            setOf(
                ResourceFieldKey.NAME,
                ResourceFieldKey.PATH,
                ResourceFieldKey.COMMENT,
                ResourceFieldKey.ACCESS_PIN
            ),
            visibleKeys
        )
    }

    // --- Field schema: folder fields absent, not merely disabled ---

    @Test
    fun `fieldSchema - MEDIA_TYPES is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.MEDIA_TYPES)
    }

    @Test
    fun `fieldSchema - SCAN_SUBDIRECTORIES is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.SCAN_SUBDIRECTORIES)
    }

    @Test
    fun `fieldSchema - ALL_FILES is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.ALL_FILES)
    }

    @Test
    fun `fieldSchema - SHOW_HIDDEN_FILES is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.SHOW_HIDDEN_FILES)
    }

    @Test
    fun `fieldSchema - SHOW_SUBFOLDERS_AS_ITEMS is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.SHOW_SUBFOLDERS_AS_ITEMS)
    }

    @Test
    fun `fieldSchema - DISABLE_THUMBNAILS is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.DISABLE_THUMBNAILS)
    }

    @Test
    fun `fieldSchema - IS_DESTINATION is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.IS_DESTINATION)
    }

    @Test
    fun `fieldSchema - IS_READ_ONLY is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.IS_READ_ONLY)
    }

    @Test
    fun `fieldSchema - SLIDESHOW_INTERVAL is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.SLIDESHOW_INTERVAL)
    }

    @Test
    fun `fieldSchema - HOST is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.HOST)
    }

    @Test
    fun `fieldSchema - PORT is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.PORT)
    }

    @Test
    fun `fieldSchema - USERNAME is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.USERNAME)
    }

    @Test
    fun `fieldSchema - PASSWORD is absent for a stream`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.PASSWORD)
    }

    // --- Validation ---

    @Test
    fun `validate - blank name produces NAME error`() {
        val result = strategy.validate(streamForm(name = ""))
        assertFalse("Expected invalid result", result.isValid)
        assertTrue("Expected NAME error", result.fieldErrors.containsKey(ResourceFieldKey.NAME))
    }

    @Test
    fun `validate - blank path produces PATH error`() {
        val result = strategy.validate(streamForm(path = ""))
        assertFalse("Expected invalid result", result.isValid)
        assertTrue("Expected PATH error", result.fieldErrors.containsKey(ResourceFieldKey.PATH))
    }

    @Test
    fun `validate - empty mediaTypes with allFiles false is still valid`() {
        val result = strategy.validate(streamForm(mediaTypes = emptySet(), allFiles = false))
        assertTrue("A stream has no media-type rule to satisfy", result.isValid)
    }

    @Test
    fun `validate - an rtsp form is valid`() {
        val result = strategy.validate(
            streamForm(type = ResourceType.RTSP_STREAM, path = "rtsp://example.com/live")
        )
        assertTrue("Expected valid result", result.isValid)
        assertTrue("Expected no field errors", result.fieldErrors.isEmpty())
    }

    // --- Normalization ---

    @Test
    fun `normalizeBeforeSave - preserves the url and clears credentials`() {
        val normalized = strategy.normalizeBeforeSave(
            streamForm(name = "  Radio  ", path = "  http://example.com/live.m3u8  ")
                .copy(host = "example.com", username = "u", password = "p", port = 8080)
        )
        assertEquals("Radio", normalized.name)
        assertEquals("http://example.com/live.m3u8", normalized.path)
        assertEquals("", normalized.host)
        assertEquals("", normalized.username)
        assertEquals("", normalized.password)
        assertNull(normalized.port)
    }

    // --- Connection test ---

    @Test
    fun `testConnection - declines rather than probing the server`() = runTest {
        val result = strategy.testConnection(streamForm())
        assertEquals(ResourceConnectionStatus.NOT_SUPPORTED, result.status)
    }
}
