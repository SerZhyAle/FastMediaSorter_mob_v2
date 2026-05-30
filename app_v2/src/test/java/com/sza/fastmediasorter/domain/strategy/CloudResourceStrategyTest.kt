package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CloudResourceStrategyTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `validate passes with a name provider and folder id`() {
        val result = CloudResourceStrategy().validate(
            ResourceFormData(
                name = "Drive",
                cloudProvider = CloudProvider.GOOGLE_DRIVE,
                cloudFolderId = "folder-123",
                supportedMediaTypes = setOf(MediaType.IMAGE),
            ),
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `validate flags missing name provider and folder location`() {
        val result = CloudResourceStrategy().validate(
            ResourceFormData(
                name = "",
                cloudProvider = null,
                cloudFolderId = null,
                path = "",
                supportedMediaTypes = emptySet(),
                allFiles = false,
            ),
        )
        assertFalse(result.isValid)
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.NAME])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.CLOUD_PROVIDER])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.CLOUD_FOLDER])
        assertEquals(ResourceErrorCode.EMPTY, result.fieldErrors[ResourceFieldKey.MEDIA_TYPES])
    }

    @Test
    fun `validate accepts a path in lieu of a folder id`() {
        val result = CloudResourceStrategy().validate(
            ResourceFormData(
                name = "Drive",
                cloudProvider = CloudProvider.GOOGLE_DRIVE,
                cloudFolderId = null,
                path = "/Photos",
                supportedMediaTypes = setOf(MediaType.IMAGE),
            ),
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `testConnection returns NOT_SUPPORTED when no tester configured`() = runTest {
        val result = CloudResourceStrategy().testConnection(ResourceFormData(name = "x"))
        assertEquals(ResourceConnectionStatus.NOT_SUPPORTED, result.status)
    }

    @Test
    fun `testConnection delegates to the configured tester`() = runTest {
        val expected = ResourceConnectionTestResult(status = ResourceConnectionStatus.SUCCESS)
        val strategy = CloudResourceStrategy(connectionTester = { expected })
        assertEquals(expected, strategy.testConnection(ResourceFormData(name = "x")))
    }

    @Test
    fun `normalizeBeforeSave collapses duplicate slashes and clears host credentials`() {
        val result = CloudResourceStrategy().normalizeBeforeSave(
            ResourceFormData(
                name = "  Drive  ",
                path = "  /Photos//2024///trip  ",
                host = "h",
                port = 443,
                username = "u",
                password = "p",
            ),
        )
        assertEquals("Drive", result.name)
        assertEquals("/Photos/2024/trip", result.path)
        assertEquals("", result.host)
        assertEquals(null, result.port)
        assertEquals("", result.username)
        assertEquals("", result.password)
    }

    @Test
    fun `fieldSchema requires name and provider but not folder`() {
        val schema = CloudResourceStrategy().fieldSchema().associateBy { it.key }
        assertTrue(schema.getValue(ResourceFieldKey.NAME).required)
        assertTrue(schema.getValue(ResourceFieldKey.CLOUD_PROVIDER).required)
        assertFalse(schema.getValue(ResourceFieldKey.CLOUD_FOLDER).required)
    }
}
