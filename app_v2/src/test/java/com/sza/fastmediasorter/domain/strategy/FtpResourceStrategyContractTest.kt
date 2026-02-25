package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A2-T5/T7/T8/T9: Field schema and validation contract tests for FTP strategy.
 *
 * Key difference from SMB/SFTP: PATH is optional in FTP schema and not validated as required.
 */
class FtpResourceStrategyContractTest : ResourceEditorContractTestBase() {

    private lateinit var strategy: FtpResourceStrategy

    @Before
    fun setUp() {
        strategy = FtpResourceStrategy()
    }

    // --- Field schema: required fields ---

    @Test
    fun `fieldSchema - NAME is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.NAME)
    }

    @Test
    fun `fieldSchema - HOST is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.HOST)
    }

    // --- Field schema: optional fields (FTP differs from SMB/SFTP: PATH is optional) ---

    @Test
    fun `fieldSchema - PATH is optional for FTP`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.PATH)
    }

    @Test
    fun `fieldSchema - PORT is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.PORT)
    }

    @Test
    fun `fieldSchema - USERNAME is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.USERNAME)
    }

    @Test
    fun `fieldSchema - PASSWORD is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.PASSWORD)
    }

    // --- Field schema: cloud fields absent (A2-T7) ---

    @Test
    fun `fieldSchema - CLOUD_PROVIDER is absent for FTP type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.CLOUD_PROVIDER)
    }

    @Test
    fun `fieldSchema - CLOUD_FOLDER is absent for FTP type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.CLOUD_FOLDER)
    }

    // --- Validation (A2-T8) ---

    @Test
    fun `validate - blank name produces NAME error`() {
        val result = strategy.validate(networkForm(ResourceType.FTP, name = ""))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.NAME))
    }

    @Test
    fun `validate - blank host produces HOST error`() {
        val result = strategy.validate(networkForm(ResourceType.FTP, host = ""))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.HOST))
    }

    @Test
    fun `validate - blank path is valid (FTP allows empty path)`() {
        val result = strategy.validate(networkForm(ResourceType.FTP, path = ""))
        assertTrue("FTP path is optional — blank path must be valid", result.isValid)
    }

    // --- Port validation (A2-T9) ---

    @Test
    fun `validate - port null accepted`() {
        val result = strategy.validate(networkForm(ResourceType.FTP, port = null))
        assertTrue("port=null should be accepted", result.isValid)
    }

    @Test
    fun `validate - port 21 is valid`() {
        val result = strategy.validate(networkForm(ResourceType.FTP, port = 21))
        assertTrue("Standard FTP port 21 should be valid", result.isValid)
    }

    @Test
    fun `validate - port 0 produces PORT error`() {
        val result = strategy.validate(networkForm(ResourceType.FTP, port = 0))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.PORT))
    }

    @Test
    fun `validate - port 65536 produces PORT error`() {
        val result = strategy.validate(networkForm(ResourceType.FTP, port = 65536))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.PORT))
    }

    @Test
    fun `validate - valid FTP form produces no errors`() {
        val result = strategy.validate(networkForm(ResourceType.FTP))
        assertTrue("Expected valid result", result.isValid)
    }
}
