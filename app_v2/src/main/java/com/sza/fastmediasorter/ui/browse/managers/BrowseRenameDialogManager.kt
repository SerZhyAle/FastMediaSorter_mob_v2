package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogRenameMultipleBinding
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.ui.dialog.RenameDialog
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

internal class BrowseRenameDialogManager(
    private val activity: AppCompatActivity,
    private val callbacks: BrowseDialogHelper.DialogCallbacks
) {
    fun showRenameDialog(selectedFiles: List<MediaFile>) {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(activity, R.string.no_files_selected, Toast.LENGTH_SHORT).show()
            return
        }

        when {
            selectedFiles.size == 1 && selectedFiles.first().isDirectory -> {
                showRenameDirectoryDialog(selectedFiles.first())
            }

            selectedFiles.size == 1 -> {
                showRenameSingleDialog(selectedFiles.first())
            }

            else -> {
                showRenameMultipleDialog(selectedFiles)
            }
        }
    }

    private fun showRenameDirectoryDialog(file: MediaFile) {
        val currentName = file.name.ifBlank { file.path.trimEnd('/').substringAfterLast('/') }
        val editText = android.widget.EditText(activity).apply {
            setText(currentName)
            selectAll()
            inputType = InputType.TYPE_CLASS_TEXT
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.rename)
            .setView(editText)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    callbacks.onDirectoryRenameConfirmed(file.path, newName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(activity)
    }

    private fun showRenameSingleDialog(mediaFile: MediaFile) {
        val file = createDisplayFile(mediaFile.path, mediaFile.name)
        RenameDialog(
            context = activity,
            lifecycleOwner = callbacks.getLifecycleOwner(),
            files = listOf(file),
            sourceFolderName = callbacks.getResourceName() ?: "",
            fileOperationUseCase = callbacks.getFileOperationUseCase(),
            onComplete = { oldPath, newFile ->
                callbacks.setIgnoringFileChanges(true)
                callbacks.updateFile(oldPath, callbacks.createMediaFileFromFile(newFile))
                restoreFileObserverLater()
            }
        ).show()
    }

    private fun showRenameMultipleDialog(mediaFiles: List<MediaFile>) {
        val files = mediaFiles.map { createDisplayFile(it.path, it.name) }
        val dialogBinding = DialogRenameMultipleBinding.inflate(LayoutInflater.from(activity))
        val adapter = BrowseRenameFilesAdapter(files.map { it.name }.toMutableList())
        dialogBinding.rvFileNames.apply {
            layoutManager = LinearLayoutManager(activity)
            this.adapter = adapter
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(
                activity.getString(
                    R.string.renaming_n_files_from_folder,
                    files.size,
                    callbacks.getResourceName() ?: ""
                )
            )
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialogBinding.btnApply.setOnClickListener {
            val newNames = adapter.getFileNames()
            callbacks.getLifecycleOwner().lifecycleScope.launch {
                var renamedCount = 0
                val errors = mutableListOf<String>()
                val undoPairs = mutableListOf<Pair<String, String>>()

                files.forEachIndexed { index, file ->
                    val newName = newNames.getOrNull(index)?.trim().orEmpty()
                    if (newName.isBlank() || newName == file.name) {
                        return@forEachIndexed
                    }

                    val oldPath = file.absolutePath
                    val originalName = file.name

                    try {
                        when (val result = callbacks.getFileOperationUseCase()
                            .execute(FileOperation.Rename(file, newName))) {
                            is FileOperationResult.Success -> {
                                renamedCount++
                                val newPath = result.copiedFilePaths.firstOrNull()
                                    ?: resolveRenamedPath(file, newName)
                                undoPairs.add(newPath to originalName)
                                callbacks.setIgnoringFileChanges(true)
                                callbacks.updateFile(
                                    oldPath,
                                    callbacks.createMediaFileFromFile(createDisplayFile(newPath, newName))
                                )
                                restoreFileObserverLater()
                            }

                            is FileOperationResult.Failure -> {
                                val message = result.errorRes?.let {
                                    activity.getString(it, *result.formatArgs.toTypedArray())
                                } ?: if (result.error.contains("already exists", ignoreCase = true)) {
                                    activity.getString(R.string.file_already_exists, newName)
                                } else {
                                    activity.getString(R.string.rename_failed_generic)
                                }
                                errors.add("${file.name}: $message")
                            }

                            else -> {
                                errors.add("${file.name}: ${activity.getString(R.string.rename_failed_generic)}")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "showRenameMultipleDialog: batch rename failed for %s", file.path)
                        errors.add("${file.name}: ${activity.getString(R.string.rename_failed_generic)}")
                    }
                }

                if (undoPairs.isNotEmpty()) {
                    callbacks.saveUndoOperation(
                        UndoOperation(
                            type = FileOperationType.RENAME,
                            sourceFiles = undoPairs.map { it.first },
                            destinationFolder = null,
                            copiedFiles = null,
                            oldNames = undoPairs
                        )
                    )
                }

                if (renamedCount > 0) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.renamed_n_files, renamedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                if (errors.isNotEmpty()) {
                    Toast.makeText(activity, errors.joinToString("\n"), Toast.LENGTH_LONG).show()
                }

                dialog.dismiss()
            }
        }

        dialog.showBoundToHost(activity)
        com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper.applyInitialFocus(dialog)
        dialogBinding.rvFileNames.postDelayed({
            val firstViewHolder = dialogBinding.rvFileNames.findViewHolderForAdapterPosition(0)
            if (firstViewHolder is BrowseRenameFilesAdapter.ViewHolder) {
                firstViewHolder.binding.etFileName.requestFocus()
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(firstViewHolder.binding.etFileName, InputMethodManager.SHOW_IMPLICIT)
            }
        }, FILE_OBSERVER_RESUME_DELAY_MS)
    }

    private fun createDisplayFile(path: String, displayName: String): File {
        if (!isVirtualPath(path)) {
            return File(path)
        }
        return object : File(path) {
            override fun getAbsolutePath(): String = path
            override fun getPath(): String = path
            override fun getName(): String = displayName
        }
    }

    private fun resolveRenamedPath(file: File, newName: String): String {
        val path = file.path
        return if (isVirtualPath(path)) {
            "${path.substring(0, path.lastIndexOf('/'))}/$newName"
        } else {
            File(file.parent, newName).absolutePath
        }
    }

    private fun restoreFileObserverLater() {
        Handler(Looper.getMainLooper()).postDelayed({
            callbacks.setIgnoringFileChanges(false)
        }, FILE_OBSERVER_RESUME_DELAY_MS)
    }

    private fun isVirtualPath(path: String): Boolean {
        return path.startsWith("smb://") ||
            path.startsWith("sftp://") ||
            path.startsWith("ftp://") ||
            path.startsWith("cloud://")
    }

    private companion object {
        const val FILE_OBSERVER_RESUME_DELAY_MS = 200L
    }
}
