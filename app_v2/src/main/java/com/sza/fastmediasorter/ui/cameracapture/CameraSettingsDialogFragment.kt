package com.sza.fastmediasorter.ui.cameracapture

import android.app.Dialog
import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.os.Bundle
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogCameraSettingsBinding
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraSettingsDialogRotationManager
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraUseCaseFactory
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class CameraSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogCameraSettingsBinding? = null
    private val binding: DialogCameraSettingsBinding
        get() = requireNotNull(_binding) { "Camera settings binding is only valid while the dialog view exists." }

    private var exposureChangeListener: Slider.OnChangeListener? = null
    private var isoChangeListener: Slider.OnChangeListener? = null
    private var shutterChangeListener: Slider.OnChangeListener? = null

    // Mutable working copy shared by every control listener while the dialog view exists.
    // Holds the value identically to the previous local `var draft`; reset on each onCreateDialog.
    private lateinit var draft: CameraSettingsState

    interface Callbacks {
        fun onCameraSettingsPreviewChanged(state: CameraSettingsState)
        fun onCameraSettingsApplied(state: CameraSettingsState)
        fun onCameraSettingsCancelled(state: CameraSettingsState)

        // S1336: queried fresh from onAttach/onCreateDialog instead of injected once before show() -
        // a FragmentManager-driven restore (theme/language/font-size change, "don't keep activities",
        // process death) recreates this fragment through its no-arg constructor, so anything set only
        // by the original show() caller would be lost. Re-reading the host's live state also means a
        // restored dialog reflects the currently-applied settings, not a frozen first-open snapshot.
        fun currentCameraCapabilities(): CameraRuntimeCapabilities
        fun currentCameraSettingsState(): CameraSettingsState
        fun cameraRotationBucket(): StateFlow<Int>
    }

    /**
     * S1336: lets [onAttach] recover [callbacks] after a framework-driven recreation, when the
     * original show() caller is gone and cannot re-inject fields. Nullable, not a plain accessor -
     * `BaseActivity` defers `setupViews()` (where the real handler is built) via `binding.root.post {}`
     * for a fast first frame, so a restored fragment can attach before the handler exists; the
     * fragment awaits the flow instead of reading a not-yet-initialized field.
     */
    interface Host {
        val cameraSettingsCallbacksFlow: StateFlow<Callbacks?>
    }

    data class CameraSettingsState(
        val selfTimerSeconds: Int,
        val gridEnabled: Boolean,
        val aspectRatio: Int?,
        val resolution: Size?,
        val exposureCompensationIndex: Int,
        val whiteBalanceMode: Int,
        val manualSensorEnabled: Boolean,
        val manualIso: Int?,
        val manualShutterNs: Long?,
        val hdrEnabled: Boolean,
    )

    private lateinit var capabilities: CameraRuntimeCapabilities
    private lateinit var initialSettings: CameraSettingsState
    private lateinit var callbacks: Callbacks
    private lateinit var host: Host

    private val rotationManager by lazy { CameraSettingsDialogRotationManager(requireContext()) }
    private var rotationJob: Job? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // S1336: only the type-safe reference is captured here - reading through it happens later,
        // once the caller of onCreateDialog knows whether the host's callback handler exists yet.
        host = context as? Host
            ?: error("${context::class.simpleName} must implement CameraSettingsDialogFragment.Host")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCameraSettingsBinding.inflate(layoutInflater)
        // S0924: wrap content in the rotate-and-swap-measure container so it can turn with the device.
        val content = rotationManager.wrap(binding.root)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(content)
            .create()
            .also { rotationManager.attach(it) }

        val readyCallbacks = host.cameraSettingsCallbacksFlow.value
        if (readyCallbacks != null) {
            bindDialogContent(readyCallbacks)
        } else {
            // S1336: the handler is built inside setupViews(), which BaseActivity defers via
            // binding.root.post {} for a fast first frame - a framework-restored fragment (theme,
            // language, "don't keep activities", process death) attaches before that post{} runs, so
            // reading the host synchronously here would throw the same way the pre-fix lateinit
            // fields did. Wait for the flow instead; the rows populate one frame later than usual.
            lifecycleScope.launch {
                bindDialogContent(host.cameraSettingsCallbacksFlow.filterNotNull().first())
            }
        }
        return dialog
    }

    private fun bindDialogContent(resolvedCallbacks: Callbacks) {
        callbacks = resolvedCallbacks
        capabilities = callbacks.currentCameraCapabilities()
        initialSettings = callbacks.currentCameraSettingsState()
        draft = initialSettings.copy()

        bindCanonicalDropdowns()
        bindCanonicalToggles()
        setupExposureSlider()
        setupManualSensorControls()
        wireActionButtons()

        // S0924: subscribe to the device rotation bucket; symmetric deregistration in onDestroyView.
        // repeatOnLifecycle waits out STARTED itself, so this is safe whether bound synchronously
        // (onCreateDialog, before onStart) or from the async wait above (possibly after onStart).
        if (rotationJob != null) return
        rotationJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                callbacks.cameraRotationBucket().collect { bucket -> rotationManager.applyRotation(bucket) }
            }
        }
    }

    private fun preview() {
        callbacks.onCameraSettingsPreviewChanged(draft)
    }

    private fun bindCanonicalDropdowns() {
        val timerLabels = TIMER_OPTIONS.map { seconds ->
            if (seconds == 0) "0 s" else "$seconds s"
        }
        bindDropdown(
            row = binding.rowCameraTimer,
            labels = timerLabels,
            selectedIndex = TIMER_OPTIONS.indexOf(draft.selfTimerSeconds).coerceAtLeast(0),
        ) { index ->
            draft = draft.copy(selfTimerSeconds = TIMER_OPTIONS[index])
        }

        val aspectLabels = capabilities.availableAspectRatios.map(::aspectRatioLabel)
        binding.rowCameraAspect.isVisible = aspectLabels.isNotEmpty()
        if (aspectLabels.isNotEmpty()) {
            val selectedAspect = capabilities.availableAspectRatios.indexOf(draft.aspectRatio).coerceAtLeast(0)
            bindDropdown(binding.rowCameraAspect, aspectLabels, selectedAspect) { index ->
                draft = draft.copy(aspectRatio = capabilities.availableAspectRatios[index])
            }
        }

        // S1457: the photo pipeline pins the 4:3 sensor stream, and CameraUseCaseFactory silently
        // drops any resolution that does not match it - offering one offered a value the capture
        // would ignore, so the saved file came back at a size the user never chose.
        val resolutions = capabilities.photoResolutions.filter {
            CameraUseCaseFactory.resolutionMatchesAspect(it, CameraUseCaseFactory.PHOTO_ASPECT_RATIO)
        }
        val resolutionLabels = resolutions.map(::resolutionLabel)
        binding.rowCameraResolution.isVisible = resolutionLabels.isNotEmpty()
        if (resolutionLabels.isNotEmpty()) {
            // S1457: -1 when nothing is applied yet, which SettingsDropdownRow renders as no
            // selection. Coercing it to 0 showed the first entry as chosen while the session ran on
            // whatever CameraX picked for itself.
            val selectedResolution = resolutions.indexOf(draft.resolution)
            bindDropdown(binding.rowCameraResolution, resolutionLabels, selectedResolution) { index ->
                draft = draft.copy(resolution = resolutions[index])
            }
        }

        val awbModes = presentableWhiteBalanceModes(capabilities.awbModes)
        binding.rowCameraWhiteBalance.isVisible = awbModes.size > 1
        if (awbModes.size > 1) {
            // A draft holding a mode the dialog no longer offers - a session left on OFF before
            // S1574 - would show Auto in the row while confirming the dialog wrote OFF back into
            // the session. Coerce the draft itself, not just the displayed index.
            if (draft.whiteBalanceMode !in awbModes) {
                draft = draft.copy(whiteBalanceMode = CameraMetadata.CONTROL_AWB_MODE_AUTO)
            }
            bindDropdown(
                binding.rowCameraWhiteBalance,
                awbModes.map(::whiteBalanceLabel),
                awbModes.indexOf(draft.whiteBalanceMode).coerceAtLeast(0),
            ) { index ->
                draft = draft.copy(whiteBalanceMode = awbModes[index])
                preview()
            }
        }
    }

    private fun bindCanonicalToggles() {
        binding.rowCameraGrid.isChecked = draft.gridEnabled
        binding.rowCameraGrid.setOnCheckedChangeListener { checked ->
            draft = draft.copy(gridEnabled = checked)
        }

        binding.rowCameraHdr.isVisible = capabilities.supportsHdrExtension
        binding.rowCameraHdr.isChecked = draft.hdrEnabled
        binding.rowCameraHdr.setOnCheckedChangeListener { checked ->
            draft = draft.copy(hdrEnabled = checked)
            preview()
        }
    }

    private fun setupExposureSlider() {
        binding.rowCameraExposure.isVisible = capabilities.supportsExposureCompensation
        if (!capabilities.supportsExposureCompensation) return
        val maxIndex = capabilities.maxExposureCompensationIndex.toFloat()
        binding.sliderCameraExposure.valueFrom = -maxIndex
        binding.sliderCameraExposure.valueTo = maxIndex
        binding.sliderCameraExposure.stepSize = SLIDER_STEP
        binding.sliderCameraExposure.setSnappedValue(draft.exposureCompensationIndex.toFloat())
        binding.tvCameraExposureValue.text = signedValue(draft.exposureCompensationIndex)
        val listener = Slider.OnChangeListener { _, value, fromUser ->
            binding.tvCameraExposureValue.text = signedValue(value.toInt())
            if (fromUser) {
                draft = draft.copy(exposureCompensationIndex = value.toInt())
                preview()
            }
        }
        exposureChangeListener = listener
        binding.sliderCameraExposure.addOnChangeListener(listener)
    }

    private fun setupManualSensorControls() {
        binding.rowCameraManualSensor.isVisible = capabilities.supportsManualSensor
        binding.layoutCameraIsoControls.isVisible = draft.manualSensorEnabled
        binding.layoutCameraShutterControls.isVisible = draft.manualSensorEnabled
        if (!capabilities.supportsManualSensor) return
        val isoRange = capabilities.isoRange!!
        val shutterRange = capabilities.shutterRangeNs!!
        val defaultIso = draft.manualIso ?: ((isoRange.lower + isoRange.upper) / 2)
        val defaultShutter = draft.manualShutterNs ?: ((shutterRange.lower + shutterRange.upper) / 2)
        binding.rowCameraManualSensor.isChecked = draft.manualSensorEnabled
        binding.sliderCameraIso.valueFrom = isoRange.lower.toFloat()
        binding.sliderCameraIso.valueTo = isoRange.upper.toFloat()
        binding.sliderCameraIso.stepSize = SLIDER_STEP
        binding.sliderCameraIso.setSnappedValue(defaultIso.toFloat())
        binding.tvCameraIsoValue.text = defaultIso.toString()
        binding.sliderCameraShutter.valueFrom = SHUTTER_SLIDER_MIN
        binding.sliderCameraShutter.valueTo = SHUTTER_SLIDER_MAX
        binding.sliderCameraShutter.stepSize = SLIDER_STEP
        binding.sliderCameraShutter.setSnappedValue(
            shutterNsToSlider(defaultShutter, shutterRange.lower, shutterRange.upper),
        )
        binding.tvCameraShutterValue.text = shutterLabel(defaultShutter)
        binding.rowCameraManualSensor.setOnCheckedChangeListener { checked ->
            binding.layoutCameraIsoControls.isVisible = checked
            binding.layoutCameraShutterControls.isVisible = checked
            Timber.d("S1590: manual sensor toggled to $checked, iso/shutter rows now in scroll region")
            draft = draft.copy(
                manualSensorEnabled = checked,
                manualIso = binding.sliderCameraIso.value.toInt(),
                manualShutterNs = sliderToShutterNs(
                    binding.sliderCameraShutter.value,
                    shutterRange.lower,
                    shutterRange.upper,
                ),
            )
            preview()
        }
        val isoListener = Slider.OnChangeListener { _, value, fromUser ->
            binding.tvCameraIsoValue.text = value.toInt().toString()
            if (fromUser) {
                draft = draft.copy(manualIso = value.toInt())
                if (draft.manualSensorEnabled) preview()
            }
        }
        isoChangeListener = isoListener
        binding.sliderCameraIso.addOnChangeListener(isoListener)
        val shutterListener = Slider.OnChangeListener { _, value, fromUser ->
            val shutterNs = sliderToShutterNs(value, shutterRange.lower, shutterRange.upper)
            binding.tvCameraShutterValue.text = shutterLabel(shutterNs)
            if (fromUser) {
                draft = draft.copy(manualShutterNs = shutterNs)
                if (draft.manualSensorEnabled) preview()
            }
        }
        shutterChangeListener = shutterListener
        binding.sliderCameraShutter.addOnChangeListener(shutterListener)
    }

    private fun wireActionButtons() {
        binding.btnCameraSettingsCancel.setOnClickListener {
            callbacks.onCameraSettingsCancelled(initialSettings)
            dismissAllowingStateLoss()
        }
        binding.btnCameraSettingsApply.setOnClickListener {
            callbacks.onCameraSettingsApplied(draft)
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        // S0924: symmetric deregistration of the rotation-bucket subscription started in onStart().
        rotationJob?.cancel()
        rotationJob = null
        _binding?.let { dialogBinding ->
            exposureChangeListener?.let(dialogBinding.sliderCameraExposure::removeOnChangeListener)
            isoChangeListener?.let(dialogBinding.sliderCameraIso::removeOnChangeListener)
            shutterChangeListener?.let(dialogBinding.sliderCameraShutter::removeOnChangeListener)
        }
        exposureChangeListener = null
        isoChangeListener = null
        shutterChangeListener = null
        _binding = null
        super.onDestroyView()
    }

    private fun bindDropdown(
        row: SettingsDropdownRow,
        labels: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit,
    ) {
        // Entries must be set before the silent selection so the row can render the chosen label.
        row.setEntries(labels)
        row.setSelection(selectedIndex)
        row.setOnItemSelectedListener(onSelected)
    }

    private fun aspectRatioLabel(value: Int): String = when (value) {
        AspectRatio.RATIO_16_9 -> "16:9"
        else -> "4:3"
    }

    private fun resolutionLabel(size: Size): String = "${size.width} x ${size.height}"

    private fun signedValue(value: Int): String = if (value > 0) "+$value" else value.toString()

    private fun whiteBalanceLabel(mode: Int): String = getString(WHITE_BALANCE_LABELS.getValue(mode))

    /**
     * S1583: assign a slider value that Material will accept. Call after `valueFrom`, `valueTo` and
     * `stepSize` are set - the snap is computed from the track as it stands.
     */
    private fun Slider.setSnappedValue(rawValue: Float) {
        value = snapToSliderStep(rawValue, valueFrom, valueTo, stepSize)
    }

    private fun sliderToShutterNs(value: Float, minNs: Long, maxNs: Long): Long {
        val fraction = (value / SHUTTER_SLIDER_MAX).coerceIn(0f, 1f)
        val minLog = ln(minNs.toDouble())
        val maxLog = ln(maxNs.toDouble())
        return exp(minLog + ((maxLog - minLog) * fraction)).roundToLong().coerceIn(minNs, maxNs)
    }

    private fun shutterNsToSlider(value: Long, minNs: Long, maxNs: Long): Float {
        val minLog = ln(minNs.toDouble())
        val maxLog = ln(maxNs.toDouble())
        val valueLog = ln(value.toDouble())
        val fraction = (valueLog - minLog) / (maxLog - minLog)
        return (fraction * SHUTTER_SLIDER_MAX).toFloat().coerceIn(SHUTTER_SLIDER_MIN, SHUTTER_SLIDER_MAX)
    }

    private fun shutterLabel(valueNs: Long): String {
        val millis = valueNs / NANOS_PER_MILLI
        val seconds = valueNs / NANOS_PER_SECOND
        return when {
            valueNs >= ONE_SECOND_NANOS -> String.format(Locale.US, "%.1f s", seconds)
            millis >= 1.0 -> String.format(Locale.US, "%.0f ms", millis)
            else -> {
                val denominator = (NANOS_PER_SECOND / valueNs.toDouble()).roundToLong().coerceAtLeast(1)
                "1/$denominator s"
            }
        }
    }

    companion object {
        const val TAG = "CameraSettingsDialog"
        private val TIMER_OPTIONS = listOf(0, 3, 5, 10)

        /**
         * S1574: the white-balance modes this dialog offers, in display order (automatic first).
         * One map serves as both the filter and the label source, so a mode can no longer reach the
         * list without a label of its own - the previous `else -> "Auto"` fallback made
         * CONTROL_AWB_MODE_OFF, which most devices report, appear as a second "Auto" row that did
         * the opposite thing.
         *
         * CONTROL_AWB_MODE_OFF is deliberately absent rather than relabelled: Camera2 hands white
         * balance to the app in that mode, expecting COLOR_CORRECTION_TRANSFORM and
         * COLOR_CORRECTION_GAINS, and this app sets neither - so the mode has no defined result to
         * offer a user.
         */
        private val WHITE_BALANCE_LABELS: Map<Int, Int> = linkedMapOf(
            CameraMetadata.CONTROL_AWB_MODE_AUTO to R.string.camera_setting_wb_auto,
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT to R.string.camera_setting_wb_incandescent,
            CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT to R.string.camera_setting_wb_fluorescent,
            CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT to R.string.camera_setting_wb_warm_fluorescent,
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT to R.string.camera_setting_wb_daylight,
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to R.string.camera_setting_wb_cloudy,
            CameraMetadata.CONTROL_AWB_MODE_TWILIGHT to R.string.camera_setting_wb_twilight,
            CameraMetadata.CONTROL_AWB_MODE_SHADE to R.string.camera_setting_wb_shade,
        )

        /**
         * Intersect what the camera reports with what this dialog can label and honour. Display
         * order comes from [WHITE_BALANCE_LABELS], so automatic stays first without a separate sort.
         */
        internal fun presentableWhiteBalanceModes(reportedModes: List<Int>): List<Int> {
            val reported = reportedModes.toSet()
            return WHITE_BALANCE_LABELS.keys.filter { it in reported }
        }

        /**
         * S1583: Material's slider rejects any value that is not `valueFrom` plus a whole multiple of
         * `stepSize`, and it validates lazily - on the first measure pass, which for a row that starts
         * GONE happens long after the assignment, so the crash surfaced as an unrelated layout failure.
         * The shutter position is log-mapped and lands on fractions (91.168594 on the reporting device);
         * exposure and ISO come from a saved draft that a lens switch can push outside the live range.
         * Both are the same defect - a value assigned without being fitted to the track it is going on.
         */
        internal fun snapToSliderStep(rawValue: Float, from: Float, to: Float, step: Float): Float {
            if (step <= 0f) return rawValue.coerceIn(from, to)
            val lastStep = ((to - from) / step).toInt()
            val stepIndex = ((rawValue - from) / step).roundToInt().coerceIn(0, lastStep)
            return from + (stepIndex * step)
        }

        // Material Slider config: 1-unit steps for all sliders; the shutter slider maps log-scaled
        // nanoseconds onto a fixed 0..100 track (see shutterNsToSlider/sliderToShutterNs).
        private const val SLIDER_STEP = 1f
        private const val SHUTTER_SLIDER_MIN = 0f
        private const val SHUTTER_SLIDER_MAX = 100f

        // Shutter-duration formatting thresholds and divisors.
        private const val NANOS_PER_MILLI = 1_000_000.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}
