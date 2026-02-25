package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A2-T5/T7/T8/T9: Field schema and validation contract tests for SMB strategy.
 */
class SmbResourceStrategyContractTest : ResourceEditorContractTestBase() {

    private lateinit var strategy: SmbResourceStrategy

    @Before
    fun setUp() {
        strategy = SmbResourceStrategy()
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

    @Test
    fun `fieldSchema - PATH is required`() {
        assertRequired(strategy.fieldSchema(), ResourceFieldKey.PATH)
    }

    // --- Field schema: optional fields ---

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

    @Test
    fun `fieldSchema - MEDIA_TYPES is optional`() {
        assertOptional(strategy.fieldSchema(), ResourceFieldKey.MEDIA_TYPES)
    }

    // --- Field schema: cloud fields absent (A2-T7) ---

    @Test
    fun `fieldSchema - CLOUD_PROVIDER is absent for SMB type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.CLOUD_PROVIDER)
    }

    @Test
    fun `fieldSchema - CLOUD_FOLDER is absent for SMB type`() {
        assertFieldAbsent(strategy.fieldSchema(), ResourceFieldKey.CLOUD_FOLDER)
    }

    // --- Validation (A2-T8) ---

    @Test
    fun `validate - blank name produces NAME error`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, name = ""))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.NAME))
    }

    @Test
    fun `validate - blank host produces HOST error`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, host = ""))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.HOST))
    }

    @Test
    fun `validate - blank path produces PATH error`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, path = ""))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.PATH))
    }

    @Test
    fun `validate - blank name and blank host produces errors for both fields`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, name = "", host = ""))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.NAME))
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.HOST))
    }

    // --- Port validation (A2-T9) ---

    @Test
    fun `validate - port null produces no port error`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, port = null))
        assertTrue("Port=null should be accepted", result.isValid)
    }

    @Test
    fun `validate - port 445 is valid`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, port = 445))
        assertTrue("Standard SMB port 445 should be valid", result.isValid)
    }

    @Test
    fun `validate - port 0 produces PORT error`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, port = 0))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.PORT))
    }

    @Test
    fun `validate - port 65536 produces PORT error`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, port = 65536))
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.containsKey(ResourceFieldKey.PORT))
    }

    @Test
    fun `validate - port 1 is valid lower bound`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, port = 1))
        assertTrue("Port 1 is in range", result.isValid)
    }

    @Test
    fun `validate - port 65535 is valid upper bound`() {
        val result = strategy.validate(networkForm(ResourceType.SMB, port = 65535))
        assertTrue("Port 65535 is in range", result.isValid)
    }

    // --- Valid full form ---

    @Test
    fun `validate - valid SMB form produces no errors`() {
        val result = strategy.validate(networkForm(ResourceType.SMB))
        assertTrue("Expected valid result", result.isValid)
        assertTrue("Expected no field errors", result.fieldErrors.isEmpty())
    }
}
