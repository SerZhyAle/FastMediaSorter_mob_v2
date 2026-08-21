package com.sza.fastmediasorter.ui.launcher.menu

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.databinding.FragmentLauncherStartMenuBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.ui.applaunchpanel.edit.ResourcePickerDialogFragment
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherResourceCreateManager
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.settings.LauncherSettingsDialogFragment
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.util.showBoundTo
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404: the Start menu - the one place that reaches everything the desktop does not show: the app
 * itself, our resources, every installed app, Android settings, and the way out of launcher mode.
 */
@AndroidEntryPoint
class LauncherStartMenuFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var roleManager: LauncherRoleManager

    @Inject
    lateinit var resourceCreateManager: LauncherResourceCreateManager

    private val viewModel: LauncherHomeViewModel by activityViewModels()

    private var _binding: FragmentLauncherStartMenuBinding? = null
    private val binding get() = _binding!!

    // The exit dialog is the feature's escape hatch, so it must not survive its host: an Activity-recreating
    // config change (dark mode, locale, density - none of them in this Activity's configChanges) would leak
    // the window and leave the positive lambda holding a detached fragment (S0892 precedent).
    private var exitDialog: Dialog? = null

    /**
     * S1643: the panel opens from the edge of the bar it belongs to (owner ruling, strategic §6 item 3).
     *
     * The bottom placement keeps the Material bottom sheet untouched, so that branch delegates upward
     * unchanged. The top placement gets a floating window pinned under the Start button instead: a sheet
     * anchored to the opposite screen edge would leave the menu as far from its own button as the layout
     * allows.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Timber.d("S1643: start menu opening, taskbarAtTop=${viewModel.taskbarAtTop.value}")
        if (!viewModel.taskbarAtTop.value) {
            return super.onCreateDialog(savedInstanceState)
        }
        return Dialog(requireContext(), R.style.Theme_FastMediaSorter_Launcher_TopPanel).apply {
            window?.let { panel ->
                panel.setGravity(Gravity.TOP or Gravity.START)
                panel.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
                panel.attributes = panel.attributes.apply { y = startButtonBottom() }
            }
        }
    }

    /**
     * Window coordinates rather than a dimension sum: the bar's distance from the top edge depends on the
     * system inset the root applies and on whether the launcher's own status strip is shown above it, and
     * neither is knowable from resources alone. A missing anchor yields the top edge, which is where the
     * panel would sit anyway before the bar has been laid out.
     */
    private fun startButtonBottom(): Int {
        val anchor = activity?.findViewById<View>(R.id.btnStart) ?: return 0
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        return location[1] + anchor.height
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLauncherStartMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowOpenApp.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java))
            dismiss()
        }
        binding.rowResources.setOnClickListener { openResourcePicker() }
        binding.rowCreateResource.setOnClickListener {
            resourceCreateManager.startCreateResource(requireContext())
            dismiss()
        }
        binding.rowAndroidSettings.setOnClickListener {
            viewModel.run(LauncherCellCommand.OsShortcut(OsShortcutCatalog.KEY_SETTINGS))
            dismiss()
        }
        binding.rowAppSettings.setOnClickListener {
            // S1088: plain app (FMS) settings; the launcher's own settings now have a dedicated row below.
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
            dismiss()
        }
        binding.rowLauncherSettings.setOnClickListener {
            // S1088: the launcher's own settings dialog, opened in place without leaving the launcher. It
            // attaches to the activity FragmentManager, so dismissing this sheet does not take it down.
            LauncherSettingsDialogFragment().show(parentFragmentManager, LauncherSettingsDialogFragment.TAG)
            dismiss()
        }
        binding.rowEditDesktop.setOnClickListener {
            viewModel.setEditMode(true)
            dismiss()
        }
        binding.rowReboot.setOnClickListener { confirmReboot() }
        binding.rowShutdown.setOnClickListener { confirmShutdown() }
        binding.rowExitMode.setOnClickListener { confirmExit() }

        listenForPickedResource()
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { DialogKeyboardDelegate.applyToDialogFragment(it, onConfirm = {}) }
        expandSheet()
        capTopPanelToAvailableHeight()
        binding.rowOpenApp.requestFocus()
    }

    // S1588: left collapsed, the sheet's visible height is Material's auto-peek formula
    // max(peekHeightMin, parentHeight - parentWidth * 9 / 16), which covers the rows in portrait by
    // accident and collapses to a single row in landscape. Expanding explicitly drops that dependency
    // on screen geometry; skipCollapsed keeps a downward swipe from parking at the peek height.
    private fun expandSheet() {
        val behavior = (dialog as? BottomSheetDialog)?.behavior ?: return
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    /**
     * S1643: the top panel is a floating window that wraps its rows and starts below the bar, so a menu
     * taller than what is left of the screen would be cut off at the bottom instead of scrolling - the
     * window's own height is measured against the whole display, not against the part below its offset.
     *
     * Capping the scroll container to that remainder gives it back the overflow to scroll (strategic §5.2),
     * and it only ever shrinks: a menu that already fits keeps wrapping its content.
     */
    private fun capTopPanelToAvailableHeight() {
        val attributes = (dialog?.window ?: return).attributes
        if (dialog is BottomSheetDialog) {
            return
        }
        val available = resources.displayMetrics.heightPixels - attributes.y
        binding.root.post {
            val root = _binding?.root ?: return@post
            if (available > 0 && root.height > available) {
                root.updateLayoutParams { height = available }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exitDialog?.dismiss()
        exitDialog = null
        powerDialog?.dismiss()
        powerDialog = null
        _binding = null
    }

    private fun openResourcePicker() {
        // The sheet stays touchable behind the picker, so a second tap must not stack a second instance.
        if (parentFragmentManager.findFragmentByTag(RESOURCE_PICKER_TAG) != null) return
        ResourcePickerDialogFragment.newInstance(RESOURCE_REQUEST_KEY)
            .show(parentFragmentManager, RESOURCE_PICKER_TAG)
    }

    private fun listenForPickedResource() {
        setFragmentResultListener(RESOURCE_REQUEST_KEY) { _, bundle ->
            val resourceId = bundle.getLong(ResourcePickerDialogFragment.RESULT_RESOURCE_ID, -1L)
            if (resourceId <= 0L) return@setFragmentResultListener
            viewModel.run(LauncherCellCommand.Resource(resourceId, LauncherResourceMode.BROWSE))
            dismiss()
        }
    }

    private fun confirmExit() {
        // Buttons are not styled here on purpose: the app theme's materialAlertDialogTheme maps
        // buttonBarPositive/NegativeButtonStyle to the S0538 DialogConfirm/DialogCancel pair, so a
        // per-call style would be exactly the drift that seam exists to prevent.
        exitDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.launcher_menu_exit_confirm_title)
            .setMessage(R.string.launcher_menu_exit_confirm_message)
            .setPositiveButton(R.string.launcher_menu_exit_confirm_action) { _, _ ->
                val host = activity ?: return@setPositiveButton
                roleManager.disableMode()
                roleManager.openHomeChooser(host)
                dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(this@LauncherStartMenuFragment)
    }

    private var powerDialog: Dialog? = null

    private fun confirmReboot() {
        powerDialog?.dismiss()
        powerDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.launcher_menu_reboot_confirm_title)
            .setMessage(R.string.launcher_menu_reboot_confirm_message)
            .setPositiveButton(R.string.launcher_menu_reboot) { _, _ ->
                performPowerAction(isReboot = true)
                dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(this@LauncherStartMenuFragment)
    }

    private fun confirmShutdown() {
        powerDialog?.dismiss()
        powerDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.launcher_menu_shutdown_confirm_title)
            .setMessage(R.string.launcher_menu_shutdown_confirm_message)
            .setPositiveButton(R.string.launcher_menu_shutdown) { _, _ ->
                performPowerAction(isReboot = false)
                dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(this@LauncherStartMenuFragment)
    }

    private fun performPowerAction(isReboot: Boolean) {
        val context = context?.applicationContext ?: return
        if (isReboot) {
            // Unsafe cast on purpose: an absent PowerManager has to reach the broadcast fallback,
            // and runCatching turns it into the same failure a SecurityException produces.
            val success = runCatching {
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager).reboot(null)
            }.isSuccess
            if (!success) {
                val intent = Intent(Intent.ACTION_REBOOT).apply {
                    putExtra("nowait", 1)
                    putExtra("interval", 1)
                    putExtra("window", 0)
                }
                runCatching { context.sendBroadcast(intent) }
                runCatching { Runtime.getRuntime().exec(arrayOf("reboot")) }
            }
        } else {
            val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN").apply {
                putExtra("android.intent.extra.KEY_CONFIRM", false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val success = runCatching { context.startActivity(intent) }.isSuccess
            if (!success) {
                runCatching { Runtime.getRuntime().exec(arrayOf("reboot", "-p")) }
            }
        }
    }

    companion object {
        const val TAG = "launcher_start_menu"

        private const val RESOURCE_REQUEST_KEY = "launcher_start_menu_resource"
        private const val RESOURCE_PICKER_TAG = "launcher_start_menu_resource_picker"
    }
}
