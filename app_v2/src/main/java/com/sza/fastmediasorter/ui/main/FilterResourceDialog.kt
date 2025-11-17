package com.sza.fastmediasorter.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.databinding.DialogFilterResourceBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode

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
        // Sort mode spinner
        setupSortSpinner()

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

    private fun setupSortSpinner() {
        val sortOptions = listOf(
            "Manual Order" to SortMode.MANUAL,
            "Name (A → Z)" to SortMode.NAME_ASC,
            "Name (Z → A)" to SortMode.NAME_DESC,
            "Date (Oldest First)" to SortMode.DATE_ASC,
            "Date (Newest First)" to SortMode.DATE_DESC,
            "File Count (Low → High)" to SortMode.SIZE_ASC,
            "File Count (High → Low)" to SortMode.SIZE_DESC
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sortOptions.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = adapter

        // Set current selection
        val currentIndex = sortOptions.indexOfFirst { it.second == currentSortMode }
        if (currentIndex >= 0) {
            binding.spinnerSort.setSelection(currentIndex)
        }

        // Handle selection
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSortMode = sortOptions[position].second
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Keep current sort mode
            }
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
        currentSortMode = SortMode.MANUAL
        selectedResourceTypes.clear()
        selectedMediaTypes.clear()
        nameFilter = ""
        
        binding.spinnerSort.setSelection(0) // MANUAL is first
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
            sortMode: SortMode = SortMode.MANUAL,
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
