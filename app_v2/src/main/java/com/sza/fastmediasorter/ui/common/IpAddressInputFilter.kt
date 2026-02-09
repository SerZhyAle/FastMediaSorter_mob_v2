package com.sza.fastmediasorter.ui.common

import android.text.InputFilter
import android.text.Spanned

/**
 * InputFilter for IP address and hostname input fields.
 * Allows only digits, dots, letters, dash, and underscore.
 * Automatically replaces comma, space with dot for convenience.
 * 
 * Example: "192,168,1,1" -> "192.168.1.1"
 */
class IpAddressInputFilter : InputFilter {
    
    override fun filter(
        source: CharSequence?,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        if (source == null || source.isEmpty()) {
            return null // Accept deletion
        }
        
        val filtered = StringBuilder()
        
        for (i in start until end) {
            val char = source[i]
            when {
                // Allow digits, dots, letters, dash, underscore
                char.isDigit() || char == '.' || char.isLetter() || char == '-' || char == '_' -> {
                    filtered.append(char)
                }
                // Replace comma, space, dash with dot
                char == ',' || char == ' ' -> {
                    filtered.append('.')
                }
                // Ignore all other characters
                else -> {
                    // Skip this character
                }
            }
        }
        
        // Return filtered string if different from source, null if no changes needed
        return if (filtered.toString() != source.substring(start, end)) {
            filtered.toString()
        } else {
            null
        }
    }
}
