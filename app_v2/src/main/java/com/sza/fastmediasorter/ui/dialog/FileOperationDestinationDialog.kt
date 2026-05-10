package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import timber.log.Timber
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogCopyToBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Unified dialog for file operations (Copy/Move) to destinations.
 * Replaces separate CopyToDialog and MoveToDialog with single parameterized implementation.
 */
class FileOperationDestinationDialog(
    context: Context,
    private val operationType: FileOperationType, // COPY or MOVE
    private val sourceFiles: List<File>,
    private val sourceFolderName: String,
    private val currentResourceId: Long,
    private val currentBrowsePath: String?, // Current browsing path for network destinations
    private val sourceCredentialsId: String?, // Credentials ID for source resource
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val overwriteFiles: Boolean,
    private val showDetailedErrors: Boolean,
    private val onComplete: (UndoOperation?) -> Unit,

    private val onAuthRequest: ((String) -> Unit)? = null,
    // Extended callback includes destination for Move retry after permission grant
    private val onPermissionRequired: ((android.app.PendingIntent, com.sza.fastmediasorter.domain.model.MediaResource?) -> Unit)? = null,
    // Callback invoked when user clicks "Select folder" button (folder picker delegated to Activity)
    private val onSelectFolderClicked: ((FileOperationType, List<File>, String?) -> Unit)? = null,
    // Callback invoked immediately when the user selects a destination (before the operation runs).
    // Used by BrowseFileOperationsManager to dispatch directory copy/move to the same destination.
    private val onDestinationSelected: ((com.sza.fastmediasorter.domain.model.MediaResource) -> Unit)? = null
) : Dialog(context) {
    
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + scopeJob)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "FileOperationDestinationDialog"
    }

    private lateinit var binding: DialogCopyToBinding

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scopeJob.cancel()  // Cancel all pending coroutines when dialog is dismissed (ML-005)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogCopyToBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set dialog width to 90% of screen width to accommodate buttons
        val width = (context.resources.displayMetrics.widthPixels * 0.90).toInt()
        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        
        setupUI()
        loadDestinations()
    }

    private fun setupUI() {
        binding.apply {
            // Set message based on operation type
            tvFileCount.text = when (operationType) {
                FileOperationType.COPY -> context.getString(
                    R.string.copying_n_files_from_folder,
                    sourceFiles.size,
                    sourceFolderName
                )
                FileOperationType.MOVE -> context.getString(
                    R.string.moving_n_files_from_folder,
                    sourceFiles.size,
                    sourceFolderName
                )
                FileOperationType.ARCHIVE -> context.getString(
                    R.string.archiving_n_files_from_folder,
                    sourceFiles.size,
                    sourceFolderName
                )
                else -> "" // DELETE and RENAME not used in this dialog
            }

            btnCancel.setOnClickListenerDebounced { dismiss() }

            // ARCHIVE mode uses only registered destinations for folder selection —
            // SAF external folder picking is out of scope here.
            btnSelectFolder.visibility = if (operationType == FileOperationType.ARCHIVE) View.GONE else View.VISIBLE
            // "Select folder" button — dismiss dialog and delegate to Activity
            btnSelectFolder.setOnClickListenerDebounced {
                Timber.tag(TAG).d("Select folder button clicked")
                onSelectFolderClicked?.invoke(operationType, sourceFiles, sourceCredentialsId)
                dismiss()
            }
        }
    }

    private fun loadDestinations() {
        Timber.tag(TAG).d("loadDestinations() called")
        scope.launch {
            try {
                val destinations = withContext(Dispatchers.IO) {
                    getDestinationsUseCase.getDestinationsExcluding(currentResourceId)
                }
                
                Timber.tag(TAG).d("Loaded ${destinations.size} destinations")
                destinations.forEach { dest ->
                    Timber.tag(TAG).d("Destination: ${dest.name}, order=${dest.destinationOrder}, color=${dest.destinationColor}")
                }
                
                if (destinations.isEmpty()) {
                    // No registered destinations — show dialog with only "Select folder" button
                    Timber.tag(TAG).d("No destinations: showing dialog with Select Folder button only")
                } else {
                    createDestinationButtons(destinations)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error loading destinations")
                Toast.makeText(context, context.getString(R.string.toast_error_loading_destinations), Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    /**
     * Create colored destination buttons in two rows (max 5 buttons per row)
     * Distribution: 1-5: single row, 6: 3+3, 7: 4+3, 8: 4+4, 9: 5+4, 10: 5+5
     */
    private fun createDestinationButtons(destinations: List<MediaResource>) {
        Timber.tag(TAG).d("createDestinationButtons() called with ${destinations.size} destinations")
        val container = binding.layoutDestinations
        container.removeAllViews()
        
        val destinationsList = destinations.take(10)
        val count = destinationsList.size
        
        // Calculate button distribution across rows (max 5 per row)
        val distribution = when (count) {
            0, 1, 2, 3, 4, 5 -> listOf(count) // Single row
            6 -> listOf(3, 3)
            7 -> listOf(4, 3)
            8 -> listOf(4, 4)
            9 -> listOf(5, 4)
            10 -> listOf(5, 5)
            else -> listOf(5, 5) // Fallback
        }
        
        Timber.tag(TAG).d("Button distribution: $distribution for $count destinations")
        
        // Small margins for spacing (4dp on each side = 8dp total between buttons)
        val marginSize = (4 * context.resources.displayMetrics.density).toInt()
        
        // Create rows
        var destIndex = 0
        distribution.forEach { rowCount ->
            if (rowCount > 0) {
                val buttonRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                
                repeat(rowCount) {
                    if (destIndex < destinationsList.size) {
                        val destination = destinationsList[destIndex]
                        val button = androidx.appcompat.widget.AppCompatButton(context).apply {
                            text = destination.name
                            setTextColor(Color.WHITE)
                            textSize = 16f
                            isAllCaps = false
                            setPadding(8, 32, 8, 32)
                            
                            // Equal weight for buttons in this row
                            layoutParams = LinearLayout.LayoutParams(
                                0, // width 0 with weight for equal distribution
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f // each button gets equal weight
                            ).apply {
                                setMargins(marginSize, 8, marginSize, 8)
                            }
                            
                            minimumWidth = 0
                            minimumHeight = resources.getDimensionPixelSize(R.dimen.destination_button_min_height)
                            elevation = 6f
                            
                            // Rounded corners background
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(destination.destinationColor)
                                cornerRadius = 12f
                            }
                            
                            setOnClickListener {
                                performOperation(destination)
                            }
                        }
                        
                        buttonRow.addView(button)
                        Timber.tag(TAG).d("Added button for ${destination.name} at position $destIndex with color ${destination.destinationColor}")
                        destIndex++
                    }
                }
                
                container.addView(buttonRow)
            }
        }
        
        Timber.tag(TAG).d("Finished creating $destIndex destination buttons in ${distribution.size} rows")
    }

    private fun performOperation(destination: MediaResource) {
        Timber.i("performOperation: ENTRY - destination=${destination.name} (${destination.path}), operationType=$operationType, sourceFiles=${sourceFiles.size}")
        sourceFiles.forEachIndexed { index, file ->
            Timber.d("performOperation: Source[$index]: path=${file.path}, length=${file.length()}")
        }

        // Notify caller of selected destination before executing the operation.
        onDestinationSelected?.invoke(destination)

        // ARCHIVE mode: dialog only captures the destination; the caller performs the archive.
        if (operationType == FileOperationType.ARCHIVE) {
            Timber.d("performOperation: ARCHIVE mode — destination captured, dismissing without running operation")
            dismiss()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.layoutDestinations.isEnabled = false
        
        // Show start message based on operation type
        val totalSize = try {
            sourceFiles.sumOf { it.length() }
        } catch (e: Exception) {
            Timber.e(e, "performOperation: Failed to calculate total size")
            0L
        }
        Timber.d("performOperation: Total size = $totalSize bytes")
        
        if (totalSize > 1024 * 1024) { // > 1MB
            val messageResId = when (operationType) {
                FileOperationType.COPY -> R.string.msg_copy_started
                FileOperationType.MOVE -> R.string.msg_move_started
                else -> R.string.msg_copy_started
            }
            Toast.makeText(
                context,
                context.getString(messageResId, destination.name),
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Create cancellable job for operation
        scope.launch {
            Timber.i("performOperation: Coroutine STARTED in scope.launch")
            // Show progress dialog immediately
            val progressTitleResId = when (operationType) {
                FileOperationType.COPY -> R.string.copying_files
                FileOperationType.MOVE -> R.string.moving_files
                else -> R.string.copying_files
            }
            
            Timber.d("performOperation: Showing progress dialog")
            val progressDialog = FileOperationProgressDialog.show(
                context,
                context.getString(progressTitleResId),
                onCancel = { 
                    Timber.d("performOperation: cancel requested by user") // S0055-D
                    cancel() // Cancel this coroutine job
                }
            )

            try {
                // Determine destination path: for network destinations, use currentBrowsePath if it matches the destination protocol
                // Always use the selected destination's path
                val destinationPath = destination.path
                Timber.i("performOperation: destinationPath = $destinationPath")
                
                // Create File object that preserves network/cloud paths
                val destinationFolder = if (destinationPath.startsWith("smb://") || 
                                            destinationPath.startsWith("sftp://") || 
                                            destinationPath.startsWith("ftp://") ||
                                            destinationPath.startsWith("cloud://")) {
                    object : File(destinationPath) {
                        override fun getAbsolutePath(): String = destinationPath
                        override fun getPath(): String = destinationPath
                    }
                } else {
                    File(destinationPath)
                }
                Timber.d("performOperation: destinationFolder created: ${destinationFolder.path}")
                
                // Create operation based on type
                val operation = when (operationType) {
                    FileOperationType.COPY -> FileOperation.Copy(
                        sources = sourceFiles,
                        destination = destinationFolder,
                        overwrite = overwriteFiles,
                        sourceCredentialsId = sourceCredentialsId
                    )
                    FileOperationType.MOVE -> FileOperation.Move(
                        sources = sourceFiles,
                        destination = destinationFolder,
                        overwrite = overwriteFiles,
                        sourceCredentialsId = sourceCredentialsId
                    )
                    else -> throw IllegalArgumentException("Unsupported operation type: $operationType")
                }
                
                Timber.i("performOperation: Operation created: $operation")
                
                // Use executeWithProgress to get progress updates
                var completed = false
                var lastLoggedPercent = -1
                Timber.i("performOperation: Calling executeWithProgress NOW")
                withContext(Dispatchers.IO) {
                    Timber.d("performOperation: Inside withContext(Dispatchers.IO)")
                    fileOperationUseCase.executeWithProgress(operation).collect { progress ->
                        if (progress is com.sza.fastmediasorter.domain.usecase.FileOperationProgress.Processing) {
                            val pct = if (progress.totalBytes > 0) (progress.bytesTransferred * 100 / progress.totalBytes).toInt() else -1
                            if (pct / 5 != lastLoggedPercent / 5) {
                                lastLoggedPercent = pct
                                Timber.d("performOperation: Progress %d%% (%d/%d bytes)", pct, progress.bytesTransferred, progress.totalBytes)
                            }
                        } else {
                            Timber.d("performOperation: Progress received: $progress")
                        }
                        if (completed) {
                            Timber.w("performOperation: Already completed, ignoring progress")
                            return@collect
                        }
                        
                        // Update progress dialog on main thread
                        withContext(Dispatchers.Main) {
                            progressDialog.updateProgress(progress)
                            
                            // Handle completion
                            if (progress is com.sza.fastmediasorter.domain.usecase.FileOperationProgress.Completed) {
                                Timber.i("performOperation: Operation completed with result: ${progress.result}")
                                completed = true
                                progressDialog.dismiss()
                                handleOperationResult(progress.result, destinationFolder, destination)
                            }
                        }
                    }
                }
                Timber.i("performOperation: executeWithProgress completed, flow ended")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // S0055-D: routine user cancellation — no stack needed
                Timber.i("performOperation: Operation cancelled by user (${operationType.name})")
                withContext(Dispatchers.Main) {
                    val cancelMsgResId = when (operationType) {
                        FileOperationType.COPY -> R.string.toast_copy_cancelled
                        FileOperationType.MOVE -> R.string.toast_move_cancelled
                        else -> R.string.toast_copy_cancelled
                    }
                    Toast.makeText(context, cancelMsgResId, Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                    binding.layoutDestinations.isEnabled = true
                }
            } catch (e: Exception) {
                Timber.e(e, "performOperation: EXCEPTION - ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val errorTitleResId = when (operationType) {
                        FileOperationType.COPY -> R.string.error_operation_title_copy
                        FileOperationType.MOVE -> R.string.error_operation_title_move
                        else -> R.string.error_operation_failed
                    }
                    val failMsgResId = when (operationType) {
                        FileOperationType.COPY -> R.string.copy_failed
                        FileOperationType.MOVE -> R.string.move_failed
                        else -> R.string.copy_failed
                    }

                    val errorMessage = context.getString(failMsgResId)
                    Timber.e("performOperation: Showing error dialog: $errorMessage")

                    showOperationError(
                        title = context.getString(errorTitleResId),
                        message = errorMessage,
                        detailedInfo = e.stackTraceToString()
                    )
                    
                    binding.progressBar.visibility = View.GONE
                    binding.layoutDestinations.isEnabled = true
                }
            }
        }
        Timber.d("performOperation: EXIT (after scope.launch)")
    }
    
    private fun handleOperationResult(
        result: FileOperationResult, 
        destinationFolder: File,
        destinationResource: MediaResource? = null
    ) {
        Timber.i("handleOperationResult: ENTRY - result type=${result.javaClass.simpleName}")
        when (result) {
            is FileOperationResult.Success -> {
                Timber.i("handleOperationResult: SUCCESS - processed ${result.processedCount} files")
                val successMsgResId = when (operationType) {
                    FileOperationType.COPY -> R.string.copied_n_files
                    FileOperationType.MOVE -> R.string.moved_n_files
                    else -> R.string.copied_n_files
                }
                
                Toast.makeText(
                    context,
                    context.getString(successMsgResId, result.processedCount),
                    Toast.LENGTH_SHORT
                ).show()
                
                // Create UndoOperation
                val undoOp = UndoOperation(
                    type = operationType,
                    sourceFiles = sourceFiles.map { it.absolutePath },
                    destinationFolder = destinationFolder.absolutePath,
                    copiedFiles = result.copiedFilePaths,
                    oldNames = null,
                    timestamp = System.currentTimeMillis()
                )
                
                Timber.d("handleOperationResult: Calling onComplete callback")
                onComplete(undoOp)
                dismiss()
            }
            is FileOperationResult.PartialSuccess -> {
                Timber.w("handleOperationResult: PARTIAL SUCCESS - ${result.processedCount} of ${result.processedCount + result.failedCount}")
                val partialMsgResId = when (operationType) {
                    FileOperationType.COPY -> R.string.copied_n_of_m_files
                    FileOperationType.MOVE -> R.string.moved_n_of_m_files
                    else -> R.string.copied_n_of_m_files
                }
                
                val message = buildString {
                    append(context.getString(
                        partialMsgResId,
                        result.processedCount,
                        result.processedCount + result.failedCount
                    ))
                    append("\n\n")
                    append(context.getString(R.string.failed_files))
                    append(":\n")
                    result.errors.take(5).forEach { error ->
                        append("\n")
                        append(error)
                        append("\n")
                    }
                    if (result.errors.size > 5) {
                        append("\n")
                        append(context.getString(R.string.and_more_errors, result.errors.size - 5))
                    }
                }

                com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                    context,
                    context.getString(R.string.error_partial_success),
                    message
                )
                
                onComplete(null)
                dismiss()
            }
            is FileOperationResult.Failure -> {
                Timber.e("handleOperationResult: FAILURE - ${result.error}")
                // Check if this is a Cloud authentication error
                if (result.error.contains("Google Drive authentication required", ignoreCase = true) ||
                    result.error.contains("Not authenticated", ignoreCase = true) ||
                    result.error.contains("expired_access_token", ignoreCase = true)) {
                    showCloudAuthenticationError(result.error, destinationResource)
                } else {
                    val failTitleResId = when (operationType) {
                        FileOperationType.COPY -> R.string.error_operation_title_copy
                        FileOperationType.MOVE -> R.string.error_operation_title_move
                        else -> R.string.error_operation_failed
                    }
                    val failMsgResId = when (operationType) {
                        FileOperationType.COPY -> R.string.copy_failed
                        FileOperationType.MOVE -> R.string.move_failed
                        else -> R.string.copy_failed
                    }

                    val message = if (result.errorRes != null) {
                        context.getString(result.errorRes, *result.formatArgs.toTypedArray())
                    } else {
                        context.getString(failMsgResId)
                    }
                    // Keep the main copy calm; raw transfer details belong only in the optional details surface.
                    val detailedInfo = when {
                        result.formatArgs.isNotEmpty() -> result.formatArgs.joinToString("\n")
                        result.error.isNotBlank() && result.error != message -> result.error
                        else -> null
                    }
                    showOperationError(
                        title = context.getString(failTitleResId),
                        message = message,
                        detailedInfo = detailedInfo
                    )
                }
                
                binding.progressBar.visibility = View.GONE
                binding.layoutDestinations.isEnabled = true
            }
            is FileOperationResult.AuthenticationRequired -> {
                Timber.w("handleOperationResult: AUTHENTICATION REQUIRED - ${result.message}")
                showCloudAuthenticationError(result.message, destinationResource)
                binding.progressBar.visibility = View.GONE
                binding.layoutDestinations.isEnabled = true
            }
            is FileOperationResult.PermissionRequired -> {
                Timber.w("handleOperationResult: PERMISSION REQUIRED")
                // Handle PermissionRequired by invoking callback
                // This allows the Activity to launch the PendingIntent
                Timber.i("Permission required result in dialog - passing to activity with destination=${destinationResource?.name}")
                
                binding.progressBar.visibility = View.GONE
                binding.layoutDestinations.isEnabled = true
                
                if (onPermissionRequired != null) {
                    onPermissionRequired.invoke(result.pendingIntent, destinationResource)
                    dismiss()
                } else {
                    // Fallback if no callback provided
                    Toast.makeText(
                        context,
                        context.getString(R.string.error_delete_permission_required),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showOperationError(title: String, message: String, detailedInfo: String? = null) {
        if (showDetailedErrors && !detailedInfo.isNullOrBlank()) {
            com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                context,
                title,
                message,
                detailedInfo
            )
            return
        }

        com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
            context,
            title,
            message
        )
    }
    
    private fun showCloudAuthenticationError(errorMessage: String, destinationResource: MediaResource? = null) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.authentication_required))
            .setMessage(context.getString(R.string.cloud_auth_copy_error))
            .setNeutralButton(context.getString(R.string.copy_error)) { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Error", errorMessage)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)

        if (onAuthRequest != null) {
            // Try to determine provider from destination resource or error message
            val provider = when {
                destinationResource?.path?.startsWith("cloud://dropbox") == true -> "dropbox"
                destinationResource?.path?.startsWith("cloud://google_drive") == true -> "google_drive"
                destinationResource?.path?.startsWith("cloud://onedrive") == true -> "onedrive"
                errorMessage.contains("Dropbox", ignoreCase = true) -> "dropbox"
                errorMessage.contains("Google", ignoreCase = true) -> "google_drive"
                errorMessage.contains("OneDrive", ignoreCase = true) -> "onedrive"
                else -> null
            }
            
            if (provider != null) {
                builder.setPositiveButton(context.getString(R.string.sign_in)) { _, _ ->
                    onAuthRequest.invoke(provider)
                    dismiss()
                }
            } else {
                builder.setPositiveButton(context.getString(R.string.go_to_resources)) { _, _ ->
                    dismiss()
                }
            }
        } else {
            builder.setPositiveButton(context.getString(R.string.go_to_resources)) { _, _ ->
                dismiss()
            }
        }
            
        builder.show()
    }
}
