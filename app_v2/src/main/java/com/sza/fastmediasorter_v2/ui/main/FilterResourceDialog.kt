package com.sza.fastmediasorter_v2.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter_v2.databinding.DialogFilterResourceBinding
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.model.SortMode

/**
 * Dialog for filtering and sorting resources on Main Screen
 * According to V2 Specification: "Filter and Sort Resource List Screen"
 */
class FilterResourceDialog : DialogFragment() {

    private var _binding: DialogFilterResourceBinding? = null
    private val binding get() = _binding!!

    private var currentSortMode: SortMode = SortMode.NAME_ASC
    private var selectedResourceTypes = mutableSetOf<ResourceType>()
    private var selectedMediaTypes = mutableSetOf<MediaType>()
    private var nameFilter: String = ""

    private var onApplyListener: ((SortMode, Set<ResourceType>?, Set<MediaType>?, String?) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFilterResourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
    }

    private fun setupViews() {
        // Sort mode radio buttons
        binding.radioGroupSort.setOnCheckedChangeListener { _, checkedId ->
            currentSortMode = when (checkedId) {
                binding.rbNameAsc.id -> SortMode.NAME_ASC
                binding.rbNameDesc.id -> SortMode.NAME_DESC
                binding.rbDateAsc.id -> SortMode.DATE_ASC
                binding.rbDateDesc.id -> SortMode.DATE_DESC
                binding.rbSizeAsc.id -> SortMode.SIZE_ASC
                binding.rbSizeDesc.id -> SortMode.SIZE_DESC
                else -> SortMode.NAME_ASC
            }
        }

        // Set current sort mode
        when (currentSortMode) {
            SortMode.NAME_ASC -> binding.rbNameAsc.isChecked = true
            SortMode.NAME_DESC -> binding.rbNameDesc.isChecked = true
            SortMode.DATE_ASC -> binding.rbDateAsc.isChecked = true
            SortMode.DATE_DESC -> binding.rbDateDesc.isChecked = true
            SortMode.SIZE_ASC -> binding.rbSizeAsc.isChecked = true
            SortMode.SIZE_DESC -> binding.rbSizeDesc.isChecked = true
            else -> binding.rbNameAsc.isChecked = true
        }

        // Resource type chips
        setupResourceTypeChips()

        // Media type chips
        setupMediaTypeChips()

        // Name filter
        binding.etNameFilter.setText(nameFilter)
        binding.etNameFilter.addTextChangedListener { text ->
            nameFilter = text?.toString() ?: ""
        }

        // Buttons
        binding.btnApply.setOnClickListener {
            applyFilters()
        }

        binding.btnClear.setOnClickListener {
            clearFilters()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun setupResourceTypeChips() {
        binding.chipGroupResourceType.removeAllViews()
        
        ResourceType.values().forEach { type ->
            val chip = Chip(requireContext()).apply {
                text = type.name.replace("_", " ")
                isCheckable = true
                isChecked = type in selectedResourceTypes
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedResourceTypes.add(type)
                    } else {
                        selectedResourceTypes.remove(type)
                    }
                }
            }
            binding.chipGroupResourceType.addView(chip)
        }
    }

    private fun setupMediaTypeChips() {
        binding.chipGroupMediaType.removeAllViews()
        
        MediaType.values().forEach { type ->
            val chip = Chip(requireContext()).apply {
                text = when (type) {
                    MediaType.IMAGE -> "Images (I)"
                    MediaType.VIDEO -> "Videos (V)"
                    MediaType.AUDIO -> "Audio (A)"
                    MediaType.GIF -> "GIFs (G)"
                }
                isCheckable = true
                isChecked = type in selectedMediaTypes
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedMediaTypes.add(type)
                    } else {
                        selectedMediaTypes.remove(type)
                    }
                }
            }
            binding.chipGroupMediaType.addView(chip)
        }
    }

    private fun applyFilters() {
        val resourceTypes = if (selectedResourceTypes.isEmpty()) null else selectedResourceTypes.toSet()
        val mediaTypes = if (selectedMediaTypes.isEmpty()) null else selectedMediaTypes.toSet()
        val name = nameFilter.takeIf { it.isNotBlank() }
        
        onApplyListener?.invoke(currentSortMode, resourceTypes, mediaTypes, name)
        dismiss()
    }

    private fun clearFilters() {
        currentSortMode = SortMode.NAME_ASC
        selectedResourceTypes.clear()
        selectedMediaTypes.clear()
        nameFilter = ""
        
        binding.rbNameAsc.isChecked = true
        binding.etNameFilter.text?.clear()
        
        setupResourceTypeChips()
        setupMediaTypeChips()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            sortMode: SortMode = SortMode.NAME_ASC,
            resourceTypes: Set<ResourceType>? = null,
            mediaTypes: Set<MediaType>? = null,
            nameFilter: String? = null,
            onApply: (SortMode, Set<ResourceType>?, Set<MediaType>?, String?) -> Unit
        ): FilterResourceDialog {
            return FilterResourceDialog().apply {
                this.currentSortMode = sortMode
                this.selectedResourceTypes = resourceTypes?.toMutableSet() ?: mutableSetOf()
                this.selectedMediaTypes = mediaTypes?.toMutableSet() ?: mutableSetOf()
                this.nameFilter = nameFilter ?: ""
                this.onApplyListener = onApply
            }
        }
    }
}
