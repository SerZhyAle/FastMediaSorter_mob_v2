package com.sza.fastmediasorter.ui.player.sheets

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.BottomSheetEpubTocBinding
import com.sza.fastmediasorter.ui.common.dialog.BaseAppBottomSheet
import com.sza.fastmediasorter.ui.player.helpers.EpubTocAdapter

/**
 * Recreation-safe replacement for the raw `BottomSheetDialog` table of contents that
 * `EpubSearchAndTocPresenter` used to build (S3244). The chapter list is resolved by the presenter
 * and handed in by [newInstance] - the sheet is a modal picker tied to the book currently open,
 * never restored from the back stack, the same convention `PdfThumbnailBottomSheet` uses.
 */
class EpubTocBottomSheet : BaseAppBottomSheet() {

    override val contentLayout: Int = R.layout.bottom_sheet_epub_toc
    override val requestKey: String = REQUEST_KEY

    private var bookTitle: String? = null
    private var chapters: List<Pair<String, Int>> = emptyList()
    private var currentChapterIndex: Int = 0
    private var chapterCount: Int = 0
    private var onChapterSelected: ((Int) -> Unit)? = null

    companion object {
        const val TAG = "epub_toc_sheet"
        private const val REQUEST_KEY = "epub_toc_sheet"

        fun newInstance(
            bookTitle: String?,
            chapters: List<Pair<String, Int>>,
            currentChapterIndex: Int,
            chapterCount: Int,
            onChapterSelected: (Int) -> Unit,
        ): EpubTocBottomSheet = EpubTocBottomSheet().also {
            it.bookTitle = bookTitle
            it.chapters = chapters
            it.currentChapterIndex = currentChapterIndex
            it.chapterCount = chapterCount
            it.onChapterSelected = onChapterSelected
        }
    }

    override fun bindContent(content: View) {
        val binding = BottomSheetEpubTocBinding.bind(content)
        binding.tvTocTitle.text = bookTitle ?: getString(R.string.epub_table_of_contents)
        binding.tvChapterProgress.text =
            getString(R.string.epub_chapter_progress, currentChapterIndex + 1, chapterCount)

        val adapter = EpubTocAdapter(
            chapters = chapters,
            currentChapterSpineIndex = currentChapterIndex,
            onChapterSelected = { spineIndex ->
                dismiss()
                onChapterSelected?.invoke(spineIndex)
            },
        )
        binding.rvTocChapters.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTocChapters.adapter = adapter

        val currentPosition = adapter.findCurrentChapterPosition()
        if (currentPosition > 0) binding.rvTocChapters.scrollToPosition(currentPosition)
    }
}
