package com.sza.fastmediasorter_v2.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.databinding.DialogMoveToBinding
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.FileOperationType
import com.sza.fastmediasorter_v2.domain.model.UndoOperation
import com.sza.fastmediasorter_v2.domain.usecase.FileOperation
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetDestinationsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MoveToDialog(
    context: Context,
    private val sourceFiles: List<File>,
    private val sourceFolderName: String,
    private val currentResourceId: Long,
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val overwriteFiles: Boolean,
    private val onComplete: (UndoOperation?) -> Unit
) : Dialog(context) {
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "MoveToDialog"
    }

    private lateinit var binding: DialogMoveToBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogMoveToBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadDestinations()
    }

    private fun setupUI() {
        binding.apply {
            tvFileCount.text = context.getString(
                R.string.moving_n_files_from_folder,
                sourceFiles.size,
                sourceFolderName
            )
            
            btnCancel.setOnClickListener { dismiss() }
        }
    }

    private fun loadDestinations() {
        Log.d(TAG, "loadDestinations() called")
        scope.launch {
            try {
                val destinations = withContext(Dispatchers.IO) {
                    getDestinationsUseCase.getDestinationsExcluding(currentResourceId)
                }
                
                Log.d(TAG, "Loaded ${destinations.size} destinations")
                destinations.forEach { dest ->
                    Log.d(TAG, "Destination: ${dest.name}, order=${dest.destinationOrder}, color=${dest.destinationColor}")
                }
                
                if (destinations.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.no_destinations_available),
                        Toast.LENGTH_SHORT
                    ).show()
                    dismiss()
                } else {
                    createDestinationButtons(destinations)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading destinations", e)
                Toast.makeText(context, "Error loading destinations: ${e.message}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    /**
     * Create colored destination buttons dynamically based on count
     * Per spec: 1-5 buttons per row, arranged in rows
     */
    private fun createDestinationButtons(destinations: List<MediaResource>) {
        Log.d(TAG, "createDestinationButtons() called with ${destinations.size} destinations")
        val container = binding.layoutDestinations
        container.removeAllViews()
        
        val buttonsPerRow = when {
            destinations.size == 1 -> 1
            destinations.size == 2 -> 2
            destinations.size == 3 -> 3
            destinations.size == 4 -> 4
            destinations.size <= 5 -> 5
            destinations.size <= 8 -> 4
            else -> 5
        }
        
        Log.d(TAG, "buttonsPerRow = $buttonsPerRow")
        
        var currentRow: LinearLayout? = null
        
        destinations.forEachIndexed { index, destination ->
            // Create new row if needed
            if (index % buttonsPerRow == 0) {
                Log.d(TAG, "Creating new row for index $index")
                currentRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = 8
                    }
                    gravity = Gravity.CENTER
                }
                container.addView(currentRow)
            }
            
            // Create button
            val button = MaterialButton(context).apply {
                text = destination.name
                setBackgroundColor(destination.destinationColor)
                setTextColor(Color.WHITE)
                textSize = 14f
                
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    if (index % buttonsPerRow > 0) leftMargin = 8
                }
                
                setOnClickListener {
                    moveToDestination(destination)
                }
            }
            
            Log.d(TAG, "Added button for ${destination.name} with color ${destination.destinationColor}")
            currentRow?.addView(button)
        }
        
        Log.d(TAG, "Finished creating ${destinations.size} destination buttons")
    }

    private fun moveToDestination(destination: MediaResource) {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutDestinations.isEnabled = false
        
        scope.launch {
            try {
                val destinationFolder = File(destination.path)
                
                val operation = FileOperation.Move(
                    sources = sourceFiles,
                    destination = destinationFolder,
                    overwrite = overwriteFiles
                )
                
                val result = withContext(Dispatchers.IO) {
                    fileOperationUseCase.execute(operation)
                }
                
                when (result) {
                    is FileOperationResult.Success -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.moved_n_files, result.processedCount),
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Create UndoOperation for move
                        val undoOp = UndoOperation(
                            type = FileOperationType.MOVE,
                            sourceFiles = sourceFiles.map { it.absolutePath },
                            destinationFolder = destinationFolder.absolutePath,
                            copiedFiles = result.copiedFilePaths,
                            oldNames = null,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        onComplete(undoOp)
                        dismiss()
                    }
                    is FileOperationResult.PartialSuccess -> {
                        val message = buildString {
                            append(context.getString(
                                R.string.moved_n_of_m_files,
                                result.processedCount,
                                result.processedCount + result.failedCount
                            ))
                            append("\n\nErrors:\n")
                            result.errors.take(5).forEach { error ->
                                append("\n$error\n")
                            }
                            if (result.errors.size > 5) {
                                append("\n... and ${result.errors.size - 5} more errors")
                            }
                        }
                        
                        com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                            context,
                            "Partial Move Success",
                            message
                        )
                        
                        onComplete(null)
                        dismiss()
                    }
                    is FileOperationResult.Failure -> {
                        com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                            context,
                            "Move Failed",
                            context.getString(R.string.move_failed, result.error),
                            "Check logcat for detailed information (tag: FileOperation)"
                        )
                        
                        binding.progressBar.visibility = View.GONE
                        binding.layoutDestinations.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                    context,
                    "Move Error",
                    e.message ?: "Unknown error",
                    e.stackTraceToString()
                )
                
                binding.progressBar.visibility = View.GONE
                binding.layoutDestinations.isEnabled = true
            }
        }
    }
}
