package com.sza.fastmediasorter.ui.settings.helpers

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.databinding.DialogListSelectionBinding
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import kotlinx.coroutines.launch

/**
 * S1038: grouped variant of [com.sza.fastmediasorter.ui.dialog.ListSelectionDialog] for the gesture
 * action picker. Reuses the shared dialog chrome (title + capped scrolling list + Cancel) but renders
 * sectioned [GesturePickerRow]s via [GesturePickerAdapter] instead of a flat formatter list. Rows are
 * bound on the owner's [lifecycleScope] so the list is never populated after the owner is destroyed,
 * mirroring the loader lifecycle of the flat dialog it replaces.
 */
class GesturePickerDialog(
    context: Context,
    private val title: CharSequence,
    private val lifecycleOwner: LifecycleOwner,
    private val rows: List<GesturePickerRow>,
    private val selectedAction: ScreenshotGestureAction,
    private val onPicked: (ScreenshotGestureAction) -> Unit,
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DialogListSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val width = (context.resources.displayMetrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt()
        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        binding.tvTitle.text = title
        binding.btnClear.visibility = View.GONE
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.listSelectionRecycler.layoutManager = LinearLayoutManager(context)

        lifecycleOwner.lifecycleScope.launch {
            binding.listSelectionRecycler.adapter = GesturePickerAdapter(
                rows = rows,
                selectedAction = selectedAction,
                onClick = { action ->
                    onPicked(action)
                    dismiss()
                },
            )
        }
    }

    private companion object {
        const val DIALOG_WIDTH_FRACTION = 0.85
    }
}
