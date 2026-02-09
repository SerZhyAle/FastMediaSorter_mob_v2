package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogImageEditBinding
import com.sza.fastmediasorter.domain.usecase.AdjustImageUseCase
import com.sza.fastmediasorter.domain.usecase.ApplyImageFilterUseCase
import com.sza.fastmediasorter.domain.usecase.FlipImageUseCase
import com.sza.fastmediasorter.domain.usecase.NetworkImageEditUseCase
import com.sza.fastmediasorter.domain.usecase.RotateImageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Dialog for image editing operations:
 * - Rotation (90°, 180°, -90°)
 * - Flip (horizontal, vertical)
 * - Filters (grayscale, sepia, negative)
 * - Adjustments (brightness, contrast, saturation)
 * 
 * Supports both local and network (SMB/S/FTP) files
 */
class ImageEditDialog(
    context: Context,
    private val imagePath: String,
    private val rotateImageUseCase: RotateImageUseCase,
    private val flipImageUseCase: FlipImageUseCase,
    private val networkImageEditUseCase: NetworkImageEditUseCase,
    private val applyImageFilterUseCase: ApplyImageFilterUseCase,
    private val adjustImageUseCase: AdjustImageUseCase,
    private val onEditComplete: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogImageEditBinding
    private var progressDialog: MaterialProgressDialog? = null
    
    // Coroutine scope tied to dialog lifecycle
    private val dialogJob = Job()
    private val dialogScope = CoroutineScope(Dispatchers.Main + dialogJob)

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
                performRotation(-90f)  // Rotate counter-clockwise
            }
            
            btnRotate180.setOnClickListener {
                performRotation(180f)
            }
            
            btnRotateRight.setOnClickListener {
                performRotation(90f)  // Rotate clockwise
            }
            
            // Flip buttons
            btnFlipHorizontal.setOnClickListener {
                performFlip(FlipImageUseCase.FlipDirection.HORIZONTAL)
            }
            
            btnFlipVertical.setOnClickListener {
                performFlip(FlipImageUseCase.FlipDirection.VERTICAL)
            }
            
            // Filter buttons
            btnGrayscale.setOnClickListener {
                performFilter(ApplyImageFilterUseCase.FilterType.GRAYSCALE)
            }
            
            btnSepia.setOnClickListener {
                performFilter(ApplyImageFilterUseCase.FilterType.SEPIA)
            }
            
            btnNegative.setOnClickListener {
                performFilter(ApplyImageFilterUseCase.FilterType.NEGATIVE)
            }
            
            // Apply adjustments button
            btnApplyAdjustments.setOnClickListener {
                performAdjustments()
            }
            
            // Close button
            btnClose.setOnClickListener {
                dismiss()
            }
        }
    }
    
    private fun performFilter(filterType: ApplyImageFilterUseCase.FilterType) {
        showProgress("Applying filter...")
        setButtonsEnabled(false)
        
        dialogScope.launch {
            // Check if it's a network file
            val isNetworkFile = imagePath.startsWith("smb://") || 
                               imagePath.startsWith("sftp://") || 
                               imagePath.startsWith("ftp://")
            
            val result = if (isNetworkFile) {
                Timber.d("Applying filter to network image: $imagePath")
                networkImageEditUseCase.applyFilter(imagePath, filterType)
            } else {
                Timber.d("Applying filter to local image: $imagePath")
                applyImageFilterUseCase.execute(imagePath, filterType)
            }
            
            launch(Dispatchers.Main) {
                hideProgress()
                setButtonsEnabled(true)
                
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, R.string.toast_filter_applied, Toast.LENGTH_SHORT).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to apply filter")
                        Toast.makeText(context, context.getString(R.string.toast_filter_failed, error.message), Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
    
    private fun performAdjustments() {
        val brightness = (binding.seekBrightness.progress - 100).toFloat() // -100 to +100
        val contrast = binding.seekContrast.progress / 100f // 0.0 to 2.0
        val saturation = binding.seekSaturation.progress / 100f // 0.0 to 2.0
        
        showProgress("Applying adjustments...")
        setButtonsEnabled(false)
        
        dialogScope.launch {
            // Check if it's a network file
            val isNetworkFile = imagePath.startsWith("smb://") || 
                               imagePath.startsWith("sftp://") || 
                               imagePath.startsWith("ftp://")
            
            val adjustments = AdjustImageUseCase.Adjustments(
                brightness = brightness,
                contrast = contrast,
                saturation = saturation
            )
            val result = if (isNetworkFile) {
                Timber.d("Applying adjustments to network image: $imagePath")
                networkImageEditUseCase.applyAdjustments(imagePath, adjustments)
            } else {
                Timber.d("Applying adjustments to local image: $imagePath")
                adjustImageUseCase.execute(imagePath, adjustments)
            }
            
            launch(Dispatchers.Main) {
                hideProgress()
                setButtonsEnabled(true)
                
                result.fold(
                    onSuccess = {
                        Toast.makeText(context, R.string.toast_adjustments_applied, Toast.LENGTH_SHORT).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to apply adjustments")
                        Toast.makeText(context, context.getString(R.string.toast_adjustments_failed, error.message), Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun performRotation(angle: Float) {
        showProgress("Rotating image...")
        setButtonsEnabled(false)
        
        dialogScope.launch {
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
                        Toast.makeText(context, R.string.toast_image_rotated, Toast.LENGTH_SHORT).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to rotate image")
                        Toast.makeText(context, context.getString(R.string.toast_rotate_failed, error.message), Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun performFlip(direction: FlipImageUseCase.FlipDirection) {
        val directionText = if (direction == FlipImageUseCase.FlipDirection.HORIZONTAL) "horizontally" else "vertically"
        showProgress("Flipping image $directionText...")
        setButtonsEnabled(false)
        
        dialogScope.launch {
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
                        Toast.makeText(context, R.string.toast_image_flipped, Toast.LENGTH_SHORT).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to flip image")
                        Toast.makeText(context, context.getString(R.string.toast_flip_failed, error.message), Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.apply {
            btnRotateLeft.isEnabled = enabled
            btnRotate180.isEnabled = enabled
            btnRotateRight.isEnabled = enabled
            btnFlipHorizontal.isEnabled = enabled
            btnFlipVertical.isEnabled = enabled
            btnGrayscale.isEnabled = enabled
            btnSepia.isEnabled = enabled
            btnNegative.isEnabled = enabled
            btnApplyAdjustments.isEnabled = enabled
            seekBrightness.isEnabled = enabled
            seekContrast.isEnabled = enabled
            seekSaturation.isEnabled = enabled
            btnClose.isEnabled = enabled
        }
    }

    private fun showProgress(message: String) {
        progressDialog = MaterialProgressDialog.show(context, null, message, true, false)
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dialogJob.cancel() // Cancel all running coroutines
        hideProgress()
    }
}
