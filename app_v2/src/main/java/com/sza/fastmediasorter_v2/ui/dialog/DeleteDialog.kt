package com.sza.fastmediasorter_v2.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.databinding.DialogDeleteBinding
import com.sza.fastmediasorter_v2.domain.usecase.FileOperation
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationUseCase
import kotlinx.coroutines.launch
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
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            try {
                val operation = FileOperation.Delete(files)
                val result = fileOperationUseCase.execute(operation)
                
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
            } catch (e: Exception) {
                Toast.makeText(context, "Delete error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
