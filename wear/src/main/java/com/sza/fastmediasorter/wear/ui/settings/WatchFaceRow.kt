package com.sza.fastmediasorter.wear.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFaceLinks
import com.sza.fastmediasorter.wear.ui.common.WearLinkRow
import com.sza.fastmediasorter.wear.ui.testing.WearTestTags
import timber.log.Timber

/**
 * S4009: the About row that opens the watch face's Play page on the watch itself.
 *
 * The outcome stays local: the only thing to remember is that this watch has no store, and that
 * matters only while the screen is open. A store link moves no content, so the row is offered in
 * both watch flavors.
 */
@Composable
fun WatchFaceRow() {
    val context = LocalContext.current
    var noStore by rememberSaveable { mutableStateOf(false) }
    val message = if (noStore) {
        stringResource(R.string.about_watch_face_no_store)
    } else {
        stringResource(R.string.about_watch_face_caption)
    }
    WearLinkRow(
        label = stringResource(R.string.about_watch_face),
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WearFaceLinks.MARKET_URL))
            noStore = try {
                context.startActivity(intent)
                false
            } catch (e: ActivityNotFoundException) {
                // Not swallowed: the row turns it into the "no Google Play" line under itself.
                Timber.w(e, "No store to open the watch face page")
                true
            }
        },
        modifier = Modifier.testTag(WearTestTags.WEAR_ABOUT_WATCH_FACE),
        message = message
    )
}
