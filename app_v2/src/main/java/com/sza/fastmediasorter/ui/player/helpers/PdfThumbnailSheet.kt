package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.ui.player.sheets.PdfThumbnailBottomSheet
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

/** Bottom-sheet thumbnail grid for PDF navigation. Extracted from `PdfViewerManager.showThumbnailNavigation` to keep the host class under the 1000-LOC budget. */
internal object PdfThumbnailSheet {

    // S0380: takes the layout root instead of ActivityPlayerUnifiedBinding so it works on both the
    // full unified player layout and the trimmed document standalone layout.
    fun show(
        root: View,
        safeViews: PlayerBindingSafeViews,
        rendererWrapper: PdfRendererWrapper?,
        pdfPageCount: Int,
        currentPdfPageIndex: Int,
        coroutineScope: CoroutineScope,
        isScrollMode: Boolean,
        onPageScrollSelected: (Int) -> Unit,
        onPagePicked: (Int) -> Unit,
    ) {
        val wrapper = rendererWrapper
        val host = root.context as? FragmentActivity
        if (wrapper == null || pdfPageCount <= 1) return
        if (host == null) {
            Timber.w("PdfThumbnailSheet: host is not a FragmentActivity, cannot show thumbnail sheet")
            return
        }

        PdfThumbnailBottomSheet.newInstance(
            rendererWrapper = wrapper,
            pdfPageCount = pdfPageCount,
            currentPdfPageIndex = currentPdfPageIndex,
            coroutineScope = coroutineScope,
            isScrollMode = isScrollMode,
            onPageScrollSelected = onPageScrollSelected,
            onPagePicked = onPagePicked,
        ).show(host.supportFragmentManager, "pdf_thumbnail_sheet")
    }
}
