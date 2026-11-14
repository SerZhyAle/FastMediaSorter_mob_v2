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
import com.sza.fastmediasorter.data.local.preferences.SharedPreferencesCollapsibleSectionStore
import com.sza.fastmediasorter.databinding.ActivityWearResourceSelectionBinding
import com.sza.fastmediasorter.ui.common.input.UiSurface
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class WearResourceSelectionActivity : BaseActivity<ActivityWearResourceSelectionBinding>() {

    private val viewModel: WearResourceSelectionViewModel by viewModels()

    private val adapter = WearResourceSelectionAdapter(
        onCategoryToggle = ::toggleCategory,
        onSelectionChanged = { resource, selected -> viewModel.setSelected(resource.id, selected) }
    )

    private val sectionStore by lazy { SharedPreferencesCollapsibleSectionStore(this) }

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
        binding.toolbar.setUpNavigation(this)
        binding.rvResources.adapter = adapter
        binding.btnSelectAll.setOnClickListener { viewModel.selectAll() }
        viewModel.restoreExpandedCategories(
            ResourceCategory.entries.filter { sectionStore.isExpanded(sectionKey(it), true) }.toSet(),
        )
    }

    // COLLAPSIBLE-GROUP section 4 rule 7: the user's expansion choice outlives the screen.
    private fun toggleCategory(category: ResourceCategory) {
        Timber.d("S4626: wear resource category toggled category=$category")
        viewModel.toggleCategoryExpanded(category)
        sectionStore.setExpanded(sectionKey(category), category in viewModel.uiState.value.expandedCategories)
    }

    private fun sectionKey(category: ResourceCategory) = "wear_resources__${category.name.lowercase()}"

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

        val grouped = state.resources.groupBy { it.getResourceCategory() }
        // COLLAPSIBLE-GROUP section 2 rule 2: one populated category is shown directly, with no wrapper header.
        val showHeaders = grouped.size > 1

        for ((category, titleRes) in categories) {
            val groupResources = grouped[category].orEmpty()
            if (groupResources.isNotEmpty()) {
                val isExpanded = !showHeaders || category in state.expandedCategories
                if (showHeaders) {
                    result.add(
                        WearResourceAdapterItem.Header(
                            category = category,
                            titleRes = titleRes,
                            isExpanded = isExpanded,
                            count = groupResources.size
                        )
                    )
                }
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
