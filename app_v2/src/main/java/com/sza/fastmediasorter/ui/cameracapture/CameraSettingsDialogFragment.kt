package com.sza.fastmediasorter.ui.cameracapture

import android.app.Dialog
import android.hardware.camera2.CameraMetadata
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.camera.core.AspectRatio
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogCameraSettingsBinding
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
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
        var draft = initialSettings.copy()

        fun applyPreview() {
            callbacks.onCameraSettingsPreviewChanged(draft)
        }

        val timerLabels = TIMER_OPTIONS.map { seconds ->
            if (seconds == 0) "0 s" else "$seconds s"
        }
        bindSpinner(
            view = binding.spinnerCameraTimer,
            labels = timerLabels,
            selectedIndex = TIMER_OPTIONS.indexOf(draft.selfTimerSeconds).coerceAtLeast(0),
        ) { index ->
            draft = draft.copy(selfTimerSeconds = TIMER_OPTIONS[index])
        }

        binding.switchCameraGrid.isChecked = draft.gridEnabled
        binding.switchCameraGrid.setOnCheckedChangeListener { _, checked ->
            draft = draft.copy(gridEnabled = checked)
        }

        val aspectLabels = capabilities.availableAspectRatios.map(::aspectRatioLabel)
        binding.rowCameraAspect.isVisible = aspectLabels.isNotEmpty()
        if (aspectLabels.isNotEmpty()) {
            val selectedAspect = capabilities.availableAspectRatios.indexOf(draft.aspectRatio).coerceAtLeast(0)
            bindSpinner(binding.spinnerCameraAspect, aspectLabels, selectedAspect) { index ->
                draft = draft.copy(aspectRatio = capabilities.availableAspectRatios[index])
            }
        }

        val resolutionLabels = capabilities.photoResolutions.map(::resolutionLabel)
        binding.rowCameraResolution.isVisible = resolutionLabels.isNotEmpty()
        if (resolutionLabels.isNotEmpty()) {
            val selectedResolution = capabilities.photoResolutions.indexOf(draft.resolution).coerceAtLeast(0)
            bindSpinner(binding.spinnerCameraResolution, resolutionLabels, selectedResolution) { index ->
                draft = draft.copy(resolution = capabilities.photoResolutions[index])
            }
        }

        binding.rowCameraExposure.isVisible = capabilities.supportsExposureCompensation
        if (capabilities.supportsExposureCompensation) {
            val maxIndex = capabilities.maxExposureCompensationIndex.toFloat()
            binding.sliderCameraExposure.valueFrom = -maxIndex
            binding.sliderCameraExposure.valueTo = maxIndex
            binding.sliderCameraExposure.stepSize = 1f
            binding.sliderCameraExposure.value = draft.exposureCompensationIndex.toFloat()
            binding.tvCameraExposureValue.text = signedValue(draft.exposureCompensationIndex)
            val listener = Slider.OnChangeListener { _, value, fromUser ->
                binding.tvCameraExposureValue.text = signedValue(value.toInt())
                if (fromUser) {
                    draft = draft.copy(exposureCompensationIndex = value.toInt())
                    applyPreview()
                }
            }
            exposureChangeListener = listener
            binding.sliderCameraExposure.addOnChangeListener(listener)
        }

        val awbModes = capabilities.awbModes.distinct().sortedBy { if (it == CameraMetadata.CONTROL_AWB_MODE_AUTO) 0 else 1 }
        binding.rowCameraWhiteBalance.isVisible = awbModes.size > 1
        if (awbModes.size > 1) {
            bindSpinner(
                binding.spinnerCameraWhiteBalance,
                awbModes.map(::whiteBalanceLabel),
                awbModes.indexOf(draft.whiteBalanceMode).coerceAtLeast(0),
            ) { index ->
                draft = draft.copy(whiteBalanceMode = awbModes[index])
                applyPreview()
            }
        }

        binding.rowCameraManualSensor.isVisible = capabilities.supportsManualSensor
        binding.layoutCameraIsoControls.isVisible = draft.manualSensorEnabled
        binding.layoutCameraShutterControls.isVisible = draft.manualSensorEnabled
        if (capabilities.supportsManualSensor) {
            val isoRange = capabilities.isoRange!!
            val shutterRange = capabilities.shutterRangeNs!!
            val defaultIso = draft.manualIso ?: ((isoRange.lower + isoRange.upper) / 2)
            val defaultShutter = draft.manualShutterNs ?: ((shutterRange.lower + shutterRange.upper) / 2)
            binding.switchCameraManualSensor.isChecked = draft.manualSensorEnabled
            binding.sliderCameraIso.valueFrom = isoRange.lower.toFloat()
            binding.sliderCameraIso.valueTo = isoRange.upper.toFloat()
            binding.sliderCameraIso.stepSize = 1f
            binding.sliderCameraIso.value = defaultIso.toFloat()
            binding.tvCameraIsoValue.text = defaultIso.toString()
            binding.sliderCameraShutter.valueFrom = 0f
            binding.sliderCameraShutter.valueTo = 100f
            binding.sliderCameraShutter.stepSize = 1f
            binding.sliderCameraShutter.value = shutterNsToSlider(defaultShutter, shutterRange.lower, shutterRange.upper)
            binding.tvCameraShutterValue.text = shutterLabel(defaultShutter)
            binding.switchCameraManualSensor.setOnCheckedChangeListener { _, checked ->
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
                applyPreview()
            }
            val isoListener = Slider.OnChangeListener { _, value, fromUser ->
                binding.tvCameraIsoValue.text = value.toInt().toString()
                if (fromUser) {
                    draft = draft.copy(manualIso = value.toInt())
                    if (draft.manualSensorEnabled) applyPreview()
                }
            }
            isoChangeListener = isoListener
            binding.sliderCameraIso.addOnChangeListener(isoListener)
            val shutterListener = Slider.OnChangeListener { _, value, fromUser ->
                val shutterNs = sliderToShutterNs(value, shutterRange.lower, shutterRange.upper)
                binding.tvCameraShutterValue.text = shutterLabel(shutterNs)
                if (fromUser) {
                    draft = draft.copy(manualShutterNs = shutterNs)
                    if (draft.manualSensorEnabled) applyPreview()
                }
            }
            shutterChangeListener = shutterListener
            binding.sliderCameraShutter.addOnChangeListener(shutterListener)
        }

        binding.rowCameraHdr.isVisible = capabilities.supportsHdrExtension
        binding.switchCameraHdr.isChecked = draft.hdrEnabled
        binding.switchCameraHdr.setOnCheckedChangeListener { _, checked ->
            draft = draft.copy(hdrEnabled = checked)
            applyPreview()
        }

        binding.btnCameraSettingsCancel.setOnClickListener {
            callbacks.onCameraSettingsCancelled(initialSettings)
            dismissAllowingStateLoss()
        }
        binding.btnCameraSettingsApply.setOnClickListener {
            callbacks.onCameraSettingsApplied(draft)
            dismissAllowingStateLoss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
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

    private fun bindSpinner(
        view: android.widget.Spinner,
        labels: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit,
    ) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        view.adapter = adapter
        view.setSelection(selectedIndex, false)
        view.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, selectedView: View?, position: Int, id: Long) {
                onSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
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
        val fraction = (value / 100f).coerceIn(0f, 1f)
        val minLog = ln(minNs.toDouble())
        val maxLog = ln(maxNs.toDouble())
        return exp(minLog + ((maxLog - minLog) * fraction)).roundToLong().coerceIn(minNs, maxNs)
    }

    private fun shutterNsToSlider(value: Long, minNs: Long, maxNs: Long): Float {
        val minLog = ln(minNs.toDouble())
        val maxLog = ln(maxNs.toDouble())
        val valueLog = ln(value.toDouble())
        return (((valueLog - minLog) / (maxLog - minLog)) * 100.0).toFloat().coerceIn(0f, 100f)
    }

    private fun shutterLabel(valueNs: Long): String {
        val millis = valueNs / 1_000_000.0
        val seconds = valueNs / 1_000_000_000.0
        return when {
            valueNs >= 1_000_000_000L -> String.format("%.1f s", seconds)
            millis >= 1.0 -> String.format("%.0f ms", millis)
            else -> {
                val denominator = (1_000_000_000.0 / valueNs.toDouble()).roundToLong().coerceAtLeast(1)
                "1/$denominator s"
            }
        }
    }

    companion object {
        const val TAG = "CameraSettingsDialog"
        private val TIMER_OPTIONS = listOf(0, 3, 5, 10)
    }
}
