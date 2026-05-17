package com.sza.fastmediasorter.ui.keybinding

import android.os.Bundle
import android.os.CountDownTimer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogCaptureKeybindingBinding
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.input.fromGamepadButton
import com.sza.fastmediasorter.domain.input.fromKeyEvent
import com.sza.fastmediasorter.ui.keybinding.helpers.KeybindingRowLabelFormatter
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class CaptureDialogFragment : DialogFragment() {

    @Inject lateinit var formatter: KeybindingRowLabelFormatter

    private var _binding: DialogCaptureKeybindingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: KeybindingRemapViewModel by activityViewModels()

    private var capturedTrigger: InputTrigger? = null
    private var countDownTimer: CountDownTimer? = null

    companion object {
        const val RESULT_KEY = "capture_result"
        const val KEY_TRIGGER = "trigger_serialized"
        private const val CAPTURE_TIMEOUT_MS = 30_000L
        private const val ANALOG_THRESHOLD = 0.7f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, bundle: Bundle?): View {
        _binding = DialogCaptureKeybindingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvCaptured.text = getString(R.string.keybinding_row_no_binding)
        binding.btnCaptureCommit.isEnabled = false

        binding.btnCaptureClear.setOnClickListener {
            viewModel.onCaptureCancelled()
            dismiss()
        }

        binding.btnCaptureCommit.setOnClickListener {
            val trigger = capturedTrigger ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putString(KEY_TRIGGER, trigger.serialize()) }
            )
            dismiss()
        }

        setupInputListeners()
        startCountdown()
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        _binding = null
        super.onDestroyView()
    }

    // ----- input capture -----

    private fun setupInputListeners() {
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()

        // Keyboard + gamepad button capture (both arrive via KeyEvent)
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_BACK) return@setOnKeyListener false

            val trigger: InputTrigger = if (isGamepadSource(event.source)) {
                InputTrigger.fromGamepadButton(keyCode)
            } else {
                InputTrigger.fromKeyEvent(event)
            }
            onTriggerCaptured(trigger)
            true
        }

        // Gamepad axis + mouse button capture (arrive via MotionEvent)
        binding.root.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_BUTTON_PRESS) {
                onTriggerCaptured(InputTrigger.MouseButton(event.actionButton))
                return@setOnGenericMotionListener true
            }
            val source = event.source
            if (source and InputDevice.SOURCE_JOYSTICK != 0 ||
                source and InputDevice.SOURCE_GAMEPAD != 0
            ) {
                captureAxisFrom(event)
                return@setOnGenericMotionListener true
            }
            false
        }
    }

    private fun captureAxisFrom(event: MotionEvent) {
        val axes = intArrayOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER
        )
        for (axis in axes) {
            val value = event.getAxisValue(axis)
            if (kotlin.math.abs(value) >= ANALOG_THRESHOLD) {
                val direction = if (value > 0) 1 else -1
                onTriggerCaptured(InputTrigger.GamepadAxis(axis, direction, ANALOG_THRESHOLD))
                return
            }
        }
    }

    private fun onTriggerCaptured(trigger: InputTrigger) {
        capturedTrigger = trigger
        binding.tvCaptured.text = getString(R.string.keybinding_capture_current, formatter.format(trigger))

        // Conflict check — block policy: disable commit if trigger already used by another command.
        val pendingCapture = viewModel.state.value.pendingCapture
        val conflictMap = viewModel.state.value.conflicts
        val conflictingCommands = conflictMap[trigger]
            ?.filter { it != pendingCapture?.commandId }
            ?: emptyList()

        if (conflictingCommands.isNotEmpty()) {
            val conflictLabel = conflictingCommands.joinToString(", ") { formatter.resolveCommandLabel(it) }
            binding.tvConflict.text = getString(R.string.keybinding_capture_conflict, conflictLabel)
            binding.tvConflict.visibility = android.view.View.VISIBLE
            binding.btnCaptureCommit.isEnabled = false
        } else {
            binding.tvConflict.visibility = android.view.View.GONE
            binding.btnCaptureCommit.isEnabled = true
        }

        Timber.d("CaptureDialog: captured trigger=%s conflict=%s", trigger.serialize(), conflictingCommands)
    }

    private fun isGamepadSource(source: Int): Boolean =
        source and InputDevice.SOURCE_GAMEPAD != 0

    // ----- countdown -----

    private fun startCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(CAPTURE_TIMEOUT_MS, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1_000L).toInt()
                _binding?.tvCountdown?.text = getString(R.string.keybinding_capture_countdown, secs)
            }
            override fun onFinish() {
                _binding?.tvCountdown?.text = getString(R.string.keybinding_capture_countdown, 0)
                viewModel.onCaptureCancelled()
                dismiss()
            }
        }.start()
    }
}
