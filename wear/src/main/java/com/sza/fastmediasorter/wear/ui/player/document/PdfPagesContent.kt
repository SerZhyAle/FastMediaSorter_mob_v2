package com.sza.fastmediasorter.wear.ui.player.document

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn

/** Space a page holds while it is being drawn, so the list does not jump when it arrives. */
private val PAGE_PLACEHOLDER_HEIGHT = 160.dp

/**
 * S4227: a PDF in the reader - its title, then one drawn page per list item, scrolled by the crown and by touch
 * (WearListColumn carries the rotary hookup). Pages are drawn lazily at the screen width, so only what the list
 * composes costs memory (research 05).
 */
@Composable
internal fun PdfPagesContent(
    fileName: String,
    pageCount: Int,
    listState: ScalingLazyListState,
    loadPage: suspend (index: Int, widthPx: Int) -> Bitmap?,
) {
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    WearListColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        item { PdfTitle(fileName) }
        items(pageCount) { index ->
            PdfPage(index = index, pageCount = pageCount, widthPx = widthPx, loadPage = loadPage)
        }
    }
}

@Composable
private fun PdfTitle(fileName: String) {
    Text(
        text = fileName,
        style = MaterialTheme.typography.title3,
        modifier = Modifier.fillMaxWidth(),
        maxLines = 2,
    )
}

@Composable
private fun PdfPage(
    index: Int,
    pageCount: Int,
    widthPx: Int,
    loadPage: suspend (Int, Int) -> Bitmap?,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, index, widthPx) { value = loadPage(index, widthPx) }
    val label = stringResource(R.string.wear_document_pdf_page, index + 1, pageCount)
    val page = bitmap
    if (page == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PAGE_PLACEHOLDER_HEIGHT)
                .semantics { contentDescription = label }
        )
    } else {
        Image(
            bitmap = page.asImageBitmap(),
            contentDescription = label,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
