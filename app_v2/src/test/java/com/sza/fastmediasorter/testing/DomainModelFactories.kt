package com.sza.fastmediasorter.testing

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ResumeState
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScreenType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.TimeFilter

/**
 * Central factories for the domain value types that recur most across the unit-test suite.
 *
 * Each factory mirrors the real constructor parameter-by-parameter with safe defaults so a test
 * can call `createX()` with zero args or override only the fields under test via named params.
 *
 * Scope here is deliberately limited to the 6 most-referenced models (S0300 Phase 01). Later phases
 * extend this file with additional factories as more domain/data slices gain coverage - keep the
 * "one top-level `fun create<ModelName>` per model, defaults mirror the real constructor" convention.
 */

/**
 * [MediaResource] - the folder/source aggregate (most-referenced domain model).
 */
fun createMediaResource(
    id: Long = 1L,
    name: String = "Test Resource",
    path: String = "/storage/emulated/0/Test",
    type: ResourceType = ResourceType.LOCAL,
    credentialsId: String? = null,
    cloudProvider: com.sza.fastmediasorter.data.cloud.CloudProvider? = null,
    cloudFolderId: String? = null,
    accountId: String? = null,
    supportedMediaTypes: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO),
    sortMode: SortMode = SortMode.NAME_ASC,
    displayMode: DisplayMode = DisplayMode.LIST,
    lastViewedFile: String? = null,
    lastScrollPosition: Int = 0,
    fileCount: Int = 0,
    subfolderCount: Int = 0,
    lastAccessedDate: Long = 0L,
    slideshowInterval: Int = 10,
    isDestination: Boolean = false,
    destinationOrder: Int? = null,
    destinationColor: Int = 0xFF4CAF50.toInt(),
    isWritable: Boolean = false,
    isReadOnly: Boolean = false,
    isAvailable: Boolean = true,
    showCommandPanel: Boolean? = null,
    createdDate: Long = 0L,
    lastBrowseDate: Long? = null,
    lastSyncDate: Long? = null,
    displayOrder: Int = 0,
    scanSubdirectories: Boolean = false,
    disableThumbnails: Boolean = false,
    allFiles: Boolean = false,
    showHiddenFiles: Boolean = false,
    showSubfoldersAsItems: Boolean = false,
    rememberFileList: Boolean = false,
    accessPin: String? = null,
    comment: String? = null,
    profile: ResourceProfile = ResourceProfile.NONE,
    readSpeedMbps: Double? = null,
    writeSpeedMbps: Double? = null,
    recommendedThreads: Int? = null,
    lastSpeedTestDate: Long? = null,
    iconId: String? = null,
    hostKeyFingerprint: String? = null,
    needsSignIn: Boolean = false,
): MediaResource = MediaResource(
    id = id,
    name = name,
    path = path,
    type = type,
    credentialsId = credentialsId,
    cloudProvider = cloudProvider,
    cloudFolderId = cloudFolderId,
    accountId = accountId,
    supportedMediaTypes = supportedMediaTypes,
    sortMode = sortMode,
    displayMode = displayMode,
    lastViewedFile = lastViewedFile,
    lastScrollPosition = lastScrollPosition,
    fileCount = fileCount,
    subfolderCount = subfolderCount,
    lastAccessedDate = lastAccessedDate,
    slideshowInterval = slideshowInterval,
    isDestination = isDestination,
    destinationOrder = destinationOrder,
    destinationColor = destinationColor,
    isWritable = isWritable,
    isReadOnly = isReadOnly,
    isAvailable = isAvailable,
    showCommandPanel = showCommandPanel,
    createdDate = createdDate,
    lastBrowseDate = lastBrowseDate,
    lastSyncDate = lastSyncDate,
    displayOrder = displayOrder,
    scanSubdirectories = scanSubdirectories,
    disableThumbnails = disableThumbnails,
    allFiles = allFiles,
    showHiddenFiles = showHiddenFiles,
    showSubfoldersAsItems = showSubfoldersAsItems,
    rememberFileList = rememberFileList,
    accessPin = accessPin,
    comment = comment,
    profile = profile,
    readSpeedMbps = readSpeedMbps,
    writeSpeedMbps = writeSpeedMbps,
    recommendedThreads = recommendedThreads,
    lastSpeedTestDate = lastSpeedTestDate,
    iconId = iconId,
    hostKeyFingerprint = hostKeyFingerprint,
    needsSignIn = needsSignIn,
)

/**
 * [MediaFile] - a single browsable media item.
 */
