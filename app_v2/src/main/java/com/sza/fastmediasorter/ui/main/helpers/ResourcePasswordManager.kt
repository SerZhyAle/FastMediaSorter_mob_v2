package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.editresource.EditResourceActivity
import timber.log.Timber

/**
 * Handles PIN protection dialogs for resource access and editing.
 * Validates entered PINs and triggers appropriate actions on success.
 * 
 * Responsibilities:
 * - Show PIN dialog for Browse/Slideshow access
 * - Show PIN dialog before editing resource
 * - Validate PIN and show error if incorrect
 * - Auto-focus password field and show keyboard
 */
class ResourcePasswordManager(
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {
    
    /**
     * Check PIN before accessing resource (Browse or Slideshow).
     * 
     * @param resource The resource to access
     * @param forSlideshow True if starting slideshow, false for browse
     * @param onPasswordValidated Callback when PIN is correct, receives resourceId and forSlideshow flag
     */
    fun checkResourcePassword(
        resource: MediaResource,
        forSlideshow: Boolean,
        onPasswordValidated: (resourceId: Long, forSlideshow: Boolean) -> Unit
    ) {
        showPinDialog(
            title = resource.name,
            correctPin = resource.accessPin ?: "",
            onSuccess = {
                Timber.d("PIN validated for resource: ${resource.name}, forSlideshow=$forSlideshow")
                onPasswordValidated(resource.id, forSlideshow)
            }
        )
    }
    
    /**
     * Check PIN before editing resource.
     * Opens EditResourceActivity on success.
     * 
     * @param resource The resource to edit
     */
    fun checkResourcePinForEdit(resource: MediaResource) {
        showPinDialog(
            title = resource.name,
            correctPin = resource.accessPin ?: "",
            onSuccess = {
                Timber.d("PIN validated for editing resource: ${resource.name}")
                val intent = Intent(context, EditResourceActivity::class.java).apply {
                    putExtra("resourceId", resource.id)
                }
                context.startActivity(intent)
            }
        )
    }
    
    /**
     * Generic PIN dialog with validation.
     * 
     * @param title Dialog title (usually resource name)
     * @param correctPin Expected PIN
     * @param onSuccess Callback when PIN is correct
     */
    private fun showPinDialog(
        title: String,
        correctPin: String,
        onSuccess: () -> Unit
    ) {
        // Inflate dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_access_password, null)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etPassword)
        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.tilPassword)
        
        // Create dialog
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null) // Set to null to override click
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        
        // Setup dialog behavior
        dialog.setOnShowListener {
            val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                val enteredPin = etPassword.text.toString()
                
                if (enteredPin == correctPin) {
                    // PIN correct - dismiss and proceed
                    dialog.dismiss()
                    onSuccess()
                } else {
                    // PIN incorrect - show error
                    tilPassword.error = context.getString(R.string.pin_incorrect)
                    Timber.w("Incorrect PIN entered for: $title")
                }
            }
            
            // Show keyboard and focus on password field
            etPassword.requestFocus()
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        }
        
        dialog.show()
    }
}
