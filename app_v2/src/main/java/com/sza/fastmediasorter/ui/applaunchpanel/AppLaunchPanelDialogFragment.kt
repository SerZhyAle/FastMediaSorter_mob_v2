package com.sza.fastmediasorter.ui.applaunchpanel

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogAppLaunchPanelBinding
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.ui.applaunchpanel.edit.EditAppLaunchPanelActivity
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * Large quick-launch grid shown over the foreground app. Tapping a filled tile launches its target
 * and closes the host; an empty slot or the Edit affordance opens [EditAppLaunchPanelActivity].
 * Mirrors DeviceProfilePickerDialogFragment (programmatic window sizing in [onStart]). No business
 * logic here - launch / route decisions come from [AppLaunchPanelViewModel] (Rule 3).
 */
@AndroidEntryPoint
class AppLaunchPanelDialogFragment : DialogFragment() {

    private val viewModel: AppLaunchPanelViewModel by activityViewModels()

    private var _binding: DialogAppLaunchPanelBinding? = null
    private val binding get() = _binding!!

    private lateinit var tileAdapter: AppLaunchPanelTileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogAppLaunchPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tileAdapter = AppLaunchPanelTileAdapter(onTileClick = ::onTileClicked)
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) 5 else 3
        binding.rvPanelTiles.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvPanelTiles.adapter = tileAdapter
        binding.btnEditPanel.setOnClickListener { openEditPanel() }

        collectOnLifecycle(viewModel.tiles) { tiles -> tileAdapter.submit(tiles) }
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val width = minOf((metrics.widthPixels * 0.94f).toInt(), (metrics.density * 720f).toInt())
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

    private fun onTileClicked(tile: AppLaunchPanelTileUi) {
        when (viewModel.onTileSelected(tile)) {
            PanelResult.LAUNCH -> {
                viewModel.launch(tile)
                dismiss()
                requireActivity().finish()
            }
            PanelResult.EDIT -> openEditPanel()
        }
    }

    private fun openEditPanel() {
        startActivity(Intent(requireContext(), EditAppLaunchPanelActivity::class.java))
    }

    companion object {
        const val TAG = "AppLaunchPanelDialog"
    }
}
