package com.sza.fastmediasorter.ui.player.helpers

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import kotlinx.coroutines.CoroutineScope

/** Bottom-sheet thumbnail grid for PDF navigation. Extracted from `PdfViewerManager.showThumbnailNavigation` to keep the host class under the 1000-LOC budget. */
internal object PdfThumbnailSheet {

    fun show(
        binding: ActivityPlayerUnifiedBinding,
        safeViews: PlayerBindingSafeViews,
        rendererWrapper: PdfRendererWrapper?,
        pdfPageCount: Int,
        currentPdfPageIndex: Int,
        coroutineScope: CoroutineScope,
        isScrollMode: Boolean,
        onPageScrollSelected: (Int) -> Unit,
        onPagePicked: (Int) -> Unit,
    ) {
        val wrapper = rendererWrapper ?: return
        if (pdfPageCount <= 1) return

        val context = binding.root.context
        val bottomSheet = BottomSheetDialog(context)
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_pdf_thumbnails, null)

        val rvThumbnails = view.findViewById<RecyclerView>(R.id.rvThumbnails)
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvThumbnailTitle)
        tvTitle.text = context.getString(R.string.pdf_thumbnails) + " (${pdfPageCount})"

        val adapter = PdfThumbnailAdapter(
            rendererWrapper = wrapper,
            pageCount = pdfPageCount,
            coroutineScope = coroutineScope,
            currentPage = currentPdfPageIndex,
            onPageSelected = { page ->
                bottomSheet.dismiss()
                if (isScrollMode) onPageScrollSelected(page) else onPagePicked(page)
            },
        )

        val spanCount = 3
        rvThumbnails.layoutManager = GridLayoutManager(context, spanCount)
        rvThumbnails.adapter = adapter

        val targetRow = currentPdfPageIndex / spanCount
        rvThumbnails.scrollToPosition(targetRow * spanCount)

        bottomSheet.setContentView(view)
        bottomSheet.setOnDismissListener { adapter.clearCache() }
        bottomSheet.show()
    }
}
