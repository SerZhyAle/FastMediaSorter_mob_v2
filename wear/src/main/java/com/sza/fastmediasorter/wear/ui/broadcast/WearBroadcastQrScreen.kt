package com.sza.fastmediasorter.wear.ui.broadcast

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionState
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold

private val CODE_EDGE = 120.dp
private val SECTION_GAP = 8.dp
private val TEXT_HORIZONTAL_PADDING = 12.dp

/**
 * S2509: the broadcast address as a code the listener's camera reads, shown only when asked for.
 *
 * The owner ruled QR a deliberate button rather than a permanent panel, and the reason is visible in
 * the layout: a code large enough to scan from a watch face leaves no room beside it, so a screen
 * carrying both would have to shrink either the code or the stop action.
 *
 * It renders the descriptor the LIVE state carries rather than encoding an address of its own. A
 * session that has ended therefore has nothing to show here, and says so - a stale code that scans
 * cleanly and connects to nothing is worse than no code at all.
 */
@Composable
fun WearBroadcastQrScreen(viewModel: WearBroadcastViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val live = state as? WearBroadcastSessionState.Live

    WearScreenScaffold(contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (live == null) {
                Caption(text = stringResource(R.string.wear_broadcast_qr_unavailable))
            } else {
                BroadcastCode(payload = live.descriptorQrPayload)
            }
        }
    }
}

@Composable
private fun BroadcastCode(payload: String) {
    // Encoding walks a bitmap pixel by pixel, so it is keyed on the payload and the pixel size and
    // survives every recomposition that changes neither - which is every one this screen has.
    val edgePx = with(LocalDensity.current) { CODE_EDGE.roundToPx() }
    val bitmap = remember(payload, edgePx) { WearQrCodeEncoder.encode(payload, edgePx) }
    if (bitmap == null) {
        Caption(text = stringResource(R.string.wear_broadcast_qr_unavailable))
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            // The caption below names what the code is for; describing the image as well would make
            // TalkBack read the same purpose twice, and the pixels themselves describe nothing.
            contentDescription = null,
            modifier = Modifier.size(CODE_EDGE)
        )
        Caption(text = stringResource(R.string.wear_broadcast_qr_caption))
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TEXT_HORIZONTAL_PADDING)
    )
}
