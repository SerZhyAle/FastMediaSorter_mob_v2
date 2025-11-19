package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogRenameBinding
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import kotlinx.coroutines.launch
import java.io.File

class RenameDialog(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val files: List<File>,
    private val sourceFolderName: String,
    private val fileOperationUseCase: FileOperationUseCase,
    private val onComplete: (oldPath: String, newFile: File) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogRenameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogRenameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            tvFileCount.text = context.getString(
                R.string.renaming_n_files_from_folder,
                files.size,
                sourceFolderName
            )
            
            if (files.size == 1) {
                // Single file rename
                tilFileName.visibility = android.view.View.VISIBLE
                rvFileNames.visibility = android.view.View.GONE
                
                etFileName.setText(files.first().name)
                etFileName.setSelection(files.first().nameWithoutExtension.length)
                
                etFileName.addTextChangedListener {
                    tilFileName.error = null
                }
            } else {
                // Multiple files rename - would need RecyclerView adapter
                tilFileName.visibility = android.view.View.GONE
                rvFileNames.visibility = android.view.View.VISIBLE
                // TODO: Implement RecyclerView adapter for multiple file rename
            }
            
            btnCancel.setOnClickListener { dismiss() }
            btnApply.setOnClickListener { renameFiles() }
        }
        
        window?.setBackgroundDrawableResource(R.drawable.bg_rename_dialog)
    }

    private fun renameFiles() {
        if (files.size == 1) {
            renameSingleFile()
        } else {
            renameMultipleFiles()
        }
    }

    private fun renameSingleFile() {
        val newName = binding.etFileName.text.toString().trim()
        
        if (newName.isEmpty()) {
            binding.tilFileName.error = "File name cannot be empty"
            return
        }
        
        val file = files.first()
        
        if (newName == file.name) {
            dismiss()
            return
        }
        
        lifecycleOwner.lifecycleScope.launch {
            try {
                val operation = FileOperation.Rename(file, newName)
                val result = fileOperationUseCase.execute(operation)
                
                when (result) {
                    is FileOperationResult.Success -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.renamed_n_files, 1),
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Pass old path and new file to callback for instant update
                        val oldPath = file.absolutePath
                        val newFile = File(file.parent, newName)
                        onComplete(oldPath, newFile)
                        
                        dismiss()
                    }
                    is FileOperationResult.Failure -> {
                        if (result.error.contains("already exists")) {
                            binding.tilFileName.error = context.getString(
                                R.string.file_already_exists,
                                newName
                            )
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.rename_failed, result.error),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    else -> {
                        Toast.makeText(context, "Unexpected result", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Rename error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renameMultipleFiles() {
        // TODO: Implement multiple file rename
        Toast.makeText(context, "Multiple file rename not yet implemented", Toast.LENGTH_SHORT).show()
        dismiss()
    }
}
