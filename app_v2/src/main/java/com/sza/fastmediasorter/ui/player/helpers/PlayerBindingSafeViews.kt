package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.view.ViewStub
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.cardview.widget.CardView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.google.android.material.button.MaterialButton
import com.github.chrisbanes.photoview.PhotoView

class PlayerBindingSafeViews(
    private val binding: ActivityPlayerUnifiedBinding
) {
    private fun <T : View> required(@IdRes id: Int): T {
        return binding.root.findViewById(id)
            ?: error("Required view not found: id=$id")
    }

    private fun <T : View> required(view: T?, @IdRes id: Int): T {
        return view ?: binding.root.findViewById(id)
        ?: error("Required view not found: id=$id")
    }

    private fun ensureLyricsInflated() {
        val exists = binding.root.findViewById<View>(R.id.lyricsViewerContainer)
        if (exists != null) return
        binding.root.findViewById<ViewStub>(R.id.lyricsViewerStub)?.inflate()
    }

    val btnRenameCmd: ImageButton get() = required(binding.btnRenameCmd, R.id.btnRenameCmd)
    val btnOverflowMenu: ImageButton get() = required(binding.btnOverflowMenu, R.id.btnOverflowMenu)
    val btnEditCmd: ImageButton get() = required(binding.btnEditCmd, R.id.btnEditCmd)
    val btnSaveFrameCmd: ImageButton get() = required(R.id.btnSaveFrameCmd)
    val btn3dVrCmd: ImageButton get() = required(R.id.btn3dVrCmd)
    val btnPrintCmd: ImageButton get() = required(R.id.btnPrintCmd)
    // S0217: inline accessors for image-edit commands (root-lookup — added to XML post-binding)
    val btnOpenInSeparateWindowCmd: ImageButton get() = required(R.id.btnOpenInSeparateWindowCmd)
    val btnCropCmd: ImageButton get() = required(R.id.btnCropCmd)
    val btnCropToFileCmd: ImageButton get() = required(R.id.btnCropToFileCmd)
    val btnCompressCopyCmd: ImageButton get() = required(R.id.btnCompressCopyCmd)
    val btnDrawOverlayCmd: ImageButton get() = required(R.id.btnDrawOverlayCmd)
    val btnUndoCmd: ImageButton get() = required(binding.btnUndoCmd, R.id.btnUndoCmd)
    val btnLyricsCmd: ImageButton get() = required(binding.btnLyricsCmd, R.id.btnLyricsCmd)
    val btnSearchYoutubeMusicCmd: ImageButton get() = required(binding.btnSearchYoutubeMusicCmd, R.id.btnSearchYoutubeMusicCmd)
    val btnRotationToggleCmd: ImageButton get() = required(R.id.btnRotationToggleCmd)
    val btnCastCmd: ImageButton get() = required(R.id.btnCastCmd)

    val btnGoogleLensPdfCmd: ImageButton get() = required(binding.btnGoogleLensPdfCmd, R.id.btnGoogleLensPdfCmd)
    val btnPdfThumbnailsCmd: ImageButton get() = required(binding.btnPdfThumbnailsCmd, R.id.btnPdfThumbnailsCmd)
    val btnOcrPdfCmd: TextView get() = required(binding.btnOcrPdfCmd, R.id.btnOcrPdfCmd)
    val btnTranslatePdfCmd: ImageButton get() = required(binding.btnTranslatePdfCmd, R.id.btnTranslatePdfCmd)
    val btnSearchPdfCmd: ImageButton get() = required(binding.btnSearchPdfCmd, R.id.btnSearchPdfCmd)
    val btnPdfTextSettingsCmd: ImageButton get() = required(binding.btnPdfTextSettingsCmd, R.id.btnPdfTextSettingsCmd)

    val btnSearchTextCmd: ImageButton get() = required(binding.btnSearchTextCmd, R.id.btnSearchTextCmd)
    val btnEditTextCmd: ImageButton get() = required(binding.btnEditTextCmd, R.id.btnEditTextCmd)
    val btnTranslateTextCmd: ImageButton get() = required(binding.btnTranslateTextCmd, R.id.btnTranslateTextCmd)
    val btnTextSettingsCmd: ImageButton get() = required(binding.btnTextSettingsCmd, R.id.btnTextSettingsCmd)
    val btnCopyTextCmd: ImageButton get() = required(binding.btnCopyTextCmd, R.id.btnCopyTextCmd)

    val btnSearchEpubCmd: ImageButton get() = required(binding.btnSearchEpubCmd, R.id.btnSearchEpubCmd)
    val btnTranslateEpubCmd: ImageButton get() = required(binding.btnTranslateEpubCmd, R.id.btnTranslateEpubCmd)
    val btnEpubTextSettingsCmd: ImageButton get() = required(binding.btnEpubTextSettingsCmd, R.id.btnEpubTextSettingsCmd)
    val btnOcrEpubCmd: ImageButton get() = required(binding.btnOcrEpubCmd, R.id.btnOcrEpubCmd)

    val btnTranslateImageCmd: ImageButton get() = required(binding.btnTranslateImageCmd, R.id.btnTranslateImageCmd)
    val btnImageTextSettingsCmd: ImageButton get() = required(binding.btnImageTextSettingsCmd, R.id.btnImageTextSettingsCmd)
    val btnOcrImageCmd: TextView get() = required(binding.btnOcrImageCmd, R.id.btnOcrImageCmd)
    val btnGoogleLensImageCmd: ImageButton get() = required(binding.btnGoogleLensImageCmd, R.id.btnGoogleLensImageCmd)

    val btnTranslateImage: ImageButton get() = required(R.id.btnTranslateImage)
    val btnGoogleLensImage: ImageButton get() = required(R.id.btnGoogleLensImage)
    val btnOcrImage: TextView get() = required(R.id.btnOcrImage)
    val pdfFullscreenOverlay: FrameLayout?
        get() = binding.root.findViewById(R.id.pdfFullscreenOverlay)
    val pdfFullscreenPhotoView: PhotoView?
        get() = binding.root.findViewById(R.id.pdfFullscreenPhotoView)
    val btnExitPdfFullscreen: ImageButton?
        get() = binding.root.findViewById(R.id.btnExitPdfFullscreen)

    val audioMetadata: TextView get() = required(R.id.audioMetadata)
    val audioFileName: TextView get() = required(R.id.audioFileName)
    val audioFileInfo: TextView get() = required(R.id.audioFileInfo)
    val tvBackgroundMusicTrack: TextView get() = required(R.id.tvBackgroundMusicTrack)
    val tvCountdown: TextView get() = required(R.id.tvCountdown)

    val btnCloseLyricsViewer: ImageButton
        get() {
            ensureLyricsInflated()
            return required(R.id.btnCloseLyricsViewer)
        }

    val btnTranslateLyrics: ImageButton
        get() {
            ensureLyricsInflated()
            return required(R.id.btnTranslateLyrics)
        }

    val tvLyricsContent: TextView
        get() {
            ensureLyricsInflated()
            return required(R.id.tvLyricsContent)
        }

    val lyricsViewerContainer: FrameLayout
        get() {
            ensureLyricsInflated()
            return required(R.id.lyricsViewerContainer)
        }

    val searchPanel: LinearLayout get() = required(R.id.searchPanel)
    val etSearchQuery: EditText get() = required(R.id.etSearchQuery)
    val tvSearchCounter: TextView get() = required(R.id.tvSearchCounter)
    val btnSearchPrev: ImageButton get() = required(R.id.btnSearchPrev)
    val btnSearchNext: ImageButton get() = required(R.id.btnSearchNext)
    val btnCloseSearch: ImageButton get() = required(R.id.btnCloseSearch)

    val textViewerContainer: FrameLayout get() = required(R.id.textViewerContainer)
    val btnCloseTextViewer: ImageButton get() = required(R.id.btnCloseTextViewer)
    val textScrollView: ScrollView get() = required(R.id.textScrollView)
    val tvTextContent: TextView get() = required(R.id.tvTextContent)
    val textEditContainer: LinearLayout get() = required(R.id.textEditContainer)
    val etTextContent: EditText get() = required(R.id.etTextContent)
    // S0189: scroll container that hosts etTextContent in edit mode
    val textEditScrollView: ScrollView get() = required(R.id.textEditScrollView)
    // S0189: 5-action editor buttons. Now live inside [editorToolbar] (top bar) instead of the
    // former bottom action panel — that panel was removed because the soft keyboard covered it
    // during edit. The dirty-state tint is applied to [editorToolbar] itself via actionPanelManager.
    val btnEditorSave: ImageButton get() = required(R.id.btnEditorSave)
    val btnEditorSaveClose: ImageButton get() = required(R.id.btnEditorSaveClose)
    val btnEditorSaveSend: ImageButton get() = required(R.id.btnEditorSaveSend)
    val btnEditorSendKeep: ImageButton get() = required(R.id.btnEditorSendKeep)
    val btnEditorCancel: ImageButton get() = required(R.id.btnEditorCancel)

    // Page navigation
    val textPageNavigation: LinearLayout get() = required(R.id.textPageNavigation)
    val btnTextPagePrev: ImageButton get() = required(R.id.btnTextPagePrev)
    val btnTextPageNext: ImageButton get() = required(R.id.btnTextPageNext)
    val tvTextPageIndicator: TextView get() = required(R.id.tvTextPageIndicator)
    val tvTextEncodingIndicator: TextView get() = required(R.id.tvTextEncodingIndicator)

    // Editor toolbar
    val editorToolbar: LinearLayout get() = required(R.id.editorToolbar)
    val btnUndo: ImageButton get() = required(R.id.btnUndo)
    val btnRedo: ImageButton get() = required(R.id.btnRedo)
    val btnEditorFind: ImageButton get() = required(R.id.btnEditorFind)
    val btnEditorFindReplace: ImageButton get() = required(R.id.btnEditorFindReplace)
    val tvEditorCursorPos: TextView get() = required(R.id.tvEditorCursorPos)

    // Find & Replace panel
    val textFindReplacePanel: LinearLayout get() = required(R.id.textFindReplacePanel)
    val etFindQuery: EditText get() = required(R.id.etFindQuery)
    val tvFindCounter: TextView get() = required(R.id.tvFindCounter)
    val btnFindPrev: ImageButton get() = required(R.id.btnFindPrev)
    val btnFindNext: ImageButton get() = required(R.id.btnFindNext)
    val btnFindClose: ImageButton get() = required(R.id.btnFindClose)
    val replaceRow: LinearLayout get() = required(R.id.replaceRow)
    val etReplaceQuery: EditText get() = required(R.id.etReplaceQuery)
    val btnReplace: MaterialButton get() = required(R.id.btnReplace)
    val btnReplaceAll: MaterialButton get() = required(R.id.btnReplaceAll)

    val pdfScrollRecyclerView: androidx.recyclerview.widget.RecyclerView
        get() = required(R.id.pdfScrollRecyclerView)
    val pdfControlsLayout: LinearLayout get() = required(R.id.pdfControlsLayout)
    val epubControlsLayout: LinearLayout get() = required(R.id.epubControlsLayout)
    val translationOverlay: CardView get() = required(R.id.translationOverlay)
    val translationOverlayBackground: View get() = required(R.id.translationOverlayBackground)
    val btnCloseTranslation: ImageButton get() = required(R.id.btnCloseTranslation)
    val translationScrollView: ScrollView get() = required(R.id.translationScrollView)
    val tvTranslatedText: TextView get() = required(R.id.tvTranslatedText)
    val touchZonesOverlay: LinearLayout get() = required(R.id.touchZonesOverlay)
    val touchZones3Overlay: LinearLayout get() = required(R.id.touchZones3Overlay)
    val touchZonePrevious: View get() = required(R.id.touchZonePrevious)
    val touchZoneNext: View get() = required(R.id.touchZoneNext)
    val touchZone3Previous: View get() = required(R.id.touchZone3Previous)
    val touchZone3Gestures: View get() = required(R.id.touchZone3Gestures)
    val touchZone3Next: View get() = required(R.id.touchZone3Next)
    val touchZonesOverlayNew: View get() = required(R.id.touchZonesOverlayNew)
    val btnDocumentFullscreenExit: ImageButton get() = required(R.id.btnDocumentFullscreenExit)
    val bottomPanelsContainer: LinearLayout get() = required(R.id.bottomPanelsContainer)
    val copyToPanel: LinearLayout get() = required(R.id.copyToPanel)
    val moveToPanel: LinearLayout get() = required(R.id.moveToPanel)
    val copyToPanelHeader: LinearLayout get() = required(R.id.copyToPanelHeader)
    val moveToPanelHeader: LinearLayout get() = required(R.id.moveToPanelHeader)
    val copyToPanelIndicator: TextView get() = required(R.id.copyToPanelIndicator)
    val moveToPanelIndicator: TextView get() = required(R.id.moveToPanelIndicator)
    val copyToButtonsGrid: GridLayout get() = required(R.id.copyToButtonsGrid)
    val moveToButtonsGrid: GridLayout get() = required(R.id.moveToButtonsGrid)
    val btnTranslationFontDecrease: ImageButton?
        get() = binding.root.findViewById(R.id.btnTranslationFontDecrease)
    val btnTranslationFontIncrease: ImageButton?
        get() = binding.root.findViewById(R.id.btnTranslationFontIncrease)
    val btnSelectTextPdf: TextView?
        get() = binding.root.findViewById(R.id.btnSelectTextPdf)
    val audioTouchZonesOverlay: GridLayout get() = required(R.id.audioTouchZonesOverlay)
    val firstRunHintOverlay: FrameLayout get() = required(R.id.firstRunHintOverlay)
    val tvFirstRunHintText: TextView get() = required(R.id.tvFirstRunHintText)
}

