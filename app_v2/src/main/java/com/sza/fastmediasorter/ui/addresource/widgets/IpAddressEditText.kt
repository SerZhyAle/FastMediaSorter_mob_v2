package com.sza.fastmediasorter.ui.addresource.widgets

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.ui.common.IpAddressInputFilter
import timber.log.Timber

/**
 * Custom EditText for IP address input with validation highlighting.
 * - Red highlight for invalid IP addresses
 * - Automatic character filtering (digits, dots, letters, dash, underscore)
 * - Auto-correction: comma/space → dot
 */
class IpAddressEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    private var validationEnabled = true
    
    init {
        // Apply IP address input filter
        filters = arrayOf(IpAddressInputFilter())
        
        // Configure input type
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        maxLines = 1
        
        // Add text watcher for validation
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (validationEnabled) {
                    validateAndHighlight(s?.toString())
                }
            }
        })
    }
    
    /**
     * Enable or disable validation highlighting
     */
    fun setValidationEnabled(enabled: Boolean) {
        validationEnabled = enabled
        if (!enabled) {
            clearValidationHighlight()
        } else {
            validateAndHighlight(text?.toString())
        }
    }
    
    /**
     * Validate input and apply red highlight if invalid
     */
    private fun validateAndHighlight(input: String?) {
        if (input.isNullOrEmpty()) {
            clearValidationHighlight()
            return
        }
        
        val isValid = isValidIpOrHostname(input)
        
        if (isValid) {
            clearValidationHighlight()
        } else {
            applyErrorHighlight()
        }
        
        Timber.d("IP validation: '$input' -> ${if (isValid) "valid" else "invalid"}")
    }
    
    /**
     * Check if input is a valid IP address or hostname
     */
    private fun isValidIpOrHostname(input: String): Boolean {
        // Check for valid IPv4 address
        if (isValidIpv4(input)) {
            return true
        }
        
        // Check for valid hostname
        if (isValidHostname(input)) {
            return true
        }
        
        return false
    }
    
    /**
     * Validate IPv4 address (e.g., 192.168.1.1)
     */
    private fun isValidIpv4(input: String): Boolean {
        val parts = input.split(".")
        
        // IPv4 must have exactly 4 octets
        if (parts.size != 4) {
            return false
        }
        
        // Each octet must be 0-255
        return parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } ?: false
        }
    }
    
    /**
     * Validate hostname (e.g., server01, nas-storage)
     * Allows: letters, digits, dash, underscore, dots
     * Must not start/end with dash or dot
     */
    private fun isValidHostname(input: String): Boolean {
        if (input.isEmpty() || input.length > 253) {
            return false
        }
        
        // Cannot start/end with dash or dot
        if (input.startsWith("-") || input.endsWith("-") || 
            input.startsWith(".") || input.endsWith(".")) {
            return false
        }
        
        // Hostname pattern: letters, digits, dash, underscore, dots
        val hostnamePattern = Regex("^[a-zA-Z0-9._-]+$")
        if (!hostnamePattern.matches(input)) {
            return false
        }
        
        // Each label (between dots) should be valid
        val labels = input.split(".")
        return labels.all { label ->
            label.isNotEmpty() && 
            label.length <= 63 && 
            !label.startsWith("-") && 
            !label.endsWith("-")
        }
    }
    
    /**
     * Apply red background tint for invalid input
     */
    private fun applyErrorHighlight() {
        setBackgroundColor(Color.parseColor("#33FF0000")) // 20% opacity red
    }
    
    /**
     * Clear validation highlight
     */
    private fun clearValidationHighlight() {
        setBackgroundColor(Color.TRANSPARENT)
    }
    
    /**
     * Get current validation state
     */
    fun isValid(): Boolean {
        return isValidIpOrHostname(text?.toString() ?: "")
    }
}
