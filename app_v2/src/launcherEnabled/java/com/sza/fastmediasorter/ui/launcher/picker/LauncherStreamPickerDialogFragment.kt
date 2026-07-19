package com.sza.fastmediasorter.ui.launcher.picker

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.databinding.DialogSearchableOptionPickerBinding
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerController
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.LeadingVisual
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * S0404: lists the user's channel catalog (pinned first, then their order) and returns the chosen
 * stream id to the host via a FragmentResult. Net-new rather than a retrofit: no shared picker lists
 * streams, and [StreamSourceRepository][com.sza.fastmediasorter.data.repository.StreamSourceRepository]
 * has no sync accessor, so the list is snapshotted with `observeSources().first()`.
 *
 * A channel favicon comes from the packed atlas beside the catalog - one decode, cropped per row -
 * never the network; a row with no favicon falls back to [R.drawable.ic_cast].
 */
@AndroidEntryPoint
class LauncherStreamPickerDialogFragment : DialogFragment() {

    @Inject
    lateinit var observeStreams: ObserveStreamSourcesUseCase

    @Inject
    lateinit var faviconAtlasStore: FaviconAtlasStore

    private var _binding: DialogSearchableOptionPickerBinding? = null
    private val binding get() = _binding!!

    // Decodes the atlas at most once for this dialog (the store's file provider is stable), matching how
    // StreamsGadget scopes its slicer to the host rather than to a row.
    private val faviconSlicer = FaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogSearchableOptionPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvOptionPickerTitle.text = getString(R.string.launcher_edit_pick_stream_title)
        binding.tvOptionPickerTitle.isVisible = true
        collectOnLifecycle(flow { emit(buildOptions()) }) { options ->
            SearchableOptionPickerController.attach(binding, options, selectedId = null, resetRow = null) { picked ->
                picked?.let { onStreamPicked(it.id) }
            }
        }
    }

    private suspend fun buildOptions(): List<Option> {
        // Ordered in SQL (pinned DESC, sortIndex ASC) - never re-sort, or the picker disagrees with the
        // Streams screen. A picker list is static, so one snapshot is enough.
        val sources = observeStreams().first()
        val coords = runCatching { faviconAtlasStore.coords() }.getOrDefault(emptyMap())
        return sources.map { source ->
            val tile = coords[source.url]?.let { faviconSlicer.tileFor(it) }
            Option(
                id = source.id,
                label = source.title,
                leading = tile?.let { LeadingVisual.Thumbnail(it) } ?: LeadingVisual.IconRes(R.drawable.ic_cast),
            )
        }
    }

    private fun onStreamPicked(streamId: String) {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_STREAM_ID to streamId))
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width = minOf(
            (metrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt(),
            (metrics.density * DIALOG_MAX_WIDTH_DP).toInt(),
        )
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
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

        private const val DIALOG_WIDTH_FRACTION = 0.92f
        private const val DIALOG_MAX_WIDTH_DP = 560f

        fun newInstance(): LauncherStreamPickerDialogFragment = LauncherStreamPickerDialogFragment()
    }
}
