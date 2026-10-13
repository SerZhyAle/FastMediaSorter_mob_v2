package com.sza.fastmediasorter.ui.tourist.counter

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityTouristCounterBinding
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.tourist.TouristCounterState
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.ui.tourist.counter.helpers.TouristCounterDialogsManager
import com.sza.fastmediasorter.ui.tourist.counter.helpers.TouristCounterHistoryAdapter
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** S4184: the Tourist tally counter, opened from its dashboard tile. */
@AndroidEntryPoint
class TouristCounterActivity : BaseActivity<ActivityTouristCounterBinding>() {

    private val viewModel: TouristCounterViewModel by viewModels()

    @Inject
    lateinit var quantityFormatter: QuantityFormatter

    @Inject
    lateinit var unitSystemProvider: UnitSystemProvider

    private lateinit var dialogs: TouristCounterDialogsManager
    private lateinit var historyAdapter: TouristCounterHistoryAdapter

    override fun getViewBinding(): ActivityTouristCounterBinding =
        ActivityTouristCounterBinding.inflate(layoutInflater)

    override fun getInitialFocusView(): View = binding.btnPlusOne

    override fun setupViews() {
        binding.counterRoot.applySystemBarInsetPadding()
        binding.toolbar.setUpNavigation(this)
        dialogs = TouristCounterDialogsManager(this)
        historyAdapter = TouristCounterHistoryAdapter(::formatMoment)
        binding.history.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.history.rvHistory.adapter = historyAdapter

        binding.btnPlusOne.text = getString(R.string.tourist_counter_step_plus, 1)
        binding.btnMinusOne.text = getString(R.string.tourist_counter_step_minus, 1)
        binding.btnPlusOne.setOnClickListener { viewModel.add(1L) }
        binding.btnMinusOne.setOnClickListener { viewModel.add(-1L) }
        bindExtraSteps()

        binding.switchExtraSteps.setOnCheckedChangeListener { viewModel.setExtraStepsVisible(it) }
        binding.btnStartValue.setOnClickListener {
            dialogs.askStartValue(viewModel.state.value.startValue) { viewModel.setStartValue(it) }
        }
        binding.btnReset.setOnClickListener {
            dialogs.confirmReset(viewModel.state.value.startValue) { viewModel.reset() }
        }
        binding.history.btnClearHistory.setOnClickListener {
            dialogs.confirmClearHistory { viewModel.clearHistory() }
        }
    }

    private fun bindExtraSteps() {
        val steps = binding.extraSteps
        val buttons = listOf(
            steps.btnPlus3,
            steps.btnPlus5,
            steps.btnPlus10,
            steps.btnPlus50,
            steps.btnPlus100,
            steps.btnPlus1000,
        )
        buttons.zip(TouristCounterState.EXTRA_STEPS).forEach { (button, step) ->
            button.text = getString(R.string.tourist_counter_step_plus, step)
            button.setOnClickListener { viewModel.add(step) }
        }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { render(it) }
        // A unit-system flip changes the clock format of every history row and the session line.
        collectOnLifecycle(unitSystemProvider.current) {
            render(viewModel.state.value)
            historyAdapter.notifyItemRangeChanged(0, historyAdapter.itemCount)
        }
    }

    private fun render(state: TouristCounterState) {
        binding.tvCounterValue.text = state.value.toString()
        val startLine = getString(R.string.tourist_counter_start_label, state.startValue.toString())
        val sessionLine = state.sessionStartedAtMillis?.let {
            getString(R.string.tourist_counter_session_label, formatMoment(it))
        }
        binding.tvCounterStart.text = listOfNotNull(startLine, sessionLine).joinToString(LINE_BREAK)
        binding.btnMinusOne.isEnabled = state.value > 0L
        // Silent, so restoring the stored choice never writes it back.
        binding.switchExtraSteps.setCheckedSilently(state.extraStepsVisible)
        binding.extraSteps.root.visibility = if (state.extraStepsVisible) View.VISIBLE else View.GONE
        binding.history.tvHistoryEmpty.visibility = if (state.history.isEmpty()) View.VISIBLE else View.GONE
        binding.history.btnClearHistory.isEnabled = state.history.isNotEmpty()
        historyAdapter.submitList(state.history)
    }

    private fun formatMoment(epochMillis: Long): String =
        quantityFormatter.format(Quantity.DateTime(epochMillis), unitSystemProvider.value)

    companion object {
        private const val LINE_BREAK = "\n"

        fun createIntent(context: Context): Intent = Intent(context, TouristCounterActivity::class.java)
    }
}
