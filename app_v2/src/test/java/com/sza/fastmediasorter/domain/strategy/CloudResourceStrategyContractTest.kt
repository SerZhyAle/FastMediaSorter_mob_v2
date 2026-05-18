package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A2-T6/T7/T8: Field schema visibility and validation contract tests for CLOUD strategy.
 */
class CloudResourceStrategyContractTest : ResourceEditorContractTestBase() {

    private lateinit var strategy: CloudResourceStrategy

    @Before
    fun setUp() {
        strategy = CloudResourceStrategy()
    }

    // --- Field schema: required fields ---

    @Test
    fun `fieldSchema - NAME is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.NAME)
    }

    @Test
    fun `fieldSchema - CLOUD_PROVIDER is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.CLOUD_PROVIDER)
    }

    // --- Field schema: optional fields ---

    @Test
    fun `fieldSchema - CLOUD_FOLDER is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.CLOUD_FOLDER)
    }

    @Test
    fun `fieldSchema - MEDIA_TYPES is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.MEDIA_TYPES)
    }

    @Test
    fun `fieldSchema - SCAN_SUBDIRECTORIES is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.SCAN_SUBDIRECTORIES)
    }

    @Test
    fun `fieldSchema - IS_DESTINATION is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.IS_DESTINATION)
    }

    // --- Field schema: network fields absent (A2-T7) ---

    @Test
    fun `fieldSchema - HOST is absent for CLOUD type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.HOST)
    }

    @Test
    fun `fieldSchema - PORT is absent for CLOUD type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.PORT)
    }

    @Test
    fun `fieldSchema - USERNAME is absent for CLOUD type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.USERNAME)
    }

    @Test
    fun `fieldSchema - PASSWORD is absent for CLOUD type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.PASSWORD)
    }

    // --- Validation (A2-T8) ---

    @Test
    fun `validate - blank name produces NAME error`() {
        val result = strategy.validate(cloudForm(name = ""))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.NAME))
    }

    @Test
    fun `validate - null cloudProvider produces CLOUD_PROVIDER error`() {
        val result = strategy.validate(cloudForm(cloudProvider = null))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.CLOUD_PROVIDER))
    }

    @Test
    fun `validate - null cloudFolderId and blank path produces CLOUD_FOLDER error`() {
        val form = cloudForm(cloudFolderId = null).copy(path = "")
        val result = strategy.validate(form)
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.CLOUD_FOLDER))
    }

    @Test
    fun `validate - null cloudFolderId but non-blank path is valid`() {
        val form = cloudForm(cloudFolderId = null).copy(path = "/photos")
        val result = strategy.validate(form)
        assertTrue("Non-blank path compensates missing cloudFolderId", result.isValid)
    }

    @Test
    fun `validate - empty mediaTypes and allFiles=false produces MEDIA_TYPES error`() {
        val result = strategy.validate(cloudForm(mediaTypes = emptySet(), allFiles = false))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.MEDIA_TYPES))
    }

    @Test
    fun `validate - empty mediaTypes but allFiles=true is valid`() {
        val result = strategy.validate(cloudForm(mediaTypes = emptySet(), allFiles = true))
        assertTrue("allFiles=true should bypass MEDIA_TYPES validation", result.isValid)
    }

    @Test
    fun `validate - OneDrive provider is accepted`() {
        val result = strategy.validate(cloudForm(cloudProvider = CloudProvider.ONEDRIVE))
        assertTrue("OneDrive provider must pass validation", result.isValid)
    }

    @Test
    fun `validate - Dropbox provider is accepted`() {
        val result = strategy.validate(cloudForm(cloudProvider = CloudProvider.DROPBOX))
        assertTrue("Dropbox provider must pass validation", result.isValid)
    }

    @Test
    fun `validate - valid Google Drive form produces no errors`() {
        val result = strategy.validate(cloudForm())
        assertTrue("Expected valid result", result.isValid)
        assertTrue("Expected no field errors", result.fieldErrors.isEmpty())
    }

    // --- A2-T11: Save enabled conditions ---

    @Test
    fun `validate - null provider and blank name should not be saveable`() {
        val result = strategy.validate(cloudForm(name = "", cloudProvider = null))
        assertFalse("Both NAME and CLOUD_PROVIDER missing - save must be disabled", result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.NAME))
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.CLOUD_PROVIDER))
    }
}
