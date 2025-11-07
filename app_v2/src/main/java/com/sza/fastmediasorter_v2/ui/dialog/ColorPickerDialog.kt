package com.sza.fastmediasorter_v2.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.core.util.ColorPalette
import com.sza.fastmediasorter_v2.databinding.DialogColorPickerBinding
import com.sza.fastmediasorter_v2.databinding.ItemColorBinding

/**
 * Color picker dialog for selecting destination colors
 */
class ColorPickerDialog : DialogFragment() {

    private var _binding: DialogColorPickerBinding? = null
    private val binding get() = _binding!!

    private var initialColor: Int = ColorPalette.DEFAULT_COLORS[0]
    private var selectedColor: Int = ColorPalette.DEFAULT_COLORS[0]
    private var onColorSelected: ((Int) -> Unit)? = null

    private lateinit var colorAdapter: ColorAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogColorPickerBinding.inflate(layoutInflater)

        initialColor = arguments?.getInt(ARG_INITIAL_COLOR, ColorPalette.DEFAULT_COLORS[0])
            ?: ColorPalette.DEFAULT_COLORS[0]
        selectedColor = initialColor

        setupViews()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupViews() {
        // Setup color preview
        updateColorPreview()

        // Setup RecyclerView
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
            layoutManager = GridLayoutManager(requireContext(), 5)
            adapter = colorAdapter
        }

        // Setup buttons
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnOk.setOnClickListener {
            onColorSelected?.invoke(selectedColor)
            dismiss()
        }
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
        private const val ARG_INITIAL_COLOR = "initial_color"

        fun newInstance(
            initialColor: Int,
            onColorSelected: (Int) -> Unit
        ): ColorPickerDialog {
            return ColorPickerDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_INITIAL_COLOR, initialColor)
                }
                this.onColorSelected = onColorSelected
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
