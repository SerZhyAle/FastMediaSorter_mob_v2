package com.sza.fastmediasorter.ui.settings.gesture

import android.app.Dialog
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import com.sza.fastmediasorter.domain.usecase.panel.QueryLaunchableAppsUseCase
import com.sza.fastmediasorter.ui.applaunchpanel.edit.AppPickerDialogFragment
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.helpers.LocalFolderDestinationPickerManager
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
class EdgeGestureConfigDialogFragment : DialogFragment(), EdgeGestureConfigManager.AppSlotHost {

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

    // S1036: the inline app rows read their label from the picker's own source, so a row and the picker
    // can never disagree about what an app is called.
    @Inject
    lateinit var queryLaunchableApps: QueryLaunchableAppsUseCase

    // Guards render() writes so setCheckedSilently never bounces back into a settings update.
    private var isUpdatingFromSettings = false

    private val gestureActionPickerManager by lazy {
        ScreenshotGestureActionPickerManager(
            capabilityAvailability,
            screenRecordingAvailable = screenVideoRecordingControllers.isNotEmpty(),
            systemActionsAvailable = gestureAccessibilityActions.isNotEmpty(),
        )
    }

    private val localFolderDestinationPickerManager by lazy {
        LocalFolderDestinationPickerManager(this, viewModel, localFolderDestinationPickerLauncher)
    }

