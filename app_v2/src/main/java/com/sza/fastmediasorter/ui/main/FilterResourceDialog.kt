package com.sza.fastmediasorter.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.capability.RemoteSourceId
import com.sza.fastmediasorter.databinding.DialogFilterResourceBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.scanner.WearWatchMediaScanner
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dialog for filtering and sorting resources on Main Screen
 * According to V2 Specification: "Filter and Sort Resource List Screen"
 *
 * Both directions travel through Bundles - starting values through [newInstance] arguments, the
 * applied filter through a FragmentResult keyed by [RESULT_KEY]. The FragmentManager rebuilds a
 * restored dialog with the no-arg constructor, so inputs assigned onto fields by the caller, and a
 * caller-held apply callback, are both gone once the host is recreated.
 */
@AndroidEntryPoint
class FilterResourceDialog : DialogFragment() {

    @Inject lateinit var remoteSourceGate: RemoteSourceAvailabilityGate

    @Inject lateinit var mediaCapabilities: MediaCapabilities

    @Inject lateinit var wearWatchMediaScanner: WearWatchMediaScanner

    private var _binding: DialogFilterResourceBinding? = null
    private val binding get() = _binding!!

    private var currentSortMode: SortMode = SortMode.MANUAL
    private var selectedResourceTypes = mutableSetOf<ResourceType>()
    private var selectedMediaTypes = mutableSetOf<MediaType>()
    private var nameFilter: String = ""

    private var requestKey: String = RESULT_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        currentSortMode = args.getString(ARG_SORT_MODE)
            ?.let { name -> runCatching { enumValueOf<SortMode>(name) }.getOrNull() }
            ?: SortMode.MANUAL
        selectedResourceTypes = args.toEnumSet(ARG_RESOURCE_TYPES)
        selectedMediaTypes = args.toEnumSet(ARG_MEDIA_TYPES)
        nameFilter = args.getString(ARG_NAME_FILTER).orEmpty()
        requestKey = args.getString(ARG_REQUEST_KEY) ?: RESULT_KEY
    }

    // Enums cross the Bundle as names, so a Bundle written before an app update can still name a
    // constant that no longer exists - an unknown name is dropped rather than thrown.
    private inline fun <reified T : Enum<T>> Bundle.toEnumSet(key: String): MutableSet<T> =
        getStringArrayList(key).orEmpty().mapNotNullTo(mutableSetOf()) { name ->
            runCatching { enumValueOf<T>(name) }.getOrNull()
        }

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

        // S2085: HTTP_STREAM/RTSP_STREAM creation point added to AddResourceActivity.
        val allowedResourceTypes = ResourceType.values().filter { type ->
            when (type) {
                ResourceType.LOCAL -> true
                ResourceType.CLOUD -> remoteSourceGate.anyCloudEnabled()
                ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> true
                // S2106: mirrors AddResourceViewModel.isPairedWatchAvailable - the same build-capability
                // flag (source set wearGms vs wearStub) that decides whether the add-resource screen
                // offers a paired-watch entry point, not whether a watch is reachable right now.
                ResourceType.WEAR_WATCH -> wearWatchMediaScanner.isCompanionAvailable
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
        val result = Bundle().apply {
            putString(RESULT_SORT_MODE, currentSortMode.name)
            putStringArrayList(RESULT_RESOURCE_TYPES, selectedResourceTypes.namesOrNull())
            putStringArrayList(RESULT_MEDIA_TYPES, selectedMediaTypes.namesOrNull())
            putString(RESULT_NAME_FILTER, nameFilter.takeIf { it.isNotBlank() })
        }
        setFragmentResult(requestKey, result)
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
        const val RESULT_KEY = "filter_resource_result"
        const val RESULT_SORT_MODE = "result_sort_mode"
        const val RESULT_RESOURCE_TYPES = "result_resource_types"
        const val RESULT_MEDIA_TYPES = "result_media_types"
        const val RESULT_NAME_FILTER = "result_name_filter"

        private const val ARG_SORT_MODE = "arg_sort_mode"
        private const val ARG_RESOURCE_TYPES = "arg_resource_types"
        private const val ARG_MEDIA_TYPES = "arg_media_types"
        private const val ARG_NAME_FILTER = "arg_name_filter"
        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(
            sortMode: SortMode = SortMode.MANUAL,
            resourceTypes: Set<ResourceType>? = null,
            mediaTypes: Set<MediaType>? = null,
            nameFilter: String? = null,
            requestKey: String = RESULT_KEY
        ): FilterResourceDialog {
            val args = Bundle().apply {
                putString(ARG_SORT_MODE, sortMode.name)
                putStringArrayList(ARG_RESOURCE_TYPES, resourceTypes.namesOrNull())
                putStringArrayList(ARG_MEDIA_TYPES, mediaTypes.namesOrNull())
                putString(ARG_NAME_FILTER, nameFilter)
                putString(ARG_REQUEST_KEY, requestKey)
            }
            return FilterResourceDialog().apply { arguments = args }
        }
    }
}

// Absent, not empty: an empty selection reads as "no filter" to the form and to the host.
private fun <T : Enum<T>> Set<T>?.namesOrNull(): ArrayList<String>? =
    if (isNullOrEmpty()) null else mapTo(ArrayList<String>(size)) { it.name }
