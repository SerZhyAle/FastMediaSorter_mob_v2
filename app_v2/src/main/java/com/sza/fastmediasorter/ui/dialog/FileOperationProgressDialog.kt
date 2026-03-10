package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.FileOperationProgress
import timber.log.Timber
import java.text.DecimalFormat

/**
 * Progress dialog for file operations (copy, move, delete)
 * Shows current file being processed, progress percentage, and transfer speed
 * Supports cancellation via callback
 */
@android.annotation.SuppressLint("SetTextI18n")
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

    private var startTime: Long = 0
    private var lastUpdateTime: Long = 0
    private var isStarted: Boolean = false
    private val SHOW_DELAY_MS = 2000L
    private val UPDATE_INTERVAL_MS = 500L // Update UI every 500ms to be more responsive

    private val showHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val showRunnable = Runnable {
        if (!isShowing) {
            try {
                super.show()
                android.widget.Toast.makeText(context, context.getString(R.string.please_wait), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Error showing progress dialog")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dialogRoot = android.widget.FrameLayout(context)
        val view = LayoutInflater.from(context).inflate(
            R.layout.dialog_file_operation_progress,
            dialogRoot,
            false
        )
        
        tvTitle = view.findViewById(R.id.tvProgressTitle)
        tvCurrentFile = view.findViewById(R.id.tvCurrentFile)
        tvProgress = view.findViewById(R.id.tvProgressText)
        tvSpeed = view.findViewById(R.id.tvSpeed)
        progressBar = view.findViewById(R.id.progressBar)
        btnCancel = view.findViewById(R.id.btnCancel)
        
        tvTitle.text = operationType
        
        // Setup cancel button
        btnCancel.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }
        
        setContentView(view)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    fun startShowTimer() {
        showHandler.postDelayed(showRunnable, SHOW_DELAY_MS)
    }

    override fun dismiss() {
        showHandler.removeCallbacks(showRunnable)
        super.dismiss()
    }

    // Cache last received progress so we can apply it when dialog finally shows
    private var pendingProgress: FileOperationProgress.Processing? = null

    fun updateProgress(progress: FileOperationProgress) {
        when (progress) {
            is FileOperationProgress.Starting -> {
                Timber.d("FileOperationProgressDialog: Starting - ${progress.totalFiles} files")
                startTime = System.currentTimeMillis()
                isStarted = true
            }
            is FileOperationProgress.Processing -> {
                if (!isStarted) {
                    startTime = System.currentTimeMillis()
                    isStarted = true
                }
                
                val currentTime = System.currentTimeMillis()
                
                // Always cache latest state so dialog shows correct data when it appears
                pendingProgress = progress
                
                // Apply to UI: always update when file changes (speedBytesPerSecond == 0 means onFileStarted),
                // throttle byte-level updates to avoid flooding the UI
                val isFileChange = progress.speedBytesPerSecond == 0L && progress.bytesTransferred == 0L
                if (isShowing && (isFileChange || currentTime - lastUpdateTime > UPDATE_INTERVAL_MS)) {
                    lastUpdateTime = currentTime
                    applyProgressToUI(progress)
                }
            }
            is FileOperationProgress.Completed -> {
                Timber.d("FileOperationProgressDialog: Completed")
                dismiss()
            }
        }
    }

    private fun applyProgressToUI(progress: FileOperationProgress.Processing) {
        Timber.d("FileOperationProgressDialog: Processing ${progress.currentFile} (${progress.currentIndex + 1}/${progress.totalFiles})")
        
        progressBar.isIndeterminate = false
        progressBar.max = progress.totalFiles
        progressBar.progress = progress.currentIndex + 1
        
        tvProgress.text = "${progress.currentIndex + 1} / ${progress.totalFiles}"
        tvCurrentFile.text = progress.currentFile
        
        // Show speed if available
        if (progress.speedBytesPerSecond > 0) {
            tvSpeed.text = formatSpeed(progress.speedBytesPerSecond)
        } else {
            tvSpeed.text = ""
        }
    }

    override fun onStart() {
        super.onStart()
        // Set dialog width to 90% of screen width — default Dialog window is too narrow
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        // Apply any progress that arrived before the dialog was shown
        pendingProgress?.let { applyProgressToUI(it) }
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
            val dialog = FileOperationProgressDialog(context, operationType, onCancel)
            dialog.startShowTimer()
            return dialog
        }
    }
}
