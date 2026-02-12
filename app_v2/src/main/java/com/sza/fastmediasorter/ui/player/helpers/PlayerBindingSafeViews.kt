package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.view.ViewStub
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.IdRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding

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
}
