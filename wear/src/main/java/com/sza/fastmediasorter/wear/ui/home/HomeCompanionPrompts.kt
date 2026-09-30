package com.sza.fastmediasorter.wear.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.PhoneCompanionHint
import com.sza.fastmediasorter.wear.domain.model.WearOpenUrlOnPhoneOutcome
import com.sza.fastmediasorter.wear.ui.common.StandardWearAlertDialog
import com.sza.fastmediasorter.wear.ui.testing.WearTestTags

/**
 * S4011: the modest line under the home rows saying why the phone-bound rows are missing.
 *
 * Plain caption text in the secondary colour rather than a chip: it explains, and a row styled like
 * its neighbours would read as one more place to tap.
 */
@Composable
fun HomeCompanionHintText(hint: PhoneCompanionHint) {
    Text(
        text = stringResource(
            when (hint) {
                PhoneCompanionHint.CONNECT_PHONE -> R.string.wear_companion_hint_connect
                PhoneCompanionHint.INSTALL_ON_PHONE -> R.string.wear_companion_hint_install
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(WearTestTags.WEAR_HOME_COMPANION_HINT),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant
    )
}

/** S4011: the install prompts bound to the home view model, kept out of the screen's own body. */
@Composable
fun HomePhoneInstallPrompts(viewModel: HomeViewModel) {
    val showOffer by viewModel.showInstallOffer.collectAsStateWithLifecycle()
    val outcome by viewModel.installOutcome.collectAsStateWithLifecycle()
    HomePhoneInstallPrompts(
        showOffer = showOffer,
        outcome = outcome,
        onAccept = viewModel::acceptInstallOffer,
        onDismiss = viewModel::dismissInstallOffer,
        onOutcomeShown = viewModel::clearInstallOutcome
    )
}

/**
 * S4011: the one-time offer to install FastMediaSorter on the phone, then what became of the request.
 *
 * Either button answers the offer for good; the outcome alert follows only an accepted offer, because
 * the phone is where the store opens and nothing on the watch would otherwise say so.
 */
@Composable
fun HomePhoneInstallPrompts(
    showOffer: Boolean,
    outcome: WearOpenUrlOnPhoneOutcome?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onOutcomeShown: () -> Unit
) {
    StandardWearAlertDialog(
        show = showOffer,
        title = stringResource(R.string.wear_companion_offer_title),
        message = stringResource(R.string.wear_companion_offer_message),
        confirmLabel = stringResource(R.string.wear_companion_offer_install),
        cancelLabel = stringResource(R.string.wear_companion_offer_later),
        onConfirm = onAccept,
        onDismissRequest = onDismiss
    )
    StandardWearAlertDialog(
        show = outcome != null,
        title = outcome?.let { stringResource(installOutcomeRes(it)) }.orEmpty(),
        cancelLabel = null,
        onConfirm = onOutcomeShown,
        onDismissRequest = onOutcomeShown
    )
}

/** S4011: the portal link's sentences, since both ask the phone to open an address. */
@StringRes
fun installOutcomeRes(outcome: WearOpenUrlOnPhoneOutcome): Int = when (outcome) {
    WearOpenUrlOnPhoneOutcome.OPENED -> R.string.about_web_portal_phone_opened
    WearOpenUrlOnPhoneOutcome.NO_CONNECTED_PHONE -> R.string.about_web_portal_no_phone
    WearOpenUrlOnPhoneOutcome.FAILED -> R.string.about_web_portal_phone_failed
}
