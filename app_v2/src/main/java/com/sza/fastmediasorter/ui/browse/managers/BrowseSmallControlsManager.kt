package com.sza.fastmediasorter.ui.browse.managers

import android.view.View
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Manages command panel button height adjustments for small controls mode.
 * Applies scaled button heights when enabled to optimize space usage.
 */
class BrowseSmallControlsManager(
    private val binding: ActivityBrowseBinding
) {
    companion object {
        private const val SMALL_CONTROLS_SCALE = 0.5f
    }
    
    private var smallControlsApplied = false
    private val originalCommandButtonHeights = mutableMapOf<Int, Int>()
    private val originalMargins = mutableMapOf<Int, android.graphics.Rect>()
    private val originalPaddings = mutableMapOf<Int, android.graphics.Rect>()
    private val originalToolbarPadding = android.graphics.Rect()
    
    fun applySmallControlsIfNeeded() {
        if (smallControlsApplied) return

        commandPanelButtons().forEach { button ->
            // Scale button height
            val baseline = originalCommandButtonHeights.getOrPut(button.id) {
                resolveOriginalButtonHeight(button)
            }

            if (baseline <= 0) {
                Timber.w("BrowseSmallControlsManager: Skipping button ${button.id} with baseline=$baseline")
                return@forEach
            }

            val params = button.layoutParams
            if (params != null) {
                // Save and scale height
                params.height = (baseline * SMALL_CONTROLS_SCALE).roundToInt().coerceAtLeast(1)
                
                // Save and scale margins
                if (params is android.view.ViewGroup.MarginLayoutParams) {
                    originalMargins.putIfAbsent(
                        button.id,
                        android.graphics.Rect(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)
                    )
                    
                    params.setMargins(
                        (params.leftMargin * SMALL_CONTROLS_SCALE).roundToInt(),
                        (params.topMargin * SMALL_CONTROLS_SCALE).roundToInt(),
                        (params.rightMargin * SMALL_CONTROLS_SCALE).roundToInt(),
                        (params.bottomMargin * SMALL_CONTROLS_SCALE).roundToInt()
                    )
                }
                
                button.layoutParams = params
            }
            
            // Save and scale paddings
            originalPaddings.putIfAbsent(
                button.id,
                android.graphics.Rect(button.paddingLeft, button.paddingTop, button.paddingRight, button.paddingBottom)
            )
            
            button.setPadding(
                (button.paddingLeft * SMALL_CONTROLS_SCALE).roundToInt(),
                (button.paddingTop * SMALL_CONTROLS_SCALE).roundToInt(),
                (button.paddingRight * SMALL_CONTROLS_SCALE).roundToInt(),
                (button.paddingBottom * SMALL_CONTROLS_SCALE).roundToInt()
            )
        }
        
        // Save and scale controls layout padding
        if (originalToolbarPadding.isEmpty) {
            originalToolbarPadding.set(
                binding.layoutControls.paddingLeft,
                binding.layoutControls.paddingTop,
                binding.layoutControls.paddingRight,
                binding.layoutControls.paddingBottom
            )
        }
        
        binding.layoutControls.setPadding(
            (binding.layoutControls.paddingLeft * SMALL_CONTROLS_SCALE).roundToInt(),
            binding.layoutControls.paddingTop, // Do NOT scale — contains statusBar/captionBar inset; scaling hides buttons under OS bar
            (binding.layoutControls.paddingRight * SMALL_CONTROLS_SCALE).roundToInt(),
            (binding.layoutControls.paddingBottom * SMALL_CONTROLS_SCALE).roundToInt()
        )

        smallControlsApplied = true
    }

    fun restoreCommandButtonHeightsIfNeeded() {
        if (!smallControlsApplied) return

        commandPanelButtons().forEach { button ->
            // Restore height
            val baseline = originalCommandButtonHeights[button.id] ?: return@forEach
            val params = button.layoutParams ?: return@forEach
            params.height = baseline
            
            // Restore margins
            if (params is android.view.ViewGroup.MarginLayoutParams) {
                val originalMargin = originalMargins[button.id]
                if (originalMargin != null) {
                    params.setMargins(
                        originalMargin.left,
                        originalMargin.top,
                        originalMargin.right,
                        originalMargin.bottom
                    )
                }
            }
            
            button.layoutParams = params
            
            // Restore padding
            val originalPadding = originalPaddings[button.id]
            if (originalPadding != null) {
                button.setPadding(
                    originalPadding.left,
                    originalPadding.top,
                    originalPadding.right,
                    originalPadding.bottom
                )
            }
        }
        
        // Restore controls layout padding
        if (!originalToolbarPadding.isEmpty) {
            binding.layoutControls.setPadding(
                originalToolbarPadding.left,
                originalToolbarPadding.top,
                originalToolbarPadding.right,
                originalToolbarPadding.bottom
            )
        }

        smallControlsApplied = false
    }

    private fun commandPanelButtons(): List<View> = listOf(
        binding.btnBack,
        binding.btnFilter
    )

    private fun resolveOriginalButtonHeight(button: View): Int {
        val paramsHeight = button.layoutParams?.height ?: 0
        return when {
            paramsHeight > 0 -> paramsHeight
            button.height > 0 -> button.height
            button.measuredHeight > 0 -> button.measuredHeight
            else -> 0
        }
    }
}