private fun <T : View> ActivityPlayerUnifiedBinding.requiredFromRoot(@IdRes id: Int): T {
    return root.findViewById(id) ?: error("Required view not found: id=$id")
}

val ActivityPlayerUnifiedBinding.searchPanel: LinearLayout
    get() = requiredFromRoot(R.id.searchPanel)

val ActivityPlayerUnifiedBinding.etSearchQuery: EditText
    get() = requiredFromRoot(R.id.etSearchQuery)

val ActivityPlayerUnifiedBinding.tvSearchCounter: TextView
    get() = requiredFromRoot(R.id.tvSearchCounter)

val ActivityPlayerUnifiedBinding.btnSearchPrev: ImageButton
    get() = requiredFromRoot(R.id.btnSearchPrev)

val ActivityPlayerUnifiedBinding.btnSearchNext: ImageButton
    get() = requiredFromRoot(R.id.btnSearchNext)

val ActivityPlayerUnifiedBinding.btnCloseSearch: ImageButton
    get() = requiredFromRoot(R.id.btnCloseSearch)

val ActivityPlayerUnifiedBinding.textViewerContainer: FrameLayout
    get() = requiredFromRoot(R.id.textViewerContainer)

val ActivityPlayerUnifiedBinding.btnCloseTextViewer: ImageButton
    get() = requiredFromRoot(R.id.btnCloseTextViewer)

