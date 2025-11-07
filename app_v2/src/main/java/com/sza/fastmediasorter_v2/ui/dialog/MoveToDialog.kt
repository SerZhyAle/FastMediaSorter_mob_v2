package com.sza.fastmediasorter_v2.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
    private val destinationAdapter = DestinationAdapter { destination ->
        moveToDestination(destination)
    }

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
            
            rvDestinations.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = destinationAdapter
            }
            
            btnCancel.setOnClickListener { dismiss() }
        }
        
        window?.setBackgroundDrawableResource(R.drawable.bg_move_dialog)
    }

    private fun loadDestinations() {
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val destinations = getDestinationsUseCase.getDestinationsExcluding(currentResourceId)
                
                (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
                    destinationAdapter.submitList(destinations)
                    
                    if (destinations.isEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.no_destinations_available),
                            Toast.LENGTH_SHORT
                        ).show()
                        dismiss()
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

    private fun moveToDestination(destination: MediaResource) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvDestinations.isEnabled = false
        
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
                        binding.rvDestinations.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Move error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
                binding.rvDestinations.isEnabled = true
            }
        }
    }
}
