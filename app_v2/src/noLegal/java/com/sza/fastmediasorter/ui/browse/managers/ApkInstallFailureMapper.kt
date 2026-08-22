package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.ApkInstallFailure

/**
 * S1686: the message shown for each classified install failure.
 *
 * The classification itself lives in [ApkInstallFailure], which is testable without resources; this object
 * holds only the flavor's strings, because the install screen ships in `noLegal` alone.
 */
object ApkInstallFailureMapper {

    /** String resource for [failure]. An unknown cause keeps the generic message the screen always had. */
    fun messageRes(failure: ApkInstallFailure): Int = when (failure) {
        ApkInstallFailure.VERSION_DOWNGRADE -> R.string.s1686_apk_install_failed_downgrade
        ApkInstallFailure.UPDATE_INCOMPATIBLE -> R.string.s1686_apk_install_failed_incompatible
        ApkInstallFailure.INSUFFICIENT_STORAGE -> R.string.s1686_apk_install_failed_storage
        ApkInstallFailure.CORRUPT_PACKAGE -> R.string.s1686_apk_install_failed_corrupt
        ApkInstallFailure.UNKNOWN -> R.string.s0183_apk_install_failed
    }
}