val ActivityPlayerUnifiedBinding.textScrollView: ScrollView
    get() = requiredFromRoot(R.id.textScrollView)

val ActivityPlayerUnifiedBinding.tvTextContent: TextView
    get() = requiredFromRoot(R.id.tvTextContent)

val ActivityPlayerUnifiedBinding.textEditContainer: LinearLayout
    get() = requiredFromRoot(R.id.textEditContainer)

val ActivityPlayerUnifiedBinding.etTextContent: EditText
    get() = requiredFromRoot(R.id.etTextContent)

// S0189: scroll container hosting etTextContent in edit mode
val ActivityPlayerUnifiedBinding.textEditScrollView: ScrollView
    get() = requiredFromRoot(R.id.textEditScrollView)

// S0189: 5-action editor buttons (moved into editorToolbar — see safeViews property docs).
val ActivityPlayerUnifiedBinding.btnEditorSave: ImageButton
    get() = requiredFromRoot(R.id.btnEditorSave)

val ActivityPlayerUnifiedBinding.btnEditorSaveClose: ImageButton
    get() = requiredFromRoot(R.id.btnEditorSaveClose)

val ActivityPlayerUnifiedBinding.btnEditorSaveSend: ImageButton
    get() = requiredFromRoot(R.id.btnEditorSaveSend)

