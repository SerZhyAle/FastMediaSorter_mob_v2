package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogResourcePickerBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Dialog for selecting a destination resource (for frame save target, etc.).
 * Shows only resources marked as destinations, excluding virtual and read-only.
 * Supports all resource types: Local, SMB, SFTP, FTP, and Cloud.
 * Reuses the same dialog_resource_picker layout as ResourcePickerDialog.
 */
class DestinationPickerDialog(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val currentSelection: Long?,
    private val title: String,
    private val allowClear: Boolean = true,
    private val onResourceSelected: (MediaResource?) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogResourcePickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogResourcePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val width = (context.resources.displayMetrics.widthPixels * 0.85).toInt()
        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        setupUI()
        loadDestinations()
    }

    private fun setupUI() {
        binding.apply {
            tvTitle.text = title
            btnCancel.setOnClickListener { dismiss() }

            if (allowClear && currentSelection != null) {
                btnClear.visibility = android.view.View.VISIBLE
                btnClear.setOnClickListener {
                    onResourceSelected(null)
                    dismiss()
                }
            } else {
                btnClear.visibility = android.view.View.GONE
            }
        }
    }

    private fun loadDestinations() {
        lifecycleOwner.lifecycleScope.launch {
            try {
                val destinations = getDestinationsUseCase.invoke().first()

                if (destinations.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.no_destinations_available),
                        Toast.LENGTH_SHORT
                    ).show()
                    dismiss()
                } else {
                    createResourceButtons(destinations)
                }
            } catch (e: Exception) {
                Timber.e(e, "DestinationPickerDialog: error loading destinations")
                Toast.makeText(
                    context,
                    context.getString(R.string.save_frame_error),
                    Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
        }
    }

    private fun createResourceButtons(resources: List<MediaResource>) {
        val container = binding.layoutResources
        container.removeAllViews()

        val marginSize = (8 * context.resources.displayMetrics.density).toInt()

        resources.forEach { resource ->
            val button = androidx.appcompat.widget.AppCompatButton(context).apply {
                text = resource.name
                isAllCaps = false
                setPadding(32, 32, 32, 32)
                textSize = 16f

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(marginSize, marginSize / 2, marginSize, marginSize / 2)
                }

                minimumHeight = context.resources.getDimensionPixelSize(R.dimen.destination_button_min_height)
                elevation = 4f

                // Highlight current selection
                if (resource.id == currentSelection) {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#FF4CAF50"))
                }

                setOnClickListener {
                    onResourceSelected(resource)
                    dismiss()
                }
            }
            container.addView(button)
        }
    }
}
