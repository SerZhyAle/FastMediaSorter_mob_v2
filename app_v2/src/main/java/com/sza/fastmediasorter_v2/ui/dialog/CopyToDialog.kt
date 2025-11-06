package com.sza.fastmediasorter_v2.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.databinding.DialogCopyToBinding
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.usecase.FileOperation
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult
import com.sza.fastmediasorter_v2.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetDestinationsUseCase
import kotlinx.coroutines.launch
import java.io.File

class CopyToDialog(
    context: Context,
    private val sourceFiles: List<File>,
    private val sourceFolderName: String,
    private val currentResourceId: Long,
    private val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val overwriteFiles: Boolean,
    private val onComplete: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogCopyToBinding
    private val destinationAdapter = DestinationAdapter { destination ->
        copyToDestination(destination)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogCopyToBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadDestinations()
    }

    private fun setupUI() {
        binding.apply {
            tvFileCount.text = context.getString(
                R.string.copying_n_files_from_folder,
                sourceFiles.size,
                sourceFolderName
            )
            
            rvDestinations.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = destinationAdapter
            }
            
            btnCancel.setOnClickListener { dismiss() }
        }
        
        // Set dialog background color (dark-green for dark theme, light-green for light theme)
        window?.setBackgroundDrawableResource(R.drawable.bg_copy_dialog)
    }

    private fun loadDestinations() {
        // Using context as CoroutineScope is not ideal, but for dialog it's acceptable
        // In production, should pass CoroutineScope from caller
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

    private fun copyToDestination(destination: MediaResource) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvDestinations.isEnabled = false
        
        (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch {
            try {
                val destinationFolder = File(destination.path)
                
                val operation = FileOperation.Copy(
                    sources = sourceFiles,
                    destination = destinationFolder,
                    overwrite = overwriteFiles
                )
                
                val result = fileOperationUseCase.execute(operation)
                
                when (result) {
                    is FileOperationResult.Success -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.copied_n_files, result.processedCount),
                            Toast.LENGTH_SHORT
                        ).show()
                        onComplete()
                        dismiss()
                    }
                    is FileOperationResult.PartialSuccess -> {
                        val message = context.getString(
                            R.string.copied_n_of_m_files,
                            result.processedCount,
                            result.processedCount + result.failedCount
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        onComplete()
                        dismiss()
                    }
                    is FileOperationResult.Failure -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.copy_failed, result.error),
                            Toast.LENGTH_LONG
                        ).show()
                        binding.progressBar.visibility = View.GONE
                        binding.rvDestinations.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Copy error: ${e.message}", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
                binding.rvDestinations.isEnabled = true
            }
        }
    }
}
