package com.sza.fastmediasorter.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.capability.RemoteSourceId
import com.sza.fastmediasorter.databinding.DialogFilterResourceBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dialog for filtering and sorting resources on Main Screen
 * According to V2 Specification: "Filter and Sort Resource List Screen"
 */
@AndroidEntryPoint
class FilterResourceDialog : DialogFragment() {

    @Inject lateinit var remoteSourceGate: RemoteSourceAvailabilityGate

    @Inject lateinit var mediaCapabilities: MediaCapabilities

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

        // S0567: spinnerSort migrated from raw Spinner to SettingsDropdownRow (ADR-1).
        binding.spinnerSort.setEntries(sortOptions.map { it.first as CharSequence })

        val currentIndex = sortOptions.indexOfFirst { it.second == currentSortMode }
        if (currentIndex >= 0) {
            binding.spinnerSort.setSelection(currentIndex)
        }

        binding.spinnerSort.setOnItemSelectedListener { position ->
            currentSortMode = sortOptions[position].second
        }
    }

    private fun setupResourceTypeChips() {
        binding.chipGroupResourceType.removeAllViews()
        
        // S0391: chips reflect the availability node (compile support AND user toggle), not just flavor.
        val allowedResourceTypes = ResourceType.values().filter { type ->
            when (type) {
                ResourceType.LOCAL -> true
                ResourceType.CLOUD -> remoteSourceGate.anyCloudEnabled()
                else -> RemoteSourceId.networkFromResourceType(type)
                    ?.let { remoteSourceGate.isEnabled(it) } ?: true
            }
        }
        
        allowedResourceTypes.forEach { type ->
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
        
        // Filter media types based on product flavor
        val allowedMediaTypes = MediaType.values().filter { type ->
            when (type) {
                MediaType.VIDEO -> mediaCapabilities.supportsVideo
                MediaType.AUDIO -> mediaCapabilities.supportsAudio
                MediaType.IMAGE -> mediaCapabilities.supportsImages
                MediaType.GIF -> mediaCapabilities.supportsImages
                MediaType.TEXT -> mediaCapabilities.supportsDocuments
                MediaType.PDF -> mediaCapabilities.supportsDocuments
                MediaType.EPUB -> mediaCapabilities.supportsEpub
                MediaType.OFFICE_DOCUMENT -> mediaCapabilities.supportsDocuments
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> false
            }
        }
        
        allowedMediaTypes.forEach { type ->
            val chip = Chip(requireContext()).apply {
                text = when (type) {
                    MediaType.IMAGE -> "Images (I)"
                    MediaType.VIDEO -> "Videos (V)"
                    MediaType.AUDIO -> "Audio (A)"
                    MediaType.GIF -> "GIFs (G)"
                    MediaType.TEXT -> "Text (T)"
                    MediaType.PDF -> "PDF (P)"
                    MediaType.EPUB -> "EPUB (E)"
                    MediaType.OFFICE_DOCUMENT -> "Office (O)"
                    MediaType.BINARY_ARCHIVE -> "Archive (AR)"
                    MediaType.BINARY_DISK -> "Disk (DI)"
                    MediaType.BINARY_EXECUTABLE -> "Executable (EX)"
                    MediaType.BINARY_OTHER -> "Binary (BN)"
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

    override fun onStart() {
        super.onStart()
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = ::applyFilters)
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
