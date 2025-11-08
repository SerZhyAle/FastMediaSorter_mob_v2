package com.sza.fastmediasorter_v2.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch
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
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val destinations = getDestinationsUseCase.getDestinationsExcluding(currentResourceId)
                
                (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
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
                }
            } catch (e: Exception) {
                (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                    Toast.makeText(context, "Error loading destinations: ${e.message}", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    /**
     * Create colored destination buttons dynamically based on count
     * Per spec: 1-5 buttons per row, arranged in rows
     */
    private fun createDestinationButtons(destinations: List<MediaResource>) {
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
        
        var currentRow: LinearLayout? = null
        
        destinations.forEachIndexed { index, destination ->
            // Create new row if needed
            if (index % buttonsPerRow == 0) {
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
            
            currentRow?.addView(button)
        }
    }

    private fun moveToDestination(destination: MediaResource) {
        binding.progressBar.visibility = View.VISIBLE
        
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            try {
                val destinationFolder = File(destination.path)
                
                val operation = FileOperation.Move(
                    sources = sourceFiles,
                    destination = destinationFolder,
                    overwrite = overwriteFiles
                )
                
                val result = fileOperationUseCase.execute(operation)
                
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
                        val message = context.getString(
                            R.string.moved_n_of_m_files,
                            result.processedCount,
                            result.processedCount + result.failedCount
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        onComplete(null)
                        dismiss()
                    }
                    is FileOperationResult.Failure -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.move_failed, result.error),
                            Toast.LENGTH_LONG
                        ).show()
                        binding.progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Move error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
