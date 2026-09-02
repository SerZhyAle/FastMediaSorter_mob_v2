package com.sza.fastmediasorter.ui.launcher.picker

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.databinding.DialogLauncherStreamPickerBinding
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamCatalogSnapshotUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerWindow
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404 / S1763: lists the user's channel catalog with filtering options (media type: All/Audio/Video,
 * category/topic, and language) combined with text search. S1832: what it hands back is the chosen
 * channel's identity, not the catalog row's id - see [RESULT_STREAM_IDENTITY].
 *
 * S2021: rows and thumbnails are two separate readinesses. This dialog used to slice one atlas tile per
 * catalog row before it drew anything, which on an imported catalog is thousands of mutex-guarded crops
 * out of a 23 MB sheet - the list never appeared and the loading label looked like a hang. Tiles are now
 * cut only for the rows about to be shown, the catalog itself comes from a shared snapshot so a burst of
 * shortcut additions reads it once, and a large catalog asks for a narrowing input before listing
 * anything (see [RESULT_CAP] and [MIN_QUERY_LENGTH]).
 */
@AndroidEntryPoint
class LauncherStreamPickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var observeCatalogSnapshot: ObserveStreamCatalogSnapshotUseCase

    @Inject
    lateinit var faviconAtlasStore: FaviconAtlasStore

    private var _binding: DialogLauncherStreamPickerBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var mediaKindListener: MaterialButtonToggleGroup.OnButtonCheckedListener? = null

    private val faviconSlicer = FaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    private var allSources: List<StreamSourceEntity> = emptyList()
    private var atlasCoords: Map<String, Int> = emptyMap()
    private var catalogLoaded = false
    private var coordsLoaded = false
    private var dropdownsPopulated = false
    private var selectedMediaKind: String? = null // null = ALL, "AUDIO", "VIDEO"
    private var selectedTopic: String? = null
    private var selectedLanguage: String? = null

    // Only the newest filter pass may attach its result: an older pass whose tiles resolved later would
    // otherwise overwrite the list with the previous query's rows.
    private var attachJob: Job? = null

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

        // The search field is how a large catalog is opened at all, so it stays visible unconditionally
        // rather than being hidden by the shared controller when a short result happens to fit.
        binding.layoutOptionSearch.isVisible = true
        showEmptyState(R.string.launcher_edit_streams_loading)

        collectOnLifecycle(observeCatalogSnapshot()) { sources ->
            if (sources == null) return@collectOnLifecycle
            Timber.d("S2021: stream picker snapshot rows=${sources.size}")
            allSources = sources
            catalogLoaded = true
            if (!dropdownsPopulated) {
                dropdownsPopulated = true
                populateFilterDropdowns(sources)
            }
            applyFiltersAndAttach()
        }
    }

    /** Shows [messageRes] in place of the list. */
    private fun showEmptyState(@StringRes messageRes: Int) {
        binding.tvOptionsEmpty.text = getString(messageRes)
        binding.tvOptionsEmpty.isVisible = true
    }

    /**
     * The "N match, refine" line, or nothing when the result fits. It rides the search field's helper
     * slot rather than the empty-state view below the list: that view sits after the RecyclerView in a
     * height-capped column, so a full list would push the very hint explaining the list off-screen.
     */
    private fun showCapHint(totalMatches: Int?) {
        binding.layoutOptionSearch.helperText = totalMatches
            ?.let { getString(R.string.launcher_edit_streams_too_many, it) }
    }

    private fun setupFilterListeners() {
        binding.toggleMediaKind.check(R.id.btnMediaAll)
        val mediaKindListener = MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedMediaKind = when (checkedId) {
                    R.id.btnMediaAudio -> KIND_AUDIO
                    R.id.btnMediaVideo -> KIND_VIDEO
                    else -> null
                }
                applyFiltersAndAttach()
            }
        }
        this.mediaKindListener = mediaKindListener
        binding.toggleMediaKind.addOnButtonCheckedListener(mediaKindListener)

        binding.spinnerTopic.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position)?.toString()
                selectedTopic = if (position == 0 || item == null) null else item
                applyFiltersAndAttach()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position)?.toString()
                selectedLanguage = if (position == 0 || item == null) null else item
                applyFiltersAndAttach()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.editOptionSearch.doOnTextChanged { _, _, _, _ ->
            applyFiltersAndAttach()
        }
    }

    /** The "<facet>: All" row every filter spinner opens with. */
    private fun allOf(@StringRes facetRes: Int): String =
        getString(facetRes) + ": " + getString(R.string.streams_filter_all)

    private fun populateFilterDropdowns(sources: List<StreamSourceEntity>) {
        val topics = listOf(allOf(R.string.streams_filter_topic)) +
            sources.mapNotNull { (it.topic ?: it.category)?.takeIf(String::isNotBlank) }.distinct().sorted()

        val languages = listOf(allOf(R.string.streams_filter_language)) +
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

    /**
     * True when the user has told the dialog what to look for. The media-kind toggle is deliberately
     * not a narrowing input: it splits the catalog roughly in half, which on an imported catalog still
     * leaves thousands of rows and so answers nothing (strategic ADR-1).
     */
    private fun hasNarrowingInput(query: String): Boolean =
        query.length >= MIN_QUERY_LENGTH || selectedTopic != null || selectedLanguage != null

    private fun matches(source: StreamSourceEntity, query: String): Boolean {
        val matchesMedia = when (selectedMediaKind) {
            KIND_AUDIO -> source.mediaKind.equals(KIND_AUDIO, ignoreCase = true)
            KIND_VIDEO -> source.mediaKind.equals(KIND_VIDEO, ignoreCase = true) ||
                source.mediaKind.equals(KIND_RTSP, ignoreCase = true)
            else -> true
        }
        val matchesTopic = selectedTopic == null ||
            (source.topic ?: source.category)?.equals(selectedTopic, ignoreCase = true) == true
        val matchesLanguage = selectedLanguage == null ||
            source.language?.split(",")
                ?.any { it.trim().equals(selectedLanguage, ignoreCase = true) } == true
        val matchesQuery = query.isEmpty() || source.title.lowercase().contains(query)

        return matchesMedia && matchesTopic && matchesLanguage && matchesQuery
    }

    private fun applyFiltersAndAttach() {
        if (!catalogLoaded) return
        val query = binding.editOptionSearch.text?.toString().orEmpty().trim().lowercase()

        // A catalog that fits on screen is listed as it always was; only a catalog past the cap has a
        // wait worth trading for a keystroke (strategic ADR-1).
        if (allSources.size > RESULT_CAP && !hasNarrowingInput(query)) {
            attachJob?.cancel()
            attachRows(emptyList())
            showCapHint(null)
            showEmptyState(R.string.launcher_edit_streams_narrow_prompt)
            return
        }

        val filtered = allSources.filter { matches(it, query) }
        val shown = filtered.take(RESULT_CAP)

        showCapHint(filtered.size.takeIf { it > RESULT_CAP })
        when {
            filtered.isEmpty() -> showEmptyState(R.string.streams_picker_empty)
            else -> binding.tvOptionsEmpty.isVisible = false
        }

        attachJob?.cancel()
        attachJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!coordsLoaded) {
                // The store memoises this against the sidecar file, so only the first pick in a burst
                // pays the parse. It answers with an empty map on a missing or corrupt sidecar.
                atlasCoords = faviconAtlasStore.coords()
                coordsLoaded = true
            }
            attachRows(shown.map { source -> source to tileFor(source) })
        }
    }

    /** The atlas tile for one row, or null when the catalog carries no icon for it. */
    private suspend fun tileFor(source: StreamSourceEntity): Bitmap? =
        atlasCoords[source.url]?.let { index -> faviconSlicer.tileFor(index) }

    private fun attachRows(rows: List<Pair<StreamSourceEntity, Bitmap?>>) {
        val options = rows.map { (source, tile) ->
            Option(
                id = source.id,
                label = source.title,
                leading = tile?.let { LeadingVisual.Thumbnail(it) } ?: LeadingVisual.IconRes(R.drawable.ic_cast),
            )
        }

        // S2021 / ADR-3: the search field, its visibility and the empty-state text stay with this
        // fragment. Handing them to the controller added one more text listener per keystroke, because
        // this is the one caller that re-attaches on every filter change.
        SearchableOptionPickerController.attachViews(
            recyclerOptions = binding.recyclerOptions,
            options = options,
            selectedId = null,
            resetRow = null,
        ) { picked ->
            // S1832: the picked option carries the row id, which is unique and so the only safe diff key
            // for the list; the cell stores the identity, which is not. The entity is right here, so the
            // translation happens at the one point that holds both.
            picked?.let { option ->
                allSources.firstOrNull { it.id == option.id }?.let { onStreamPicked(it) }
            }
        }
    }

    /**
     * S2031: the chosen channel's kind rides back with its identity.
     *
     * A caller that sizes a cell by the kind would otherwise re-read the catalog for a value this dialog
     * is holding at the moment of the tap.
     */
    private fun onStreamPicked(source: StreamSourceEntity) {
        val requestKey = arguments?.getString(ARG_REQUEST_KEY) ?: RESULT_KEY
        setFragmentResult(
            requestKey,
            bundleOf(
                RESULT_STREAM_IDENTITY to source.identityKey,
                RESULT_STREAM_MEDIA_KIND to source.mediaKind,
            ),
        )
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
        attachJob?.cancel()
        attachJob = null
        // Detach before dropping the binding: this dialog is reopened per pick, so a listener left on
        // a destroyed view hierarchy accumulates one leaked instance per open.
        _binding?.let { views ->
            mediaKindListener?.let { views.toggleMediaKind.removeOnButtonCheckedListener(it) }
            views.spinnerTopic.onItemSelectedListener = null
            views.spinnerLanguage.onItemSelectedListener = null
        }
        mediaKindListener = null
        _binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).also { it.setCanceledOnTouchOutside(true) }

    companion object {
        const val TAG = "LauncherStreamPicker"
        const val RESULT_KEY = "launcher_stream_picker_result"

        /** S1832: what the picked channel is addressed by from here on - its identity, not its row id. */
        const val RESULT_STREAM_IDENTITY = "result_stream_identity"

        /** S2031: the picked channel's `AUDIO` / `VIDEO` / `RTSP` kind, for a caller that sizes by it. */
        const val RESULT_STREAM_MEDIA_KIND = "result_stream_media_kind"

        /**
         * How many rows this dialog is willing to render, and the catalog size past which it asks for
         * a narrowing input first. One number serves both: a catalog it could show in full has nothing
         * to narrow, and a catalog it could not is exactly the one worth narrowing.
         */
        private const val RESULT_CAP = 200

        /** Characters of search text that count as a narrowing input on their own. */
        private const val MIN_QUERY_LENGTH = 2

        // The values stored in StreamSourceEntity.mediaKind that this picker filters on.
        private const val KIND_AUDIO = "AUDIO"
        private const val KIND_VIDEO = "VIDEO"
        private const val KIND_RTSP = "RTSP"

        /**
         * S2031: the caller names the key its answer comes back on.
         *
         * The dialog now answers two questions - which channel a shortcut opens, and which channel a
         * window cell is bound to - and an answer delivered on one key would complete the other flow.
         */
        private const val ARG_REQUEST_KEY = "arg_request_key"

        fun newInstance(requestKey: String = RESULT_KEY): LauncherStreamPickerDialogFragment =
            LauncherStreamPickerDialogFragment().apply {
                arguments = bundleOf(ARG_REQUEST_KEY to requestKey)
            }
    }
}
