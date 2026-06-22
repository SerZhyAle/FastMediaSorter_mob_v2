package com.sza.fastmediasorter.ui.player.helpers

import androidx.appcompat.app.AppCompatActivity

/**
 * Host contract for [DocumentPrintManager] (S0613). Replaces the manager's former hard dependency on
 * `PlayerActivity` so any print-capable host - the in-app player and the standalone document/text
 * players - reuses one print implementation instead of duplicating the pipeline.
 */
interface DocumentPrintHost {

    /** Hosting activity: source of system services, UI-thread dispatch, lifecycleScope, and child-view Context. */
    val printHostActivity: AppCompatActivity

    /** Materialises any resource (local / content / network / cloud) to a local readable File. */
    val printNetworkFileManager: NetworkFileManager

    /** @return true when an internal Office print path handled the job; hosts without an Office viewer return false. */
    fun printOfficeDocument(): Boolean

    /** Surface a print status/error message (Snackbar in the player, Toast in standalone hosts). */
    fun showPrintMessage(message: String)
}
