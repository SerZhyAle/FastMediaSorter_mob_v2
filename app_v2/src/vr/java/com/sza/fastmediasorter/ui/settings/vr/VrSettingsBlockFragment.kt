package com.sza.fastmediasorter.ui.settings.vr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.ui.settings.fragments.BaseSettingsFragment
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
class VrSettingsBlockFragment : BaseSettingsFragment() {

    @Inject lateinit var preferences: MasterTogglePreferences
    @Inject lateinit var detection: XrDetectionFacade
    @Inject lateinit var startVrPlaybackUseCase: StartVrPlaybackUseCase
    @Inject lateinit var entryGateway: XrEntryGateway
    @Inject lateinit var payloadHolder: com.sza.fastmediasorter.core.xr.VrLaunchPayloadHolder

    private val settingsViewModel: SettingsViewModel by activityViewModels()

    // S0326: spinner position ↔ value maps.
    private val layoutModes = listOf(StereoMode.MONO, StereoMode.SBS_FULL, StereoMode.OU)
    private val projectionModes = listOf(
        StereoMode.MONO,
        StereoMode.EQUIRECT_180_MONO,
        StereoMode.EQUIRECT_360_MONO,
        StereoMode.CYLINDER_180,
    )
    private val renderModes = listOf("CINEMA", "FULL_SBS", "FULL_OU")

    private lateinit var immersiveLauncher: ActivityResultLauncher<VrLaunchInput>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersiveLauncher = registerForActivityResult(
            VrPlaybackActivityContract(entryGateway, payloadHolder),
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
        val detailGroups = view.findViewById<View>(R.id.vrDetailGroupsContainer)

        val autoDetectRow = view.findViewById<SettingsToggleRow>(R.id.autoDetectRow)
        val trustFilenameRow = view.findViewById<SettingsToggleRow>(R.id.trustFilenameRow)
        val trustMetadataRow = view.findViewById<SettingsToggleRow>(R.id.trustMetadataRow)
        val trustAspectRatioRow = view.findViewById<SettingsToggleRow>(R.id.trustAspectRatioRow)
        val ambiguityRow = view.findViewById<SettingsToggleRow>(R.id.ambiguityBestGuessRow)
        val layoutSpinner = view.findViewById<AppCompatSpinner>(R.id.defaultLayoutSpinner)
        val projectionSpinner = view.findViewById<AppCompatSpinner>(R.id.defaultProjectionSpinner)
        val autoImmersiveRow = view.findViewById<SettingsToggleRow>(R.id.vrAutoImmersiveRow)
        val renderModeSpinner = view.findViewById<AppCompatSpinner>(R.id.renderModeSpinner)
        val showFpsRow = view.findViewById<SettingsToggleRow>(R.id.vrShowFpsRow)

        // Compact layout so the "Test Immersive" button sits next to the master toggle,
        // not at the right edge of the landscape screen.
        masterRow.setHugsTextContent(true)

        // S0326: unified 3D/VR switch. The VR master toggle is the single user control; toggling it
        // also writes the global 3D kill-switch (disable3dVr = !enabled) so the two never contradict.
        masterRow.setOnCheckedChangeListener { isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setEnabled(isChecked)
                writeSettings { it.copy(disable3dVr = !isChecked) }
                Timber.d("VrSettingsBlockFragment: 3D/VR enabled -> $isChecked")
            }
        }

        testRow.setOnClickListener { launchDiagnosticImmerse() }

        // S0326: bind autorecognition, default-mode, immersive and diagnostics controls.
        bindSwitch(autoDetectRow) { v -> writeSettings { it.copy(stereoAutoDetectEnabled = v) } }
        bindSwitch(trustFilenameRow) { v -> writeSettings { it.copy(stereoTrustFilename = v) } }
        bindSwitch(trustMetadataRow) { v -> writeSettings { it.copy(stereoTrustMetadata = v) } }
        bindSwitch(trustAspectRatioRow) { v -> writeSettings { it.copy(stereoTrustAspectRatio = v) } }
        bindSwitch(ambiguityRow) { v -> writeSettings { it.copy(stereoAmbiguityBestGuess = v) } }
        bindSwitch(autoImmersiveRow) { v -> writeSettings { it.copy(vrAutoImmersive = v) } }
        bindSwitch(showFpsRow) { v -> writeSettings { it.copy(vrShowFps = v) } }
        bindSpinner(layoutSpinner) { pos ->
            writeSettings { it.copy(stereoDefaultLayout = layoutModes.getOrElse(pos) { StereoMode.MONO }) }
        }
        bindSpinner(projectionSpinner) { pos ->
            writeSettings { it.copy(stereoDefaultProjection = projectionModes.getOrElse(pos) { StereoMode.MONO }) }
        }
        bindSpinner(renderModeSpinner) { pos ->
            writeSettings { it.copy(vrRenderingMode = renderModes.getOrElse(pos) { "CINEMA" }) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(detection.state(), preferences.enabled) { state, masterOn ->
                    state to masterOn
                }.onEach { (state, masterOn) ->
                    applyState(advisory, masterRow, testRow, detailGroups, state, masterOn)
                }.collect()
            }
        }

        // S0326: push current settings into the rows without re-triggering writes.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.settings.collect { s ->
                    withSettingsUpdate {
                        setSwitchChecked(autoDetectRow, s.stereoAutoDetectEnabled)
                        setSwitchChecked(trustFilenameRow, s.stereoTrustFilename)
                        setSwitchChecked(trustMetadataRow, s.stereoTrustMetadata)
                        setSwitchChecked(trustAspectRatioRow, s.stereoTrustAspectRatio)
                        setSwitchChecked(ambiguityRow, s.stereoAmbiguityBestGuess)
                        setSwitchChecked(autoImmersiveRow, s.vrAutoImmersive)
                        setSwitchChecked(showFpsRow, s.vrShowFps)
                        setSpinnerSelection(layoutSpinner, layoutModes.indexOf(s.stereoDefaultLayout).coerceAtLeast(0))
                        setSpinnerSelection(projectionSpinner, projectionModes.indexOf(s.stereoDefaultProjection).coerceAtLeast(0))
                        setSpinnerSelection(renderModeSpinner, renderModes.indexOf(s.vrRenderingMode).coerceAtLeast(0))
                    }
                }
            }
        }
    }

    private fun writeSettings(transform: (AppSettings) -> AppSettings) {
        settingsViewModel.updateSettings(transform(settingsViewModel.settings.value))
    }

    private fun applyState(
        advisory: View,
        masterRow: SettingsToggleRow,
        testRow: View,
        detailGroups: View,
        state: XrDetectionState,
        masterOn: Boolean,
    ) {
        val xrPresent = state != XrDetectionState.NONE
        advisory.visibility = if (xrPresent) View.GONE else View.VISIBLE
        masterRow.isEnabled = xrPresent
        if (masterRow.isChecked != masterOn) {
            masterRow.setCheckedSilently(masterOn)
        }
        // Detail groups + Test Immersive appear only when 3D/VR is available and enabled.
        // When disabled or on a non-XR device, the advisory + master toggle stay; the rest hides.
        val showDetails = xrPresent && masterOn
        detailGroups.visibility = if (showDetails) View.VISIBLE else View.GONE
        testRow.visibility = if (showDetails) View.VISIBLE else View.GONE
        testRow.isClickable = showDetails
        testRow.isFocusable = showDetails
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
