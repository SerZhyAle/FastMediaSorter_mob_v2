package com.sza.fastmediasorter.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R

/**
 * Interactive breadcrumb navigation view.
 * Displays path as "Root > Folder1 > Folder2" with clickable segments.
 */
class BreadcrumbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val container: LinearLayout
    private var onSegmentClickListener: ((depth: Int) -> Unit)? = null
    
    // Store current path structure
    private data class PathSegment(
        val name: String,
        val depth: Int
    )
    
    private val segments = mutableListOf<PathSegment>()

    init {
        // Container for breadcrumb segments
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.padding_small)
            setPadding(padding, 0, padding, 0)
        }
        addView(container, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
    }

    /**
     * Set breadcrumb path.
     * @param resourceName Name of the resource (root)
     * @param folders List of folder names in path (empty = root only)
     */
    fun setPath(resourceName: String, folders: List<String>) {
        container.removeAllViews()
        segments.clear()
        
        segments.add(PathSegment(resourceName, 0))
        addSegment(resourceName, 0, isLast = folders.isEmpty())
        
        folders.forEachIndexed { index, folderName ->
            addSeparator()
            
            val depth = index + 1
            segments.add(PathSegment(folderName, depth))
            addSegment(folderName, depth, isLast = index == folders.lastIndex)
        }
        
        // Scroll to end to show current folder
        post {
            fullScroll(View.FOCUS_RIGHT)
        }
    }

    /**
     * Set listener for segment clicks.
     * @param listener Callback with depth (0=root, 1=first folder, etc.)
     */
    fun setOnSegmentClickListener(listener: (depth: Int) -> Unit) {
        onSegmentClickListener = listener
    }

    private fun addSegment(name: String, depth: Int, isLast: Boolean) {
        val textView = TextView(context).apply {
            text = name
            textSize = 12f
            val textColor = if (isLast) {
                // Current folder - bold and primary color
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                ContextCompat.getColor(context, R.color.blue_500)
            } else {
                // Clickable parent - normal weight, light blue
                ContextCompat.getColor(context, android.R.color.holo_blue_light)
            }
            setTextColor(textColor)
            
            // Make clickable if not last
            if (!isLast) {
                background = ContextCompat.getDrawable(context, 
                    android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    onSegmentClickListener?.invoke(depth)
                }
            } else {
                setPadding(8, 8, 8, 8)
            }
            
            // Ensure text doesn't wrap
            setSingleLine()
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            maxWidth = resources.displayMetrics.widthPixels / 3 // Max 1/3 screen width per segment
        }
        
        container.addView(textView)
    }

    private fun addSeparator() {
        val separator = TextView(context).apply {
            text = " > "
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setPadding(4, 8, 4, 8)
        }
        container.addView(separator)
    }
    
    /**
     * Clear breadcrumb (hide all segments).
     */
    fun clear() {
        container.removeAllViews()
        segments.clear()
    }
    
    /**
     * Get number of path segments.
     */
    fun getDepth(): Int = segments.size
}
