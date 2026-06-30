package com.sza.fastmediasorter.ui.cameracapture

import android.app.Dialog
import android.content.res.Configuration
import android.hardware.camera2.CameraMetadata
import android.os.Bundle
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogCameraSettingsBinding
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.common.widget.SettingsDropdownRow
import timber.log.Timber
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
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

    lateinit var capabilities: CameraRuntimeCapabilities
    lateinit var initialSettings: CameraSettingsState
    lateinit var callbacks: Callbacks

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCameraSettingsBinding.inflate(layoutInflater)
        val orientation = resources.configuration.orientation
        val orientationLabel = if (orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        Timber.d("S0813: camera settings dialog inflated orientation=$orientationLabel")
        draft = initialSettings.copy()

        bindCanonicalDropdowns()
        bindCanonicalToggles()
        setupExposureSlider()
        setupManualSensorControls()
        wireActionButtons()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
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

        val resolutionLabels = capabilities.photoResolutions.map(::resolutionLabel)
        binding.rowCameraResolution.isVisible = resolutionLabels.isNotEmpty()
        if (resolutionLabels.isNotEmpty()) {
            val selectedResolution = capabilities.photoResolutions.indexOf(draft.resolution).coerceAtLeast(0)
            bindDropdown(binding.rowCameraResolution, resolutionLabels, selectedResolution) { index ->
                draft = draft.copy(resolution = capabilities.photoResolutions[index])
            }
        }

        val awbModes = capabilities.awbModes.distinct()
            .sortedBy { if (it == CameraMetadata.CONTROL_AWB_MODE_AUTO) 0 else 1 }
        binding.rowCameraWhiteBalance.isVisible = awbModes.size > 1
        if (awbModes.size > 1) {
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
        binding.sliderCameraExposure.value = draft.exposureCompensationIndex.toFloat()
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
        binding.sliderCameraIso.value = defaultIso.toFloat()
        binding.tvCameraIsoValue.text = defaultIso.toString()
        binding.sliderCameraShutter.valueFrom = SHUTTER_SLIDER_MIN
        binding.sliderCameraShutter.valueTo = SHUTTER_SLIDER_MAX
        binding.sliderCameraShutter.stepSize = SLIDER_STEP
        binding.sliderCameraShutter.value = shutterNsToSlider(defaultShutter, shutterRange.lower, shutterRange.upper)
        binding.tvCameraShutterValue.text = shutterLabel(defaultShutter)
        binding.rowCameraManualSensor.setOnCheckedChangeListener { checked ->
            binding.layoutCameraIsoControls.isVisible = checked
            binding.layoutCameraShutterControls.isVisible = checked
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

    private fun whiteBalanceLabel(mode: Int): String = when (mode) {
        CameraMetadata.CONTROL_AWB_MODE_AUTO -> getString(R.string.camera_setting_wb_auto)
        CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> getString(R.string.camera_setting_wb_incandescent)
        CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> getString(R.string.camera_setting_wb_fluorescent)
        CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT -> getString(R.string.camera_setting_wb_warm_fluorescent)
        CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> getString(R.string.camera_setting_wb_daylight)
        CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> getString(R.string.camera_setting_wb_cloudy)
        CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> getString(R.string.camera_setting_wb_twilight)
        CameraMetadata.CONTROL_AWB_MODE_SHADE -> getString(R.string.camera_setting_wb_shade)
        else -> getString(R.string.camera_setting_wb_auto)
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
