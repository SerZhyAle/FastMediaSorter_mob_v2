package com.sza.fastmediasorter.ui.resourceeditor

import android.content.Context
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentResourceEditorBinding
import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Renders the read-only output sections of the resource editor: connection-test result strip,
 * loading indicators, save-button label, validation error messages, and resource statistics.
 *
 * Extracted from ResourceEditorFragment to keep the fragment below the 1000-line cap.
 */
class ResourceEditorOutcomeRenderer(
    private val context: Context,
    private val binding: FragmentResourceEditorBinding,
    private val getMode: () -> ResourceEditorMode,
    private val getCurrentResourceType: () -> ResourceType
) {

    fun renderConnectionResult(result: ResourceConnectionTestResult?) {
        result ?: return

        binding.groupConnectionResult.isVisible = true
        binding.tvConnectionStatus.text = when (result.status) {
            // SUCCESS / PARTIAL: prefer live diagnostic message (subfolder/file count) when available
            ResourceConnectionStatus.SUCCESS -> result.diagnosticMessage ?: context.getString(R.string.connection_success)
            // FAILED diagnostics often come from backend exception text, so keep this surface resource-backed.
            ResourceConnectionStatus.FAILED -> result.errorCode
                ?.let(::getErrorMessage)
                ?: context.getString(R.string.error_connection_failed)
            ResourceConnectionStatus.PARTIAL -> result.diagnosticMessage ?: context.getString(R.string.connection_success)
            ResourceConnectionStatus.NOT_SUPPORTED -> context.getString(R.string.connection_test_not_supported)
        }

        val statusColor = when (result.status) {
            ResourceConnectionStatus.SUCCESS -> context.getColor(R.color.success_green)
            ResourceConnectionStatus.FAILED -> context.getColor(R.color.error_red)
            ResourceConnectionStatus.PARTIAL -> context.getColor(R.color.warning_color)
            ResourceConnectionStatus.NOT_SUPPORTED -> context.getColor(R.color.text_color_secondary)
        }
        binding.tvConnectionStatus.setTextColor(statusColor)
        binding.btnRetry.isVisible = result.status == ResourceConnectionStatus.FAILED
    }

    fun renderLoadingStates(isTestingConnection: Boolean, isSaving: Boolean) {
        binding.progressConnection.isVisible = isTestingConnection
        binding.btnTestConnection.isEnabled = !isTestingConnection && !isSaving
        // btnSave.isEnabled is owned exclusively by renderSaveButton(state.canSave),
        // which already includes !isSaving && !isTestingConnection in the canSave formula.
        binding.progressSave.isVisible = isSaving
    }

    fun renderSaveButton(canSave: Boolean) {
        binding.btnSave.text = when (getMode()) {
            ResourceEditorMode.CREATE -> context.getString(R.string.btn_add_resource)
            ResourceEditorMode.EDIT -> context.getString(R.string.btn_save_changes)
            ResourceEditorMode.COPY -> context.getString(R.string.btn_save_copy)
        }
        binding.btnSave.isEnabled = canSave
    }

    fun getErrorMessage(errorCode: ResourceErrorCode): String = when (errorCode) {
        ResourceErrorCode.EMPTY -> context.getString(R.string.error_field_required)
        ResourceErrorCode.INVALID -> context.getString(R.string.error_invalid_path)
        ResourceErrorCode.TOO_SHORT -> context.getString(R.string.error_invalid_username)
        ResourceErrorCode.TOO_LONG -> context.getString(R.string.error_invalid_username)
        ResourceErrorCode.OUT_OF_RANGE -> context.getString(R.string.error_invalid_port)
        ResourceErrorCode.UNSUPPORTED -> context.getString(R.string.error_unknown)
        ResourceErrorCode.UNREACHABLE -> context.getString(R.string.error_connection_failed)
        ResourceErrorCode.ACCESS_DENIED -> context.getString(R.string.error_connection_failed)
        ResourceErrorCode.TIMEOUT -> context.getString(R.string.error_connection_failed)
        ResourceErrorCode.CONFLICT -> context.getString(R.string.error_save_failed)
        ResourceErrorCode.UNKNOWN -> context.getString(R.string.error_unknown)
    }

    @android.annotation.SuppressLint("SetTextI18n")
    fun renderStatistics(statistics: ResourceStatistics?) {
        val show = statistics != null && getMode() == ResourceEditorMode.EDIT
        binding.cardStatistics.isVisible = show
        binding.headerStatistics.isVisible = show
        binding.groupStatistics.isVisible = show

        if (!show) return
        statistics!! // Smart-cast: `show` already established non-null

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

        val fileCountText = context.getString(R.string.label_file_count, statistics.fileCount)
        val subfolderCountText = context.getString(R.string.label_subfolder_count, statistics.subfolderCount)
        binding.tvStatFileCount.text = "$fileCountText    $subfolderCountText"
        binding.tvStatSubfolderCount.isVisible = false
        binding.tvStatCreatedDate.text = context.getString(
            R.string.label_created_date,
            statistics.createdDate?.let { dateFormat.format(java.util.Date(it)) } ?: context.getString(R.string.label_never)
        )
        binding.tvStatLastBrowseDate.text = context.getString(
            R.string.label_last_browse_date,
            statistics.lastBrowseDate?.let { dateFormat.format(java.util.Date(it)) } ?: context.getString(R.string.label_never)
        )

        val isNetwork = getCurrentResourceType() != ResourceType.LOCAL
        binding.tvStatLastSyncDate.isVisible = isNetwork
        if (isNetwork) {
            binding.tvStatLastSyncDate.text = context.getString(
                R.string.label_last_sync_date,
                statistics.lastSyncDate?.let { dateFormat.format(java.util.Date(it)) } ?: context.getString(R.string.label_never)
            )
        }

        binding.tvStatReadSpeed.isVisible = statistics.readSpeedMbps != null
        if (statistics.readSpeedMbps != null) {
            binding.tvStatReadSpeed.text = context.getString(R.string.label_read_speed, statistics.readSpeedMbps)
        }

        binding.tvStatWriteSpeed.isVisible = statistics.writeSpeedMbps != null
        if (statistics.writeSpeedMbps != null) {
            binding.tvStatWriteSpeed.text = context.getString(R.string.label_write_speed, statistics.writeSpeedMbps)
        }
    }
}
