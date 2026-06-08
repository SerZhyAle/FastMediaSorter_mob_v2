package com.sza.fastmediasorter.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Background worker to periodically verify file existence and update file counts
 * for all resources: local directories, network shares (SMB, SFTP, FTP).
 * Cloud resources (OAuth-dependent) are excluded intentionally.
 */
@HiltWorker
class NetworkFilesSyncWorker @AssistedInject constructor(
    @Assisted applicationContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaScannerFactory: MediaScannerFactory,
    private val workManagerScheduler: WorkManagerScheduler
) : CoroutineWorker(applicationContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("NetworkFilesSyncWorker: Starting background sync (local + network)")
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Timber.w(e, "NetworkFilesSyncWorker: setForeground failed (non-fatal)")
        }
        return try {
            val resources = resourceRepository.getAllResources().first()
            val settings = settingsRepository.getSettings().first()
            
            // Build size filter from settings (use null if feature disabled)
            val sizeFilter = if (settings.supportImages || settings.supportVideos || settings.supportAudio) {
                com.sza.fastmediasorter.domain.usecase.SizeFilter(
                    imageSizeMin = settings.imageSizeMin,
                    imageSizeMax = settings.imageSizeMax,
                    videoSizeMin = settings.videoSizeMin,
                    videoSizeMax = settings.videoSizeMax,
                    audioSizeMin = settings.audioSizeMin,
                    audioSizeMax = settings.audioSizeMax
                )
            } else null
            
            // Exclude CLOUD: requires active OAuth tokens, not suitable for background worker
            val resourcesToSync = resources.filter {
                it.type == ResourceType.LOCAL ||
                it.type == ResourceType.SMB ||
                it.type == ResourceType.SFTP ||
                it.type == ResourceType.FTP
            }

            if (resourcesToSync.isEmpty()) {
                Timber.d("NetworkFilesSyncWorker: No resources to sync")
                return Result.success()
            }

            val localCount = resourcesToSync.count { it.type == ResourceType.LOCAL }
            val networkCount = resourcesToSync.size - localCount
            Timber.d("NetworkFilesSyncWorker: Syncing ${resourcesToSync.size} resources (local=$localCount, network=$networkCount)")

            resourcesToSync.forEach { resource ->
                try {
                    val scanner = mediaScannerFactory.getScanner(resource.type)
                    
                    val fileCount = scanner.getFileCount(
                        resource.path,
                        resource.supportedMediaTypes,
                        sizeFilter,
                        resource.credentialsId,
                        scanSubdirectories = resource.scanSubdirectories
                    )
                    
                    // Update resource if count changed
                    if (fileCount != resource.fileCount) {
                        Timber.i("NetworkFilesSyncWorker: ${resource.name} file count changed: ${resource.fileCount} → $fileCount")
                        val updatedResource = resource.copy(
                            fileCount = fileCount,
                            lastSyncDate = System.currentTimeMillis()
                        )
                        resourceRepository.updateResource(updatedResource)
                    } else {
                        Timber.d("NetworkFilesSyncWorker: ${resource.name} file count unchanged: $fileCount")
                        // Update lastSyncDate even if count unchanged
                        val updatedResource = resource.copy(lastSyncDate = System.currentTimeMillis())
                        resourceRepository.updateResource(updatedResource)
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "NetworkFilesSyncWorker: Failed to sync ${resource.name}")
                    // Continue with other resources even if one fails
                }
            }
            
            Timber.i("NetworkFilesSyncWorker: Background sync completed (local + network)")

            // X.11: Trigger thumbnail preload for each network resource (SMB/SFTP/FTP)
            if (settings.enableThumbnailPreload) {
                val networkResources = resourcesToSync.filter {
                    it.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB ||
                    it.type == com.sza.fastmediasorter.domain.model.ResourceType.SFTP ||
                    it.type == com.sza.fastmediasorter.domain.model.ResourceType.FTP
                }
                networkResources.forEach { resource ->
                    workManagerScheduler.scheduleThumbnailPreload(resource.id, settings.thumbnailPreloadWifiOnly)
                }
                if (networkResources.isNotEmpty()) {
                    Timber.i("NetworkFilesSyncWorker: queued thumbnail preload for ${networkResources.size} network resource(s)")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "NetworkFilesSyncWorker: Background sync failed")
            Result.failure()
        }
    }
    
    private fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = buildNotification()
        // FOREGROUND_SERVICE_DATA_SYNC permission is only declared in debug builds.
        // In release we use a plain ForegroundInfo without a service type.
        return if (BuildConfig.ENABLE_SCHEDULED_OPERATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.network_sync_settings))
            .setContentText(applicationContext.getString(R.string.sync_status_in_progress))
            .setSmallIcon(R.drawable.ic_notification_audio) // Use generic or sync icon
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.network_sync_settings),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val WORK_NAME = "network_files_sync"
        const val NOTIFICATION_CHANNEL_ID = "network_sync_channel"
        private const val NOTIFICATION_ID = 4201
    }
}
