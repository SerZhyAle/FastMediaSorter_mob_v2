package com.sza.fastmediasorter.ui.settings.vr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.xr.MasterTogglePreferences
import com.sza.fastmediasorter.core.xr.StartVrPlaybackUseCase
import com.sza.fastmediasorter.core.xr.StartVrPlaybackRequest
import com.sza.fastmediasorter.core.xr.VrLaunchDeliveryMode
import com.sza.fastmediasorter.core.xr.VrLaunchInput
import com.sza.fastmediasorter.core.xr.VrLaunchMode
import com.sza.fastmediasorter.core.xr.VrLaunchPoint
import com.sza.fastmediasorter.core.xr.VrLaunchResult
import com.sza.fastmediasorter.core.xr.VrLaunchUnavailableReason
import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.core.xr.VrPlaybackActivityContract
import com.sza.fastmediasorter.core.xr.XrDetectionFacade
import com.sza.fastmediasorter.core.xr.XrDetectionState
import com.sza.fastmediasorter.core.xr.XrEntryGateway
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0249 Stage 1A: VR controls block fragment, hosted inside the Media settings screen.
 *
 * Responsibilities:
 * - Render advisory text + master toggle + Test Immersive button.
 * - Observe [XrDetectionFacade] state and toggle UI accordingly:
 *   - `NONE` (non-XR device): advisory visible, master toggle disabled, button hidden.
 *   - `AVAILABLE_DISABLED_BY_USER`: advisory hidden, toggle enabled, button hidden.
 *   - `AVAILABLE_ENABLED`: advisory hidden, toggle enabled, button visible.
 * - Route Test Immersive click through [StartVrPlaybackUseCase] and the shared
 *   [VrPlaybackActivityContract]. Native session lifecycle is owned by `XrEntryGatewayImpl` +
 *   `NativeDiagnosticXrRuntime`; this fragment only signals user intent and handles results.
 *
 * Replaces the S0245 `VrSettingsFragment` (which lived in a separate 5th VR tab). The
 * standalone tab was removed in Phase 04 as part of merging VR into the Media section.
 */
@AndroidEntryPoint
class VrSettingsBlockFragment : Fragment() {

    @Inject lateinit var preferences: MasterTogglePreferences
    @Inject lateinit var detection: XrDetectionFacade
    @Inject lateinit var startVrPlaybackUseCase: StartVrPlaybackUseCase
    @Inject lateinit var entryGateway: XrEntryGateway

    private lateinit var immersiveLauncher: ActivityResultLauncher<VrLaunchInput>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersiveLauncher = registerForActivityResult(
            VrPlaybackActivityContract(entryGateway),
            ::handleVrLaunchResult,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_vr_settings_block, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val advisory = view.findViewById<TextView>(R.id.xrUnavailableAdvisory)
        val masterRow = view.findViewById<SettingsToggleRow>(R.id.masterToggleRow)
        val testRow = view.findViewById<View>(R.id.testImmersiveRow)

        // Compact layout so the "Test Immersive" button sits next to the master toggle,
        // not at the right edge of the landscape screen.
        masterRow.setHugsTextContent(true)

        masterRow.setOnCheckedChangeListener { isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setEnabled(isChecked)
                Timber.d("VrSettingsBlockFragment: master toggle -> $isChecked")
            }
        }

        testRow.setOnClickListener { launchDiagnosticImmerse() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(detection.state(), preferences.enabled) { state, masterOn ->
                    state to masterOn
                }.onEach { (state, masterOn) ->
                    applyState(advisory, masterRow, testRow, state, masterOn)
                }.collect()
            }
        }
    }

    private fun applyState(
        advisory: View,
        masterRow: SettingsToggleRow,
        testRow: View,
        state: XrDetectionState,
        masterOn: Boolean,
    ) {
        val xrPresent = state != XrDetectionState.NONE
        advisory.visibility = if (xrPresent) View.GONE else View.VISIBLE
        masterRow.isEnabled = xrPresent
        if (masterRow.isChecked != masterOn) {
            masterRow.setCheckedSilently(masterOn)
        }
        val showButton = xrPresent && masterOn
        testRow.visibility = if (showButton) View.VISIBLE else View.GONE
        testRow.isClickable = showButton
        testRow.isFocusable = showButton
    }

    private fun launchDiagnosticImmerse() {
        Timber.d("VrSettingsBlockFragment: user tapped Test Immersive")
        viewLifecycleOwner.lifecycleScope.launch {
            val request = StartVrPlaybackRequest(
                launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST,
                mediaType = VrMediaType.IMAGE,
                source = VrLaunchPoint.SETTINGS_TEST,
                deliveryMode = VrLaunchDeliveryMode.ACTIVITY_RESULT,
            )
            when (val result = startVrPlaybackUseCase(request)) {
                is StartVrPlaybackUseCase.Result.Ready -> {
                    Timber.d("VrSettingsBlockFragment: launching immersive contract")
                    immersiveLauncher.launch(result.input)
                }
                is StartVrPlaybackUseCase.Result.Completed -> handleVrLaunchResult(result.result)
            }
        }
    }

    private fun handleVrLaunchResult(result: VrLaunchResult) {
        Timber.d("VrSettingsBlockFragment: immersive result=$result")
        when (result) {
            VrLaunchResult.CancelledByUser,
            VrLaunchResult.CompletedNormally -> Unit
            is VrLaunchResult.Crashed -> showToast(R.string.vr_settings_test_immersive_init_failure_toast)
            is VrLaunchResult.Unavailable -> {
                if (result.reason != VrLaunchUnavailableReason.DisabledByUser) {
                    showToast(R.string.vr_settings_test_immersive_init_failure_toast)
                }
            }
        }
    }

    private fun showToast(messageRes: Int) {
        val context = context ?: return
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }
}
