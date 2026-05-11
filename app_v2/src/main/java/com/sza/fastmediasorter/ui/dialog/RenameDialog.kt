package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogRenameBinding
import com.sza.fastmediasorter.databinding.ItemRenameFileBinding
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class RenameDialog(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val files: List<File>,
    private val sourceFolderName: String,
    private val fileOperationUseCase: FileOperationUseCase,
    private val onNameChosen: ((oldPath: String, newName: String) -> Unit)? = null,
    private val onComplete: (oldPath: String, newFile: File) -> Unit,
    private val onBeforeRename: ((oldPath: String) -> Unit)? = null,
) : Dialog(context) {

    private lateinit var binding: DialogRenameBinding
    private var renameFilesAdapter: RenameFilesAdapter? = null

    // Keep rename errors resource-driven so UI does not depend on raw handler wording.
    private fun FileOperationResult.Failure.toRenameFailureMessage(newName: String): String {
        val localizedMessage = errorRes?.let { context.getString(it, *formatArgs.toTypedArray()) }
        return when {
            errorRes == R.string.file_already_exists -> localizedMessage.orEmpty()
            error.contains("already exists", ignoreCase = true) -> context.getString(R.string.file_already_exists, newName)
            localizedMessage != null -> localizedMessage
            else -> context.getString(R.string.rename_failed_generic)
        }
    }

    private fun FileOperationResult.Failure.isAlreadyExistsFailure(): Boolean {
        return errorRes == R.string.file_already_exists || error.contains("already exists", ignoreCase = true)
    }

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
                
                // Show keyboard automatically
                etFileName.requestFocus()
                etFileName.postDelayed({
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(etFileName, InputMethodManager.SHOW_IMPLICIT)
                }, 200)
            } else {
                val fileNames = files.map { it.name }.toMutableList()
                tilFileName.visibility = android.view.View.GONE
                rvFileNames.visibility = android.view.View.VISIBLE
                renameFilesAdapter = RenameFilesAdapter(fileNames)
                rvFileNames.layoutManager = LinearLayoutManager(context)
                rvFileNames.adapter = renameFilesAdapter

                rvFileNames.postDelayed({
                    val firstViewHolder = rvFileNames.findViewHolderForAdapterPosition(0)
                    if (firstViewHolder is RenameFilesAdapter.ViewHolder) {
                        firstViewHolder.binding.etFileName.requestFocus()
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showSoftInput(firstViewHolder.binding.etFileName, InputMethodManager.SHOW_IMPLICIT)
                    }
                }, 200)
            }
            
            btnCancel.setOnClickListenerDebounced { dismiss() }
            btnApply.setOnClickListenerDebounced { renameFiles() }
        }

        window?.setBackgroundDrawableResource(R.drawable.bg_rename_dialog)
        DialogKeyboardDelegate.applyTo(this, onConfirm = ::renameFiles)
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
            binding.tilFileName.error = context.getString(R.string.rename_file_name_empty)
            return
        }
        
        val file = files.first()
        
        if (newName == file.name) {
            dismiss()
            return
        }

        val oldPath = file.absolutePath
        onBeforeRename?.invoke(oldPath)

        onNameChosen?.let { callback ->
            callback(oldPath, newName)
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
                        
                        // For network paths, manually construct new path
                        val filePath = file.path
                        val newFile = if (filePath.startsWith("smb://") || filePath.startsWith("sftp://") || filePath.startsWith("ftp://")) {
                            val lastSlashIndex = filePath.lastIndexOf('/')
                            val parentPath = filePath.substring(0, lastSlashIndex)
                            val newPath = "$parentPath/$newName"
                            object : File(newPath) {
                                override fun getPath(): String = newPath
                                override fun getAbsolutePath(): String = newPath
                                override fun getName(): String = newName
                            }
                        } else {
                            File(file.parent, newName)
                        }
                        onComplete(oldPath, newFile)
                        
                        dismiss()
                    }
                    is FileOperationResult.Failure -> {
                        val message = result.toRenameFailureMessage(newName)
                        if (result.isAlreadyExistsFailure()) {
                            binding.tilFileName.error = message
                        } else {
                            Timber.w(
                                "RenameDialog: rename failed for %s: %s",
                                file.path,
                                result.error
                            )
                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    else -> {
                        Toast.makeText(context, R.string.toast_unexpected_result, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "RenameDialog: rename failed for ${file.path}")
                Toast.makeText(context, R.string.rename_failed_generic, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renameMultipleFiles() {
        val adapter = renameFilesAdapter ?: run {
            dismiss()
            return
        }

        val newNames = adapter.getFileNames()

        lifecycleOwner.lifecycleScope.launch {
            var renamedCount = 0
            val errors = mutableListOf<String>()

            files.forEachIndexed { index, file ->
                val newName = newNames.getOrNull(index)?.trim().orEmpty()

                if (newName.isEmpty() || newName == file.name) {
                    return@forEachIndexed
                }

                onBeforeRename?.invoke(file.absolutePath)

                try {
                    val operation = FileOperation.Rename(file, newName)
                    when (val result = fileOperationUseCase.execute(operation)) {
                        is FileOperationResult.Success -> {
                            renamedCount++
                            val oldPath = file.absolutePath
                            val filePath = file.path
                            val newFile = if (
                                filePath.startsWith("smb://") ||
                                filePath.startsWith("sftp://") ||
                                filePath.startsWith("ftp://") ||
                                filePath.startsWith("cloud://")
                            ) {
                                val lastSlashIndex = filePath.lastIndexOf('/')
                                val parentPath = filePath.substring(0, lastSlashIndex)
                                val newPath = "$parentPath/$newName"
                                object : File(newPath) {
                                    override fun getPath(): String = newPath
                                    override fun getAbsolutePath(): String = newPath
                                    override fun getName(): String = newName
                                }
                            } else {
                                File(file.parent, newName)
                            }
                            onComplete(oldPath, newFile)
                        }

                        is FileOperationResult.Failure -> {
                            val message = result.toRenameFailureMessage(newName)
                            Timber.w(
                                "RenameDialog: batch rename failed for %s: %s",
                                file.path,
                                result.error
                            )
                            errors.add("${file.name}: $message")
                        }

                        else -> {
                            errors.add("${file.name}: ${context.getString(R.string.toast_unexpected_result)}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "RenameDialog: batch rename failed for ${file.path}")
                    errors.add("${file.name}: ${context.getString(R.string.rename_failed_generic)}")
                }
            }

            if (renamedCount > 0) {
                Toast.makeText(
                    context,
                    context.getString(R.string.renamed_n_files, renamedCount),
                    Toast.LENGTH_SHORT
                ).show()
            }

            if (errors.isNotEmpty()) {
                Toast.makeText(context, errors.joinToString("\n"), Toast.LENGTH_LONG).show()
            }

            dismiss()
        }
    }

    private inner class RenameFilesAdapter(
        private val fileNames: MutableList<String>
    ) : RecyclerView.Adapter<RenameFilesAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemRenameFileBinding) : RecyclerView.ViewHolder(binding.root) {
            private var textWatcher: TextWatcher? = null

            fun bind(fileName: String, position: Int) {
                textWatcher?.let { binding.etFileName.removeTextChangedListener(it) }

                if (binding.etFileName.text.toString() != fileName) {
                    binding.etFileName.setText(fileName)
                }

                textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                    override fun afterTextChanged(s: Editable?) {
                        fileNames[position] = s?.toString().orEmpty()
                    }
                }

                binding.etFileName.addTextChangedListener(textWatcher)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemRenameFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(fileNames[position], position)
        }

        override fun getItemCount(): Int = fileNames.size

        fun getFileNames(): List<String> = fileNames.toList()
    }
}
