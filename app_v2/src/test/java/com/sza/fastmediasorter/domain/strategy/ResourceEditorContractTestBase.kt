package com.sza.fastmediasorter.domain.strategy

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Shared helpers for ResourceStrategy contract tests.
 * All strategies are pure domain objects - no DI or Room needed.
 */
abstract class ResourceEditorContractTestBase {

    // --- Form factories ---

    protected fun localForm(
        name: String = "My Local",
        path: String = "/storage/emulated/0/DCIM",
        mediaTypes: Set<MediaType> = setOf(MediaType.IMAGE),
        allFiles: Boolean = false
    ) = ResourceFormData(
        type = ResourceType.LOCAL,
        name = name,
        path = path,
        supportedMediaTypes = mediaTypes,
        allFiles = allFiles
    )

    protected fun networkForm(
        type: ResourceType,
        name: String = "My Network",
        host: String = "192.168.1.100",
        path: String = "/share",
        port: Int? = null
    ) = ResourceFormData(
        type = type,
        name = name,
        host = host,
        path = path,
        port = port,
        supportedMediaTypes = setOf(MediaType.IMAGE)
    )

    protected fun streamForm(
        type: ResourceType = ResourceType.HTTP_STREAM,
        name: String = "My Stream",
        path: String = "http://example.com/live.m3u8",
        mediaTypes: Set<MediaType> = setOf(MediaType.VIDEO),
        allFiles: Boolean = false
    ) = ResourceFormData(
        type = type,
        name = name,
        path = path,
        supportedMediaTypes = mediaTypes,
        allFiles = allFiles
    )

    protected fun cloudForm(
        name: String = "My Cloud",
        cloudProvider: CloudProvider? = CloudProvider.GOOGLE_DRIVE,
        cloudFolderId: String? = "folder123",
        mediaTypes: Set<MediaType> = setOf(MediaType.IMAGE),
        allFiles: Boolean = false
    ) = ResourceFormData(
        type = ResourceType.CLOUD,
        name = name,
        cloudProvider = cloudProvider,
        cloudFolderId = cloudFolderId,
        supportedMediaTypes = mediaTypes,
        allFiles = allFiles,
        mode = ResourceEditorMode.CREATE
    )

    // --- Schema assertion helpers ---

    protected fun assertRequired(schema: List<ResourceFieldSchema>, key: ResourceFieldKey) {
        val field = schema.find { it.key == key }
        assertNotNull("Field $key must be present in schema", field)
        assertTrue("Field $key must be required", field!!.required)
    }

    protected fun assertOptional(schema: List<ResourceFieldSchema>, key: ResourceFieldKey) {
        val field = schema.find { it.key == key }
        assertNotNull("Field $key must be present in schema", field)
        assertFalse("Field $key must be optional (not required)", field!!.required)
    }

    /** Present in the schema (so validation still guards it) but never rendered by the editor. */
    protected fun assertPresentButHidden(schema: List<ResourceFieldSchema>, key: ResourceFieldKey) {
        val field = schema.find { it.key == key }
        assertNotNull("Field $key must be present in schema", field)
        assertFalse("Field $key must not be visible to the editor", field!!.visible)
    }

    protected fun assertFieldAbsent(schema: List<ResourceFieldSchema>, key: ResourceFieldKey) {
        val field = schema.find { it.key == key }
        assertNull("Field $key must NOT be present in schema for this type, but was found", field)
    }
}
