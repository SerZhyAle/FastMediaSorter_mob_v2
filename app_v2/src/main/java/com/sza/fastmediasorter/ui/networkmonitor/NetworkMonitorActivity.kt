package com.sza.fastmediasorter.ui.networkmonitor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityNetworkMonitorBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.networkmonitor.helpers.NetworkMonitorSectionHost
import com.sza.fastmediasorter.ui.networkmonitor.helpers.NetworkMonitorSectionNavigator
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1433: host of the Network Monitor program.
 *
 * Availability is re-checked on entry rather than trusted from the caller, the way CalculatorActivity does:
 * a panel tile, a pinned shortcut or a launcher cell can outlive the setting that created it, and the entry
 * point that opened this screen is not the one that knows whether the feature is still on.
 *
 * No orientation/screenSize entry in the manifest `configChanges`, unlike the calculator: the Summary screen
 * ships a `layout-land` variant, and an Activity that absorbs the configuration change is never re-inflated,
 * so the landscape layout would exist and never be used.
 */
@AndroidEntryPoint
class NetworkMonitorActivity : BaseActivity<ActivityNetworkMonitorBinding>(), NetworkMonitorSectionHost {

    private val viewModel: NetworkMonitorViewModel by viewModels()

    private lateinit var navigator: NetworkMonitorSectionNavigator

    /**
     * The section the caller asked for, held until the settings gate answers.
     *
     * Null after a recreation: the FragmentManager restored the section the user was on, and navigating again
     * would throw them back to the launch section on every rotation.
     */
    private var pendingInitialSection: NetworkMonitorSection? = null

    override fun getViewBinding(): ActivityNetworkMonitorBinding =
        ActivityNetworkMonitorBinding.inflate(layoutInflater)

    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInitialSection = if (savedInstanceState == null) intent.readNetworkMonitorSection() else null
    }

    override fun setupViews() {
        navigator = NetworkMonitorSectionNavigator(supportFragmentManager, R.id.networkMonitorContainer)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { navigateUpOrFinish() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateUpOrFinish()
                }
            },
        )
        binding.btnNetworkMonitorOpenSettings.setOnClickListener { openSettings() }
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.availability) { availability ->
            renderAvailability(availability)
        }
    }

    override fun openSection(section: NetworkMonitorSection) {
        navigator.openSection(section)
    }

    override fun getInitialFocusView(): View? =
        if (binding.networkMonitorFallbackGroup.isVisible) {
            binding.btnNetworkMonitorOpenSettings
        } else {
            binding.networkMonitorContainer
        }

    private fun renderAvailability(availability: NetworkMonitorAvailability) {
        val enabled = availability == NetworkMonitorAvailability.AVAILABLE
        val supported = availability != NetworkMonitorAvailability.UNSUPPORTED_IN_BUILD
        binding.networkMonitorContainer.isVisible = enabled
        // Neither pane is shown while the answer is still UNKNOWN: the settings read returns within a frame,
        // and flashing the "switched off" fallback at someone who has it switched on reads as a fault.
        binding.networkMonitorFallbackGroup.isVisible =
            !enabled && availability != NetworkMonitorAvailability.UNKNOWN
        binding.networkMonitorFallbackMessage.setText(
            if (supported) {
                R.string.network_monitor_disabled_message
            } else {
                R.string.network_monitor_unsupported_message
            }
        )
        // The settings row does not exist in a build without the feature, so offering to open it would be a
        // dead end - the fallback then states the fact and stops there.
        binding.btnNetworkMonitorOpenSettings.isVisible = supported
        applySectionForCurrentAvailability(enabled)
    }

    /**
     * Sections are mounted only once the gate says yes, and torn down when it says no.
     *
     * A Fragment left in the container while the fallback covers it would keep collecting the snapshot and
     * keep the platform observers registered behind a screen the user cannot see.
     */
    private fun applySectionForCurrentAvailability(enabled: Boolean) {
        if (!enabled) {
            navigator.clear()
            return
        }
        pendingInitialSection?.let { section ->
            pendingInitialSection = null
            navigator.openSection(section)
        }
    }

    private fun navigateUpOrFinish() {
        if (navigator.canGoBack()) {
            navigator.returnToSummary()
            return
        }
        if (intent.hasNetworkMonitorLauncherOrigin()) {
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(homeIntent)
        }
        finish()
    }

    private fun openSettings() {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_INITIAL_TAB, SettingsActivity.TAB_OPERATIONS)
        )
    }

    companion object {

        /** Launch intent for the Monitor, optionally opening straight into [section]. */
        fun createIntent(
            context: Context,
            section: NetworkMonitorSection = NetworkMonitorSection.Summary,
        ): Intent = Intent(context, NetworkMonitorActivity::class.java)
            .putNetworkMonitorSection(section)
    }
}
