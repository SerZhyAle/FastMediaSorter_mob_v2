package com.sza.fastmediasorter.ui.player.fileops

import android.app.PendingIntent

sealed class PlayerFileOperationEvent {
    data class Enqueued(val op: PlayerFileOperation) : PlayerFileOperationEvent()
    data class Started(val op: PlayerFileOperation) : PlayerFileOperationEvent()
    data class Succeeded(
        val op: PlayerFileOperation,
        val processedCount: Int,
    ) : PlayerFileOperationEvent()

    data class Failed(
        val op: PlayerFileOperation,
        val message: String,
        val retryable: Boolean,
    ) : PlayerFileOperationEvent()

    data class PermissionRequired(
        val op: PlayerFileOperation,
        val pendingIntent: PendingIntent,
    ) : PlayerFileOperationEvent()

    data class AuthRequired(
        val op: PlayerFileOperation,
        val provider: String,
        val message: String,
    ) : PlayerFileOperationEvent()

    data object Drained : PlayerFileOperationEvent()
}