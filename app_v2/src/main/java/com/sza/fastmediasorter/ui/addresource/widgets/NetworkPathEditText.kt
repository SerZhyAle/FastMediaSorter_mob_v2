package com.sza.fastmediasorter.ui.addresource.widgets

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText
import timber.log.Timber

/**
 * Custom EditText for network path input with auto-correction.
 * - Automatically replaces backslashes (\) with forward slashes (/)
 * - Removes leading/trailing spaces
 * - Prevents double slashes (except for protocol prefix like smb://)
 */
class NetworkPathEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    private var autoCorrectEnabled = true
    private var isInternalChange = false
    
    init {
        // Apply network path input filter
        filters = arrayOf(NetworkPathInputFilter())
        
        // Configure input type
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        maxLines = 1
        
        // Add text watcher for auto-correction
        addTextChangedListener(object : TextWatcher {
            private var beforeText = ""
            private var cursorPosition = 0
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                beforeText = s?.toString() ?: ""
                cursorPosition = selectionStart
            }
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (isInternalChange || !autoCorrectEnabled) {
                    return
                }
                
                val originalText = s?.toString() ?: ""
                val correctedText = autoCorrectPath(originalText)
                
                if (originalText != correctedText) {
                    isInternalChange = true
                    
                    // Replace text
                    s?.replace(0, s.length, correctedText)
                    
                    // Restore cursor position (adjust for length change)
                    val lengthDiff = correctedText.length - originalText.length
                    val newPosition = (cursorPosition + lengthDiff).coerceIn(0, correctedText.length)
                    setSelection(newPosition)
                    
                    Timber.d("Path auto-corrected: '$originalText' -> '$correctedText'")
                    
                    isInternalChange = false
                }
            }
        })
    }
    
    /**
     * Enable or disable auto-correction
     */
    fun setAutoCorrectEnabled(enabled: Boolean) {
        autoCorrectEnabled = enabled
    }
    
    /**
     * Auto-correct network path:
     * - Replace backslashes with forward slashes
     * - Trim leading/trailing spaces
     * - Remove duplicate slashes (except protocol prefix)
     */
    private fun autoCorrectPath(input: String): String {
        var corrected = input
        
        // Trim spaces
        corrected = corrected.trim()
        
        // Replace backslashes with forward slashes
        corrected = corrected.replace('\\', '/')
        
        // Remove duplicate slashes (except after protocol)
        corrected = removeDuplicateSlashes(corrected)
        
        return corrected
    }
    
    /**
     * Remove duplicate slashes while preserving protocol prefix (e.g., smb://)
     */
    private fun removeDuplicateSlashes(path: String): String {
        if (path.isEmpty()) {
            return ""
        }
        
        val result = StringBuilder()
        var previousWasSlash = false
        var isProtocolPrefix = false
        
        for (i in path.indices) {
            val char = path[i]
            
            // Check if we're in protocol prefix (e.g., "smb://", "ftp://")
            if (i < path.length - 2 && path[i] == ':' && path[i + 1] == '/' && path[i + 2] == '/') {
                isProtocolPrefix = true
                result.append("://")
                previousWasSlash = true
                continue
            }
            
            // Skip duplicate slashes (except in protocol prefix)
            if (char == '/') {
                if (!previousWasSlash || isProtocolPrefix) {
                    result.append(char)
                    previousWasSlash = true
                    isProtocolPrefix = false
                }
            } else {
                result.append(char)
                previousWasSlash = false
            }
        }
        
        return result.toString()
    }
    
    /**
     * Get normalized path (with all corrections applied)
     */
    fun getNormalizedPath(): String {
        return autoCorrectPath(text?.toString() ?: "")
    }
    
    /**
     * InputFilter for network path characters
     * Allows: letters, digits, slash, backslash, dash, underscore, dot, colon, space
     */
    private class NetworkPathInputFilter : InputFilter {
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
                    // Allow path characters
                    char.isLetterOrDigit() || char in "/-_.:~@ " -> {
                        filtered.append(char)
                    }
                    // Allow backslash (will be auto-corrected to forward slash)
                    char == '\\' -> {
                        filtered.append(char)
                    }
                    // Ignore other characters
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
}
