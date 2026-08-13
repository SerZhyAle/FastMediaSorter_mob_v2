package com.sza.fastmediasorter.ui.calculator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityCalculatorBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorInputManager
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class CalculatorActivity : BaseActivity<ActivityCalculatorBinding>() {

    private lateinit var inputManager: CalculatorInputManager

    override fun getViewBinding(): ActivityCalculatorBinding =
        ActivityCalculatorBinding.inflate(layoutInflater)

    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = false

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finishWithResult() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithResult()
                }
            },
        )
        inputManager = CalculatorInputManager(binding, this).also {
            it.bind()
            it.applyInitialInput(intent.getStringExtra(EXTRA_INITIAL_INPUT))
        }
        binding.btnCalculatorOpenSettings.setOnClickListener { openSettings() }
    }

    override fun observeData() {
        collectOnLifecycle(appSettings) { settings ->
            renderAvailability(settings.enableCalculator)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::inputManager.isInitialized && inputManager.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun getInitialFocusView(): View? =
        if (binding.calculatorContentGroup.isVisible) {
            binding.btnCalculatorSeven
        } else {
            binding.btnCalculatorOpenSettings
        }

    private fun renderAvailability(enabled: Boolean) {
        binding.calculatorContentGroup.isVisible = enabled
        binding.calculatorFallbackGroup.isVisible = !enabled
        if (enabled && ::inputManager.isInitialized) {
            inputManager.render()
        }
    }

    private fun openSettings() {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_PLAYBACK)
        )
    }

    private fun finishWithResult() {
        if (intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)) {
            val result = if (binding.calculatorContentGroup.isVisible && ::inputManager.isInitialized) {
                inputManager.currentResultOrNull()
            } else {
                null
            }
            if (result.isNullOrBlank()) {
                setResult(Activity.RESULT_CANCELED)
            } else {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, result))
            }
        }
        finish()
    }

    companion object {
        private const val EXTRA_FROM_WIDGET = "extra_from_widget"
        private const val EXTRA_INITIAL_INPUT = "extra_initial_input"
        private const val EXTRA_RETURN_RESULT = "extra_return_result"
        private const val EXTRA_RESULT = "extra_result"

        fun createIntent(
            context: Context,
            fromWidget: Boolean = false,
            initialInput: String? = null,
            returnResult: Boolean = false,
        ): Intent =
            Intent(context, CalculatorActivity::class.java)
                .putExtra(EXTRA_FROM_WIDGET, fromWidget)
                .putExtra(EXTRA_RETURN_RESULT, returnResult)
                .apply {
                    if (!initialInput.isNullOrBlank()) {
                        putExtra(EXTRA_INITIAL_INPUT, initialInput)
                    }
                }

        fun readResult(data: Intent?): String? = data?.getStringExtra(EXTRA_RESULT)
    }
}
