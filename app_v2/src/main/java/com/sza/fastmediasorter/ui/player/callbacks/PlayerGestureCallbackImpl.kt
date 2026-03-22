package com.sza.fastmediasorter.ui.player.callbacks

import android.view.View
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerGestureHelper
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.github.chrisbanes.photoview.PhotoView
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel

import com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
import com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
import timber.log.Timber

class PlayerGestureCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val binding: ActivityPlayerUnifiedBinding,
    private val pdfViewerManagerProvider: () -> PdfViewerManager,
    private val epubViewerManagerProvider: () -> EpubViewerManager
) : PlayerGestureHelper.GestureCallback {

    override fun onSwipeLeft() {
        val currentFile = viewModel.state.value.currentFile
        when (currentFile?.type) {
            MediaType.PDF -> {
                // PDF: Swipe left = next page
                pdfViewerManagerProvider().showNextPage()
            }
            MediaType.EPUB -> {
                // EPUB: Swipe left = next chapter
                epubViewerManagerProvider().showNextChapter()
            }
            else -> {
                // Other files: Swipe left = next file
                Timber.tag("TOUCH_ZONE_DEBUG").d("NEXT triggered by: Swipe LEFT (GestureHelper)")
                viewModel.nextFile()
            }
        }
    }

    override fun onSwipeRight() {
        val currentFile = viewModel.state.value.currentFile
        when (currentFile?.type) {
            MediaType.PDF -> {
                // PDF: Swipe right = previous page
                pdfViewerManagerProvider().showPreviousPage()
            }
            MediaType.EPUB -> {
                // EPUB: Swipe right = previous chapter
                epubViewerManagerProvider().showPreviousChapter()
            }
            else -> {
                // Other files: Swipe right = previous file
                Timber.tag("TOUCH_ZONE_DEBUG").d("PREVIOUS triggered by: Swipe RIGHT (GestureHelper)")
                viewModel.previousFile()
            }
        }
    }

    override fun onSwipeUp() {
        val currentFile = viewModel.state.value.currentFile
        if (currentFile?.type == MediaType.PDF) {
            // PDF: Swipe up = zoom out (decrease scale)
            val currentScale = binding.photoView.scale
            if (currentScale > binding.photoView.minimumScale) {
                binding.photoView.setScale(currentScale - 0.5f, true)
            }
        } else {
            if (!viewModel.state.value.showCommandPanel) {
                // Already in fullscreen, do nothing
            } else {
                viewModel.enterFullscreenMode()
            }
        }
    }

    override fun onSwipeDown() {
        val currentFile = viewModel.state.value.currentFile
        if (currentFile?.type == MediaType.PDF) {
            // PDF: Swipe down = zoom in (increase scale)
            val currentScale = binding.photoView.scale
            if (currentScale < binding.photoView.maximumScale) {
                binding.photoView.setScale(currentScale + 0.5f, true)
            }
        } else {
            if (viewModel.state.value.showCommandPanel) {
                // Already in command panel mode, do nothing
            } else {
                viewModel.enterCommandPanelMode()
            }
        }
    }

    override fun onDoubleTap() {
        viewModel.togglePause()
        if (viewModel.state.value.currentFile?.type == MediaType.IMAGE) {
            activity.updateSlideShow()
        }
    }

    override fun onLongPress() {
        // Long press could show quick actions menu
        activity.showFileInfo()
    }

    override fun onTouchZone(zone: PlayerGestureHelper.TouchZone) {
        activity.navigationManager.handleTouchZoneNavigation(zone)
    }
    
    fun setPhotoViewZoom(scale: Float) {
        val currentFile = viewModel.state.value.currentFile
        val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
        val currentScale = binding.photoView.scale
        
        Timber.w("╔═══════════════════════════════════════════════════════════════╗")
        Timber.w("║ ZOOM REQUEST: ${scale}x                                       ")
        Timber.w("╚═══════════════════════════════════════════════════════════════╝")
        Timber.w("ZOOM: isImage=$isImage, PhotoView.visible=${binding.photoView.visibility == View.VISIBLE}")
        Timber.w("ZOOM: currentScale=${"%.2f".format(currentScale)}x → targetScale=${scale}x")
        
        if (isImage && binding.photoView.visibility == View.VISIBLE) {
            Timber.w("ZOOM: ✓ Applying zoom to PhotoView with animation")
            binding.photoView.setScale(scale, true)
        } else {
            Timber.w("ZOOM: ✗ Cannot set zoom - not an image or PhotoView not visible")
        }
    }
}
