package com.sza.fastmediasorter.ui.duplicates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentDuplicatesBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.core.util.formatFileSize
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@AndroidEntryPoint
class DuplicatesFragment : Fragment() {

    private var _binding: FragmentDuplicatesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DuplicatesViewModel by viewModels()
    private lateinit var adapter: DuplicateGroupAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuplicatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // Применяем параметры запуска (только при первом создании)
        if (savedInstanceState == null) {
            val resourceId = arguments?.getLong(DuplicatesActivity.EXTRA_RESOURCE_ID, -1L) ?: -1L
            val autoDelete = arguments?.getBoolean(DuplicatesActivity.EXTRA_AUTO_DELETE, false) ?: false
            if (resourceId != -1L) {
                viewModel.initWithResource(resourceId, autoDelete)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = DuplicateGroupAdapter { file ->
            viewModel.toggleFileSelection(file.path)
        }
        binding.rvDuplicates.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnStartScan.setOnClickListener {
            viewModel.startScan()
        }
        binding.btnCancelScan.setOnClickListener {
            viewModel.cancelScan()
        }
        binding.fabDeleteSelected.setOnClickListener {
            val count = viewModel.state.value.selectedFilePaths.size
            if (count > 0 && isAdded) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Selected")
                    .setMessage("Are you sure you want to permanently delete $count items? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteSelectedFiles() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collectLatest { state ->
                        updateUi(state)
                    }
                }
                launch {
                    viewModel.events.collectLatest { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUi(state: DuplicatesState) {
        if (binding.cgResources.childCount == 0 && state.availableResources.isNotEmpty()) {
            buildResourceChips(state.availableResources, state.selectedResourceIds, state.pinnedResourceId)
        } else {
            for (i in 0 until binding.cgResources.childCount) {
                val chip = binding.cgResources.getChildAt(i) as? Chip
                if (chip != null) {
                    val resourceId = chip.tag as Long
                    chip.isChecked = state.selectedResourceIds.contains(resourceId)
                }
            }
        }

        binding.btnStartScan.isEnabled = state.selectedResourceIds.isNotEmpty()

        binding.layoutSetup.visibility = View.GONE
        binding.layoutProgress.visibility = View.GONE
        binding.layoutResults.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        if (state.result != null) {
            val result = state.result
            if (result.groups.isEmpty() && state.scanState !is ScanState.Running) {
                binding.tvEmpty.visibility = View.VISIBLE
            } else if (result.groups.isNotEmpty()) {
                binding.layoutResults.visibility = View.VISIBLE
                adapter.selectedFilePaths = state.selectedFilePaths
                adapter.submitList(result.groups)
                
                val wastedText = getString(R.string.duplicate_wasted_bytes, formatFileSize(result.totalWastedBytes))
                binding.tvSummary.text = "Found ${result.groups.size} groups | $wastedText"
                
                val selectedCount = state.selectedFilePaths.size
                if (selectedCount > 0) {
                    binding.fabDeleteSelected.visibility = View.VISIBLE
                    val selectedSize = result.groups.flatMap { it.files }
                        .filter { it.path in state.selectedFilePaths }
                        .sumOf { it.size }
                    binding.fabDeleteSelected.text = "Delete Selected ($selectedCount items, ${formatFileSize(selectedSize)})"
                } else {
                    binding.fabDeleteSelected.visibility = View.GONE
                }
            }
        }
        if (state.scanState !is ScanState.Idle) {
            when (state.scanState) {
                is ScanState.Running -> {
                    binding.layoutProgress.visibility = View.VISIBLE
                    val progress = state.scanState.progress
                    binding.tvScanPhase.text = progress.phase.name
                    binding.tvScanFiles.text = "${progress.filesProcessed} / ${progress.totalFiles} files"
                }
                is ScanState.Error -> {
                    binding.layoutSetup.visibility = View.VISIBLE
                    Toast.makeText(context, state.scanState.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        } else if (state.result == null) {
             binding.layoutSetup.visibility = View.VISIBLE
        }
    }

    private fun buildResourceChips(
        resources: List<MediaResource>,
        selectedIds: Set<Long>,
        pinnedId: Long? = null
    ) {
        binding.cgResources.removeAllViews()
        // Прикреплённый ресурс (из которого открыли меню) выводим первым
        val sorted = if (pinnedId != null) {
            resources.sortedByDescending { it.id == pinnedId }
        } else resources
        sorted.forEach { resource ->
            val chip = Chip(requireContext()).apply {
                text = "${resource.name} (${resource.type})"
                isCheckable = true
                isChecked = selectedIds.contains(resource.id)
                tag = resource.id
                setOnClickListener {
                    viewModel.toggleResourceSelection(resource.id)
                }
            }
            binding.cgResources.addView(chip)
        }
    }

    private fun handleEvent(event: DuplicatesEvent) {
        when (event) {
            is DuplicatesEvent.ShowNetworkWarning -> {
                if (!isAdded) return
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.duplicate_scan_start)
                    .setMessage(R.string.duplicate_scan_network_warning)
                    .setPositiveButton(android.R.string.ok) { dialog: android.content.DialogInterface, _: Int -> viewModel.startScanConfirmed() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            is DuplicatesEvent.ShowError -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
            is DuplicatesEvent.FileDeleted -> Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
            is DuplicatesEvent.ScanComplete -> {
                // Режим «Найти и удалить»: автоматически запускаем удаление без дополнительного подтверждения
                if (viewModel.autoDeleteMode && viewModel.state.value.selectedFilePaths.isNotEmpty()) {
                    viewModel.deleteSelectedFiles()
                }
            }
            is DuplicatesEvent.BatchDeletionComplete -> {
                Toast.makeText(requireContext(), "Selected files deleted successfully", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
