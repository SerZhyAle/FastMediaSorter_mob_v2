package com.sza.fastmediasorter.wear.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.systeminfo.WearSystemInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.WearSystemInfoOrder
import com.sza.fastmediasorter.wear.domain.systeminfo.section
import com.sza.fastmediasorter.wear.domain.systeminfo.text
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject

private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xFF
private const val HEX_PAD = 2

/**
 * The one extra section the sideload build shows.
 *
 * S2165 §6 item 2 found that the head group of facts needs nothing from `noLegal` at all - every one
 * of them reads under a permission the watch already declares - and that exactly four topics differ.
 * Three of those are unavailable to this class on their own terms: enumerating installed packages
 * needs `QUERY_ALL_PACKAGES`, which Play grants only against an approved use case and "show the user
 * diagnostics" is not one; SELinux, verified boot and bootloader state are reachable only by
 * reflection into a non-public class, which the non-SDK interface restrictions close a little further
 * each release; live body readings need a Play review against the store description (S2013).
 *
 * That leaves the signing certificate fingerprint. It is not restricted at all - the phone simply
 * chose to hide it behind a confirmation, and the watch has no such confirmation to put it behind, so
 * it ships only in the build that is not distributed through the store.
 *
 * This file and its Hilt module are the first content of `wear/src/noLegal/`, which S2090 created
 * empty on purpose and `dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 8 reserved for exactly this.
 */
class NoLegalWearInfoContributor @Inject constructor(
    @ApplicationContext private val context: Context
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.EXTENDED

    override suspend fun sections(): List<WearSystemInfoSection> {
        Timber.d("S2165: noLegal extended section requested")
        return listOf(
            section(
                titleRes = R.string.system_info_section_extended,
                fields = listOfNotNull(text(R.string.system_info_signing_fingerprint, fingerprint())),
                emptyReasonRes = R.string.system_info_empty_unreadable
            )
        )
    }

    private fun fingerprint(): String? = runCatching {
        val signatures = signingCertificates() ?: return@runCatching null
        val first = signatures.firstOrNull() ?: return@runCatching null
        MessageDigest.getInstance("SHA-256").digest(first).joinToString(":") { byte ->
            (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_PAD, '0').uppercase()
        }
    }.onFailure { error ->
        Timber.w(error, "System info: signing fingerprint unavailable")
    }.getOrNull()

    /**
     * `GET_SIGNING_CERTIFICATES` is API 28, which is this module's own floor, so no guard is needed for
     * the flag itself - only for the type-safe `getPackageInfo` overload that replaced the raw-int one
     * in API 33 (CLAUDE.md Rule 21, S0467).
     */
    private fun signingCertificates(): Array<ByteArray>? {
        val manager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        return info.signingInfo?.apkContentsSigners?.map { signature -> signature.toByteArray() }
            ?.toTypedArray()
    }
}
