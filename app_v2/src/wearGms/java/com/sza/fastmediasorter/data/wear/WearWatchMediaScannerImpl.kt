package com.sza.fastmediasorter.data.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.scanner.WearWatchMediaScanner
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1861: GMS-backed scanner for the paired-watch resource (`wearGms` source set).
 *
 * Reachability is answered for real here, over [Wearable.getNodeClient]. The listing itself is still
 * empty: the watch side that answers a listing request lands with the receiving half in phase 2 of
 * the tactical plan, and issuing a request nobody answers would cost every browse the bridge's full
 * ten-second response timeout. An empty list keeps browse on its ordinary empty-folder path instead.
 */
@Singleton
class WearWatchMediaScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearWatchMediaScanner {

    override val isCompanionAvailable: Boolean = true

    // GMS answers a reachability query with anything from ApiException to SecurityException, and the
    // reply is the same for all of them: no watch. Naming the subtypes would only invite the next one.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun isWatchReachable(): Boolean = try {
        Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Expected whenever Play Services Wearable is absent or the pairing was removed - a watch we
        // cannot ask about is a watch that is not there, which is a state the resource already draws.
        Timber.i(e, "Watch reachability probe failed, treating the watch as disconnected")
        false
    }

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: ScanProgressCallback?
    ): List<MediaFile> {
        if (!isWatchReachable()) {
            Timber.d("S2483: watch is not reachable, returning empty list")
            return emptyList()
        }
        Timber.d("S2483: watch reachable, scanning watch folder %s", path)
        return emptyList()
    }

    override suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        offset: Int,
        limit: Int,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): MediaFilePage {
        if (!isWatchReachable()) {
            Timber.d("WearWatchMediaScannerImpl: watch is not reachable, returning empty page")
            return MediaFilePage(files = emptyList(), hasMore = false)
        }
        Timber.d("WearWatchMediaScannerImpl: watch reachable, scanning paged watch folder %s", path)
        return MediaFilePage(files = emptyList(), hasMore = false)
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int {
        if (!isWatchReachable()) return 0
        return 0
    }

    /**
     * Writability is reachability: the watch's own storage imposes no per-folder permission the phone
     * could probe, so what decides whether a send can be attempted is whether the bridge is up.
     */
    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = isWatchReachable()
}
