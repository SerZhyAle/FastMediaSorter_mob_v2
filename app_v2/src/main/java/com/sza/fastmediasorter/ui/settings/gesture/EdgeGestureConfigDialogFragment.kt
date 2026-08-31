package com.sza.fastmediasorter.ui.settings.gesture

import android.app.Dialog
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
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
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

    // S2256: gates the launcher route in the shared picker - the seam is the single availability input,
    // so an edge slot can never be assigned a panel the build does not compile.
    @Inject
    lateinit var launcherModeContract: LauncherModeContract

    private val gestureActionPickerManager by lazy {
        ScreenshotGestureActionPickerManager(
            capabilityAvailability,
            screenRecordingAvailable = screenVideoRecordingControllers.isNotEmpty(),
            systemActionsAvailable = gestureAccessibilityActions.isNotEmpty(),
            launcherRouteAvailable = launcherModeContract.isAvailableInBuild,
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

    // Not `by lazy` - the manager captures the binding, so a new view hierarchy has to hand it a fresh
    // one, and a lazy cannot be reset.
    private var manager: EdgeGestureConfigManager? = null

    // S1549: TabLayout does not save its selection, and the host now recreates on rotation, so the chosen
    // zone has to be carried across by hand or the dialog jumps back to the first zone on every rotation.
    private var restoredZoneTab = 0

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
        restoredZoneTab = savedInstanceState?.getInt(STATE_SELECTED_ZONE_TAB, 0) ?: 0
        // Registered on childFragmentManager because that is the manager showAppPicker shows the picker
        // in - this host is itself a DialogFragment. The request key is this dialog's own, so the panel
        // editor's picks on the activity's manager never arrive here (S0404).
        childFragmentManager.setFragmentResultListener(APP_PICKER_REQUEST_KEY, this) { _, bundle ->
            val packageName = bundle.getString(AppPickerDialogFragment.RESULT_PACKAGE).orEmpty()
            val slot = pendingAppSlot
            pendingAppSlot = null
            if (slot != null && packageName.isNotEmpty()) {
                manager?.onAppPicked(slot.first, slot.second, packageName)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.let { outState.putInt(STATE_SELECTED_ZONE_TAB, it.tabsEdgeGestureZones.selectedTabPosition) }
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindContent()
        if (restoredZoneTab > 0) binding.tabsEdgeGestureZones.getTabAt(restoredZoneTab)?.select()
        collectOnLifecycle(viewModel.settings) { settings ->
            isUpdatingFromSettings = true
            manager?.render(settings)
            isUpdatingFromSettings = false
        }
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
        private const val STATE_SELECTED_ZONE_TAB = "selected_zone_tab"
    }
}
