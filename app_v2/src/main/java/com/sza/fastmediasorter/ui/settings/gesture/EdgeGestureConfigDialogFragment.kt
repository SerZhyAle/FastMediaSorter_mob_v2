package com.sza.fastmediasorter.ui.settings.gesture

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.core.screencapture.gesture.GestureAccessibilityActions
import com.sza.fastmediasorter.databinding.DialogEdgeGestureConfigBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.ScreenshotGestureActionPickerManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1035: full-screen host for the edge-gesture detail UI, extracted from the Operations settings tab
 * (mirrors [com.sza.fastmediasorter.ui.settings.DefaultAppsDialogFragment]). The launcher in the tab is
 * gated by the same non-empty [ScreenGestureOverlayController] set, so the dialog never renders on a
 * flavor without the capability. All binding/persistence is delegated to [EdgeGestureConfigManager],
 * which reuses the shared [ScreenshotGestureActionPickerManager] - no picker or logic is duplicated here.
 */
@AndroidEntryPoint
class EdgeGestureConfigDialogFragment : DialogFragment() {

    private var _binding: DialogEdgeGestureConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject
    lateinit var capabilityAvailability: CapabilityAvailability

    // Empty on every flavor except where the gesture-overlay capability contributes one.
    @Inject
    lateinit var screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>

    // S0797: gates the "start screen recording" action in the shared picker.
    @Inject
    lateinit var screenVideoRecordingControllers: Set<@JvmSuppressWildcards ScreenVideoRecordingController>

    // S1038: empty except on noLegal; gates the SYSTEM (accessibility) action group in the shared picker.
    @Inject
    lateinit var gestureAccessibilityActions: Set<@JvmSuppressWildcards GestureAccessibilityActions>

    // Guards render() writes so setCheckedSilently never bounces back into a settings update.
    private var isUpdatingFromSettings = false

    private val gestureActionPickerManager by lazy {
        ScreenshotGestureActionPickerManager(
            capabilityAvailability,
            screenRecordingAvailable = screenVideoRecordingControllers.isNotEmpty(),
            systemActionsAvailable = gestureAccessibilityActions.isNotEmpty(),
        )
    }

    private val manager by lazy {
        EdgeGestureConfigManager(
            binding, viewModel, this, screenGestureControllers, gestureActionPickerManager,
            { isUpdatingFromSettings }, ::showDestinationPicker, ::refreshDestinationLabel,
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Timber.d("S1035: edge-gesture dialog opened")
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogEdgeGestureConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismiss() }
        manager.setup()
        collectOnLifecycle(viewModel.settings) { settings ->
            isUpdatingFromSettings = true
            manager.render(settings)
            isUpdatingFromSettings = false
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // Every control applies immediately, so there is no positive action - a no-op confirm keeps
        // Esc-dismiss and focus traversal without a false Enter-confirm (DefaultAppsDialogFragment pattern).
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
        binding.btnClose.requestFocus()
    }

    override fun onDestroyView() {
        manager.teardown()
        super.onDestroyView()
        _binding = null
    }

    private fun showDestinationPicker(currentResourceId: Long?, onPicked: (MediaResource?) -> Unit) {
        ListSelectionDialog(
            requireContext(),
            ListSelectionConfig(
                title = getString(R.string.setting_select_destination),
                lifecycleOwner = viewLifecycleOwner,
                loader = { viewModel.destinations.value },
                formatter = object : ListSelectionAdapter.ItemFormatter<MediaResource> {
                    override fun getDisplayName(item: MediaResource): String = item.name
                },
                hasSelection = currentResourceId != null,
                isSelected = { it.id == currentResourceId },
                allowClear = true,
                emptyMessageRes = R.string.no_resources_available,
                errorMessageRes = R.string.no_resources_available,
                onSelected = onPicked,
            ),
        ).show()
    }

    private fun refreshDestinationLabel(resourceId: String?, fallbackRes: Int, setLabel: (CharSequence) -> Unit) {
        val id = resourceId?.toLongOrNull()
        if (id == null) {
            setLabel(getString(fallbackRes))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val resource = viewModel.resourceRepository.getResourceById(id)
            setLabel(resource?.name ?: getString(fallbackRes))
        }
    }

    companion object {
        const val TAG = "EdgeGestureConfigDialog"
    }
}
