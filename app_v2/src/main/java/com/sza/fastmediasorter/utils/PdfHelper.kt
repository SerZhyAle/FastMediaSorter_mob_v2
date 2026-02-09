package com.sza.fastmediasorter.utils

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.sza.fastmediasorter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.WeakHashMap

object PdfHelper {

    // Track active jobs for ImageViews to cancel recycled views
    private val activeJobs = WeakHashMap<ImageView, Job>()

    fun loadPdfThumbnail(context: Context, file: File, imageView: ImageView, size: Int) {
        // Cancel any previous job for this ImageView
        activeJobs[imageView]?.cancel()
        
        // Use View's lifecycle scope if available
        val lifecycleOwner = imageView.findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) {
            // Fallback to placeholder if no lifecycle (shouldn't happen in Activity/Fragment)
            imageView.setImageResource(R.drawable.ic_image_placeholder)
            return
        }

        val job = lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            // Show placeholder immediately
            imageView.setImageResource(R.drawable.ic_image_placeholder)
            
            try {
                // Generate bitmap in background
                val bitmap = PdfThumbnailHelper.generateThumbnail(file, size, size)
                
                if (bitmap != null) {
                    // Load into ImageView using Glide for consistent transitions/caching (if we wanted to cache the bitmap manually)
                    // But since we have raw bitmap, just set it.
                    // Ideally we'd wrap this in a Glide custom ModelLoader to get free memory caching.
                    // For now, direct set is okay for "checking capability".
                    
                    Glide.with(context)
                        .load(bitmap)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // Bitmap is already in memory
                        .into(imageView)
                }
            } catch (e: Exception) {
                // Error handling handled by keeping placeholder
            }
        }
        
        activeJobs[imageView] = job
    }
}
