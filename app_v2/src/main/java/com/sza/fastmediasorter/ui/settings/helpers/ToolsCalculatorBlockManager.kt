package com.sza.fastmediasorter.ui.settings.helpers

import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsToolsBinding
import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorKeypadMode
import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorSettingsStore

/**
 * S4622: the calculator group of the Tools page. Each control saves at once; the calculator re-reads
 * [CalculatorSettingsStore] when it resumes, which is what replaced the dialog's apply callback.
 */
class ToolsCalculatorBlockManager(
    private val binding: FragmentSettingsToolsBinding,
    private val store: CalculatorSettingsStore,
) {

    private val modes = listOf(CalculatorKeypadMode.NORMAL, CalculatorKeypadMode.LARGE, CalculatorKeypadMode.COMPACT)

    private val textSizeListener = Slider.OnChangeListener { _, value, fromUser ->
        if (fromUser) store.save(store.load().copy(displayTextSizeSp = value.toInt()).coerced())
    }

    /** Called from the page's onDestroyView, the edge that pairs with [bind]. */
    fun release() {
        binding.sliderCalculatorTextSize.removeOnChangeListener(textSizeListener)
    }

    fun bind() {
        val context = binding.root.context
        val current = store.load()
        binding.rowCalculatorGroupThousands.setCheckedSilently(current.groupThousands)
        binding.rowCalculatorGroupThousands.setOnCheckedChangeListener { checked ->
            store.save(store.load().copy(groupThousands = checked).coerced())
        }

        binding.sliderCalculatorTextSize.value = current.coerced().displayTextSizeSp.toFloat()
        binding.sliderCalculatorTextSize.addOnChangeListener(textSizeListener)

        binding.rowCalculatorKeypadMode.setEntries(
            listOf(
                context.getString(R.string.calculator_keypad_mode_normal),
                context.getString(R.string.calculator_keypad_mode_large),
                context.getString(R.string.calculator_keypad_mode_compact),
            ),
        )
        binding.rowCalculatorKeypadMode.setSelection(modes.indexOf(current.keypadMode).coerceAtLeast(0))
        binding.rowCalculatorKeypadMode.setOnItemSelectedListener { index ->
            val mode = modes.getOrNull(index) ?: return@setOnItemSelectedListener
            store.save(store.load().copy(keypadMode = mode).coerced())
        }
    }
}
