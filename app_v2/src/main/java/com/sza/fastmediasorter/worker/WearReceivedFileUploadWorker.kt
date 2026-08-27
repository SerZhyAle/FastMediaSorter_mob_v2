package com.sza.fastmediasorter.worker

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
import com.google.gson.Gson
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.domain.model.WearFileUploadOutcome
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.service.WearDataLayerPaths
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File

/**
 * S2044: uploads a watch file previously staged in local storage to its configured remote destination.
 *
 * Runs out of band after [com.sza.fastmediasorter.domain.usecase.ReceiveWatchFileUseCase] has returned
 * and the Data Layer channel is closed. Reports completion or failures through system notifications.
 */
@HiltWorker
class WearReceivedFileUploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val fileOperationUseCase: FileOperationUseCase,
    private val wearableDataLayerRepository: WearableDataLayerRepository,
    private val gson: Gson
) : CoroutineWorker(context, params) {

    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        val stagedPath = inputData.getString(KEY_STAGED_PATH) ?: return Result.failure()
        val resourceId = inputData.getLong(KEY_RESOURCE_ID, -1L)
        val parentPath = inputData.getString(KEY_PARENT_PATH) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()

        if (resourceId == -1L) return Result.failure()

        val stagedFile = File(stagedPath)
        if (!stagedFile.exists()) {
            Timber.w("WearReceivedFileUploadWorker: staged file %s missing", stagedPath)
            return Result.failure()
        }

        val destinationFile = File("$parentPath/$fileName")
        val operation = FileOperation.Move(
            sources = listOf(stagedFile),
            destination = destinationFile,
            overwrite = false
        )

        val result = fileOperationUseCase.execute(operation)
        val workResult = handleOperationResult(result, fileName, parentPath)
        publishWatchOutcome(result, fileName, parentPath)
        return workResult
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun publishWatchOutcome(
        result: FileOperationResult,
        fileName: String,
        parentPath: String
    ) {
        try {
            val destinationName = inputData.getString(KEY_DESTINATION_NAME)
                ?: parentPath.substringAfterLast('/', parentPath)
            val outcome = WearFileUploadOutcome(
                fileName = fileName,
                succeeded = result is FileOperationResult.Success,
                destination = destinationName,
                completedAtMillis = System.currentTimeMillis()
            )
            wearableDataLayerRepository.putDataItem(
                WearDataLayerPaths.FILE_UPLOAD_OUTCOME,
                gson.toJson(outcome).toByteArray(Charsets.UTF_8)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to publish WearFileUploadOutcome for %s", fileName)
        }
    }

    private fun handleOperationResult(
        result: FileOperationResult,
        fileName: String,
        parentPath: String
    ): Result {
        return when (result) {
            is FileOperationResult.Success -> {
                Timber.i("WearReceivedFileUploadWorker: successfully uploaded %s to %s", fileName, parentPath)
                postTerminalNotification(
                    title = context.getString(R.string.wear_received_upload_notif_channel),
                    message = context.getString(R.string.wear_received_upload_notif_success, fileName, parentPath)
                )
                Result.success()
            }
            is FileOperationResult.Failure -> {
                val isUnreachable = result.errorRes == R.string.transfer_destination_unreachable
                if (isUnreachable) {
                    Timber.w("WearReceivedFileUploadWorker: destination %s unreachable for %s", parentPath, fileName)
                    postTerminalNotification(
                        title = context.getString(R.string.wear_received_upload_notif_channel),
                        message = context.getString(
                            R.string.wear_received_upload_notif_unreachable,
                            fileName,
                            parentPath
                        )
                    )
                } else {
                    Timber.w(
                        "WearReceivedFileUploadWorker: failed to upload %s to %s (%s)",
                        fileName,
                        parentPath,
                        result.error
                    )
                    postTerminalNotification(
                        title = context.getString(R.string.wear_received_upload_notif_channel),
                        message = context.getString(R.string.wear_received_upload_notif_failed, fileName, parentPath)
                    )
                }
                Result.failure()
            }
            is FileOperationResult.AuthenticationRequired -> {
                Timber.w(
                    "WearReceivedFileUploadWorker: authentication required for %s (%s)",
                    parentPath,
                    result.message
                )
                postTerminalNotification(
                    title = context.getString(R.string.wear_received_upload_notif_channel),
                    message = context.getString(R.string.wear_received_upload_notif_unreachable, fileName, parentPath)
                )
                Result.failure()
            }
            is FileOperationResult.PartialSuccess -> {
                Timber.w("WearReceivedFileUploadWorker: partial success for %s to %s", fileName, parentPath)
                postTerminalNotification(
                    title = context.getString(R.string.wear_received_upload_notif_channel),
                    message = context.getString(R.string.wear_received_upload_notif_failed, fileName, parentPath)
                )
                Result.failure()
            }
            is FileOperationResult.PermissionRequired -> {
                Timber.w("WearReceivedFileUploadWorker: permission required for %s to %s", fileName, parentPath)
                postTerminalNotification(
                    title = context.getString(R.string.wear_received_upload_notif_channel),
                    message = context.getString(R.string.wear_received_upload_notif_failed, fileName, parentPath)
                )
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: ""
        val parentPath = inputData.getString(KEY_PARENT_PATH) ?: ""
        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.wear_received_upload_notif_channel))
            .setContentText(context.getString(R.string.wear_received_upload_notif_progress, fileName, parentPath))
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationIds.WEAR_RECEIVED_FILE_UPLOAD,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                NotificationIds.WEAR_RECEIVED_FILE_UPLOAD,
                notification
            )
        }
    }

    private fun postTerminalNotification(title: String, message: String) {
        createNotificationChannel()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_received_upload_notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "wear_received_file_upload"
        const val KEY_STAGED_PATH = "staged_path"
        const val KEY_RESOURCE_ID = "resource_id"
        const val KEY_PARENT_PATH = "parent_path"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_DESTINATION_NAME = "destination_name"
    }
}
