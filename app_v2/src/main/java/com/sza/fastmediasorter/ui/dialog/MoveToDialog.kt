package com.sza.fastmediasorter.ui.dialog

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
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogMoveToBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
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
        
        // Set dialog width to 90% of screen width to accommodate buttons
        val width = (context.resources.displayMetrics.widthPixels * 0.90).toInt()
        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        
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
     * Create colored destination buttons in a single horizontal row
     * Each button takes equal width (1/N of container width)
     */
    private fun createDestinationButtons(destinations: List<MediaResource>) {
        Log.d(TAG, "createDestinationButtons() called with ${destinations.size} destinations")
        val container = binding.layoutDestinations
        container.removeAllViews()
        
        // Create single horizontal row for all buttons
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(buttonRow)
        
        // Small margins for spacing (4dp on each side = 8dp total between buttons)
        val marginSize = (4 * context.resources.displayMetrics.density).toInt()
        
        destinations.forEachIndexed { index, destination ->
            val button = androidx.appcompat.widget.AppCompatButton(context).apply {
                text = destination.name
                setTextColor(Color.WHITE)
                textSize = 16f
                isAllCaps = false
                setPadding(8, 32, 8, 32)
                
                // Equal weight for all buttons to distribute width evenly
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
                    moveToDestination(destination)
                }
            }
            
            buttonRow.addView(button)
            Log.d(TAG, "Added button for ${destination.name} at position $index with color ${destination.destinationColor}")
        }
        
        Log.d(TAG, "Finished creating ${destinations.size} destination buttons in single row")
    }

    private fun moveToDestination(destination: MediaResource) {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutDestinations.isEnabled = false
        
        // Create cancellable job for move operation
        scope.launch {
            try {
                // Create File object that preserves network paths
                val destinationFolder = if (destination.path.startsWith("smb://") || 
                                            destination.path.startsWith("sftp://") || 
                                            destination.path.startsWith("ftp://")) {
                    object : File(destination.path) {
                        override fun getAbsolutePath(): String = destination.path
                        override fun getPath(): String = destination.path
                    }
                } else {
                    File(destination.path)
                }
                
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
                var completed = false
                withContext(Dispatchers.IO) {
                    fileOperationUseCase.executeWithProgress(operation).collect { progress ->
                        if (completed) return@collect
                        
                        // Update progress dialog on main thread
                        withContext(Dispatchers.Main) {
                            progressDialog.updateProgress(progress)
                            
                            // Handle completion
                            if (progress is com.sza.fastmediasorter.domain.usecase.FileOperationProgress.Completed) {
                                completed = true
                                progressDialog.dismiss()
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
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
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
                
                com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                    context,
                    "Partial Move Success",
                    message
                )
                
                onComplete(null)
                dismiss()
            }
            is FileOperationResult.Failure -> {
                com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
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
