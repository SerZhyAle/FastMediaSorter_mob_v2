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

class SftpResourceStrategyTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `validate passes for a named host with a path`() {
        val result = SftpResourceStrategy().validate(ResourceFormData(name = "Box", host = "h", path = "/home"))
        assertTrue(result.isValid)
    }

    @Test
    fun `validate requires path unlike FTP`() {
        val result = SftpResourceStrategy().validate(ResourceFormData(name = "Box", host = "h", path = ""))
        assertFalse(result.isValid)
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.PATH])
    }

    @Test
    fun `validate flags blank name host and out-of-range port`() {
        val result = SftpResourceStrategy().validate(ResourceFormData(name = "", host = "", path = "/x", port = -1))
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.NAME])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.HOST])
        assertEquals(ResourceErrorCode.OUT_OF_RANGE, result.fieldErrors[ResourceFieldKey.PORT])
    }

    @Test
    fun `testConnection returns NOT_SUPPORTED without a tester and delegates with one`() = runTest {
        assertEquals(
            ResourceConnectionStatus.NOT_SUPPORTED,
            SftpResourceStrategy().testConnection(ResourceFormData(name = "x", host = "h", path = "/x")).status,
        )
        val expected = ResourceConnectionTestResult(status = ResourceConnectionStatus.FAILED)
        assertEquals(
            expected,
            SftpResourceStrategy(connectionTester = { expected })
                .testConnection(ResourceFormData(name = "x", host = "h", path = "/x")),
        )
    }

    @Test
    fun `normalizeBeforeSave defaults the port to 22`() {
        val result = SftpResourceStrategy().normalizeBeforeSave(
            ResourceFormData(name = " Box ", host = " 10,0,0,5 ", path = "\\srv\\data\\", port = null),
        )
        assertEquals("Box", result.name)
        assertEquals("10.0.0.5", result.host)
        assertEquals("/srv/data", result.path)
        assertEquals(22, result.port)
    }

    @Test
    fun `normalizeBeforeSave maps a blank path to root`() {
        val result = SftpResourceStrategy().normalizeBeforeSave(
            ResourceFormData(name = "x", host = "h", path = "   ", port = 22),
        )
        assertEquals("/", result.path)
    }

    @Test
    fun `fieldSchema requires name host and path`() {
        val schema = SftpResourceStrategy().fieldSchema().associateBy { it.key }
        assertTrue(schema.getValue(ResourceFieldKey.NAME).required)
        assertTrue(schema.getValue(ResourceFieldKey.HOST).required)
        assertTrue(schema.getValue(ResourceFieldKey.PATH).required)
    }
}