val ActivityPlayerUnifiedBinding.btnEditorSendKeep: ImageButton
    get() = requiredFromRoot(R.id.btnEditorSendKeep)

val ActivityPlayerUnifiedBinding.btnEditorCancel: ImageButton
    get() = requiredFromRoot(R.id.btnEditorCancel)

val ActivityPlayerUnifiedBinding.textPageNavigation: LinearLayout
    get() = requiredFromRoot(R.id.textPageNavigation)

val ActivityPlayerUnifiedBinding.btnTextPagePrev: ImageButton
    get() = requiredFromRoot(R.id.btnTextPagePrev)

val ActivityPlayerUnifiedBinding.btnTextPageNext: ImageButton
    get() = requiredFromRoot(R.id.btnTextPageNext)

val ActivityPlayerUnifiedBinding.tvTextPageIndicator: TextView
    get() = requiredFromRoot(R.id.tvTextPageIndicator)

val ActivityPlayerUnifiedBinding.tvTextEncodingIndicator: TextView
    get() = requiredFromRoot(R.id.tvTextEncodingIndicator)

// Editor toolbar
val ActivityPlayerUnifiedBinding.editorToolbar: LinearLayout
    get() = requiredFromRoot(R.id.editorToolbar)
val ActivityPlayerUnifiedBinding.btnUndo: ImageButton
    get() = requiredFromRoot(R.id.btnUndo)
