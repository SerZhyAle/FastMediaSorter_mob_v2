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

        val width = (context.resources.displayMetrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt()
        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        raisePortraitDialog()

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
        }
    }

    private fun raisePortraitDialog() {
        if (context.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return
        window?.apply {
            setGravity(Gravity.CENTER)
            attributes = attributes.apply {
                y = -(PORTRAIT_VERTICAL_OFFSET_DP * context.resources.displayMetrics.density).toInt()
            }
        }
    }

    private companion object {
        const val DIALOG_WIDTH_FRACTION = 0.85
        const val PORTRAIT_VERTICAL_OFFSET_DP = 96
    }
}
