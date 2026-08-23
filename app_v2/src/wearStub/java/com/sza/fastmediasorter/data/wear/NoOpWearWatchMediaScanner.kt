package com.sza.fastmediasorter.data.wear

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.scanner.WearWatchMediaScanner
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1861: inert paired-watch scanner for the `wearStub` source set.
 *
 * Mounted into the flavors that carry no Wear companion (lite, photos, legacy, vr), so the Hilt
 * graph that
 * injects [WearWatchMediaScanner] resolves without the Play Services Wearable SDK on the classpath.
 * [isWatchReachable] is permanently false, which is the truth here rather than a placeholder: these
 * builds have no bridge to a watch at all.
 */
@Singleton
class NoOpWearWatchMediaScanner @Inject constructor() : WearWatchMediaScanner {

    override val isCompanionAvailable: Boolean = false

    override suspend fun isWatchReachable(): Boolean = false

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> = emptyList()

    override suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        offset: Int,
        limit: Int,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): MediaFilePage = MediaFilePage(files = emptyList(), hasMore = false)

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int = 0

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = false
}
