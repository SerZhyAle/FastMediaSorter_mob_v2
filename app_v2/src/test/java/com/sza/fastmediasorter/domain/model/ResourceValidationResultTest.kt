package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [ResourceValidationResult] factory helpers.
 */
class ResourceValidationResultTest {

    @Test
    fun `valid factory marks the result valid with no errors`() {
        val result = ResourceValidationResult.valid()
        assertTrue(result.isValid)
        assertTrue(result.fieldErrors.isEmpty())
        assertTrue(result.globalErrors.isEmpty())
    }

    @Test
    fun `invalid factory carries the supplied field errors`() {
        val fieldErrors = mapOf(ResourceFieldKey.NAME to ResourceErrorCode.EMPTY)
        val result = ResourceValidationResult.invalid(fieldErrors = fieldErrors)
        assertFalse(result.isValid)
        assertEquals(fieldErrors, result.fieldErrors)
        assertTrue(result.globalErrors.isEmpty())
    }

    @Test
    fun `invalid factory carries the supplied global errors`() {
        val result = ResourceValidationResult.invalid(globalErrors = listOf(ResourceErrorCode.UNREACHABLE))
        assertFalse(result.isValid)
        assertEquals(listOf(ResourceErrorCode.UNREACHABLE), result.globalErrors)
    }

    @Test
    fun `invalid factory defaults to empty error collections`() {
        val result = ResourceValidationResult.invalid()
        assertFalse(result.isValid)
        assertTrue(result.fieldErrors.isEmpty())
        assertTrue(result.globalErrors.isEmpty())
    }
}