    // S1010: SAF picker for the "Local Folder" screenshot-destination option. Must be created at
    // field-init time (registerForActivityResult must run before the fragment reaches CREATED).
    private val localFolderDestinationPickerLauncher: ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            localFolderDestinationPickerManager.onFolderPicked(uri)
        }

    // S1123: not `by lazy` - the manager captures the binding, so a re-inflate for a new orientation
    // has to hand it a fresh one, and a lazy cannot be reset.
    private var manager: EdgeGestureConfigManager? = null

    /** Orientation the currently inflated layout variant was chosen for. */
    private var inflatedOrientation = Configuration.ORIENTATION_UNDEFINED

    // S1036: the slot the app picker is currently choosing for. Held here rather than in the manager
    // because the manager is rebuilt on every re-inflate, and saved because losing it mid-pick would
    // write the chosen package into no slot at all.
    private var pendingAppSlot: Pair<ScreenshotGestureZone, ScreenshotGestureDirection>? = null

    // S1036: one lookup per dialog. The list is a device-wide fact that cannot change while a modal
    // dialog is up, and every render pass would otherwise pay for the same query twelve times over.
    private var appLabels: Map<String, String>? = null

    private fun createManager() = EdgeGestureConfigManager(
        binding,
        viewModel,
        this,
        screenGestureControllers,
        gestureActionPickerManager,
        { isUpdatingFromSettings },
        ::showDestinationPicker,
        ::refreshDestinationLabel,
        this,
    )

    /** S1036: hands back the label of [packageName], or `null` when it is no longer installed. */
    override fun resolveAppLabel(packageName: String, onResolved: (String?) -> Unit) {
        appLabels?.let {
            onResolved(it[packageName])
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val labels = queryLaunchableApps().associate { it.packageName to it.label }
            appLabels = labels
            onResolved(labels[packageName])
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAppSlot = restorePendingAppSlot(savedInstanceState)
        // Registered on childFragmentManager because that is the manager showAppPicker shows the picker
        // in - this host is itself a DialogFragment. The request key is this dialog's own, so the panel
        // editor's picks on the activity's manager never arrive here (S0404).
        childFragmentManager.setFragmentResultListener(APP_PICKER_REQUEST_KEY, this) { _, bundle ->
            val packageName = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE).orEmpty()
            val slot = pendingAppSlot
            pendingAppSlot = null
            Timber.d("S1036: app picker returned '%s' for slot %s", packageName, slot)
            if (slot != null && packageName.isNotEmpty()) {
                manager?.onAppPicked(slot.first, slot.second, packageName)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val slot = pendingAppSlot ?: return
        outState.putString(STATE_PENDING_ZONE, slot.first.name)
        outState.putString(STATE_PENDING_DIRECTION, slot.second.name)
    }

    private fun restorePendingAppSlot(state: Bundle?): Pair<ScreenshotGestureZone, ScreenshotGestureDirection>? {
        val zone = state?.getString(STATE_PENDING_ZONE)
        val direction = state?.getString(STATE_PENDING_DIRECTION)
        if (zone == null || direction == null) return null
        return ScreenshotGestureZone.valueOf(zone) to ScreenshotGestureDirection.valueOf(direction)
    }

    override fun showAppPicker(zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection) {
        pendingAppSlot = zone to direction
        AppPickerDialogFragment.newInstance(APP_PICKER_REQUEST_KEY)
            .show(childFragmentManager, AppPickerDialogFragment.TAG)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogEdgeGestureConfigBinding.inflate(inflater, container, false)
        inflatedOrientation = resources.configuration.orientation
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindContent()
        // Reads the field, not a captured instance, so a re-inflate keeps this collector driving the
        // manager that owns the views currently on screen.
        collectOnLifecycle(viewModel.settings) { settings ->
            isUpdatingFromSettings = true
            manager?.render(settings)
            isUpdatingFromSettings = false
        }
    }

    /**
     * S1123: the host Activity declares `configChanges` for orientation, so a rotation recreates
     * neither it nor this fragment and [onCreateView] never runs again - the layout variant inflated
     * when the dialog opened stays on screen for the rest of its life. Swap the content for the one
     * the new configuration resolves, which is what a recreate would have produced.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val hostDialog = dialog ?: return
        if (newConfig.orientation == inflatedOrientation) return

        // The chosen zone lives in the TabLayout, which the re-inflate replaces - carry it across, or
        // the dialog silently jumps back to the first zone on every rotation.
        val selectedZoneTab = binding.tabsEdgeGestureZones.selectedTabPosition

        manager?.teardown()
        _binding = DialogEdgeGestureConfigBinding.inflate(LayoutInflater.from(requireContext()))
        inflatedOrientation = newConfig.orientation
        hostDialog.setContentView(binding.root)
        bindContent()
        // onStart does not run again either, so the window chrome has to be re-applied by hand.
        applyDialogChrome()
        isUpdatingFromSettings = true
        manager?.render(viewModel.settings.value)
        isUpdatingFromSettings = false
        if (selectedZoneTab > 0) binding.tabsEdgeGestureZones.getTabAt(selectedZoneTab)?.select()
    }

    private fun bindContent() {
        binding.btnClose.setOnClickListener { dismiss() }
        manager = createManager().also { it.setup() }
    }

    override fun onStart() {
        super.onStart()
        applyDialogChrome()
    }

    private fun applyDialogChrome() {
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
        manager?.teardown()
        manager = null
        super.onDestroyView()
        _binding = null
    }

    private fun showDestinationPicker(currentResourceId: Long?, onPicked: (MediaResource?) -> Unit) {
        ListSelectionDialog(
            requireContext(),
            ListSelectionConfig(
                title = getString(R.string.setting_select_destination),
                lifecycleOwner = viewLifecycleOwner,
                loader = {
                    listOf(LocalFolderDestinationPickerManager.sentinelItem(requireContext())) +
                        viewModel.destinations.value
                },
                formatter = object : ListSelectionAdapter.ItemFormatter<MediaResource> {
                    override fun getDisplayName(item: MediaResource): String = item.name
                },
                hasSelection = currentResourceId != null,
                isSelected = { it.id == currentResourceId },
                allowClear = true,
                emptyMessageRes = R.string.no_resources_available,
                errorMessageRes = R.string.no_resources_available,
                onSelected = localFolderDestinationPickerManager.wrapOnSelected(currentResourceId, onPicked),
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

        // S1036: this dialog's own fragment-result key, deliberately not the picker's shared default one.
        private const val APP_PICKER_REQUEST_KEY = "edge_gesture_app_picker_result"
        private const val STATE_PENDING_ZONE = "pending_app_zone"
        private const val STATE_PENDING_DIRECTION = "pending_app_direction"
    }
}
