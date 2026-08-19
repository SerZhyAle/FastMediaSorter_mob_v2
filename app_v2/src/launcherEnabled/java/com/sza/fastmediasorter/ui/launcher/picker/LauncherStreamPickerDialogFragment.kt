package com.sza.fastmediasorter.ui.launcher.picker

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.databinding.DialogLauncherStreamPickerBinding
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerWindow
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

import android.graphics.Bitmap

/**
 * S0404 / S1763: lists the user's channel catalog with filtering options (media type: All/Audio/Video,
 * category/topic, and language) combined with text search, returning the chosen stream id to the host.
 */
@AndroidEntryPoint
class LauncherStreamPickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var observeStreams: ObserveStreamSourcesUseCase

    @Inject
    lateinit var faviconAtlasStore: FaviconAtlasStore

    private var _binding: DialogLauncherStreamPickerBinding? = null
    private val binding get() = _binding!!

    private val faviconSlicer = FaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    private var allSources: List<StreamSourceEntity> = emptyList()
    private var atlasCoords: Map<String, Int> = emptyMap()
    private var atlasTiles: Map<String, Bitmap> = emptyMap()
    private var selectedMediaKind: String? = null // null = ALL, "AUDIO", "VIDEO"
    private var selectedTopic: String? = null
    private var selectedLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogLauncherStreamPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvOptionPickerTitle.text = getString(R.string.launcher_edit_pick_stream_title)
        binding.tvOptionPickerTitle.isVisible = true

        setupFilterListeners()

        binding.tvOptionsEmpty.text = getString(R.string.launcher_edit_streams_loading)
        binding.tvOptionsEmpty.isVisible = true

        collectOnLifecycle(flow {
            val sources = observeStreams().first()
            val coords = runCatching { faviconAtlasStore.coords() }.getOrDefault(emptyMap())
            val tiles = sources.mapNotNull { source ->
                coords[source.url]?.let { idx -> faviconSlicer.tileFor(idx)?.let { tile -> source.id to tile } }
            }.toMap()
            emit(Triple(sources, coords, tiles))
        }) { (sources, coords, tiles) ->
            allSources = sources
            atlasCoords = coords
            atlasTiles = tiles
            binding.tvOptionsEmpty.isVisible = false

            populateFilterDropdowns(sources)
            applyFiltersAndAttach()
        }
    }

    private fun setupFilterListeners() {
        binding.toggleMediaKind.check(R.id.btnMediaAll)
        binding.toggleMediaKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedMediaKind = when (checkedId) {
                    R.id.btnMediaAudio -> "AUDIO"
                    R.id.btnMediaVideo -> "VIDEO"
                    else -> null
                }
                applyFiltersAndAttach()
            }
        }

        binding.spinnerTopic.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position)?.toString()
                selectedTopic = if (position == 0 || item == null) null else item
                applyFiltersAndAttach()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position)?.toString()
                selectedLanguage = if (position == 0 || item == null) null else item
                applyFiltersAndAttach()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.editOptionSearch.doOnTextChanged { _, _, _, _ ->
            applyFiltersAndAttach()
        }
    }

    private fun populateFilterDropdowns(sources: List<StreamSourceEntity>) {
        val topics = listOf(getString(R.string.streams_filter_topic) + ": " + getString(R.string.streams_filter_all)) +
            sources.mapNotNull { (it.topic ?: it.category)?.takeIf(String::isNotBlank) }.distinct().sorted()

        val languages = listOf(getString(R.string.streams_filter_language) + ": " + getString(R.string.streams_filter_all)) +
            sources.asSequence()
                .mapNotNull { it.language }
                .flatMap { it.splitToSequence(',') }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .toList()

        val context = requireContext()
        val topicAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, topics).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerTopic.adapter = topicAdapter

        val langAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, languages).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerLanguage.adapter = langAdapter
    }

    private fun applyFiltersAndAttach() {
        val query = binding.editOptionSearch.text?.toString().orEmpty().trim().lowercase()

        val filtered = allSources.filter { source ->
            val matchesMedia = when (selectedMediaKind) {
                "AUDIO" -> source.mediaKind.equals("AUDIO", ignoreCase = true)
                "VIDEO" -> source.mediaKind.equals("VIDEO", ignoreCase = true) || source.mediaKind.equals("RTSP", ignoreCase = true)
                else -> true
            }
            val matchesTopic = selectedTopic == null || (source.topic ?: source.category)?.equals(selectedTopic, ignoreCase = true) == true
            val matchesLanguage = selectedLanguage == null || source.language?.split(",")?.any { it.trim().equals(selectedLanguage, ignoreCase = true) } == true
            val matchesQuery = query.isEmpty() || source.title.lowercase().contains(query)

            matchesMedia && matchesTopic && matchesLanguage && matchesQuery
        }

        val options = filtered.map { source ->
            val tile = atlasTiles[source.id]
            Option(
                id = source.id,
                label = source.title,
                leading = tile?.let { LeadingVisual.Thumbnail(it) } ?: LeadingVisual.IconRes(R.drawable.ic_cast),
            )
        }

        SearchableOptionPickerController.attachViews(
            recyclerOptions = binding.recyclerOptions,
            searchLayout = binding.layoutOptionSearch,
            editOptionSearch = binding.editOptionSearch,
            tvOptionsEmpty = binding.tvOptionsEmpty,
            options = options,
            selectedId = null,
            resetRow = null,
        ) { picked ->
            picked?.let { onStreamPicked(it.id) }
        }
    }

    private fun onStreamPicked(streamId: String) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_STREAM_ID to streamId))
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        SearchableOptionPickerWindow.apply(dialog, binding.root)
        dialog?.let { DialogAccessibilityHelper.applyInitialFocus(it) }
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { it.setCanceledOnTouchOutside(true) }

    companion object {
        const val TAG = "LauncherStreamPicker"
        const val RESULT_KEY = "launcher_stream_picker_result"
        const val RESULT_STREAM_ID = "result_stream_id"

        fun invalidateCache() {}

        fun newInstance(): LauncherStreamPickerDialogFragment = LauncherStreamPickerDialogFragment()
    }
}
