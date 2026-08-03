package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.testing.createAppSettings
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.createScheduledOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMapperTest {

    // --- resource round-trip ---

    @Test
    fun `resource round-trips through backup preserving key fields`() {
        val resource = createMediaResource(
            name = "My SMB",
            path = "smb://h/share",
            type = ResourceType.SMB,
            displayMode = DisplayMode.GRID,
            sortMode = SortMode.DATE_DESC,
            isDestination = true,
            destinationOrder = 3,
            supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO),
        )

        val restored = BackupMapper.toMediaResource(BackupMapper.toBackupResource(resource))

        assertEquals("My SMB", restored.name)
        assertEquals("smb://h/share", restored.path)
        assertEquals(ResourceType.SMB, restored.type)
        assertEquals(DisplayMode.GRID, restored.displayMode)
        assertEquals(SortMode.DATE_DESC, restored.sortMode)
        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO), restored.supportedMediaTypes)
        assertTrue(restored.isDestination)
        assertEquals(3, restored.destinationOrder)
    }

    @Test
    fun `restored resource is marked unavailable until verified`() {
        val restored = BackupMapper.toMediaResource(
            BackupMapper.toBackupResource(createMediaResource(isAvailable = true))
        )
        assertEquals(false, restored.isAvailable)
    }

    @Test
    fun `null destinationOrder maps to -1 in backup`() {
        val backup = BackupMapper.toBackupResource(createMediaResource(destinationOrder = null))
        assertEquals(-1, backup.destinationOrder)
    }

    @Test
    fun `unknown enum strings fall back to defaults on restore`() {
        val backup = BackupMapper.toBackupResource(createMediaResource())
            .copy(type = "BOGUS", sortMode = "NOPE", displayMode = "???", profile = "X")

        val restored = BackupMapper.toMediaResource(backup)

        assertEquals(ResourceType.LOCAL, restored.type)
        assertEquals(SortMode.NAME_ASC, restored.sortMode)
        assertEquals(DisplayMode.LIST, restored.displayMode)
    }

    @Test
    fun `unknown media type strings are dropped from the set`() {
        val backup = BackupMapper.toBackupResource(createMediaResource())
            .copy(supportedMediaTypes = listOf("IMAGE", "GARBAGE", "VIDEO"))

        val restored = BackupMapper.toMediaResource(backup)

        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO), restored.supportedMediaTypes)
    }

    // --- scheduled operation mapping ---

    @Test
    fun `scheduled op maps to backup using resource paths`() {
        val src = createMediaResource(id = 1L, path = "/src", type = ResourceType.LOCAL)
        val dst = createMediaResource(id = 2L, path = "/dst", type = ResourceType.LOCAL)
        val op = createScheduledOperation(sourceResourceId = 1L, targetResourceId = 2L, operationType = ScheduledOpType.MOVE)

        val backup = BackupMapper.toBackupScheduledOperation(op, mapOf(1L to src, 2L to dst))!!

        assertEquals("/src", backup.sourceResourcePath)
        assertEquals("/dst", backup.targetResourcePath)
        assertEquals("MOVE", backup.operationType)
    }

    @Test
    fun `scheduled op backup is null when source resource missing`() {
        val op = createScheduledOperation(sourceResourceId = 99L)
        assertNull(BackupMapper.toBackupScheduledOperation(op, emptyMap()))
    }

    @Test
    fun `scheduled op restore resolves resources by path and type`() {
        val src = createMediaResource(id = 5L, path = "/src", type = ResourceType.LOCAL)
        val dst = createMediaResource(id = 6L, path = "/dst", type = ResourceType.LOCAL)
        val backup = BackupScheduledOperation(
            sourceResourcePath = "/src",
            sourceResourceType = "LOCAL",
            operationType = "COPY",
            targetResourcePath = "/dst",
            targetResourceType = "LOCAL",
        )

        val restored = BackupMapper.toScheduledOperation(backup, mapOf("a" to src, "b" to dst))!!

        assertEquals(5L, restored.sourceResourceId)
        assertEquals(6L, restored.targetResourceId)
        assertEquals(ScheduledOpType.COPY, restored.operationType)
    }

    @Test
    fun `non-delete scheduled op without target restores to null`() {
        val src = createMediaResource(id = 5L, path = "/src", type = ResourceType.LOCAL)
        val backup = BackupScheduledOperation(
            sourceResourcePath = "/src",
            sourceResourceType = "LOCAL",
            operationType = "COPY",
            targetResourcePath = null,
        )

        assertNull(BackupMapper.toScheduledOperation(backup, mapOf("a" to src)))
    }

    @Test
    fun `delete scheduled op without target is allowed`() {
        val src = createMediaResource(id = 5L, path = "/src", type = ResourceType.LOCAL)
        val backup = BackupScheduledOperation(
            sourceResourcePath = "/src",
            sourceResourceType = "LOCAL",
            operationType = "DELETE",
            targetResourcePath = null,
        )

        val restored = BackupMapper.toScheduledOperation(backup, mapOf("a" to src))!!

        assertEquals(ScheduledOpType.DELETE, restored.operationType)
        assertNull(restored.targetResourceId)
    }

    @Test
    fun `unknown operation type restores as COPY`() {
        val src = createMediaResource(id = 5L, path = "/src", type = ResourceType.LOCAL)
        val dst = createMediaResource(id = 6L, path = "/dst", type = ResourceType.LOCAL)
        val backup = BackupScheduledOperation(
            sourceResourcePath = "/src", sourceResourceType = "LOCAL",
            operationType = "WUT",
            targetResourcePath = "/dst", targetResourceType = "LOCAL",
        )

        val restored = BackupMapper.toScheduledOperation(backup, mapOf("a" to src, "b" to dst))!!

        assertEquals(ScheduledOpType.COPY, restored.operationType)
    }

    // --- favorites mapping ---

    @Test
    fun `favorites map resource name and path via lookup`() {
        val resource = createMediaResource(id = 1L, name = "Photos", path = "/p")
        val entity = FavoritesEntity(
            uri = "/p/a.jpg", resourceId = 1L, displayName = "a.jpg",
            mediaType = 1, size = 10L, lastKnownPath = "/p/a.jpg", dateModified = 5L, addedTimestamp = 7L,
        )

        val backup = BackupMapper.toBackupFavorites(listOf(entity), mapOf(1L to resource)).single()

        assertEquals("Photos", backup.resourceName)
        assertEquals("/p", backup.resourcePath)
        assertEquals("a.jpg", backup.displayName)
    }

    @Test
    fun `favorites missing resource get blank name and path`() {
        val entity = FavoritesEntity(
            uri = "u", resourceId = 99L, displayName = "x",
            mediaType = 1, size = 0L, lastKnownPath = "u", dateModified = 0L,
        )

        val backup = BackupMapper.toBackupFavorites(listOf(entity), emptyMap()).single()

        assertEquals("", backup.resourceName)
        assertEquals("", backup.resourcePath)
    }

    @Test
    fun `favorite restores into entity with resolved resource id`() {
        val backup = BackupFavorite(uri = "u", displayName = "x", mediaType = 2, size = 3L, lastKnownPath = "u")

        val entity = BackupMapper.toFavoritesEntity(backup, resolvedResourceId = 42L)

        assertEquals(42L, entity.resourceId)
        assertEquals("u", entity.uri)
        assertEquals(2, entity.mediaType)
    }

    // --- settings null-coalescing for forward compat ---

    @Test
    fun `null link auto-download flags preserve current settings on restore`() {
        val current = createAppSettings()
        val backup = BackupMapper.toBackupSettings(current).copy(
            linkAutoDownloadEnabled = null,
            linkAutoDownloadOpenInPlayer = null,
            linkDownloadMaxResolution = null,
            vrAutoImmersive = null,
            disable3dVr = null,
        )

        val restored = BackupMapper.toAppSettings(backup, current, BackupPayload.CURRENT_VERSION)

        assertEquals(current.linkAutoDownloadEnabled, restored.linkAutoDownloadEnabled)
        assertEquals(current.linkAutoDownloadOpenInPlayer, restored.linkAutoDownloadOpenInPlayer)
        assertEquals(current.linkDownloadMaxResolution, restored.linkDownloadMaxResolution)
        assertEquals(current.vrAutoImmersive, restored.vrAutoImmersive)
        assertEquals(current.disable3dVr, restored.disable3dVr)
    }

    @Test
    fun `settings round-trip preserves language and sort mode`() {
        val current = createAppSettings(language = "ru", defaultSortMode = SortMode.DATE_DESC)

        val restored = BackupMapper.toAppSettings(
            BackupMapper.toBackupSettings(current),
            current,
            BackupPayload.CURRENT_VERSION
        )

        assertEquals("ru", restored.language)
        assertEquals(SortMode.DATE_DESC, restored.defaultSortMode)
    }

    // --- S1346: pre-S0981 backup restore must not re-enable open-in-player ---

    @Test
    fun `pre-S0981 backup (version 5) forces linkAutoDownloadOpenInPlayer off even when backup value is true`() {
        val current = createAppSettings() // AppSettings default: linkAutoDownloadOpenInPlayer = false
        val backup = BackupMapper.toBackupSettings(current).copy(linkAutoDownloadOpenInPlayer = true)

        val restored = BackupMapper.toAppSettings(backup, current, payloadVersion = 5)

        assertEquals(false, restored.linkAutoDownloadOpenInPlayer)
    }

    @Test
    fun `post-S0981 backup (version 6) honors an explicit linkAutoDownloadOpenInPlayer true`() {
        val current = createAppSettings() // AppSettings default: linkAutoDownloadOpenInPlayer = false
        val backup = BackupMapper.toBackupSettings(current).copy(linkAutoDownloadOpenInPlayer = true)

        val restored = BackupMapper.toAppSettings(backup, current, payloadVersion = 6)

        assertEquals(true, restored.linkAutoDownloadOpenInPlayer)
    }
}
