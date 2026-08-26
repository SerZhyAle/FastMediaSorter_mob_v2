package com.sza.fastmediasorter.ui.calculator

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogCalculatorSettingsBinding
import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorKeypadMode
import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorSettings
import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorSettingsStore

/**
 * The calculator's own settings window (strategic S2024 §2 goal 7).
 *
 * Reachable only from the calculator's popup menu, never from a settings screen, so it follows the
 * per-feature dialog pattern of `CameraSettingsDialogFragment` and registers in
 * `SettingsDocScopeCatalog` with an empty host key (ADR-2).
 */
class CalculatorSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogCalculatorSettingsBinding? = null
    private val binding: DialogCalculatorSettingsBinding
        get() = requireNotNull(_binding) { "Calculator settings binding is only valid while the dialog exists." }

    // Working copy edited by the controls; written to the store only when the user confirms.
    private var draft: CalculatorSettings = CalculatorSettings.DEFAULT

    interface Callbacks {
        fun onCalculatorSettingsChanged(settings: CalculatorSettings)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val store = CalculatorSettingsStore(requireContext())
        _binding = DialogCalculatorSettingsBinding.inflate(layoutInflater)
        draft = store.load()
        bindControls()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.calculator_settings_title)
            .setView(binding.root)
            .create()

        binding.btnCalculatorSettingsCancel.setOnClickListener { dialog.dismiss() }
        binding.btnCalculatorSettingsApply.setOnClickListener {
            val applied = draft.coerced()
            store.save(applied)
            // Re-queried rather than captured at show() time, so a framework-driven recreation still
            // reaches the live host (the pattern CameraSettingsDialogFragment documents).
            (activity as? Callbacks)?.onCalculatorSettingsChanged(applied)
            dialog.dismiss()
        }
        return dialog
    }

    private fun bindControls() {
        binding.switchCalculatorGroupThousands.isChecked = draft.groupThousands
        binding.switchCalculatorGroupThousands.setOnCheckedChangeListener { _, checked ->
            draft = draft.copy(groupThousands = checked)
        }

        binding.sliderCalculatorTextSize.value = draft.displayTextSizeSp.toFloat()
        binding.sliderCalculatorTextSize.addOnChangeListener { _, value, _ ->
            draft = draft.copy(displayTextSizeSp = value.toInt())
        }

        binding.radioCalculatorKeypadMode.check(checkedIdFor(draft.keypadMode))
        binding.radioCalculatorKeypadMode.setOnCheckedChangeListener { _, checkedId ->
            draft = draft.copy(keypadMode = modeFor(checkedId))
        }
    }

    private fun checkedIdFor(mode: CalculatorKeypadMode): Int = when (mode) {
        CalculatorKeypadMode.NORMAL -> R.id.radioKeypadModeNormal
        CalculatorKeypadMode.LARGE -> R.id.radioKeypadModeLarge
        CalculatorKeypadMode.COMPACT -> R.id.radioKeypadModeCompact
    }

    private fun modeFor(checkedId: Int): CalculatorKeypadMode = when (checkedId) {
        R.id.radioKeypadModeLarge -> CalculatorKeypadMode.LARGE
        R.id.radioKeypadModeCompact -> CalculatorKeypadMode.COMPACT
        else -> CalculatorKeypadMode.NORMAL
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CalculatorSettingsDialog"

        fun newInstance(): CalculatorSettingsDialogFragment = CalculatorSettingsDialogFragment()
    }
}
