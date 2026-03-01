package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogDeleteBinding
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.DeletePathPolicy
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DeleteDialog(
    context: Context,
    private val files: List<File>,
    private val sourceFolderName: String,
    private val fileOperationUseCase: FileOperationUseCase,
    private val onComplete: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogDeleteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDeleteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            tvMessage.text = if (files.size == 1) {
                context.getString(
                    R.string.delete_file_confirmation,
                    files.first().name,
                    sourceFolderName
                )
            } else {
                context.getString(
                    R.string.delete_files_confirmation,
                    files.size,
                    sourceFolderName
                )
            }
            
            btnCancel.setOnClickListenerDebounced { dismiss() }
            btnDelete.setOnClickListenerDebounced { deleteFiles() }
        }
        
        window?.setBackgroundDrawableResource(R.drawable.bg_delete_dialog)
    }

    private fun deleteFiles() {
        // Create cancellable job for delete operation
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            try {
                // Convert all paths to File objects with network path preservation
                val filesWithPreservedPaths = files.map { file ->
                    val path = file.absolutePath
                    if (path.startsWith("smb://") || path.startsWith("sftp://") || path.startsWith("ftp://")) {
                        // Network file - wrap in File object with original path
                        object : java.io.File(path) {
                            override fun getAbsolutePath(): String = path
                            override fun getPath(): String = path
                        }
                    } else {
                        file
                    }
                }
                
                val canUseSoftDelete = DeletePathPolicy.canUseSoftDelete(
                    filesWithPreservedPaths.map { it.absolutePath }
                )

                val operation = FileOperation.Delete(
                    files = filesWithPreservedPaths,
                    softDelete = canUseSoftDelete
                )
                
                // Show FileOperationProgressDialog with cancel support
                val progressDialog = FileOperationProgressDialog.show(
                    context,
                    context.getString(R.string.deleting_files),
                    onCancel = { 
                        cancel() // Cancel this coroutine job
                    }
                )
                
                // Use executeWithProgress to get progress updates
                withContext(Dispatchers.IO) {
                    fileOperationUseCase.executeWithProgress(operation).collect { progress ->
                        // Update progress dialog on main thread
                        withContext(Dispatchers.Main) {
                            progressDialog.updateProgress(progress)
                            
                            // Handle completion
                            if (progress is com.sza.fastmediasorter.domain.usecase.FileOperationProgress.Completed) {
                                handleDeleteResult(progress.result)
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Operation cancelled by user
                Toast.makeText(context, R.string.toast_delete_cancelled, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_delete_error, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun handleDeleteResult(result: FileOperationResult) {
        when (result) {
            is FileOperationResult.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted_n_files, result.processedCount),
                    Toast.LENGTH_SHORT
                ).show()
                onComplete()
                dismiss()
            }
            is FileOperationResult.PartialSuccess -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted_n_of_m_files, result.processedCount, result.processedCount + result.failedCount),
                    Toast.LENGTH_LONG
                ).show()
                onComplete()
                dismiss()
            }
            is FileOperationResult.PermissionRequired -> {
                // Android 11+ batch delete - launch permission dialog
                try {
                    (context as? android.app.Activity)?.let { activity ->
                        val intentSender = result.pendingIntent.intentSender
                        activity.startIntentSenderForResult(
                            intentSender,
                            DELETE_PERMISSION_REQUEST_CODE,
                            null, 0, 0, 0
                        )
                    }
                    dismiss()
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "DeleteDialog: Failed to launch batch delete permission request")
                    Toast.makeText(
                        context,
                        context.getString(R.string.delete_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            is FileOperationResult.Failure -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.delete_failed, result.error),
                    Toast.LENGTH_LONG
                ).show()
            }
            is FileOperationResult.AuthenticationRequired -> {
                Toast.makeText(
                    context,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    companion object {
        const val DELETE_PERMISSION_REQUEST_CODE = 1001
    }
}
