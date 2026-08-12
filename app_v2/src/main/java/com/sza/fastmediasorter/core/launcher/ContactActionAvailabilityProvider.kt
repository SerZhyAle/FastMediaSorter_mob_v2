package com.sza.fastmediasorter.core.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0428: whether this device can perform a contact action at all, so the desktop editor offers only
 * the ones that would work.
 *
 * **Two checks, not one.** The platform feature alone says the hardware exists, and a data-only radio
 * reports the broad `FEATURE_TELEPHONY` while placing no calls; resolving the actual intent alone says
 * some app claims the scheme, which a stub dialler on a photo frame also does. Both have to hold.
 *
 * Opening a contact card or a messenger thread needs no telephony, so those two are always available -
 * a tablet with no radio still pins them.
 */
@Singleton
class ContactActionAvailabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun isAvailable(action: LauncherContactAction): Boolean = when (action) {
        LauncherContactAction.PROFILE, LauncherContactAction.MESSAGE -> true
        LauncherContactAction.DIAL -> hasFeature(callingFeature()) && resolves(dialProbe())
        LauncherContactAction.SMS -> hasFeature(messagingFeature()) && resolves(smsProbe())
    }

    /**
     * The granular features arrived in API 33. Below it only the broad one exists, and it is the best
     * available answer rather than a good one - the intent resolve below carries the weight there.
     */
    private fun callingFeature(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.FEATURE_TELEPHONY_CALLING
        } else {
            PackageManager.FEATURE_TELEPHONY
        }

    private fun messagingFeature(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.FEATURE_TELEPHONY_MESSAGING
        } else {
            PackageManager.FEATURE_TELEPHONY
        }

    private fun hasFeature(name: String): Boolean = context.packageManager.hasSystemFeature(name)

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.resolveActivityCompat(intent) != null

    private fun dialProbe(): Intent =
        Intent(Intent.ACTION_DIAL, Uri.fromParts(TEL_SCHEME, PROBE_NUMBER, null))

    private fun smsProbe(): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.fromParts(SMS_SCHEME, PROBE_NUMBER, null))

    private companion object {
        const val TEL_SCHEME = "tel"
        const val SMS_SCHEME = "smsto"

        // Any syntactically valid number resolves the same handler; nothing is ever dialled from here.
        const val PROBE_NUMBER = "0"
    }
}
