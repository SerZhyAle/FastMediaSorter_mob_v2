package com.sza.fastmediasorter.ui.player.sheets

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.BottomSheetPdfThumbnailsBinding
import com.sza.fastmediasorter.ui.common.dialog.BaseAppBottomSheet
import com.sza.fastmediasorter.ui.player.helpers.PdfRendererWrapper
import com.sza.fastmediasorter.ui.player.helpers.PdfThumbnailAdapter
import kotlinx.coroutines.CoroutineScope

/**
 * Recreation-safe replacement for the raw `BottomSheetDialog` `PdfThumbnailSheet` used to build
 * (S3244). Callbacks and the renderer are set by [newInstance] - the sheet is a modal picker tied to
 * the PDF page currently open, never restored from the back stack, the same convention
 * `BrowseBinaryFileBottomSheet` uses for the same reason.
 */
class PdfThumbnailBottomSheet : BaseAppBottomSheet() {

    override val contentLayout: Int = R.layout.bottom_sheet_pdf_thumbnails
    override val requestKey: String = REQUEST_KEY

    private var rendererWrapper: PdfRendererWrapper? = null
    private var pdfPageCount: Int = 0
    private var currentPdfPageIndex: Int = 0
    private var coroutineScope: CoroutineScope? = null
    private var isScrollMode: Boolean = false
    private var onPageScrollSelected: ((Int) -> Unit)? = null
    private var onPagePicked: ((Int) -> Unit)? = null
    private var adapter: PdfThumbnailAdapter? = null

    companion object {
        private const val REQUEST_KEY = "pdf_thumbnail_sheet"

        fun newInstance(
            rendererWrapper: PdfRendererWrapper,
            pdfPageCount: Int,
            currentPdfPageIndex: Int,
            coroutineScope: CoroutineScope,
            isScrollMode: Boolean,
            onPageScrollSelected: (Int) -> Unit,
            onPagePicked: (Int) -> Unit,
        ): PdfThumbnailBottomSheet = PdfThumbnailBottomSheet().also {
            it.rendererWrapper = rendererWrapper
            it.pdfPageCount = pdfPageCount
            it.currentPdfPageIndex = currentPdfPageIndex
            it.coroutineScope = coroutineScope
            it.isScrollMode = isScrollMode
            it.onPageScrollSelected = onPageScrollSelected
            it.onPagePicked = onPagePicked
        }
    }

    override fun bindContent(content: View) {
        val wrapper = rendererWrapper
        val scope = coroutineScope
        if (wrapper == null || scope == null) {
            dismiss()
            return
        }
        val binding = BottomSheetPdfThumbnailsBinding.bind(content)
        binding.tvThumbnailTitle.text = "${getString(R.string.pdf_thumbnails)} ($pdfPageCount)"

        val spanCount = 3
        val gridAdapter = PdfThumbnailAdapter(
            rendererWrapper = wrapper,
            pageCount = pdfPageCount,
            coroutineScope = scope,
            currentPage = currentPdfPageIndex,
            onPageSelected = { page ->
                dismiss()
                if (isScrollMode) onPageScrollSelected?.invoke(page) else onPagePicked?.invoke(page)
            },
        )
        adapter = gridAdapter
        binding.rvThumbnails.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvThumbnails.adapter = gridAdapter

        val targetRow = currentPdfPageIndex / spanCount
        binding.rvThumbnails.scrollToPosition(targetRow * spanCount)
    }

    override fun onDestroyView() {
        adapter?.clearCache()
        adapter = null
        super.onDestroyView()
    }
}
