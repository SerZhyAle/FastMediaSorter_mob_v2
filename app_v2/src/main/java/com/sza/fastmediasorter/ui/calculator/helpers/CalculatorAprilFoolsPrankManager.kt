package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import timber.log.Timber
import java.time.LocalDate
import java.time.Month

/**
 * Owns the calculator's April Fools prank dialog and its once-per-day April 1 gate.
 */
class CalculatorAprilFoolsPrankManager(
    private val context: Context,
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun maybeShowDailyAprilFoolsPrank() {
        val today = LocalDate.now()
        if (today.month != Month.APRIL || today.dayOfMonth != 1) return
        val todayToken = today.toString()
        if (prefs.getString(KEY_LAST_AUTO_SHOWN_DATE, null) == todayToken) return
        prefs.edit().putString(KEY_LAST_AUTO_SHOWN_DATE, todayToken).apply()
        showPrankDialog()
    }

    fun showDivisionByZeroPrank() {
        showPrankDialog()
    }

    private fun showPrankDialog() {
        val dialog = android.app.AlertDialog.Builder(
            context,
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
        )
            .setTitle(R.string.calculator_prank_title)
            .setIcon(R.drawable.ic_prank_system_alert)
            .setMessage(R.string.calculator_prank_message)
            .setPositiveButton(R.string.calculator_prank_details) { dlg, _ ->
                openWikipedia()
                dlg.dismiss()
            }
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        DialogAccessibilityHelper.applyInitialFocus(dialog)
    }

    private fun openWikipedia() {
        runCatching {
            context.startActivity(SupportIntentFactory.openUrl(wikipediaUrl()))
        }.onFailure { error ->
            Timber.w(error, "Calculator prank wiki launch failed")
        }
    }

    // Always the English April Fools' Day article regardless of app locale (S0517 owner decision).
    private fun wikipediaUrl(): String = WIKIPEDIA_URL_EN

    private companion object {
        const val PREFS_NAME = "calculator_april_fools_prank"
        const val KEY_LAST_AUTO_SHOWN_DATE = "last_auto_shown_date"
        const val WIKIPEDIA_URL_EN = "https://en.wikipedia.org/wiki/April_Fools%27_Day"
    }
}
