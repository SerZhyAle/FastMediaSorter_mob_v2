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
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Simple dialog for selecting a resource (e.g., for slideshow music source).
 * Displays all available resources as buttons in a vertical list.
 */
class ResourcePickerDialog(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val getResourcesUseCase: GetResourcesUseCase,
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
        loadResources()
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
    
    private fun loadResources() {
        lifecycleOwner.lifecycleScope.launch {
            try {
                val resources = getResourcesUseCase.invoke().first()
                
                if (resources.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.no_resources_available),
                        Toast.LENGTH_SHORT
                    ).show()
                    dismiss()
                } else {
                    createResourceButtons(resources)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading resources")
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_error_loading_resources),
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
                
                // Highlight if currently selected
                if (resource.id == currentSelection) {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(context.resources.getColor(R.color.blue_500, null))
                } else {
                    setTextColor(context.resources.getColor(android.R.color.black, null))
                    setBackgroundColor(context.resources.getColor(android.R.color.white, null))
                }
                
                // Rounded corners
                background = android.graphics.drawable.GradientDrawable().apply {
                    if (resource.id == currentSelection) {
                        setColor(context.resources.getColor(R.color.blue_500, null))
                    } else {
                        setColor(Color.WHITE)
                        setStroke(2, Color.LTGRAY)
                    }
                    cornerRadius = 12f
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
