package com.sza.fastmediasorter.ui.calculator.helpers

import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.widget.TextViewCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityCalculatorBinding

/**
 * Applies the chosen keypad geometry to the grid already inflated from XML (strategic S2024 ADR-3).
 *
 * The single place that knows what each mode looks like. No mode has its own layout file: three modes
 * across two orientations would be six copies of the same 22-button grid, and every later edit would
 * have to land in all six.
 */
class CalculatorKeypadModeManager(private val binding: ActivityCalculatorBinding) {

    private val resources = binding.root.resources

    // Captured before the first mode is applied, so NORMAL restores what the XML declared rather than
    // a hardcoded guess - portrait fixes the row height while landscape weights it.
    private val originalChildParams: List<GridLayout.LayoutParams> =
        binding.calculatorGrid.children
            .map { GridLayout.LayoutParams(it.layoutParams as GridLayout.LayoutParams) }
            .toList()
    private val originalGridWidth = binding.calculatorGrid.layoutParams.width
    private val originalGridHeight = binding.calculatorGrid.layoutParams.height
    private val originalHistoryMaxLines = binding.calculatorHistory.maxLines

    /**
     * S2024: the display is autosized, so a plain setTextSize is discarded on the next measure pass -
     * the chosen size has to become the autosize maximum instead.
     */
    fun applyDisplayTextSize(sizeSp: Int) {
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            binding.calculatorDisplay,
            CalculatorSettings.MIN_TEXT_SIZE_SP,
            sizeSp.coerceAtLeast(CalculatorSettings.MIN_TEXT_SIZE_SP),
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
    }

    fun apply(mode: CalculatorKeypadMode) {
        clearInsetPadding()
        when (mode) {
            CalculatorKeypadMode.NORMAL -> applyNormal()
            CalculatorKeypadMode.LARGE -> applyLarge()
            CalculatorKeypadMode.COMPACT -> applyCompact()
        }
        binding.calculatorGrid.requestLayout()
    }

    private fun applyNormal() {
        setContainerGravity(Gravity.FILL)
        setGridSize(originalGridWidth, originalGridHeight)
        binding.calculatorHistory.maxLines = originalHistoryMaxLines
        binding.calculatorGrid.children.forEachIndexed { index, child ->
            child.layoutParams = GridLayout.LayoutParams(originalChildParams[index])
        }
    }

    private fun applyLarge() {
        setContainerGravity(Gravity.FILL)
        setGridSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        binding.calculatorHistory.maxLines = resources.getInteger(R.integer.calculator_history_lines_large)
        binding.calculatorGrid.children.forEachIndexed { index, child ->
            child.layoutParams = GridLayout.LayoutParams(originalChildParams[index]).apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, columnSpanOf(index), FILL, columnWeightOf(index))
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, FILL, 1f)
            }
        }
    }

    private fun applyCompact() {
        setContainerGravity(Gravity.BOTTOM or Gravity.END)
        setGridSize(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        binding.calculatorHistory.maxLines = originalHistoryMaxLines
        val buttonWidth = resources.getDimensionPixelSize(R.dimen.calculator_button_width_compact)
        val buttonHeight = resources.getDimensionPixelSize(R.dimen.calculator_button_height_compact)
        binding.calculatorGrid.children.forEachIndexed { index, child ->
            val span = columnSpanOf(index)
            child.layoutParams = GridLayout.LayoutParams(originalChildParams[index]).apply {
                width = buttonWidth * span
                height = buttonHeight
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, span, FILL, 0f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, FILL, 0f)
            }
        }
        applyInsetPadding()
    }

    // Rule 17: a keypad pinned to the corner must stop at the safe area, not at the glass, or the
    // bottom row lands under the gesture bar and the end column under a cutout.
    private fun applyInsetPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.calculatorGridContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(view.paddingLeft, view.paddingTop, insets.right, insets.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.calculatorGridContainer)
    }

    private fun clearInsetPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.calculatorGridContainer, null)
        binding.calculatorGridContainer.setPadding(0, 0, 0, 0)
    }

    private fun setContainerGravity(gravity: Int) {
        val params = binding.calculatorGrid.layoutParams as? FrameLayout.LayoutParams ?: return
        params.gravity = gravity
        binding.calculatorGrid.layoutParams = params
    }

    private fun setGridSize(width: Int, height: Int) {
        val params = binding.calculatorGrid.layoutParams
        params.width = width
        params.height = height
        binding.calculatorGrid.layoutParams = params
    }

    // GridLayout.Spec keeps its span package-private, so the one wide key is identified through the
    // binding rather than read back off the layout params. The equals key spans three columns; every
    // other key spans one.
    private fun columnSpanOf(index: Int): Int =
        if (binding.calculatorGrid.getChildAt(index) === binding.btnCalculatorEquals) EQUALS_SPAN else 1

    private fun columnWeightOf(index: Int): Float = columnSpanOf(index).toFloat()

    private companion object {
        val FILL: GridLayout.Alignment = GridLayout.FILL
        const val EQUALS_SPAN = 3
    }
}
