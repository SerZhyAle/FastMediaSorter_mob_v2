package com.sza.fastmediasorter.ui.wearresources

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityWearResourceSelectionBinding
import com.sza.fastmediasorter.ui.common.input.UiSurface
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WearResourceSelectionActivity : BaseActivity<ActivityWearResourceSelectionBinding>() {

    private val viewModel: WearResourceSelectionViewModel by viewModels()

    private val adapter = WearResourceSelectionAdapter(
        onCategoryToggle = { category -> viewModel.toggleCategoryExpanded(category) },
        onSelectionChanged = { resource, selected -> viewModel.setSelected(resource.id, selected) }
    )

    /** S0289: multimodal surface marker - the picker is reached from the settings host. */
    @Suppress("unused")
    private val multimodalInputSurface: UiSurface = UiSurface.SETTINGS

    override fun getViewBinding(): ActivityWearResourceSelectionBinding =
        ActivityWearResourceSelectionBinding.inflate(layoutInflater)

    override fun getMouseScrollTargetView(): View = binding.rvResources

    override fun getInputHelpSurface(): UiSurface = UiSurface.SETTINGS

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvResources.adapter = adapter
        binding.btnSelectAll.setOnClickListener { viewModel.selectAll() }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val items = buildAdapterItems(state)
                    adapter.submitList(items)
                    adapter.setSelectedIds(state.selectedIds)
                    val empty = state.isLoaded && state.resources.isEmpty()
                    binding.tvEmpty.isVisible = empty
                    binding.btnSelectAll.isVisible = !empty
                    binding.rvResources.isVisible = !empty
                }
            }
        }
    }

    private fun buildAdapterItems(state: WearResourceSelectionUiState): List<WearResourceAdapterItem> {
        val result = mutableListOf<WearResourceAdapterItem>()
        val categories = listOf(
            ResourceCategory.VIRTUAL to R.string.wear_resource_group_virtual,
            ResourceCategory.INTERNAL to R.string.wear_resource_group_internal,
            ResourceCategory.EXTERNAL to R.string.wear_resource_group_external
        )

        for ((category, titleRes) in categories) {
            val groupResources = state.resources.filter { it.getResourceCategory() == category }
            if (groupResources.isNotEmpty()) {
                val isExpanded = category in state.expandedCategories
                result.add(
                    WearResourceAdapterItem.Header(
                        category = category,
                        titleRes = titleRes,
                        isExpanded = isExpanded,
                        count = groupResources.size
                    )
                )
                if (isExpanded) {
                    groupResources.forEach { resource ->
                        result.add(WearResourceAdapterItem.ResourceRow(resource))
                    }
                }
            }
        }
        return result
    }

    override fun getInitialFocusView(): View? =
        binding.rvResources.findViewHolderForAdapterPosition(0)?.itemView
            ?: binding.btnSelectAll.takeIf { it.isVisible }
            ?: binding.toolbar.children.firstOrNull { it.isClickable }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, WearResourceSelectionActivity::class.java))
        }
    }
}