val ActivityPlayerUnifiedBinding.btnRedo: ImageButton
    get() = requiredFromRoot(R.id.btnRedo)
val ActivityPlayerUnifiedBinding.btnEditorFind: ImageButton
    get() = requiredFromRoot(R.id.btnEditorFind)
val ActivityPlayerUnifiedBinding.btnEditorFindReplace: ImageButton
    get() = requiredFromRoot(R.id.btnEditorFindReplace)
val ActivityPlayerUnifiedBinding.tvEditorCursorPos: TextView
    get() = requiredFromRoot(R.id.tvEditorCursorPos)

// Find & Replace panel
val ActivityPlayerUnifiedBinding.textFindReplacePanel: LinearLayout
    get() = requiredFromRoot(R.id.textFindReplacePanel)
val ActivityPlayerUnifiedBinding.etFindQuery: EditText
    get() = requiredFromRoot(R.id.etFindQuery)
val ActivityPlayerUnifiedBinding.tvFindCounter: TextView
    get() = requiredFromRoot(R.id.tvFindCounter)
val ActivityPlayerUnifiedBinding.btnFindPrev: ImageButton
    get() = requiredFromRoot(R.id.btnFindPrev)
val ActivityPlayerUnifiedBinding.btnFindNext: ImageButton
    get() = requiredFromRoot(R.id.btnFindNext)
val ActivityPlayerUnifiedBinding.btnFindClose: ImageButton
    get() = requiredFromRoot(R.id.btnFindClose)
val ActivityPlayerUnifiedBinding.replaceRow: LinearLayout
    get() = requiredFromRoot(R.id.replaceRow)
val ActivityPlayerUnifiedBinding.etReplaceQuery: EditText
    get() = requiredFromRoot(R.id.etReplaceQuery)
val ActivityPlayerUnifiedBinding.btnReplace: MaterialButton
    get() = requiredFromRoot(R.id.btnReplace)
val ActivityPlayerUnifiedBinding.btnReplaceAll: MaterialButton
    get() = requiredFromRoot(R.id.btnReplaceAll)

