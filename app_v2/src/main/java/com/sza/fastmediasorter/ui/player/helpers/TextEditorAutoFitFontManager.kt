package com.sza.fastmediasorter.ui.player.helpers

import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.view.doOnLayout
import timber.log.Timber

/**
 * S0189: auto-fits the [EditText] font size so content fills (but does not overflow) the
 * parent [ScrollView] viewport.
 *
 * Runs whenever the user types or pastes. Respects floor [minSizeSp].
 * Once at floor and content still overflows, the [scrollView] handles scrolling.
 *
 * Manual swipe (font-size gesture) calls [notifyManualOverride] which locks auto-fit
 * until [reset] is called (e.g. on next enter-edit-mode).
 */
class TextEditorAutoFitFontManager(
    private val editText: EditText,
    private val scrollView: ScrollView,
    private val maxSizeSp: Float,
    private val minSizeSp: Float = 12f
) {
    private var autoFitEnabled: Boolean = true
    private var currentSizeSp: Float = maxSizeSp

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = recomputeAndApply()
    }

    /** Install text watcher. Call once on enter-edit-mode after initial content is set. */
    fun attach() {
        editText.addTextChangedListener(watcher)
    }

    /** Remove text watcher. Call on exit-edit-mode. */
    fun detach() {
        editText.removeTextChangedListener(watcher)
    }

    /** Current auto-fit or manual-override size used as the baseline for subsequent swipe gestures. */
    fun currentFontSizeSp(): Float = currentSizeSp

    /**
     * Called from gesture callbacks when the user manually adjusts font size via swipe.
     * Locks auto-fit until the next [reset].
     */
    fun notifyManualOverride(newSizeSp: Float) {
        autoFitEnabled = false
        currentSizeSp = newSizeSp
    }

    /** Re-enable auto-fit and snap back to [maxSizeSp]; triggers one recompute pass. */
    fun reset() {
        autoFitEnabled = true
        currentSizeSp = maxSizeSp
        recomputeAndApply()
    }

    private fun recomputeAndApply() {
        if (!autoFitEnabled) return

        if (editText.height == 0 || scrollView.height == 0) {
            // Not yet laid out — defer to after layout pass
            editText.doOnLayout { recomputeAndApply() }
            return
        }

        val availableHeight = scrollView.height

        // Step down from maxSizeSp until layout height fits or we hit floor
        var chosen = maxSizeSp
        while (chosen > minSizeSp) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, chosen)
            editText.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(editText.width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            val contentHeight = editText.measuredHeight
            if (contentHeight <= availableHeight) break
            chosen -= 1f
        }

        // Apply if changed
        if (chosen != currentSizeSp) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, chosen)
            currentSizeSp = chosen
        }
        Timber.d("S0189: TextEditorAutoFitFontManager.recomputeAndApply chose=${chosen}sp autoFit=$autoFitEnabled")
    }
}
