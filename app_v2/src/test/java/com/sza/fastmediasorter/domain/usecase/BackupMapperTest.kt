package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.TimeFilter
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
        val op =
            createScheduledOperation(sourceResourceId = 1L, targetResourceId = 2L, operationType = ScheduledOpType.MOVE)

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
    fun `S4238 exchange resource round-trips with binding pin and every access path`() {
        val resource = createMediaResource(path = "sftp://10.0.0.5:2222/Photos", type = ResourceType.SFTP).copy(
            hostKeyFingerprint = "SHA256:abc",
            altAccessPaths = listOf(HostPort("203.0.113.9", 2222), HostPort("tunnel.example", 8443)),
            exchangeAccount = "exchange.example.org:8443/alice",
            exchangeResourceId = "res-1"
        )

        val restored = BackupMapper.toMediaResource(BackupMapper.toBackupResource(resource))

        assertEquals("SHA256:abc", restored.hostKeyFingerprint)
        assertEquals(resource.altAccessPaths, restored.altAccessPaths)
        assertEquals("exchange.example.org:8443/alice", restored.exchangeAccount)
        assertEquals("res-1", restored.exchangeResourceId)
    }

    @Test
    fun `S4238 payload written before the exchange fields restores with none set`() {
        val restored = BackupMapper.toMediaResource(BackupResource(name = "Old", path = "sftp://h/p", type = "SFTP"))

        assertNull(restored.hostKeyFingerprint)
        assertEquals(emptyList<HostPort>(), restored.altAccessPaths)
        assertNull(restored.exchangeAccount)
        assertNull(restored.exchangeResourceId)
    }

    @Test
    fun `S4238 resource without extra paths is written as null not as an empty list`() {
        val backup = BackupMapper.toBackupResource(createMediaResource())

        assertNull(backup.altAccessPaths)
    }

    @Test
    fun `S4238 scheduled op carries the exchange identity of both sides`() {
        val src = createMediaResource(id = 1L, path = "sftp://a/x", type = ResourceType.SFTP)
            .copy(exchangeAccount = "acc", exchangeResourceId = "src-id")
        val dst = createMediaResource(id = 2L, path = "/dst", type = ResourceType.LOCAL)
        val op = createScheduledOperation(sourceResourceId = 1L, targetResourceId = 2L)

        val backup = BackupMapper.toBackupScheduledOperation(op, mapOf(1L to src, 2L to dst))!!

        assertEquals("acc", backup.sourceExchangeAccount)
        assertEquals("src-id", backup.sourceExchangeResourceId)
        assertNull(backup.targetExchangeAccount)
        assertNull(backup.targetExchangeResourceId)
    }

    @Test
    fun `S4238 scheduled op finds an exchange resource whose path changed`() {
        val src = createMediaResource(id = 5L, path = "sftp://new-host:2222/Photos", type = ResourceType.SFTP)
            .copy(exchangeAccount = "acc", exchangeResourceId = "src-id")
        val dst = createMediaResource(id = 6L, path = "/dst", type = ResourceType.LOCAL)
        val backup = BackupScheduledOperation(
            sourceResourcePath = "sftp://old-host:2222/Photos",
            sourceResourceType = "SFTP",
            sourceExchangeAccount = "acc",
            sourceExchangeResourceId = "src-id",
            operationType = "COPY",
            targetResourcePath = "/dst",
            targetResourceType = "LOCAL",
        )

        val restored = BackupMapper.toScheduledOperation(backup, mapOf("a" to src, "b" to dst))!!

        assertEquals(5L, restored.sourceResourceId)
        assertEquals(6L, restored.targetResourceId)
    }

    @Test
    fun `S4238 exchange identity wins over a path that now names another resource`() {
        val other = createMediaResource(id = 7L, path = "sftp://h/Photos", type = ResourceType.SFTP)
        val bound = createMediaResource(id = 8L, path = "sftp://moved/Photos", type = ResourceType.SFTP)
            .copy(exchangeAccount = "acc", exchangeResourceId = "src-id")
        val backup = BackupScheduledOperation(
            sourceResourcePath = "sftp://h/Photos",
            sourceResourceType = "SFTP",
            sourceExchangeAccount = "acc",
            sourceExchangeResourceId = "src-id",
            operationType = "DELETE",
        )

        val restored = BackupMapper.toScheduledOperation(backup, mapOf("a" to other, "b" to bound))!!

        assertEquals(8L, restored.sourceResourceId)
    }

    @Test
    fun `S4238 scheduled op without exchange identity still resolves by path and type`() {
        val src = createMediaResource(id = 5L, path = "/src", type = ResourceType.LOCAL)
        val backup = BackupScheduledOperation(
            sourceResourcePath = "/src",
            sourceResourceType = "LOCAL",
            operationType = "DELETE",
        )

        val restored = BackupMapper.toScheduledOperation(backup, mapOf("a" to src))!!

        assertEquals(5L, restored.sourceResourceId)
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
            sourceResourcePath = "/src",
            sourceResourceType = "LOCAL",
            operationType = "WUT",
            targetResourcePath = "/dst",
            targetResourceType = "LOCAL",
        )

        val restored = BackupMapper.toScheduledOperation(backup, mapOf("a" to src, "b" to dst))!!

        assertEquals(ScheduledOpType.COPY, restored.operationType)
    }

    @Test
    fun `S4248 watch message round-trips with no resources in the lookup`() {
        val op = ScheduledOperation(
            sourceResourceId = null,
            operationType = ScheduledOpType.WATCH_MESSAGE,
            targetResourceId = null,
            timeFilter = TimeFilter.ALL,
            startTimeHour = 7,
            startTimeMinute = 30,
            intervalHours = 24,
            intervalMinutes = 0,
            watchTitle = "Morning",
            watchText = "Drink water",
            watchImageUri = "content://picker/doc/1",
        )

        val backup = BackupMapper.toBackupScheduledOperation(op, emptyMap())!!
        val restored = BackupMapper.toScheduledOperation(backup, emptyMap())!!

        assertEquals("", backup.sourceResourcePath)
        assertEquals(ScheduledOpType.WATCH_MESSAGE, restored.operationType)
        assertNull(restored.sourceResourceId)
        assertNull(restored.targetResourceId)
        assertEquals("Morning", restored.watchTitle)
        assertEquals("Drink water", restored.watchText)
        assertEquals("content://picker/doc/1", restored.watchImageUri)
        assertEquals(7, restored.startTimeHour)
        assertEquals(24, restored.intervalHours)
    }

    @Test
    fun `S4248 watch message backup without a title is dropped on restore`() {
        val backup = BackupScheduledOperation(operationType = "WATCH_MESSAGE", watchTitle = " ")

        assertNull(BackupMapper.toScheduledOperation(backup, emptyMap()))
    }

    @Test
    fun `S4248 backup written before the watch fields restores a COPY unchanged`() {
        val src = createMediaResource(id = 5L, path = "/src", type = ResourceType.LOCAL)
        val dst = createMediaResource(id = 6L, path = "/dst", type = ResourceType.LOCAL)
        val json = """{"sourceResourcePath":"/src","sourceResourceType":"LOCAL","operationType":"COPY",""" +
            """"targetResourcePath":"/dst","targetResourceType":"LOCAL","timeFilter":"ALL"}"""
        val backup = Gson().fromJson(json, BackupScheduledOperation::class.java)

        val restored = BackupMapper.toScheduledOperation(backup, mapOf("a" to src, "b" to dst))!!

        assertEquals(ScheduledOpType.COPY, restored.operationType)
        assertEquals(5L, restored.sourceResourceId)
        assertEquals(6L, restored.targetResourceId)
        assertNull(restored.watchTitle)
        assertNull(restored.watchImageUri)
    }

    // --- favorites mapping ---

    @Test
    fun `favorites map resource name and path via lookup`() {
        val resource = createMediaResource(id = 1L, name = "Photos", path = "/p")
        val entity = FavoritesEntity(
            uri = "/p/a.jpg",
            resourceId = 1L,
            displayName = "a.jpg",
            mediaType = 1,
            size = 10L,
            lastKnownPath = "/p/a.jpg",
            dateModified = 5L,
            addedTimestamp = 7L,
        )

        val backup = BackupMapper.toBackupFavorites(listOf(entity), mapOf(1L to resource)).single()

        assertEquals("Photos", backup.resourceName)
        assertEquals("/p", backup.resourcePath)
        assertEquals("a.jpg", backup.displayName)
    }

    @Test
    fun `favorites missing resource get blank name and path`() {
        val entity = FavoritesEntity(
            uri = "u",
            resourceId = 99L,
            displayName = "x",
            mediaType = 1,
            size = 0L,
            lastKnownPath = "u",
            dateModified = 0L,
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

    // --- S2428: a backup missing the persistent-audio field must not restore as disabled ---

    @Test
    fun `backup without persistent audio field restores the enabled default`() {
        val current = createAppSettings()

        val restored = BackupMapper.toAppSettings(
            BackupSettings(),
            current,
            BackupPayload.CURRENT_VERSION
        )

        assertEquals(true, restored.enablePersistentAudioPlayback)
    }

    @Test
    fun `backup carrying an explicit persistent audio opt-out restores as disabled`() {
        val current = createAppSettings()
        val backup = BackupMapper.toBackupSettings(current).copy(enablePersistentAudioPlayback = false)

        val restored = BackupMapper.toAppSettings(backup, current, BackupPayload.CURRENT_VERSION)

        assertEquals(false, restored.enablePersistentAudioPlayback)
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

    // --- S1740: launcher export/import ---

    @Test
    fun `launcher settings round-trip through backup mapper`() {
        val current = createAppSettings().withLauncher {
            copy(
                densityFactor = 1.25f,
                taskbarPlacement = "TOP",
                taskbarShowRecents = false,
                wallpaperMode = "STATIC_STRIPES",
                wallpaperImagePath = "/path/to/custom_wallpaper.png",
                desktopLocked = true,
                allAppsSortOrder = "USAGE_COUNT",
                allAppsSortDescending = true,
                screenBlackoutTimeoutSeconds = 60,
                screenBlackoutTimeoutOnChargeSeconds = 300,
            )
        }

        val backup = BackupMapper.toBackupSettings(current)
        val restored = BackupMapper.toAppSettings(backup, createAppSettings(), BackupPayload.CURRENT_VERSION)

        assertEquals(1.25f, restored.launcherDensityFactor)
        assertEquals("TOP", restored.launcherTaskbarPlacement)
        assertEquals(false, restored.launcherTaskbarShowRecents)
        assertEquals("STATIC_STRIPES", restored.launcherWallpaperMode)
        assertEquals("/path/to/custom_wallpaper.png", restored.launcherWallpaperImagePath)
        assertEquals(true, restored.launcherDesktopLocked)
        assertEquals("USAGE_COUNT", restored.allAppsSortOrder)
        assertEquals(true, restored.allAppsSortDescending)
        assertEquals(60, restored.launcherScreenBlackoutTimeoutSeconds)
        assertEquals(300, restored.launcherScreenBlackoutTimeoutOnChargeSeconds)
    }

    // S2249: pre-S2249 backups carry no launcherDesktopDoubleTapLockEnabled field, which Gson
    // deserializes as null - the restore must keep the current value, not force the gesture off.
    @Test
    fun `older backup without double-tap lock field preserves the current launcher setting`() {
        val current = createAppSettings().withLauncher { copy(desktopDoubleTapLockEnabled = true) }
        val backup = BackupMapper.toBackupSettings(current).copy(launcherDesktopDoubleTapLockEnabled = null)

        val restored = BackupMapper.toAppSettings(backup, current, BackupPayload.CURRENT_VERSION)

        assertEquals(true, restored.launcherDesktopDoubleTapLockEnabled)
    }

    @Test
    fun `launcher cell round-trips through backup mapper`() {
        val cellEntity = com.sza.fastmediasorter.data.local.db.LauncherCellEntity(
            id = 100L,
            orientation = "PORTRAIT",
            rowIndex = 2,
            colIndex = 3,
            spanW = 2,
            spanH = 1,
            kind = "SHORTCUT",
            target = "app:com.example.app",
            labelOverride = "Custom App",
            addedAt = 123456789L,
            origin = "AUTO_INSTALL",
        )

        val backupCell = BackupMapper.toBackupLauncherCell(cellEntity)
        val restoredEntity = BackupMapper.toLauncherCellEntity(backupCell)

        assertEquals(0L, restoredEntity.id)
        assertEquals("PORTRAIT", restoredEntity.orientation)
        assertEquals(2, restoredEntity.rowIndex)
        assertEquals(3, restoredEntity.colIndex)
        assertEquals(2, restoredEntity.spanW)
        assertEquals(1, restoredEntity.spanH)
        assertEquals("SHORTCUT", restoredEntity.kind)
        assertEquals("app:com.example.app", restoredEntity.target)
        assertEquals("Custom App", restoredEntity.labelOverride)
        assertEquals(123456789L, restoredEntity.addedAt)
        assertEquals("AUTO_INSTALL", restoredEntity.origin)
    }
}
