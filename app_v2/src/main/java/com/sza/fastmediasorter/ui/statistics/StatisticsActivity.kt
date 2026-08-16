package com.sza.fastmediasorter.ui.statistics

import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityStatisticsBinding
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Statistics dashboard host (S0473 Phase 04).
 *
 * Renders the all-time snapshot through [StatisticsViewModel] into a single grid RecyclerView:
 * summary cards (multi-span), the type-distribution bar, collapsible category sections, the empty
 * state and the privacy note. All mapping/visibility lives in the ViewModel; this Activity only
 * wires the toolbar, the grid span lookup and lifecycle-safe state collection. No send/export/reset
 * actions in this phase (Phase 05 adds the bottom action bar; reset never exists, ADR-4).
 *
 * Extends [BaseActivity] rather than a bare AppCompatActivity to inherit the project's edge-to-edge,
 * locale, keep-screen-on and TV/D-pad/mouse plumbing - matching the sibling AuthSessionsActivity.
 *
 * Phase 05 adds the bottom action bar: clicks delegate to the ViewModel, which builds the TXT report
 * off the UI thread and emits a one-shot event the Activity hands to [StatisticsReportShareManager]
 * (the only place Intent/FileProvider live). No reset action exists (ADR-4).
 */
@AndroidEntryPoint
class StatisticsActivity : BaseActivity<ActivityStatisticsBinding>() {

    private val viewModel: StatisticsViewModel by viewModels()

    // S0510: no own F1 handler - declare the surface so BaseActivity's global F1 opens input help.
    override fun getInputHelpSurface(): UiSurface = UiSurface.MAIN

    @Inject
    lateinit var reportShareManager: StatisticsReportShareManager

    private val statisticsAdapter by lazy {
        StatisticsAdapter(
            spanCount = resources.getInteger(R.integer.statistics_card_span),
            onToggleSection = viewModel::toggleCategory,
        )
    }

    override fun getViewBinding(): ActivityStatisticsBinding =
        ActivityStatisticsBinding.inflate(layoutInflater)

    override fun getMouseScrollTargetView(): View = binding.rvStatistics

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val span = resources.getInteger(R.integer.statistics_card_span)
        binding.rvStatistics.layoutManager = GridLayoutManager(this, span).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = statisticsAdapter.spanSizeFor(position)
            }
        }
        binding.rvStatistics.adapter = statisticsAdapter

        binding.btnSendToAuthor.setOnClickListener { viewModel.onSendToAuthor() }
        binding.btnExport.setOnClickListener { viewModel.onExport() }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.uiState) { state ->
            statisticsAdapter.submitList(state.items)
        }
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is StatisticsEvent.SendToAuthor -> reportShareManager.sendToAuthor(event.report)
                is StatisticsEvent.Export -> reportShareManager.export(event.report)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun getInitialFocusView(): View = binding.toolbar
}
