package com.sza.fastmediasorter.widget

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

object RandomPhotoFrameWidgetRefresher {
    fun refresh(context: Context, appWidgetId: Int): RandomPhotoFrameSnapshotStore.Snapshot {
        // RemoteViews providers need a synchronous snapshot at render time; keep the blocking scope
        // tightly limited to cache/Room reads only.
        return runBlocking(Dispatchers.IO) {
            val current = RandomPhotoFrameSnapshotStore.read(context, appWidgetId)
            if (!current.isConfigured) {
                return@runBlocking current
            }

            val deps = entryPoint(context)
            val files = MediaFilesCacheManager.getCachedList(current.resourceId)
                ?: deps.cachedFileListRepository().getCachedFiles(current.resourceId)

            val imageCandidates = files
                .orEmpty()
                .filter { file -> file.type == MediaType.IMAGE && !file.isDirectory }

            if (imageCandidates.isEmpty()) {
                return@runBlocking writeFallback(
                    context = context,
                    appWidgetId = appWidgetId,
                    current = current,
                    fallbackMessage = context.getString(R.string.widget_random_photo_frame_cache_empty)
                )
            }

            val selectionPool = if (imageCandidates.size > 1 && current.selectedFilePath.isNotBlank()) {
                imageCandidates.filterNot { it.path == current.selectedFilePath }.ifEmpty { imageCandidates }
            } else {
                imageCandidates
            }
            val selectedFile = selectionPool.random()
            val renderUri = resolveRenderUri(
                thumbnailCacheRepository = deps.thumbnailCacheRepository(),
                file = selectedFile
            )

            if (renderUri == null) {
                return@runBlocking writeFallback(
                    context = context,
                    appWidgetId = appWidgetId,
                    current = current,
                    fallbackMessage = context.getString(R.string.widget_random_photo_frame_resource_missing)
                )
            }

            val snapshot = current.copy(
                selectedFilePath = selectedFile.path,
                selectedThumbnailUri = renderUri.toString(),
                hasRenderablePhoto = true,
                fallbackMessage = ""
            )
            RandomPhotoFrameSnapshotStore.write(
                context = context,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                notifyWidgets = false
            )
            snapshot
        }
    }

    private suspend fun resolveRenderUri(
        thumbnailCacheRepository: ThumbnailCacheRepository,
        file: MediaFile,
    ): Uri? {
        val cachedThumbnail = thumbnailCacheRepository.getCachedThumbnail(file.path)
        if (cachedThumbnail != null) {
            return Uri.fromFile(cachedThumbnail)
        }
        return when {
            file.contentUri?.startsWith("content://") == true -> Uri.parse(file.contentUri)
            isSafeDeviceLocalPath(file.path) -> Uri.fromFile(File(file.path))
            else -> null
        }
    }

    private fun isSafeDeviceLocalPath(path: String): Boolean {
        if (!(path.startsWith("/") || path.startsWith("file:/"))) {
            return false
        }
        val file = if (path.startsWith("file:/")) File(Uri.parse(path).path.orEmpty()) else File(path)
        return file.exists()
    }

    private fun writeFallback(
        context: Context,
        appWidgetId: Int,
        current: RandomPhotoFrameSnapshotStore.Snapshot,
        fallbackMessage: String,
    ): RandomPhotoFrameSnapshotStore.Snapshot {
        val snapshot = current.copy(
            selectedFilePath = "",
            selectedThumbnailUri = "",
            hasRenderablePhoto = false,
            fallbackMessage = fallbackMessage,
        )
        RandomPhotoFrameSnapshotStore.write(
            context = context,
            appWidgetId = appWidgetId,
            snapshot = snapshot,
            notifyWidgets = false
        )
        return snapshot
    }

    private fun entryPoint(context: Context): RandomPhotoFrameWidgetRefreshEntryPoint {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            RandomPhotoFrameWidgetRefreshEntryPoint::class.java
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RandomPhotoFrameWidgetRefreshEntryPoint {
    fun cachedFileListRepository(): CachedFileListRepository
    fun thumbnailCacheRepository(): ThumbnailCacheRepository
}