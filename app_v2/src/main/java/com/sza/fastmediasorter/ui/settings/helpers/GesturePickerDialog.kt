package com.sza.fastmediasorter.ui.settings.helpers

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.databinding.DialogListSelectionBinding
import com.sza.fastmediasorter.util.bindTo
import kotlinx.coroutines.launch

/**
 * S1038: grouped variant of [com.sza.fastmediasorter.ui.dialog.ListSelectionDialog] for the gesture
 * action picker. Reuses the shared dialog chrome (title + capped scrolling list + Cancel) but renders
 * sectioned [GesturePickerRow]s via [GesturePickerAdapter] instead of a flat formatter list. Rows are
 * bound on the owner's [lifecycleScope] so the list is never populated after the owner is destroyed,
 * mirroring the loader lifecycle of the flat dialog it replaces.
 *
 * S2256: one dialog for both assignment surfaces. The host supplies the rows and its own action type,
 * so the edge-gesture slots and the launcher desktop swipes differ only in which actions they offer -
 * never in grouping, order, icons or wording.
 */
class GesturePickerDialog<T : Any>(
    context: Context,
    private val title: CharSequence,
    private val lifecycleOwner: LifecycleOwner,
    private val rows: List<GesturePickerRow<T>>,
    private val selectedKey: T?,
    private val onPicked: (T) -> Unit,
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindTo(lifecycleOwner)
        val binding = DialogListSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val displayMetrics = context.resources.displayMetrics
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthFraction = if (isLandscape) {
            DIALOG_WIDTH_FRACTION_LANDSCAPE
        } else {
            DIALOG_WIDTH_FRACTION_PORTRAIT
        }
        val maxHeightFraction = if (isLandscape) {
            DIALOG_MAX_HEIGHT_FRACTION_LANDSCAPE
        } else {
            DIALOG_MAX_HEIGHT_FRACTION_PORTRAIT
        }

        val width = (displayMetrics.widthPixels * widthFraction).toInt()
        val maxHeight = (displayMetrics.heightPixels * maxHeightFraction).toInt()

        // S1038 / UX fix: allow recycler to occupy available screen space up to max height
        val paddingPx = (RECYCLER_PADDING_VERTICAL_DP * displayMetrics.density).toInt()
        val recyclerMaxHeight = (maxHeight - paddingPx).coerceAtLeast(MIN_RECYCLER_HEIGHT_PX)
        (binding.listSelectionRecycler.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
            ?.let { params ->
                params.matchConstraintMaxHeight = recyclerMaxHeight
                binding.listSelectionRecycler.layoutParams = params
            }

        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER)

        binding.tvTitle.text = title
        binding.btnClear.visibility = View.GONE
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.listSelectionRecycler.layoutManager = LinearLayoutManager(context)

        lifecycleOwner.lifecycleScope.launch {
            binding.listSelectionRecycler.adapter = GesturePickerAdapter(
                rows = rows,
                selectedKey = selectedKey,
                onClick = { actionKey ->
                    onPicked(actionKey)
                    dismiss()
                },
            )
            val selectedIndex = rows.indexOfFirst { it is GesturePickerRow.Entry && it.actionKey == selectedKey }
            if (selectedIndex >= 0) {
                binding.listSelectionRecycler.scrollToPosition(selectedIndex)
            }
        }
    }

    private companion object {
        const val DIALOG_WIDTH_FRACTION_PORTRAIT = 0.90
        const val DIALOG_WIDTH_FRACTION_LANDSCAPE = 0.75
        const val DIALOG_MAX_HEIGHT_FRACTION_PORTRAIT = 0.85
        const val DIALOG_MAX_HEIGHT_FRACTION_LANDSCAPE = 0.85
        const val RECYCLER_PADDING_VERTICAL_DP = 100
        const val MIN_RECYCLER_HEIGHT_PX = 150
    }
}
