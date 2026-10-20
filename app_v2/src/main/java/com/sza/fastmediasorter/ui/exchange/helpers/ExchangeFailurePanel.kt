package com.sza.fastmediasorter.ui.exchange.helpers

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.databinding.ViewExchangeFailurePanelBinding
import com.sza.fastmediasorter.domain.model.ExchangeDiagnosis
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The status area of an exchange dialog (contract DEVICE-EXCHANGE 25 item DA): a plain progress line, or a
 * failure shown as what happened and what to try, a button for the next step of the ladder, the code behind
 * "Details" and the log export. The code is never the headline; one language is shown at a time.
 */
class ExchangeFailurePanel(
    private val binding: ViewExchangeFailurePanelBinding,
    private val activity: AppCompatActivity,
) {

    private var diagnosis: ExchangeDiagnosis? = null
    private var stepIndex = 0

    init {
        binding.btnExchangeFailureNext.setOnClickListener { showNextStep() }
        binding.btnExchangeFailureDetails.setOnClickListener { toggleCode() }
        binding.btnExchangeFailureSendLog.setOnClickListener { sendLog() }
    }

    fun showProgress(text: CharSequence) {
        diagnosis = null
        binding.root.isVisible = true
        binding.textExchangeFailureWhat.text = text
        binding.textExchangeFailureAdvice.isVisible = false
        binding.textExchangeFailureCode.isVisible = false
        binding.layoutExchangeFailureActions.isVisible = false
    }

    fun showFailure(failure: ExchangeDiagnosis) {
        if (failure == diagnosis) return
        diagnosis = failure
        stepIndex = 0
        Timber.i("Exchange failure shown: code=%s steps=%d", failure.code, failure.steps.size)
        binding.root.isVisible = true
        binding.textExchangeFailureWhat.setText(ExchangeMessageText.what(failure.message))
        binding.textExchangeFailureCode.text = activity.getString(R.string.exchange_failure_details_code, failure.code)
        binding.textExchangeFailureCode.isVisible = false
        binding.layoutExchangeFailureActions.isVisible = true
        renderStep()
    }

    fun hide() {
        diagnosis = null
        binding.root.isVisible = false
    }

    private fun renderStep() {
        val steps = diagnosis?.steps.orEmpty()
        val step = steps.getOrNull(stepIndex)
        binding.textExchangeFailureAdvice.isVisible = step != null
        step?.let { binding.textExchangeFailureAdvice.setText(ExchangeMessageText.advice(it)) }
        binding.btnExchangeFailureNext.isVisible = stepIndex + 1 < steps.size
    }

    private fun showNextStep() {
        stepIndex++
        renderStep()
    }

    private fun toggleCode() {
        binding.textExchangeFailureCode.isVisible = !binding.textExchangeFailureCode.isVisible
    }

    private fun sendLog() {
        activity.lifecycleScope.launch {
            when (val result = LogExportHelper.exportLogs(activity)) {
                is LogExportHelper.ExportResult.NoLogs -> toast(activity.getString(R.string.exchange_failure_log_none))
                is LogExportHelper.ExportResult.Error -> toast(result.message)
                else -> Unit
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }
}
