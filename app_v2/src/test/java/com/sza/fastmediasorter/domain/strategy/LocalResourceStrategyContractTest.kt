package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A2-T4/T7/T8: Field schema visibility and validation contract tests for LOCAL strategy.
 */
class LocalResourceStrategyContractTest : ResourceEditorContractTestBase() {

    private lateinit var strategy: LocalResourceStrategy

    @Before
    fun setUp() {
        strategy = LocalResourceStrategy()
    }

    // --- Field schema: required fields ---

    @Test
    fun `fieldSchema - NAME is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.NAME)
    }

    @Test
    fun `fieldSchema - PATH is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.PATH)
    }

    // --- Field schema: optional fields ---

    @Test
    fun `fieldSchema - MEDIA_TYPES is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.MEDIA_TYPES)
    }

    @Test
    fun `fieldSchema - SCAN_SUBDIRECTORIES is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.SCAN_SUBDIRECTORIES)
    }

    @Test
    fun `fieldSchema - ALL_FILES is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.ALL_FILES)
    }

    @Test
    fun `fieldSchema - IS_DESTINATION is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.IS_DESTINATION)
    }

    @Test
    fun `fieldSchema - COMMENT is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.COMMENT)
    }

    // --- Field schema: network/cloud fields absent (A2-T7) ---

    @Test
    fun `fieldSchema - HOST is absent for LOCAL type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.HOST)
    }

    @Test
    fun `fieldSchema - PORT is absent for LOCAL type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.PORT)
    }

    @Test
    fun `fieldSchema - USERNAME is absent for LOCAL type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.USERNAME)
    }

    @Test
    fun `fieldSchema - PASSWORD is absent for LOCAL type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.PASSWORD)
    }

    @Test
    fun `fieldSchema - CLOUD_PROVIDER is absent for LOCAL type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.CLOUD_PROVIDER)
    }

    @Test
    fun `fieldSchema - CLOUD_FOLDER is absent for LOCAL type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.CLOUD_FOLDER)
    }

    // --- Validation: required field errors (A2-T8) ---

    @Test
    fun `validate - blank name produces NAME error`() {
        val result = strategy.validate(localForm(name = ""))
        assertFalse("Expected invalid result", result.isValid)
        assertTrue("Expected NAME error", result.fieldErrors.containsKey(ResourceFieldKey.NAME))
    }

    @Test
    fun `validate - whitespace-only name produces NAME error`() {
        val result = strategy.validate(localForm(name = "   "))
        assertFalse("Expected invalid result", result.isValid)
        assertTrue("Expected NAME error", result.fieldErrors.containsKey(ResourceFieldKey.NAME))
    }

    @Test
    fun `validate - blank path produces PATH error`() {
        val result = strategy.validate(localForm(path = ""))
        assertFalse("Expected invalid result", result.isValid)
        assertTrue("Expected PATH error", result.fieldErrors.containsKey(ResourceFieldKey.PATH))
    }

    @Test
    fun `validate - empty mediaTypes and allFiles=false produces MEDIA_TYPES error`() {
        val result = strategy.validate(localForm(mediaTypes = emptySet(), allFiles = false))
        assertFalse("Expected invalid result", result.isValid)
        assertTrue("Expected MEDIA_TYPES error", result.fieldErrors.containsKey(ResourceFieldKey.MEDIA_TYPES))
    }

    @Test
    fun `validate - empty mediaTypes but allFiles=true is valid`() {
        val result = strategy.validate(localForm(mediaTypes = emptySet(), allFiles = true))
        assertTrue("Expected valid result when allFiles=true", result.isValid)
    }

    @Test
    fun `validate - blank name and blank path produces errors for both fields`() {
        val result = strategy.validate(localForm(name = "", path = ""))
        assertFalse("Expected invalid result", result.isValid)
        assertTrue("Expected NAME error", result.fieldErrors.containsKey(ResourceFieldKey.NAME))
        assertTrue("Expected PATH error", result.fieldErrors.containsKey(ResourceFieldKey.PATH))
    }

    @Test
    fun `validate - valid form produces no errors`() {
        val result = strategy.validate(localForm())
        assertTrue("Expected valid result", result.isValid)
        assertTrue("Expected no field errors", result.fieldErrors.isEmpty())
    }

    @Test
    fun `validate - audio-only mediaType is valid`() {
        val result = strategy.validate(localForm(mediaTypes = setOf(MediaType.AUDIO)))
        assertTrue("Expected valid result with AUDIO mediaType", result.isValid)
    }

    // --- A2-T11: Save enabled / disabled conditions ---

    @Test
    fun `validate - empty form should not be saveable`() {
        val emptyForm = localForm(name = "", path = "")
        val result = strategy.validate(emptyForm)
        assertFalse("Save must be disabled for empty form", result.isValid)
    }

    @Test
    fun `validate - fully filled form should be saveable`() {
        val fullForm = localForm(
            name = "Camera",
            path = "/storage/emulated/0/DCIM/Camera",
            mediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO)
        )
        val result = strategy.validate(fullForm)
        assertTrue("Save must be enabled for valid form", result.isValid)
    }
}
