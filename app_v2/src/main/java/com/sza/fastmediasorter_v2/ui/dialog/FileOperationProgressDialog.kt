package com.sza.fastmediasorter_v2.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationProgress
import timber.log.Timber
import java.text.DecimalFormat

/**
 * Progress dialog for file operations (copy, move, delete)
 * Shows current file being processed, progress percentage, and transfer speed
 * Supports cancellation via callback
 */
class FileOperationProgressDialog(
    context: Context,
    private val operationType: String, // "Copying", "Moving", "Deleting"
    private val onCancel: (() -> Unit)? = null // Callback when user clicks cancel
) : Dialog(context) {

    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentFile: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCancel: android.widget.Button
    
    private val sizeFormatter = DecimalFormat("#,##0.##")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val view = LayoutInflater.from(context).inflate(
            R.layout.dialog_file_operation_progress,
            null,
            false
        )
        
        tvTitle = view.findViewById(R.id.tvProgressTitle)
        tvCurrentFile = view.findViewById(R.id.tvCurrentFile)
        tvProgress = view.findViewById(R.id.tvProgressText)
        tvSpeed = view.findViewById(R.id.tvSpeed)
        progressBar = view.findViewById(R.id.progressBar)
        btnCancel = view.findViewById(R.id.btnCancel)
        
        tvTitle.text = "$operationType files..."
        
        // Setup cancel button
        btnCancel.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }
        
        setContentView(view)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    fun updateProgress(progress: FileOperationProgress) {
        when (progress) {
            is FileOperationProgress.Starting -> {
                Timber.d("FileOperationProgressDialog: Starting - ${progress.totalFiles} files")
                tvProgress.text = "0 / ${progress.totalFiles}"
                tvCurrentFile.text = "Preparing..."
                tvSpeed.text = ""
                progressBar.isIndeterminate = true
            }
            is FileOperationProgress.Processing -> {
                Timber.d("FileOperationProgressDialog: Processing ${progress.currentFile} (${progress.currentIndex}/${progress.totalFiles})")
                
                progressBar.isIndeterminate = false
                progressBar.max = progress.totalFiles
                progressBar.progress = progress.currentIndex
                
                tvProgress.text = "${progress.currentIndex} / ${progress.totalFiles}"
                tvCurrentFile.text = progress.currentFile
                
                // Show speed if available
                if (progress.speedBytesPerSecond > 0) {
                    val speedText = formatSpeed(progress.speedBytesPerSecond)
                    tvSpeed.text = speedText
                } else {
                    tvSpeed.text = ""
                }
            }
            is FileOperationProgress.Completed -> {
                Timber.d("FileOperationProgressDialog: Completed")
                dismiss()
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "${sizeFormatter.format(bytesPerSecond)} B/s"
            bytesPerSecond < 1024 * 1024 -> "${sizeFormatter.format(bytesPerSecond / 1024.0)} KB/s"
            bytesPerSecond < 1024 * 1024 * 1024 -> "${sizeFormatter.format(bytesPerSecond / (1024.0 * 1024.0))} MB/s"
            else -> "${sizeFormatter.format(bytesPerSecond / (1024.0 * 1024.0 * 1024.0))} GB/s"
        }
    }

    companion object {
        fun show(
            context: Context,
            operationType: String,
            onCancel: (() -> Unit)? = null
        ): FileOperationProgressDialog {
            return FileOperationProgressDialog(context, operationType, onCancel).apply {
                show()
            }
        }
    }
}
