package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
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

    private val originalChildBackgroundTints: List<ColorStateList?> =
        binding.calculatorGrid.children
            .map { (it as? MaterialButton)?.backgroundTintList }
            .toList()

    // Portrait declares wrap_content on the container; LARGE needs 0dp + weight. Captured here so
    // applyNormal() restores whatever the XML declared rather than guessing a constant.
    private val originalContainerHeight: Int =
        (binding.calculatorGridContainer.layoutParams as? LinearLayout.LayoutParams)?.height
            ?: binding.calculatorGridContainer.layoutParams.height
    private val originalContainerWeight: Float =
        (binding.calculatorGridContainer.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f

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
        setContainerSize(originalContainerHeight, originalContainerWeight)
        setContainerGravity(Gravity.FILL)
        setGridSize(originalGridWidth, originalGridHeight)
        binding.calculatorHistory.maxLines = originalHistoryMaxLines
        binding.calculatorGrid.children.forEachIndexed { index, child ->
            (child as? MaterialButton)?.backgroundTintList = originalChildBackgroundTints[index]
            child.layoutParams = GridLayout.LayoutParams(originalChildParams[index])
        }
    }

    private fun applyLarge() {
        setContainerSize(0, LARGE_CONTAINER_WEIGHT)
        setContainerGravity(Gravity.FILL)
        setGridSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        binding.calculatorHistory.maxLines = resources.getInteger(R.integer.calculator_history_lines_large)
        applyLargeButtonBackgrounds()
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
        setContainerSize(ViewGroup.LayoutParams.WRAP_CONTENT, 0f)
        setContainerGravity(Gravity.BOTTOM or Gravity.END)
        setGridSize(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        binding.calculatorHistory.maxLines = originalHistoryMaxLines
        val buttonWidth = resources.getDimensionPixelSize(R.dimen.calculator_button_width_compact)
        val buttonHeight = resources.getDimensionPixelSize(R.dimen.calculator_button_height_compact)
        binding.calculatorGrid.children.forEachIndexed { index, child ->
            (child as? MaterialButton)?.backgroundTintList = originalChildBackgroundTints[index]
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

    /** Sets the container's height and weight inside its parent LinearLayout. */
    private fun setContainerSize(height: Int, weight: Float) {
        val params = binding.calculatorGridContainer.layoutParams as? LinearLayout.LayoutParams
            ?: return
        params.height = height
        params.weight = weight
        binding.calculatorGridContainer.layoutParams = params
    }

    /** Applies solid filled backgrounds to all buttons in LARGE mode so they are bold and visible. */
    private fun applyLargeButtonBackgrounds() {
        val operatorBg = ColorStateList.valueOf(
            resolveColor(com.google.android.material.R.attr.colorSecondaryContainer, R.color.teal_700)
        )
        val digitBg = ColorStateList.valueOf(
            resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh, R.color.item_alternate)
        )
        val auxBg = ColorStateList.valueOf(
            resolveColor(com.google.android.material.R.attr.colorSurfaceVariant, R.color.item_alternate)
        )

        binding.calculatorGrid.children.forEach { child ->
            val button = child as? MaterialButton ?: return@forEach
            when (button.id) {
                R.id.btnCalculatorClear, R.id.btnCalculatorClearEntry -> {
                    // Retain clear pink background tint
                }
                R.id.btnCalculatorEquals -> {
                    // Retain primary filled accent background
                }
                R.id.btnCalculatorDivide, R.id.btnCalculatorMultiply,
                R.id.btnCalculatorSubtract, R.id.btnCalculatorAdd,
                R.id.btnCalculatorPercent -> {
                    button.backgroundTintList = operatorBg
                }
                R.id.btnCalculatorSeven, R.id.btnCalculatorEight, R.id.btnCalculatorNine,
                R.id.btnCalculatorFour, R.id.btnCalculatorFive, R.id.btnCalculatorSix,
                R.id.btnCalculatorOne, R.id.btnCalculatorTwo, R.id.btnCalculatorThree,
                R.id.btnCalculatorZero -> {
                    button.backgroundTintList = digitBg
                }
                else -> {
                    button.backgroundTintList = auxBg
                }
            }
        }
    }

    private fun resolveColor(attrRes: Int, fallbackColorRes: Int): Int {
        val typedValue = TypedValue()
        val theme = binding.root.context.theme
        return if (theme.resolveAttribute(attrRes, typedValue, true)) {
            typedValue.data
        } else {
            ContextCompat.getColor(binding.root.context, fallbackColorRes)
        }
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

        /** Weight given to the grid container in LARGE mode so the keypad claims most of the screen. */
        const val LARGE_CONTAINER_WEIGHT = 3f
    }
}
