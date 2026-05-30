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

class SmbResourceStrategyTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `validate passes for a named host and share path`() {
        val result = SmbResourceStrategy().validate(ResourceFormData(name = "NAS", host = "h", path = "share"))
        assertTrue(result.isValid)
    }

    @Test
    fun `validate flags blank name host and path`() {
        val result = SmbResourceStrategy().validate(ResourceFormData(name = "", host = "", path = ""))
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.NAME])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.HOST])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.PATH])
    }

    @Test
    fun `validate rejects an out-of-range port`() {
        assertEquals(
            ResourceErrorCode.OUT_OF_RANGE,
            SmbResourceStrategy().validate(ResourceFormData(name = "n", host = "h", path = "s", port = 100000))
                .fieldErrors[ResourceFieldKey.PORT],
        )
    }

    @Test
    fun `testConnection returns NOT_SUPPORTED without a tester and delegates with one`() = runTest {
        assertEquals(
            ResourceConnectionStatus.NOT_SUPPORTED,
            SmbResourceStrategy().testConnection(ResourceFormData(name = "x", host = "h", path = "s")).status,
        )
        val expected = ResourceConnectionTestResult(status = ResourceConnectionStatus.PARTIAL)
        assertEquals(
            expected,
            SmbResourceStrategy(connectionTester = { expected })
                .testConnection(ResourceFormData(name = "x", host = "h", path = "s")),
        )
    }

    @Test
    fun `normalizeBeforeSave strips leading and trailing slashes from the share path and defaults port 445`() {
        val result = SmbResourceStrategy().normalizeBeforeSave(
            ResourceFormData(name = "  NAS ", host = " 10,0,0,9 ", path = "\\\\media\\videos\\", port = null),
        )
        assertEquals("NAS", result.name)
        assertEquals("10.0.0.9", result.host)
        // SMB keeps a relative share path (no leading slash), unlike FTP/SFTP.
        assertEquals("media/videos", result.path)
        assertEquals(445, result.port)
    }

    @Test
    fun `normalizeBeforeSave keeps an explicit port`() {
        val result = SmbResourceStrategy().normalizeBeforeSave(
            ResourceFormData(name = "n", host = "h", path = "share", port = 4450),
        )
        assertEquals(4450, result.port)
    }

    @Test
    fun `fieldSchema requires name host and path`() {
        val schema = SmbResourceStrategy().fieldSchema().associateBy { it.key }
        assertTrue(schema.getValue(ResourceFieldKey.NAME).required)
        assertTrue(schema.getValue(ResourceFieldKey.HOST).required)
        assertTrue(schema.getValue(ResourceFieldKey.PATH).required)
        assertFalse(schema.getValue(ResourceFieldKey.PORT).required)
    }
}
