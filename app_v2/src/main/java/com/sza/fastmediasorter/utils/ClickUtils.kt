package com.sza.fastmediasorter.utils

import android.view.View

/**
 * Utility functions for handling click events with debounce protection
 */

/**
 * Prevent double-click by ignoring clicks within specified delay
 * Default delay is 500ms
 */
fun View.setOnClickListenerDebounced(delayMs: Long = 500L, action: (View) -> Unit) {
    setOnClickListener(object : View.OnClickListener {
        private var lastClickTime = 0L

        override fun onClick(v: View) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > delayMs) {
                lastClickTime = currentTime
                action(v)
            }
        }
    })
}

/**
 * Prevent double-click for long clicks
 */
fun View.setOnLongClickListenerDebounced(delayMs: Long = 500L, action: (View) -> Boolean) {
    setOnLongClickListener(object : View.OnLongClickListener {
        private var lastClickTime = 0L

        override fun onLongClick(v: View): Boolean {
            val currentTime = System.currentTimeMillis()
            return if (currentTime - lastClickTime > delayMs) {
                lastClickTime = currentTime
                action(v)
            } else {
                false
            }
        }
    })
}
