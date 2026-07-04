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
        if (isPageSwipeSuppressed()) return
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
            MediaType.OFFICE_DOCUMENT -> {
                // S0301 Phase 05: the Office WebView owns vertical scrolling; a horizontal swipe
                // navigates to the next file like other non-paged documents.
                Timber.tag("TOUCH_ZONE_DEBUG").d("NEXT triggered by: Swipe LEFT (Office)")
                activity.navigationManager.navigateNextFromGesture()
            }
            else -> {
                // Other files: Swipe left = next file
                Timber.tag("TOUCH_ZONE_DEBUG").d("NEXT triggered by: Swipe LEFT (GestureHelper)")
                activity.navigationManager.navigateNextFromGesture()
            }
        }
    }

    override fun onSwipeRight() {
        if (isPageSwipeSuppressed()) return
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
            MediaType.OFFICE_DOCUMENT -> {
                // S0301 Phase 05: horizontal swipe navigates files for the non-paged Office viewer.
                Timber.tag("TOUCH_ZONE_DEBUG").d("PREVIOUS triggered by: Swipe RIGHT (Office)")
                activity.navigationManager.navigatePreviousFromGesture()
            }
            else -> {
                // Other files: Swipe right = previous file
                Timber.tag("TOUCH_ZONE_DEBUG").d("PREVIOUS triggered by: Swipe RIGHT (GestureHelper)")
                activity.navigationManager.navigatePreviousFromGesture()
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

    /**
     * S0927: the left-edge screen-gesture overlay sits over the app and owns edge swipes, so a
     * horizontal page-swipe collides with the capture gesture. When the overlay is enabled we
     * suppress horizontal paging (file / PDF page / EPUB chapter); vertical swipes and taps stay.
     */
    private fun isPageSwipeSuppressed(): Boolean {
        val suppressed = viewModel.settings.value.gestureOverlayEnabled
        return suppressed
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
