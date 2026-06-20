package com.sza.fastmediasorter.ui.icon.picker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.BottomSheetIconPickerBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.icon.ResourceIconSet
import timber.log.Timber

/** Bottom-sheet dialog that lets the user pick a themed resource icon (S0034 Phase 06). */
class IconPickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetIconPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: IconPickerAdapter

    companion object {
        const val KEY = "icon_picker_result"
        const val RESULT_ICON_ID = "icon_id"

        private const val ARG_CURRENT_ICON_ID = "current_icon_id"
        private const val ARG_DEFAULT_SET = "default_set"

        /** Factory - pass the currently-assigned icon id and the set to pre-open. */
        fun newInstance(currentIconId: String?, defaultSet: ResourceIconSet): IconPickerBottomSheet =
            IconPickerBottomSheet().apply {
                arguments = Bundle().apply {
                    currentIconId?.let { putString(ARG_CURRENT_ICON_ID, it) }
                    putString(ARG_DEFAULT_SET, defaultSet.name)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetIconPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentIconId = arguments?.getString(ARG_CURRENT_ICON_ID)
        val defaultSet = arguments?.getString(ARG_DEFAULT_SET)
            ?.let { runCatching { ResourceIconSet.valueOf(it) }.getOrNull() }
            ?: ResourceIconSet.OTHER

        // Build adapter with current selection
        adapter = IconPickerAdapter(currentIconId) { pickedId ->
            Timber.d("IconPicker: user picked $pickedId")
            setFragmentResult(KEY, bundleOf(RESULT_ICON_ID to pickedId))
            dismiss()
        }

        binding.rvIcons.apply {
            // 5 columns matches the spec grid density
            layoutManager = GridLayoutManager(requireContext(), 5)
            adapter = this@IconPickerBottomSheet.adapter
        }

        // Build one tab per icon set; trigger adapter refresh on tab change
        ResourceIconSet.values().forEach { set ->
            binding.tabsIconSet.addTab(
                binding.tabsIconSet.newTab().setText(setLabel(set))
            )
        }

        binding.tabsIconSet.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val set = ResourceIconSet.values().getOrNull(tab.position) ?: ResourceIconSet.OTHER
                adapter.setItems(set)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // Pre-select the default set tab (triggers onTabSelected → populates adapter)
        val preselectedIndex = ResourceIconSet.values().indexOfFirst { it == defaultSet }
        if (preselectedIndex >= 0) {
            binding.tabsIconSet.getTabAt(preselectedIndex)?.select()
        } else {
            // Fallback: manually populate the first set so the grid is not empty
            adapter.setItems(ResourceIconSet.values().first())
        }
    }

    private fun setLabel(set: ResourceIconSet): String = when (set) {
        ResourceIconSet.MUSIC -> getString(R.string.icon_set_music)
        ResourceIconSet.VIDEO -> getString(R.string.icon_set_video)
        ResourceIconSet.IMAGE -> getString(R.string.icon_set_image)
        ResourceIconSet.DOCS  -> getString(R.string.icon_set_docs)
        ResourceIconSet.OTHER -> getString(R.string.icon_set_other)
    }

    override fun onStart() {
        super.onStart()
        // Pure picker: tapping an icon selects and closes, so no-op confirm only adds Esc-dismiss and
        // focus traversal across the tabs/grid.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
