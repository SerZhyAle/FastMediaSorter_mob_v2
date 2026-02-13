package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.view.ViewStub
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.cardview.widget.CardView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.google.android.material.button.MaterialButton

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
    val btnUndoCmd: ImageButton get() = required(binding.btnUndoCmd, R.id.btnUndoCmd)
    val btnLyricsCmd: ImageButton get() = required(binding.btnLyricsCmd, R.id.btnLyricsCmd)

    val btnGoogleLensPdfCmd: ImageButton get() = required(binding.btnGoogleLensPdfCmd, R.id.btnGoogleLensPdfCmd)
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

    val btnTranslateImage: ImageButton get() = required(binding.btnTranslateImage, R.id.btnTranslateImage)

    val audioMetadata: TextView get() = required(R.id.audioMetadata)
    val audioFileName: TextView get() = required(R.id.audioFileName)
    val audioFileInfo: TextView get() = required(R.id.audioFileInfo)

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
    val btnCancelEdit: MaterialButton get() = required(R.id.btnCancelEdit)
    val btnSaveText: MaterialButton get() = required(R.id.btnSaveText)

    val pdfControlsLayout: LinearLayout get() = required(R.id.pdfControlsLayout)
    val epubControlsLayout: LinearLayout get() = required(R.id.epubControlsLayout)
    val translationOverlay: CardView get() = required(R.id.translationOverlay)
    val translationOverlayBackground: View get() = required(R.id.translationOverlayBackground)
    val btnCloseTranslation: ImageButton get() = required(R.id.btnCloseTranslation)
    val translationScrollView: ScrollView get() = required(R.id.translationScrollView)
    val tvTranslatedText: TextView get() = required(R.id.tvTranslatedText)
    val btnTranslationFontDecrease: ImageButton?
        get() = binding.root.findViewById(R.id.btnTranslationFontDecrease)
    val btnTranslationFontIncrease: ImageButton?
        get() = binding.root.findViewById(R.id.btnTranslationFontIncrease)
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

val ActivityPlayerUnifiedBinding.btnCancelEdit: MaterialButton
    get() = requiredFromRoot(R.id.btnCancelEdit)

val ActivityPlayerUnifiedBinding.btnSaveText: MaterialButton
    get() = requiredFromRoot(R.id.btnSaveText)

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
