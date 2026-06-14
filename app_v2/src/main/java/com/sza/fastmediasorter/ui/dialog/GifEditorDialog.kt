package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogGifEditorBinding
import com.sza.fastmediasorter.domain.usecase.ChangeGifSpeedUseCase
import com.sza.fastmediasorter.domain.usecase.ExtractGifFramesUseCase
import com.sza.fastmediasorter.domain.usecase.SaveGifFirstFrameUseCase
import com.sza.fastmediasorter.ui.dialog.helpers.GifEditorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Dialog for GIF editing operations:
 * - Extract all frames to PNG files
 * - Change animation speed
 * - Save first frame as static image
 * 
 * All operations save to Downloads folder
 * Only works for local GIF files
 */
class GifEditorDialog(
    context: Context,
    private val gifPath: String,
    private val extractFramesUseCase: ExtractGifFramesUseCase,
    private val saveFirstFrameUseCase: SaveGifFirstFrameUseCase,
    private val changeSpeedUseCase: ChangeGifSpeedUseCase,
    private val downloadNetworkFileUseCase: com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase,
    private val onEditComplete: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogGifEditorBinding
    private var progressDialog: MaterialProgressDialog? = null
    private var currentSpeedMultiplier = 1.0f
    private var extractFramesJob: Job? = null
    private val isGifInput: Boolean = gifPath.lowercase().endsWith(".gif")
    
    // Coroutine scope tied to dialog lifecycle
    private val dialogJob = Job()
    private val dialogScope = CoroutineScope(Dispatchers.Main + dialogJob)
    
    // Network file handling delegated to helper
    private val gifHelper = GifEditorHelper(context, downloadNetworkFileUseCase)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogGifEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.apply {
            tvGifPath.text = gifPath
            
            // Close button
            btnClose.setOnClickListener { dismiss() }
            
            // Extract frames button + help
            val extractionSupported = extractFramesUseCase.isExtractionSupported(gifPath)
            btnExtractFrames.visibility = if (extractionSupported) android.view.View.VISIBLE else android.view.View.GONE
            ivHelpExtractFrames.visibility = if (extractionSupported) android.view.View.VISIBLE else android.view.View.GONE
            btnExtractFrames.setOnClickListener { performExtractFrames() }
            ivHelpExtractFrames.setOnClickListener { showHelpDialog(context.getString(R.string.gif_edit_extract_help)) }

            // Speed and first-frame operations are GIF-only
            val gifOnlyVisibility = if (isGifInput) android.view.View.VISIBLE else android.view.View.GONE
            tvSpeedSectionTitle.visibility = gifOnlyVisibility
            tvSpeedValue.visibility = gifOnlyVisibility
            layoutSpeedSeekRow.visibility = gifOnlyVisibility
            layoutSpeedApplyRow.visibility = gifOnlyVisibility
            tvFirstFrameSectionTitle.visibility = gifOnlyVisibility
            layoutFirstFrameRow.visibility = gifOnlyVisibility
            
            // Speed control
            seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    // Map 0-100 to 0.25x-4.0x (logarithmic scale for better control)
                    // 0 = 0.25x, 50 = 1.0x, 100 = 4.0x
                    currentSpeedMultiplier = when {
                        progress < 50 -> {
                            // 0-50 maps to 0.25x-1.0x
                            0.25f + (progress / 50f) * 0.75f
                        }
                        progress == 50 -> 1.0f
                        else -> {
                            // 50-100 maps to 1.0x-4.0x
                            1.0f + ((progress - 50) / 50f) * 3.0f
                        }
                    }
                    
                    updateSpeedLabel()
                }
                
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            
            btnApplySpeed.setOnClickListener { performChangeSpeed() }
            ivHelpSpeed.setOnClickListener { showHelpDialog(context.getString(R.string.gif_edit_speed_help)) }
            
            // First frame button + help
            btnFirstFrameToImage.setOnClickListener { performSaveFirstFrame() }
            ivHelpFirstFrame.setOnClickListener { showHelpDialog(context.getString(R.string.gif_edit_first_frame_help)) }
            
            updateSpeedLabel()
        }
    }
    
    private fun updateSpeedLabel() {
        binding.tvSpeedValue.text = if (currentSpeedMultiplier == 1.0f) {
            context.getString(R.string.gif_edit_speed_normal)
        } else {
            context.getString(R.string.gif_edit_speed_value, currentSpeedMultiplier)
        }
    }
    
    private fun showHelpDialog(message: String) {
        AlertDialog.Builder(context)
            .setTitle(R.string.help)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
    
    private fun performExtractFrames() {
        if (!extractFramesUseCase.isExtractionSupported(gifPath)) {
            Toast.makeText(context, context.getString(R.string.msg_gif_extract_api_unavailable), Toast.LENGTH_LONG).show()
            return
        }

        val isNetwork = gifHelper.isNetworkPath(gifPath)
        showProgress(
            gifHelper.getPreparingMessage(com.sza.fastmediasorter.ui.dialog.helpers.GifOperation.EXTRACT_FRAMES, isNetwork),
            cancelable = true,
            onCancel = {
                extractFramesJob?.cancel()
            }
        )
        setButtonsEnabled(false)

        extractFramesJob = dialogScope.launch {
            try {
                // Prepare file (download if network)
                val localPath = gifHelper.prepareGifFile(gifPath) { _ ->
                    // Update progress if needed
                }

                if (localPath == null) {
                    Toast.makeText(context, context.getString(R.string.msg_gif_prepare_failed), Toast.LENGTH_LONG).show()
                    return@launch
                }

                val targetOutputDirectory = if (isNetwork) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                } else {
                    File(localPath).parentFile?.absolutePath
                }

                val result = extractFramesUseCase.execute(
                    animatedPath = localPath,
                    targetDirectoryPath = targetOutputDirectory,
                    isActive = { this.isActive }
                )

                result.fold(
                    onSuccess = { frameCount ->
                        Toast.makeText(
                            context,
                            gifHelper.getSuccessMessage(com.sza.fastmediasorter.ui.dialog.helpers.GifOperation.EXTRACT_FRAMES, isNetwork, frameCount),
                            Toast.LENGTH_LONG
                        ).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        if (error is kotlinx.coroutines.CancellationException) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.msg_operation_cancelled),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@fold
                        }
                        Timber.e(error, "Failed to extract frames")
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_gif_extract_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                Toast.makeText(
                    context,
                    context.getString(R.string.msg_operation_cancelled),
                    Toast.LENGTH_SHORT
                ).show()
                throw e
            } finally {
                hideProgress()
                setButtonsEnabled(true)
            }
        }
    }
    
    private fun performChangeSpeed() {
        if (currentSpeedMultiplier == 1.0f) {
            Toast.makeText(context, context.getString(R.string.msg_gif_speed_already_normal), Toast.LENGTH_SHORT).show()
            return
        }
        
        val isNetwork = gifHelper.isNetworkPath(gifPath)
        showProgress(gifHelper.getPreparingMessage(com.sza.fastmediasorter.ui.dialog.helpers.GifOperation.CHANGE_SPEED, isNetwork))
        setButtonsEnabled(false)
        
        dialogScope.launch {
            // Prepare file (download if network)
            val localPath = gifHelper.prepareGifFile(gifPath) { _ ->
                // Update progress if needed
            }
            
            if (localPath == null) {
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    hideProgress()
                    setButtonsEnabled(true)
                    Toast.makeText(context, context.getString(R.string.msg_gif_prepare_failed), Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            
            val result = changeSpeedUseCase.execute(
                gifPath = localPath,
                speedMultiplier = currentSpeedMultiplier,
                saveToDownloads = isNetwork
            )
            
            launch(kotlinx.coroutines.Dispatchers.Main) {
                hideProgress()
                setButtonsEnabled(true)
                
                result.fold(
                    onSuccess = { _ ->
                        Toast.makeText(
                            context,
                            gifHelper.getSuccessMessage(com.sza.fastmediasorter.ui.dialog.helpers.GifOperation.CHANGE_SPEED, isNetwork, currentSpeedMultiplier),
                            Toast.LENGTH_LONG
                        ).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to change speed")
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_gif_speed_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }
    
    private fun performSaveFirstFrame() {
        val isNetwork = gifHelper.isNetworkPath(gifPath)
        showProgress(gifHelper.getPreparingMessage(com.sza.fastmediasorter.ui.dialog.helpers.GifOperation.SAVE_FIRST_FRAME, isNetwork))
        setButtonsEnabled(false)
        
        dialogScope.launch {
            // Prepare file (download if network)
            val localPath = gifHelper.prepareGifFile(gifPath) { _ ->
                // Update progress if needed
            }
            
            if (localPath == null) {
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    hideProgress()
                    setButtonsEnabled(true)
                    Toast.makeText(context, context.getString(R.string.msg_gif_prepare_failed), Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            
            val result = saveFirstFrameUseCase.execute(localPath)
            
            launch(kotlinx.coroutines.Dispatchers.Main) {
                hideProgress()
                setButtonsEnabled(true)
                
                result.fold(
                    onSuccess = { _ ->
                        Toast.makeText(
                            context,
                            gifHelper.getSuccessMessage(com.sza.fastmediasorter.ui.dialog.helpers.GifOperation.SAVE_FIRST_FRAME, isNetwork),
                            Toast.LENGTH_LONG
                        ).show()
                        onEditComplete()
                        dismiss()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to save first frame")
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_gif_first_frame_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }
    
    private fun setButtonsEnabled(enabled: Boolean) {
        binding.apply {
            btnExtractFrames.isEnabled = enabled
            btnApplySpeed.isEnabled = enabled
            btnFirstFrameToImage.isEnabled = enabled
            seekSpeed.isEnabled = enabled
            btnClose.isEnabled = enabled
        }
    }
    
    private fun showProgress(
        message: String,
        cancelable: Boolean = false,
        onCancel: (() -> Unit)? = null
    ) {
        progressDialog = MaterialProgressDialog.show(context, null, message, true, cancelable).apply {
            if (cancelable && onCancel != null) {
                setOnCancelListener { onCancel.invoke() }
            }
        }
    }
    
    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        extractFramesJob?.cancel()
        dialogJob.cancel() // Cancel all running coroutines
        hideProgress()
        
        // Cleanup temp files managed by helper
        gifHelper.cleanup()
    }
}
