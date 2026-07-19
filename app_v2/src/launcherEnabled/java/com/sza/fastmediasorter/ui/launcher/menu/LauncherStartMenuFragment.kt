package com.sza.fastmediasorter.ui.launcher.menu

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.databinding.FragmentLauncherStartMenuBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.panel.QueryLaunchableAppsUseCase
import com.sza.fastmediasorter.ui.applaunchpanel.edit.ResourcePickerDialogFragment
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarIcon
import com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarIconAdapter
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.settings.LauncherSettingsDialogFragment
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S0404: the Start menu - the one place that reaches everything the desktop does not show: the app
 * itself, our resources, every installed app, Android settings, and the way out of launcher mode.
 */
@AndroidEntryPoint
class LauncherStartMenuFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var queryLaunchableApps: QueryLaunchableAppsUseCase

    @Inject
    lateinit var roleManager: LauncherRoleManager

    private val viewModel: LauncherHomeViewModel by activityViewModels()

    private var _binding: FragmentLauncherStartMenuBinding? = null
    private val binding get() = _binding!!

    // Enumerating every installed package is slow enough for the user to toggle the row again while it
    // runs; the adapter is still empty at that point, so only an explicit job guard stops a second sweep.
    private var allAppsJob: Job? = null

    // The exit dialog is the feature's escape hatch, so it must not survive its host: an Activity-recreating
    // config change (dark mode, locale, density - none of them in this Activity's configChanges) would leak
    // the window and leave the positive lambda holding a detached fragment (S0892 precedent).
    private var exitDialog: Dialog? = null

    private val appsAdapter = LauncherTaskbarIconAdapter(
        onIconClick = { icon ->
            viewModel.run(LauncherCellCommand.App(icon.id))
            dismiss()
        },
    )

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
        binding.rvAllApps.layoutManager = GridLayoutManager(requireContext(), ALL_APPS_COLUMNS)
        binding.rvAllApps.adapter = appsAdapter

        binding.rowOpenApp.setOnClickListener {
            startActivity(Intent(requireContext(), MainActivity::class.java))
            dismiss()
        }
        binding.rowResources.setOnClickListener { openResourcePicker() }
        binding.rowAllApps.setOnClickListener { toggleAllApps() }
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
        binding.rowExitMode.setOnClickListener { confirmExit() }

        listenForPickedResource()
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { DialogKeyboardDelegate.applyToDialogFragment(it, onConfirm = {}) }
        binding.rowOpenApp.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exitDialog?.dismiss()
        exitDialog = null
        _binding = null
    }

    private fun toggleAllApps() {
        val show = !binding.rvAllApps.isVisible
        binding.rvAllApps.isVisible = show
        if (!show || appsAdapter.itemCount > 0 || allAppsJob?.isActive == true) return
        allAppsJob = viewLifecycleOwner.lifecycleScope.launch {
            val apps = queryLaunchableApps().map {
                LauncherTaskbarIcon(
                    id = it.packageName,
                    label = it.label,
                    iconRes = null,
                    iconDrawable = it.icon,
                )
            }
            appsAdapter.submitIcons(apps)
        }
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
            .show()
    }

    companion object {
        const val TAG = "launcher_start_menu"

        private const val RESOURCE_REQUEST_KEY = "launcher_start_menu_resource"
        private const val RESOURCE_PICKER_TAG = "launcher_start_menu_resource_picker"
        private const val ALL_APPS_COLUMNS = 4
    }
}
