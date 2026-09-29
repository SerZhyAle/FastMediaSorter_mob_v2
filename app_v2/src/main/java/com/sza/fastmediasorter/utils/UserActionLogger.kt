package com.sza.fastmediasorter.utils

import android.view.View
import timber.log.Timber

/**
 * Centralized user action logger for debugging and analytics.
 * Logs all user interactions: clicks, scrolls, touches, gestures.
 */
object UserActionLogger {
    
    private const val TAG = "UserAction"
    
    // Enable/disable logging (can be controlled by BuildConfig.DEBUG)
    var enabled = true
    
    /**
     * Log button click
     */
    fun logButtonClick(buttonName: String, context: String = "") {
        if (!enabled) return
        val msg = if (context.isNotEmpty()) "CLICK: $buttonName ($context)" else "CLICK: $buttonName"
        Timber.tag(TAG).d(msg)
    }
    
    /**
     * Log file/item click
     */
    fun logItemClick(itemName: String, position: Int = -1, context: String = "") {
        if (!enabled) return
        val posStr = if (position >= 0) " pos=$position" else ""
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("ITEM_CLICK: $itemName$posStr$ctxStr")
    }
    
    /**
     * Log file/item long click
     */
    fun logItemLongClick(itemName: String, position: Int = -1, context: String = "") {
        if (!enabled) return
        val posStr = if (position >= 0) " pos=$position" else ""
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("ITEM_LONG_CLICK: $itemName$posStr$ctxStr")
    }
    
    /**
     * Log touch event
     */
    fun logTouch(action: String, x: Float, y: Float, context: String = "") {
        if (!enabled) return
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("TOUCH: action=$action x=${x.toInt()} y=${y.toInt()}$ctxStr")
    }
    
    /**
     * Log gesture
     */
    fun logGesture(gesture: String, details: String = "", context: String = "") {
        if (!enabled) return
        val detailsStr = if (details.isNotEmpty()) " $details" else ""
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("GESTURE: $gesture$detailsStr$ctxStr")
    }
    
    /**
     * Log key event
     */
    fun logKey(keyCode: Int, action: Int, eventName: String = "", context: String = "") {
        if (!enabled) return
        @Suppress("DEPRECATION")
        val actionStr = when (action) {
            android.view.KeyEvent.ACTION_DOWN -> "DOWN"
            android.view.KeyEvent.ACTION_UP -> "UP"
            android.view.KeyEvent.ACTION_MULTIPLE -> "MULTIPLE"
            else -> "UNKNOWN($action)"
        }
        val nameStr = if (eventName.isNotEmpty()) " [$eventName]" else ""
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("KEY: action=$actionStr code=$keyCode$nameStr$ctxStr")
    }

    /**
     * Log navigation
     */
    fun logNavigation(from: String, to: String, extras: String = "") {
        if (!enabled) return
        val extrasStr = if (extras.isNotEmpty()) " extras=[$extras]" else ""
        Timber.tag(TAG).d("NAVIGATION: $from -> $to$extrasStr")
    }
    
    /**
     * Log selection change
     */
    fun logSelection(itemName: String, selected: Boolean, totalSelected: Int = -1, context: String = "") {
        if (!enabled) return
        val selectedStr = if (selected) "SELECTED" else "DESELECTED"
        val totalStr = if (totalSelected >= 0) " total=$totalSelected" else ""
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("SELECTION: $selectedStr $itemName$totalStr$ctxStr")
    }
    
    /**
     * Create OnClickListener wrapper with logging
     */
    fun wrapClickListener(buttonName: String, context: String = "", listener: View.OnClickListener): View.OnClickListener {
        return View.OnClickListener { view ->
            logButtonClick(buttonName, context)
            listener.onClick(view)
        }
    }
}
