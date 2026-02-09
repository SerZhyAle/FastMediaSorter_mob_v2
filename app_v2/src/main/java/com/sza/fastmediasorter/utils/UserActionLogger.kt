package com.sza.fastmediasorter.utils

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
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
     * Log scroll event
     */
    fun logScroll(scrollX: Int, scrollY: Int, state: String = "", context: String = "") {
        if (!enabled) return
        val stateStr = if (state.isNotEmpty()) " state=$state" else ""
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("SCROLL: x=$scrollX y=$scrollY$stateStr$ctxStr")
    }
    
    /**
     * Log RecyclerView scroll
     */
    fun logRecyclerViewScroll(
        dx: Int,
        dy: Int,
        firstVisibleItem: Int,
        lastVisibleItem: Int,
        totalItems: Int,
        scrollState: Int,
        context: String = ""
    ) {
        if (!enabled) return
        val state = when (scrollState) {
            RecyclerView.SCROLL_STATE_IDLE -> "IDLE"
            RecyclerView.SCROLL_STATE_DRAGGING -> "DRAGGING"
            RecyclerView.SCROLL_STATE_SETTLING -> "SETTLING"
            else -> "UNKNOWN"
        }
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("RV_SCROLL: dx=$dx dy=$dy visible=[$firstVisibleItem-$lastVisibleItem]/$totalItems state=$state$ctxStr")
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
     * Log dialog action
     */
    fun logDialog(action: String, dialogName: String, details: String = "") {
        if (!enabled) return
        val detailsStr = if (details.isNotEmpty()) " $details" else ""
        Timber.tag(TAG).d("DIALOG: $action $dialogName$detailsStr")
    }
    
    /**
     * Log text input
     */
    fun logTextInput(fieldName: String, textLength: Int, context: String = "") {
        if (!enabled) return
        val ctxStr = if (context.isNotEmpty()) " ($context)" else ""
        Timber.tag(TAG).d("TEXT_INPUT: $fieldName length=$textLength$ctxStr")
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
    
    /**
     * Create RecyclerView scroll listener with logging
     */
    fun createScrollListener(context: String = ""): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager ?: return
                val firstVisible = when (layoutManager) {
                    is androidx.recyclerview.widget.LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                    is androidx.recyclerview.widget.GridLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                    else -> -1
                }
                val lastVisible = when (layoutManager) {
                    is androidx.recyclerview.widget.LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()
                    is androidx.recyclerview.widget.GridLayoutManager -> layoutManager.findLastVisibleItemPosition()
                    else -> -1
                }
                val totalItems = layoutManager.itemCount
                
                // Log only significant scrolls (threshold: 5 pixels)
                if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) {
                    logRecyclerViewScroll(dx, dy, firstVisible, lastVisible, totalItems, recyclerView.scrollState, context)
                }
            }
            
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager ?: return
                    val firstVisible = when (layoutManager) {
                        is androidx.recyclerview.widget.LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                        is androidx.recyclerview.widget.GridLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                        else -> -1
                    }
                    val lastVisible = when (layoutManager) {
                        is androidx.recyclerview.widget.LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()
                        is androidx.recyclerview.widget.GridLayoutManager -> layoutManager.findLastVisibleItemPosition()
                        else -> -1
                    }
                    Timber.tag(TAG).d("RV_SCROLL_STOPPED: visible=[$firstVisible-$lastVisible]/${layoutManager.itemCount} ($context)")
                }
            }
        }
    }
    
    /**
     * Parse MotionEvent action to string
     */
    fun motionEventActionToString(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
            MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
            else -> "UNKNOWN($action)"
        }
    }
}
