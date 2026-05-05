package com.sza.fastmediasorter.ui.player

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages destination buttons in PlayerActivity:
 * - Populates Copy/Move grids with destination buttons
 * - Calculates button distribution (5x2, 4x3, 3x3 layouts)
 * - Handles panel collapse/expand toggle
 * - Persists collapsed state to settings
 */
class DestinationButtonsManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callback: DestinationButtonsCallback
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    
    // Cache settings to avoid repeated reads
    private var cachedCopyCollapsed: Boolean? = null
    private var cachedMoveCollapsed: Boolean? = null
    private var cachedMaxRecipients: Int? = null
    
    interface DestinationButtonsCallback {
        fun onCopyClicked(destination: MediaResource)
        fun onMoveClicked(destination: MediaResource)
        fun onCustomPathPickerRequested(operationType: FileOperationType)
        fun getCurrentResourceId(): Long
        fun onUpdateCommandAvailability()
        /** Check if command panel should be visible (used to prevent race condition) */
        fun isCommandPanelVisible(): Boolean
    }
    
    /**
     * Populate destination buttons for Copy/Move panels
     * Excludes current resource, reads collapsed state from settings
     */
    fun populateDestinationButtons() {
        val resourceId = callback.getCurrentResourceId()
        Timber.d("DestinationButtonsManager: populateDestinationButtons() CALLED - resourceId=$resourceId")
        
        lifecycleScope.launch {
            Timber.d("DestinationButtonsManager: coroutine STARTED for resourceId=$resourceId")
            try {
                val allDestinations = getDestinationsUseCase().first()
                Timber.d("DestinationButtonsManager: getDestinationsUseCase returned ${allDestinations.size} resources: ${allDestinations.map { "${it.id}:${it.name}" }}")
                val destinations = allDestinations.filter { it.id != resourceId } // Exclude current resource
                Timber.d("DestinationButtonsManager: After filtering (exclude resourceId=$resourceId): ${destinations.size} destinations")
                
                // Read collapsed state and max recipients from settings (cached)
                if (cachedCopyCollapsed == null || cachedMoveCollapsed == null || cachedMaxRecipients == null) {
                    val settings = settingsRepository.getSettings().first()
                    cachedCopyCollapsed = settings.copyPanelCollapsed
                    cachedMoveCollapsed = settings.movePanelCollapsed
                    cachedMaxRecipients = settings.maxRecipients
                    Timber.d("DestinationButtonsManager: READ and CACHED settings - copyPanelCollapsed=$cachedCopyCollapsed, movePanelCollapsed=$cachedMoveCollapsed, maxRecipients=$cachedMaxRecipients")
                }
                val copyCollapsed = cachedCopyCollapsed!!
                val moveCollapsed = cachedMoveCollapsed!!
                val maxRecipients = cachedMaxRecipients!!
                
                // Clear existing buttons
                safeViews.copyToButtonsGrid.removeAllViews()
                safeViews.moveToButtonsGrid.removeAllViews()
                
                val destinationsList = destinations.take(maxRecipients)
                val count = destinationsList.size
                
                // Calculate button distribution per specification (V2_Specification_RU.md lines 357-370)
                // Standard: max 5 buttons per row for maxRecipients <= 10
                // Extended: max 10 buttons per row when maxRecipients > 10 (user explicitly increased limit)
                val distribution = if (maxRecipients > 10) {
                    // Extended mode: up to 10 buttons per row, allows 3rd row
                    when {
                        count <= 10 -> listOf(count)  // Single row up to 10 buttons
                        count <= 20 -> {
                            // Two rows, distribute evenly
                            val perRow = kotlin.math.ceil(count.toFloat() / 2).toInt()
                            listOf(perRow, count - perRow)
                        }
                        else -> {
                            // Three or more rows, max 10 per row
                            val numRows = kotlin.math.ceil(count.toFloat() / 10).toInt()
                            val basePerRow = count / numRows
                            val remainder = count % numRows
                            List(numRows) { i -> basePerRow + if (i < remainder) 1 else 0 }
                        }
                    }
                } else {
                    // Standard mode: max 5 buttons per row, specific patterns for 1-10
                    when (count) {
                        1 -> listOf(1)         // 1 row: 1 button (full width)
                        2 -> listOf(2)         // 1 row: 2 buttons (1/2 width each)
                        3 -> listOf(3)         // 1 row: 3 buttons (1/3 width each)
                        4 -> listOf(4)         // 1 row: 4 buttons (1/4 width each)
                        5 -> listOf(5)         // 1 row: 5 buttons (1/5 width each)
                        6 -> listOf(3, 3)      // 2 rows: 3+3 buttons (1/3 width each)
                        7 -> listOf(4, 3)      // 2 rows: 4+3 buttons (1/4 and 1/3 width)
                        8 -> listOf(4, 4)      // 2 rows: 4+4 buttons (1/4 width each)
                        9 -> listOf(5, 4)      // 2 rows: 5+4 buttons (1/5 and 1/4 width)
                        10 -> listOf(5, 5)     // 2 rows: 5+5 buttons (1/5 width each)
                        else -> listOf(5, 5)   // Fallback (shouldn't happen with maxRecipients <= 10)
                    }
                }
                
                // Create rows for Copy panel
                var destIndex = 0
                distribution.forEach { rowCount ->
                    if (rowCount > 0) {
                        val rowLayout = createButtonRow()
                        repeat(rowCount) {
                            if (destIndex < destinationsList.size) {
                                val btn = createDestinationButton(destinationsList[destIndex], destIndex, true, rowCount)
                                rowLayout.addView(btn)
                                destIndex++
                            }
                        }
                        // Only add row if it has buttons (avoid empty rows)
                        if (rowLayout.childCount > 0) {
                            safeViews.copyToButtonsGrid.addView(rowLayout)
                        }
                    }
                }
                
                // Create rows for Move panel
                destIndex = 0
                distribution.forEach { rowCount ->
                    if (rowCount > 0) {
                        val rowLayout = createButtonRow()
                        repeat(rowCount) {
                            if (destIndex < destinationsList.size) {
                                val btn = createDestinationButton(destinationsList[destIndex], destIndex, false, rowCount)
                                rowLayout.addView(btn)
                                destIndex++
                            }
                        }
                        // Only add row if it has buttons (avoid empty rows)
                        if (rowLayout.childCount > 0) {
                            safeViews.moveToButtonsGrid.addView(rowLayout)
                        }
                    }
                }
                
                // Append «..» to the last row of Copy grid; create a new row only when grid is empty
                if (safeViews.copyToButtonsGrid.childCount > 0) {
                    val lastRow = safeViews.copyToButtonsGrid.getChildAt(safeViews.copyToButtonsGrid.childCount - 1) as LinearLayout
                    lastRow.addView(createCustomPathButton(isCopy = true))
                } else {
                    val dotDotRow = createButtonRow()
                    dotDotRow.addView(createCustomPathButton(isCopy = true))
                    safeViews.copyToButtonsGrid.addView(dotDotRow)
                }

                // Append «..» to the last row of Move grid; create a new row only when grid is empty
                if (safeViews.moveToButtonsGrid.childCount > 0) {
                    val lastRow = safeViews.moveToButtonsGrid.getChildAt(safeViews.moveToButtonsGrid.childCount - 1) as LinearLayout
                    lastRow.addView(createCustomPathButton(isCopy = false))
                } else {
                    val dotDotRow = createButtonRow()
                    dotDotRow.addView(createCustomPathButton(isCopy = false))
                    safeViews.moveToButtonsGrid.addView(dotDotRow)
                }

                // CRITICAL: Check current state before showing panels
                // This prevents race condition when user switches to fullscreen while buttons are loading
                val shouldShowPanels = callback.isCommandPanelVisible()

                if (!shouldShowPanels) {
                    Timber.d("DestinationButtonsManager: Command panel not visible (fullscreen mode?), skipping panel show")
                    // Panels remain hidden - fullscreen mode or slideshow active
                } else {
                    // CRITICAL: Show panels EXPLICITLY when destinations exist AND command panel is visible
                    // This fixes race condition where updateCommandAvailability runs before buttons are created
                    Timber.d("DestinationButtonsManager: SHOWING panels - destinationsCount=${destinationsList.size}")
                    safeViews.copyToPanel.isVisible = true
                    safeViews.moveToPanel.isVisible = true
                    
                    // Force layout recalculation to prevent panels from being pushed off-screen
                    binding.root.requestLayout()
                    
                    // Restore collapsed state AFTER showing panels
                    Timber.d("DestinationButtonsManager: RESTORING panel states - copy=$copyCollapsed, move=$moveCollapsed")
                    updateCopyPanelVisibility(copyCollapsed)
                    updateMovePanelVisibility(moveCollapsed)
                }
                
                // Update command availability to refresh panel visibility
                // This may hide moveToPanel if canWrite=false (read-only resource)
                Timber.d("DestinationButtonsManager: Calling onUpdateCommandAvailability callback - copyGrid.childCount=${safeViews.copyToButtonsGrid.childCount}, moveGrid.childCount=${safeViews.moveToButtonsGrid.childCount}")
                callback.onUpdateCommandAvailability()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load destinations")
                Toast.makeText(binding.root.context, R.string.toast_failed_to_load_destinations, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Create horizontal LinearLayout for button row
     */
    private fun createButtonRow(): LinearLayout {
        return LinearLayout(binding.root.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL // Baseline alignment for buttons in row
        }
    }
    
    private fun createCustomPathButton(isCopy: Boolean): MaterialButton {
        val context = binding.root.context
        val density = context.resources.displayMetrics.density
        val screenWidthDp = context.resources.displayMetrics.widthPixels / density
        val buttonHeightDp = if (screenWidthDp < 500f) 44f else 56f
        val buttonHeight = (buttonHeightDp * density).toInt()
        val cornerRadiusPx = (12 * density).toInt()
        val marginPx = (2 * density).toInt()
        val paddingHPx = (12 * density).toInt()
        return MaterialButton(context).apply {
            text = ".."
            contentDescription = context.getString(R.string.btn_select_folder_description)
            textSize = 12f
            cornerRadius = cornerRadiusPx
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            minWidth = 0
            minHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                buttonHeight
            ).apply {
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            setPadding(paddingHPx, 0, paddingHPx, 0)
            gravity = Gravity.CENTER
            setOnClickListener {
                callback.onCustomPathPickerRequested(
                    if (isCopy) FileOperationType.COPY else FileOperationType.MOVE
                )
            }
        }
    }

    /**
     * Clear cached settings - call when settings change
     */
    fun clearSettingsCache() {
        cachedCopyCollapsed = null
        cachedMoveCollapsed = null
        cachedMaxRecipients = null
        Timber.d("DestinationButtonsManager: Settings cache cleared")
    }
    
    /**
     * Create destination button with short name, color, click handler
     */
    private fun createDestinationButton(
        destination: MediaResource,
        @Suppress("UNUSED_PARAMETER") index: Int,
        isCopy: Boolean,
        @Suppress("UNUSED_PARAMETER") buttonsInRow: Int
    ): MaterialButton {
        return MaterialButton(binding.root.context).apply {
            // Short name - take first 8 characters or first word
            val shortName = when {
                destination.name.length <= 10 -> destination.name
                destination.name.contains(" ") -> destination.name.substringBefore(" ").take(10)
                else -> destination.name.take(8) + ".."
            }
            text = shortName
            
            // Dynamic text size based on text length (reduced by 2sp)
            textSize = when {
                shortName.length <= 8 -> 12f  // Short names: larger font
                shortName.length <= 12 -> 10f // Medium names: standard font
                else -> 8f                    // Long names: smaller font
            }
            
            // Calculate brightness to determine text color
            val color = destination.destinationColor
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val brightness = kotlin.math.sqrt(
                0.299 * r * r +
                0.587 * g * g +
                0.114 * b * b
            )
            setTextColor(if (brightness > 130) Color.BLACK else Color.WHITE)
            
            // Set button color from destination
            setBackgroundColor(destination.destinationColor)
            
            // Rounded rectangle buttons with equal weight distribution
            val density = resources.displayMetrics.density
            val screenWidthDp = resources.displayMetrics.widthPixels / density
            // Smaller buttons for compact screens (< 500dp width)
            val buttonHeightDp = if (screenWidthDp < 500f) 44f else 56f
            val buttonHeight = (buttonHeightDp * density).toInt()
            val cornerRadius = (12 * density).toInt() // Rounded corners
            val marginPx = (2 * density).toInt() // Scale margins with density
            
            // Apply rounded corners
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(cornerRadius.toFloat())
                .build()
            
            minWidth = 0 // Allow shrinking
            minHeight = 0 // Allow vertical shrinking
            
            // Reduce padding to maximize text area (MaterialButton has 16dp default padding)
            val paddingPx = (4 * density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                buttonHeight,
                1f // Equal weight for all buttons in row
            ).apply {
                setMargins(marginPx, marginPx, marginPx, marginPx)
                gravity = Gravity.CENTER_VERTICAL // Align buttons vertically
            }
            
            // Center text and allow wrapping
            gravity = Gravity.CENTER
            maxLines = 2
            
            setOnClickListener {
                Timber.d("DestinationButtonsManager: Destination button clicked - ${destination.name}, isCopy=$isCopy")
                if (isCopy) {
                    callback.onCopyClicked(destination)
                } else {
                    callback.onMoveClicked(destination)
                }
            }
        }
    }
    
    /**
     * Toggle Copy to panel collapsed/expanded state
     */
    fun toggleCopyPanel() {
        lifecycleScope.launch {
            val currentSettings = settingsRepository.getSettings().first()
            val newCollapsedState = !currentSettings.copyPanelCollapsed
            
            // Save new state
            settingsRepository.updateSettings(currentSettings.copy(copyPanelCollapsed = newCollapsedState))
            
            // Clear cache to force reload on next populateDestinationButtons()
            cachedCopyCollapsed = newCollapsedState
            
            // Update UI
            updateCopyPanelVisibility(newCollapsedState)
        }
    }
    
    /**
     * Toggle Move to panel collapsed/expanded state
     */
    fun toggleMovePanel() {
        lifecycleScope.launch {
            val currentSettings = settingsRepository.getSettings().first()
            val newCollapsedState = !currentSettings.movePanelCollapsed
            
            // Save new state
            settingsRepository.updateSettings(currentSettings.copy(movePanelCollapsed = newCollapsedState))
            
            // Clear cache to force reload on next populateDestinationButtons()
            cachedMoveCollapsed = newCollapsedState
            
            // Update UI
            updateMovePanelVisibility(newCollapsedState)
        }
    }
    
    /**
     * Update Copy to panel buttons visibility and indicator
     */
    private fun updateCopyPanelVisibility(collapsed: Boolean) {
        safeViews.copyToButtonsGrid.isVisible = !collapsed
        safeViews.copyToPanelIndicator.text = if (collapsed) "▶" else "▼"
        updateContainerOrientation()
    }
    
    /**
     * Update Move to panel buttons visibility and indicator
     */
    private fun updateMovePanelVisibility(collapsed: Boolean) {
        safeViews.moveToButtonsGrid.isVisible = !collapsed
        safeViews.moveToPanelIndicator.text = if (collapsed) "▶" else "▼"
        updateContainerOrientation()
    }

    /**
     * Switch bottomPanelsContainer between horizontal (both collapsed) and vertical layouts.
     * When both panels are visible and both are collapsed, they share one row (50/50).
     * In any other state each panel takes full width on its own row.
     */
    private fun updateContainerOrientation() {
        val copyVisible = safeViews.copyToPanel.isVisible
        val moveVisible = safeViews.moveToPanel.isVisible
        val copyCollapsed = !safeViews.copyToButtonsGrid.isVisible
        val moveCollapsed = !safeViews.moveToButtonsGrid.isVisible

        val sideBySide = copyVisible && moveVisible && copyCollapsed && moveCollapsed

        safeViews.bottomPanelsContainer.orientation =
            if (sideBySide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        listOf(safeViews.copyToPanel, safeViews.moveToPanel).forEach { panel ->
            val lp = panel.layoutParams as LinearLayout.LayoutParams
            if (sideBySide) {
                lp.width = 0
                lp.weight = 1f
            } else {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.weight = 0f
            }
            panel.layoutParams = lp
        }
    }
}
