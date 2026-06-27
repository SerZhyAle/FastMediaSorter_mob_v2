package com.sza.fastmediasorter.ui.keybinding

import android.os.Bundle
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityKeybindingRemapBinding
import com.sza.fastmediasorter.domain.input.CommandGroup
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.keybinding.helpers.KeybindingRowLabelFormatter
import com.sza.fastmediasorter.ui.keybinding.helpers.ResetConfirmationDialog
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class KeybindingRemapActivity : BaseActivity<ActivityKeybindingRemapBinding>() {

    private val viewModel: KeybindingRemapViewModel by viewModels()

    @Inject lateinit var formatter: KeybindingRowLabelFormatter

    private lateinit var listAdapter: KeybindingListAdapter

    override fun getViewBinding(): ActivityKeybindingRemapBinding =
        ActivityKeybindingRemapBinding.inflate(layoutInflater)

    /** S0289 Phase 09: multimodal surface marker - hotkey remap lives under the settings host. */
    @Suppress("unused")
    private val multimodalInputSurface: UiSurface = UiSurface.SETTINGS

    /** S0289 §2.4: initial focus on the bindings list. */
    override fun getInitialFocusView(): android.view.View? {
        return binding.recyclerView
    }

    // S0510: no own F1 handler - declare the surface so BaseActivity's global F1 opens input help.
    override fun getInputHelpSurface(): UiSurface = UiSurface.SETTINGS

    /** S0289 Phase 09: route mouse wheel onto the bindings list. */
    override fun getMouseScrollTargetView(): android.view.View? = binding.recyclerView

    override fun setupViews() {
        binding.backButton.setOnClickListener { finish() }

        setupAdapter()
        setupSearch()
        setupFragmentResults()

        binding.btnResetAll.setOnClickListener {
            viewModel.onResetAllRequested()
        }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            val items = buildDisplayItems(state)
            listAdapter.submitList(items)

            if (state.pendingCapture != null) {
                showCaptureDialog()
            } else {
                dismissCaptureDialog()
            }

            handlePendingConfirmation(state.pendingConfirmation)
        }
    }

    // ----- setup helpers -----

    private fun setupAdapter() {
        listAdapter = KeybindingListAdapter(
            formatter = formatter,
            onRemapClick = { commandId, device, slot ->
                viewModel.onRemapRequested(commandId, device, slot)
            },
            onResetClick = { commandId ->
                viewModel.onResetRowRequested(commandId)
            },
            onGroupHeaderClick = { group ->
                viewModel.onGroupToggle(group)
            },
            onGroupResetClick = { group ->
                viewModel.onResetGroupRequested(group)
            }
        )
        val isWide = resources.configuration.isWideLayout()
        val spanCount = if (isWide) 2 else 1
        val glm = GridLayoutManager(this, spanCount)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (listAdapter.getItemViewType(position) == KeybindingListAdapter.VIEW_TYPE_HEADER) spanCount else 1
        }
        binding.recyclerView.layoutManager = glm
        binding.recyclerView.adapter = listAdapter
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.onFilterChanged(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupFragmentResults() {
        supportFragmentManager.setFragmentResultListener(
            CaptureDialogFragment.RESULT_KEY, this
        ) { _, bundle ->
            val triggerStr = bundle.getString(CaptureDialogFragment.KEY_TRIGGER) ?: return@setFragmentResultListener
            runCatching { InputTrigger.deserialize(triggerStr) }
                .onSuccess { viewModel.onCaptureCompleted(it) }
                .onFailure { Timber.w(it, "KeybindingRemap: failed to deserialize trigger") }
        }
    }

    // ----- display list builder -----

    private fun buildDisplayItems(state: RemapUiState): List<KeybindingListItem> {
        val items = mutableListOf<KeybindingListItem>()
        val groupedRows = state.rows.groupBy { it.group }
        for (group in CommandGroup.values()) {
            val rows = groupedRows[group] ?: continue
            items.add(KeybindingListItem.Header(group, group in state.expandedGroups))
            if (group in state.expandedGroups) {
                rows.forEach { items.add(KeybindingListItem.Row(it)) }
            }
        }
        return items
    }

    // ----- dialog management -----

    private fun showCaptureDialog() {
        if (supportFragmentManager.findFragmentByTag(TAG_CAPTURE) == null) {
            CaptureDialogFragment().show(supportFragmentManager, TAG_CAPTURE)
        }
    }

    private fun dismissCaptureDialog() {
        (supportFragmentManager.findFragmentByTag(TAG_CAPTURE) as? CaptureDialogFragment)?.dismiss()
    }

    private fun handlePendingConfirmation(pending: PendingConfirmation?) {
        pending ?: return
        when (pending) {
            is PendingConfirmation.GroupReset -> {
                val groupResName = "keybinding_group_" + pending.group.name.lowercase()
                val groupName = formatter.resolveGroupLabel(groupResName)
                ResetConfirmationDialog.showForGroup(this, pending.group, groupName,
                    onConfirm = { viewModel.onConfirmReset() }
                )
                viewModel.onCancelReset() // clear pending so dialog doesn't re-trigger on recompose
            }
            PendingConfirmation.AllReset -> {
                ResetConfirmationDialog.showForAll(this,
                    onConfirm = { viewModel.onConfirmReset() }
                )
                viewModel.onCancelReset()
            }
        }
    }

    companion object {
        private const val TAG_CAPTURE = "capture_dialog"
    }
}
