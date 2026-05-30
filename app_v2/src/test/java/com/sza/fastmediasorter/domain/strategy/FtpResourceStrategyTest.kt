package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
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

class FtpResourceStrategyTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `validate passes for a named host`() {
        val result = FtpResourceStrategy().validate(ResourceFormData(name = "Server", host = "10.0.0.1"))
        assertTrue(result.isValid)
    }

    @Test
    fun `validate flags blank name and host`() {
        val result = FtpResourceStrategy().validate(ResourceFormData(name = "", host = ""))
        assertFalse(result.isValid)
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.NAME])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.HOST])
    }

    @Test
    fun `validate rejects a port outside the valid range`() {
        assertEquals(
            ResourceErrorCode.OUT_OF_RANGE,
            FtpResourceStrategy().validate(ResourceFormData(name = "s", host = "h", port = 0))
                .fieldErrors[ResourceFieldKey.PORT],
        )
        assertEquals(
            ResourceErrorCode.OUT_OF_RANGE,
            FtpResourceStrategy().validate(ResourceFormData(name = "s", host = "h", port = 70000))
                .fieldErrors[ResourceFieldKey.PORT],
        )
    }

    @Test
    fun `validate accepts a port at the range boundaries`() {
        assertTrue(FtpResourceStrategy().validate(ResourceFormData(name = "s", host = "h", port = 1)).isValid)
        assertTrue(FtpResourceStrategy().validate(ResourceFormData(name = "s", host = "h", port = 65535)).isValid)
    }

    @Test
    fun `testConnection returns NOT_SUPPORTED without a tester and delegates with one`() = runTest {
        assertEquals(
            ResourceConnectionStatus.NOT_SUPPORTED,
            FtpResourceStrategy().testConnection(ResourceFormData(name = "x")).status,
        )
        val expected = ResourceConnectionTestResult(status = ResourceConnectionStatus.SUCCESS)
        assertEquals(
            expected,
            FtpResourceStrategy(connectionTester = { expected }).testConnection(ResourceFormData(name = "x")),
        )
    }

    @Test
    fun `normalizeBeforeSave converts commas in host to dots and defaults the port to 21`() {
        val result = FtpResourceStrategy().normalizeBeforeSave(
            ResourceFormData(name = "  Server ", host = " 10,0,0,1 ", path = "", port = null),
        )
        assertEquals("Server", result.name)
        assertEquals("10.0.0.1", result.host)
        assertEquals(21, result.port)
        assertEquals("/", result.path)
    }

    @Test
    fun `normalizeBeforeSave normalizes a backslash path and trims a trailing slash`() {
        val result = FtpResourceStrategy().normalizeBeforeSave(
            ResourceFormData(name = "s", host = "h", path = "\\media\\\\videos\\", port = 2121),
        )
        assertEquals("/media/videos", result.path)
        assertEquals(2121, result.port)
    }

    @Test
    fun `normalizeBeforeSave keeps an explicit port`() {
        val result = FtpResourceStrategy().normalizeBeforeSave(
            ResourceFormData(name = "s", host = "h", path = "/x", port = 9999),
        )
        assertEquals(9999, result.port)
    }

    @Test
    fun `fieldSchema requires name and host but path is optional`() {
        val schema = FtpResourceStrategy().fieldSchema().associateBy { it.key }
        assertTrue(schema.getValue(ResourceFieldKey.NAME).required)
        assertTrue(schema.getValue(ResourceFieldKey.HOST).required)
        assertFalse(schema.getValue(ResourceFieldKey.PATH).required)
    }
}
