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
     * Per spec: 1-2 buttons take full width, 3+ arranged in rows
     */
    private fun createDestinationButtons(destinations: List<MediaResource>) {
        Log.d(TAG, "createDestinationButtons() called with ${destinations.size} destinations")
        val container = binding.layoutDestinations
        container.removeAllViews()
        
        // For 1-2 destinations: full width buttons (each button takes full row)
        // For 3+ destinations: multiple buttons per row
        val buttonsPerRow = when {
            destinations.size <= 2 -> 1  // Full width for 1-2 buttons
            destinations.size == 3 -> 3
            destinations.size == 4 -> 2  // 2x2 grid
            destinations.size <= 6 -> 3  // 3 per row
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
                        if (index > 0) topMargin = 12
                    }
                    gravity = Gravity.CENTER
                }
                container.addView(currentRow)
            }
            
            // Create button with better styling
            val button = MaterialButton(context).apply {
                text = destination.name
                setBackgroundColor(destination.destinationColor)
                setTextColor(Color.WHITE)
                textSize = 16f
                minHeight = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) // ~48dp
                setPadding(16, 16, 16, 16)
                
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    if (index % buttonsPerRow > 0) leftMargin = 12
                    topMargin = 4
                    bottomMargin = 4
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
        
        // Create cancellable job for move operation
        scope.launch {
            try {
                val destinationFolder = File(destination.path)
                
                val operation = FileOperation.Move(
                    sources = sourceFiles,
                    destination = destinationFolder,
                    overwrite = overwriteFiles
                )
                
                // Show FileOperationProgressDialog with cancel support
                val progressDialog = FileOperationProgressDialog.show(
                    context,
                    "Moving",
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
                            if (progress is com.sza.fastmediasorter_v2.domain.usecase.FileOperationProgress.Completed) {
                                handleMoveResult(progress.result, destinationFolder)
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Operation cancelled by user
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Move operation cancelled", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                    binding.layoutDestinations.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
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
    
    private fun handleMoveResult(result: FileOperationResult, destinationFolder: File) {
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
    }
}