val ActivityPlayerUnifiedBinding.pdfControlsLayout: LinearLayout
    get() = requiredFromRoot(R.id.pdfControlsLayout)

val ActivityPlayerUnifiedBinding.btnPdfPrevPage: ImageButton
    get() = requiredFromRoot(R.id.btnPdfPrevPage)

val ActivityPlayerUnifiedBinding.btnPdfHome: ImageButton
    get() = requiredFromRoot(R.id.btnPdfHome)

val ActivityPlayerUnifiedBinding.btnPdfZoomOut: ImageButton
    get() = requiredFromRoot(R.id.btnPdfZoomOut)

val ActivityPlayerUnifiedBinding.tvPdfPageIndicator: TextView?
    get() = root.findViewById(R.id.tvPdfPageIndicator)

val ActivityPlayerUnifiedBinding.btnPdfZoomIn: ImageButton
    get() = requiredFromRoot(R.id.btnPdfZoomIn)

val ActivityPlayerUnifiedBinding.btnPdfNextPage: ImageButton
    get() = requiredFromRoot(R.id.btnPdfNextPage)

val ActivityPlayerUnifiedBinding.btnPdfEnd: ImageButton?
    get() = root.findViewById(R.id.btnPdfEnd)

val ActivityPlayerUnifiedBinding.btnTranslatePdf: ImageButton?
    get() = root.findViewById(R.id.btnTranslatePdf)

val ActivityPlayerUnifiedBinding.btnTranslationFontDecrease: ImageButton?
    get() = root.findViewById(R.id.btnTranslationFontDecrease)

val ActivityPlayerUnifiedBinding.btnTranslationFontIncrease: ImageButton?
    get() = root.findViewById(R.id.btnTranslationFontIncrease)

val ActivityPlayerUnifiedBinding.btnGoogleLensPdf: ImageButton?
    get() = root.findViewById(R.id.btnGoogleLensPdf)

val ActivityPlayerUnifiedBinding.btnSearchPdf: ImageButton?
    get() = root.findViewById(R.id.btnSearchPdf)

val ActivityPlayerUnifiedBinding.epubControlsLayout: LinearLayout
    get() = requiredFromRoot(R.id.epubControlsLayout)

val ActivityPlayerUnifiedBinding.btnEpubPrevChapter: ImageButton
    get() = requiredFromRoot(R.id.btnEpubPrevChapter)

val ActivityPlayerUnifiedBinding.btnEpubHome: ImageButton
    get() = requiredFromRoot(R.id.btnEpubHome)

val ActivityPlayerUnifiedBinding.tvEpubChapterIndicator: TextView
    get() = requiredFromRoot(R.id.tvEpubChapterIndicator)

val ActivityPlayerUnifiedBinding.btnEpubToc: ImageButton
    get() = requiredFromRoot(R.id.btnEpubToc)

val ActivityPlayerUnifiedBinding.btnEpubFontSizeDecrease: ImageButton
    get() = requiredFromRoot(R.id.btnEpubFontSizeDecrease)

val ActivityPlayerUnifiedBinding.btnEpubFontSizeIncrease: ImageButton
    get() = requiredFromRoot(R.id.btnEpubFontSizeIncrease)

val ActivityPlayerUnifiedBinding.btnEpubNextChapter: ImageButton
    get() = requiredFromRoot(R.id.btnEpubNextChapter)

val ActivityPlayerUnifiedBinding.translationOverlay: CardView
    get() = requiredFromRoot(R.id.translationOverlay)

val ActivityPlayerUnifiedBinding.translationOverlayBackground: View
    get() = requiredFromRoot(R.id.translationOverlayBackground)

val ActivityPlayerUnifiedBinding.btnCloseTranslation: ImageButton
    get() = requiredFromRoot(R.id.btnCloseTranslation)

val ActivityPlayerUnifiedBinding.translationScrollView: ScrollView
    get() = requiredFromRoot(R.id.translationScrollView)

val ActivityPlayerUnifiedBinding.tvTranslatedText: TextView
    get() = requiredFromRoot(R.id.tvTranslatedText)