fun createMediaFile(
    name: String = "test.jpg",
    path: String = "/storage/emulated/0/Test/test.jpg",
    type: MediaType = MediaType.IMAGE,
    size: Long = 1_024L,
    createdDate: Long = 0L,
    contentUri: String? = null,
    duration: Long? = null,
    width: Int? = null,
    height: Int? = null,
    exifOrientation: Int? = null,
    exifDateTime: Long? = null,
    exifLatitude: Double? = null,
    exifLongitude: Double? = null,
    videoCodec: String? = null,
    videoBitrate: Int? = null,
    videoFrameRate: Float? = null,
    videoRotation: Int? = null,
    artist: String? = null,
    album: String? = null,
    title: String? = null,
    thumbnailUrl: String? = null,
    webViewUrl: String? = null,
    isFavorite: Boolean = false,
    resourceId: Long? = 1L,
    isDirectory: Boolean = false,
    childCount: Int? = null,
    lastModified: Long = 0L,
    cloudDisplayPath: String? = null,
    cloudItemId: String? = null,
    attributes: com.sza.fastmediasorter.domain.model.FileAttributes? = null,
    metadataState: com.sza.fastmediasorter.domain.model.MetadataState =
        com.sza.fastmediasorter.domain.model.MetadataState.COMPLETE,
): MediaFile = MediaFile(
    name = name,
    path = path,
    type = type,
    size = size,
    createdDate = createdDate,
    contentUri = contentUri,
    duration = duration,
    width = width,
    height = height,
    exifOrientation = exifOrientation,
    exifDateTime = exifDateTime,
    exifLatitude = exifLatitude,
    exifLongitude = exifLongitude,
    videoCodec = videoCodec,
    videoBitrate = videoBitrate,
    videoFrameRate = videoFrameRate,
    videoRotation = videoRotation,
    artist = artist,
    album = album,
    title = title,
    thumbnailUrl = thumbnailUrl,
    webViewUrl = webViewUrl,
    isFavorite = isFavorite,
    resourceId = resourceId,
    isDirectory = isDirectory,
    childCount = childCount,
    lastModified = lastModified,
    cloudDisplayPath = cloudDisplayPath,
    cloudItemId = cloudItemId,
    attributes = attributes,
    metadataState = metadataState,
)

/**
 * [FileFilter] - browse-list filtering criteria (companion to [MediaFile]).
 */
fun createFileFilter(
    nameContains: String? = null,
    minDate: Long? = null,
    maxDate: Long? = null,
    minSizeMb: Float? = null,
    maxSizeMb: Float? = null,
    mediaTypes: Set<MediaType>? = null,
): FileFilter = FileFilter(
    nameContains = nameContains,
    minDate = minDate,
    maxDate = maxDate,
    minSizeMb = minSizeMb,
    maxSizeMb = maxSizeMb,
    mediaTypes = mediaTypes,
)

/**
 * [AppSettings] - global settings aggregate. The factory relies on the real data class defaults so
 * callers override only the fields a given test cares about.
 */
fun createAppSettings(
    language: String = "en",
    preventSleep: Boolean = true,
    enableSafeMode: Boolean = true,
    confirmDelete: Boolean = true,
    confirmMove: Boolean = false,
    allowRename: Boolean = true,
    allowDelete: Boolean = true,
    enableScheduledOperations: Boolean = true,
    defaultSortMode: SortMode = SortMode.NAME_ASC,
    lastUsedResourceId: Long = -1L,
): AppSettings = AppSettings(
    language = language,
    preventSleep = preventSleep,
    enableSafeMode = enableSafeMode,
    confirmDelete = confirmDelete,
    confirmMove = confirmMove,
    allowRename = allowRename,
    allowDelete = allowDelete,
    enableScheduledOperations = enableScheduledOperations,
    defaultSortMode = defaultSortMode,
    lastUsedResourceId = lastUsedResourceId,
)

/**
 * [ScheduledOperation] - a single scheduled copy/move/delete job.
 */
fun createScheduledOperation(
    id: Long = 1L,
    isEnabled: Boolean = true,
    sourceResourceId: Long = 1L,
    operationType: ScheduledOpType = ScheduledOpType.COPY,
    targetResourceId: Long? = 2L,
    fileTypeMask: Int = FileTypeFlags.DEFAULT,
    timeFilter: TimeFilter = TimeFilter.ALL,
    startTimeHour: Int = 0,
    startTimeMinute: Int = 0,
    intervalHours: Int = 1,
    intervalMinutes: Int = 0,
    overwrite: Boolean = false,
    silentMode: Boolean = false,
    lastRunAt: Long? = null,
    nextRunAt: Long? = null,
    lastRunStatus: String? = null,
    workerId: String? = null,
): ScheduledOperation = ScheduledOperation(
    id = id,
    isEnabled = isEnabled,
    sourceResourceId = sourceResourceId,
    operationType = operationType,
    targetResourceId = targetResourceId,
    fileTypeMask = fileTypeMask,
    timeFilter = timeFilter,
    startTimeHour = startTimeHour,
    startTimeMinute = startTimeMinute,
    intervalHours = intervalHours,
    intervalMinutes = intervalMinutes,
    overwrite = overwrite,
    silentMode = silentMode,
    lastRunAt = lastRunAt,
    nextRunAt = nextRunAt,
    lastRunStatus = lastRunStatus,
    workerId = workerId,
)

/**
 * [ResumeState] - persisted "resume last playback" snapshot.
 */
fun createResumeState(
    filePath: String = "/storage/emulated/0/Test/test.jpg",
    resourceId: Long = 1L,
    currentFolderPath: String? = "/storage/emulated/0/Test",
    screenType: ScreenType = ScreenType.PLAYER,
    sortMode: SortMode = SortMode.NAME_ASC,
    isPlaying: Boolean = false,
    isSlideshowEnabled: Boolean = false,
    mediaType: MediaType = MediaType.IMAGE,
    savedAt: Long = 0L,
): ResumeState = ResumeState(
    filePath = filePath,
    resourceId = resourceId,
    currentFolderPath = currentFolderPath,
    screenType = screenType,
    sortMode = sortMode,
    isPlaying = isPlaying,
    isSlideshowEnabled = isSlideshowEnabled,
    mediaType = mediaType,
    savedAt = savedAt,
)
