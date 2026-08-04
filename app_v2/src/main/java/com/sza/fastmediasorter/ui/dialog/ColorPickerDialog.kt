package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.ColorPalette
import com.sza.fastmediasorter.databinding.DialogColorPickerBinding
import com.sza.fastmediasorter.databinding.ItemColorBinding

/**
 * Color picker. Returns the confirmed color to the host through a FragmentResult ([RESULT_COLOR],
 * tagged with [RESULT_SUBJECT_ID]) rather than a constructor lambda: S1331 - FragmentManager rebuilds
 * a restored dialog with the no-arg constructor, so a field-held handler would be null and the
 * confirmed color would be dropped without a trace. The request key therefore lives in the arguments
 * bundle, which a restored instance gets back, and is read in `onCreate`.
 */
class ColorPickerDialog : DialogFragment() {

    private var _binding: DialogColorPickerBinding? = null
    private val binding get() = _binding!!

    private var initialColor: Int = ColorPalette.DEFAULT_COLORS[0]
    private var selectedColor: Int = ColorPalette.DEFAULT_COLORS[0]
    private var requestKey: String = RESULT_KEY

    // Opaque to the dialog: the host's identifier for the row being recolored, echoed back in the
    // result so one host listener can serve every row.
    private var subjectId: String = ""

    private lateinit var colorAdapter: ColorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        initialColor = args.getInt(ARG_INITIAL_COLOR, ColorPalette.DEFAULT_COLORS[0])
        // The in-progress pick lives only in this field - arguments still hold the colour the host
        // opened with - so without restoring it a recreated dialog confirms the initial colour and
        // discards what the user had actually selected.
        selectedColor = savedInstanceState?.getInt(STATE_SELECTED_COLOR, initialColor) ?: initialColor
        requestKey = args.getString(ARG_REQUEST_KEY) ?: RESULT_KEY
        subjectId = args.getString(ARG_SUBJECT_ID).orEmpty()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_COLOR, selectedColor)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogColorPickerBinding.inflate(layoutInflater)

        setupViews()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupViews() {
        updateColorPreview()

        colorAdapter = ColorAdapter(
            colors = ColorPalette.EXTENDED_PALETTE.toList(),
            selectedColor = selectedColor,
            onColorClick = { color ->
                selectedColor = color
                colorAdapter.updateSelection(color)
                updateColorPreview()
            }
        )

        binding.rvColorPalette.apply {
            val columnCount = resources.getInteger(R.integer.grid_column_count)
            layoutManager = GridLayoutManager(requireContext(), columnCount.coerceAtLeast(4))  // Min 4 columns for color palette
            adapter = colorAdapter
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnOk.setOnClickListener {
            confirmSelection()
        }
    }

    private fun confirmSelection() {
        setFragmentResult(
            requestKey,
            bundleOf(RESULT_COLOR to selectedColor, RESULT_SUBJECT_ID to subjectId)
        )
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = ::confirmSelection)
        binding.btnOk.requestFocus()
    }

    private fun updateColorPreview() {
        binding.colorPreview.setBackgroundColor(selectedColor)
        binding.tvColorName.text = ColorPalette.getColorName(selectedColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ColorPickerDialog"
        const val RESULT_KEY = "color_picker_result"
        const val RESULT_COLOR = "result_color"
        const val RESULT_SUBJECT_ID = "result_subject_id"

        private const val ARG_INITIAL_COLOR = "initial_color"
        private const val ARG_SUBJECT_ID = "arg_subject_id"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val STATE_SELECTED_COLOR = "state_selected_color"

        /** [subjectId] identifies the host-side row the color is for and is echoed back untouched. */
        fun newInstance(
            initialColor: Int,
            subjectId: String,
            requestKey: String = RESULT_KEY
        ): ColorPickerDialog {
            return ColorPickerDialog().apply {
                arguments = bundleOf(
                    ARG_INITIAL_COLOR to initialColor,
                    ARG_SUBJECT_ID to subjectId,
                    ARG_REQUEST_KEY to requestKey
                )
            }
        }
    }

    /**
     * Adapter for color grid
     */
    private class ColorAdapter(
        private val colors: List<Int>,
        private var selectedColor: Int,
        private val onColorClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val binding = ItemColorBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ColorViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            holder.bind(colors[position])
        }

        override fun getItemCount(): Int = colors.size

        fun updateSelection(color: Int) {
            val oldPosition = colors.indexOf(selectedColor)
            val newPosition = colors.indexOf(color)
            selectedColor = color

            if (oldPosition >= 0) notifyItemChanged(oldPosition)
            if (newPosition >= 0) notifyItemChanged(newPosition)
        }

        inner class ColorViewHolder(
            private val binding: ItemColorBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(color: Int) {
                binding.colorView.setBackgroundColor(color)
                binding.ivSelected.visibility = if (color == selectedColor) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                // Adjust checkmark color for visibility
                val luminance = (0.299 * Color.red(color) + 
                               0.587 * Color.green(color) + 
                               0.114 * Color.blue(color)) / 255
                binding.ivSelected.setColorFilter(
                    if (luminance > 0.5) Color.BLACK else Color.WHITE
                )

                binding.root.setOnClickListener {
                    onColorClick(color)
                }
            }
        }
    }
}
