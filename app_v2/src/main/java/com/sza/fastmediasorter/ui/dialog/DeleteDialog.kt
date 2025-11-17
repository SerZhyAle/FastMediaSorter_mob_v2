package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogDeleteBinding
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
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
            
            btnCancel.setOnClickListener { dismiss() }
            btnDelete.setOnClickListener { deleteFiles() }
        }
        
        window?.setBackgroundDrawableResource(R.drawable.bg_delete_dialog)
    }

    private fun deleteFiles() {
        // Create cancellable job for delete operation
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            try {
                val operation = FileOperation.Delete(files)
                
                // Show FileOperationProgressDialog with cancel support
                val progressDialog = FileOperationProgressDialog.show(
                    context,
                    "Deleting",
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
                Toast.makeText(context, "Delete operation cancelled", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Delete error: ${e.message}", Toast.LENGTH_LONG).show()
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
                    "Deleted ${result.processedCount} of ${result.processedCount + result.failedCount} files",
                    Toast.LENGTH_LONG
                ).show()
                onComplete()
                dismiss()
            }
            is FileOperationResult.Failure -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.delete_failed, result.error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
