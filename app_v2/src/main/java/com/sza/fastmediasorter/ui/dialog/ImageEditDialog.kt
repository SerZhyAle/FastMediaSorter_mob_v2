@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.sza.fastmediasorter.databinding.DialogImageEditBinding
import com.sza.fastmediasorter.domain.usecase.FlipImageUseCase
import com.sza.fastmediasorter.domain.usecase.NetworkImageEditUseCase
import com.sza.fastmediasorter.domain.usecase.RotateImageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Dialog for image editing operations: rotation and flipping
 * Supports both local and network (SMB/SFTP/FTP) files
 */
class ImageEditDialog(
    context: Context,
    private val imagePath: String,
    private val rotateImageUseCase: RotateImageUseCase,
    private val flipImageUseCase: FlipImageUseCase,
    private val networkImageEditUseCase: NetworkImageEditUseCase,
    private val onEditComplete: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogImageEditBinding
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogImageEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            tvImagePath.text = imagePath
            
            // Rotation buttons
            btnRotateLeft.setOnClickListener {
                performRotation(90f)
            }
            
            btnRotateRight.setOnClickListener {
                performRotation(-90f)
            }
            
            // Flip buttons
            btnFlipHorizontal.setOnClickListener {
                performFlip(FlipImageUseCase.FlipDirection.HORIZONTAL)
            }
            
            btnFlipVertical.setOnClickListener {
                performFlip(FlipImageUseCase.FlipDirection.VERTICAL)
            }
            
            btnClose.setOnClickListener {
                dismiss()
            }
        }
    }

    private fun performRotation(angle: Float) {
        showProgress("Rotating image...")
        setButtonsEnabled(false)
        
        GlobalScope.launch {
            // Check if it's a network file
            val isNetworkFile = imagePath.startsWith("smb://") || 
                               imagePath.startsWith("sftp://") || 
                               imagePath.startsWith("ftp://")
            
            val result = if (isNetworkFile) {
                Timber.d("Rotating network image: $imagePath")
                networkImageEditUseCase.rotateImage(imagePath, angle)
            } else {
                Timber.d("Rotating local image: $imagePath")
                rotateImageUseCase.execute(imagePath, angle)
            }
            
            launch(Dispatchers.Main) {
                hideProgress()
                setButtonsEnabled(true)
                
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, "Image rotated successfully", Toast.LENGTH_SHORT).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to rotate image")
                        Toast.makeText(context, "Failed to rotate: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun performFlip(direction: FlipImageUseCase.FlipDirection) {
        val directionText = if (direction == FlipImageUseCase.FlipDirection.HORIZONTAL) "horizontally" else "vertically"
        showProgress("Flipping image $directionText...")
        setButtonsEnabled(false)
        
        GlobalScope.launch {
            // Check if it's a network file
            val isNetworkFile = imagePath.startsWith("smb://") || 
                               imagePath.startsWith("sftp://") || 
                               imagePath.startsWith("ftp://")
            
            val result = if (isNetworkFile) {
                Timber.d("Flipping network image: $imagePath")
                networkImageEditUseCase.flipImage(imagePath, direction)
            } else {
                Timber.d("Flipping local image: $imagePath")
                flipImageUseCase.execute(imagePath, direction)
            }
            
            launch(Dispatchers.Main) {
                hideProgress()
                setButtonsEnabled(true)
                
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, "Image flipped successfully", Toast.LENGTH_SHORT).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to flip image")
                        Toast.makeText(context, "Failed to flip: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.apply {
            btnRotateLeft.isEnabled = enabled
            btnRotateRight.isEnabled = enabled
            btnFlipHorizontal.isEnabled = enabled
            btnFlipVertical.isEnabled = enabled
            btnClose.isEnabled = enabled
        }
    }

    private fun showProgress(message: String) {
        progressDialog = ProgressDialog.show(context, null, message, true, false)
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hideProgress()
    }
}
