package com.sza.fastmediasorter.ui.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.sza.fastmediasorter.ui.settings.fragments.WearSyncSettingsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1735: the window the Wear companion opens in, so it can be a sub-program like the calculator.
 *
 * The companion used to exist only as a sheet inside the settings screen, which made it the one program
 * with no window of its own and therefore no "open in a new window" - strategic §5.1. This activity gives
 * it that window and nothing else.
 *
 * **It hosts the existing sheet; it does not re-implement it.** Strategic non-goal §2 keeps the sheet's
 * content, layout and behaviour untouched, and CLAUDE.md Rule 32 forbids a new Compose island in `app_v2` -
 * building a second copy of the companion's UI here would break both at once. The sheet is already a
 * `BottomSheetDialogFragment` with its own themed Compose island, so showing it over a translucent window
 * is the whole implementation.
 *
 * Finishing when the sheet goes away is what makes the window feel like the sheet rather than like a
 * screen with a sheet on it: there is nothing behind it to return to.
 */
@AndroidEntryPoint
class WearCompanionActivity : AppCompatActivity() {

    /** Held so it can be unregistered on the symmetric edge; an anonymous one could not be. */
    private val sheetWatcher = SheetDismissWatcher(::finishWhenSheetGone)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            showCompanionSheet()
        }
        // Registered unconditionally, not only on a first creation: a recreated activity already has its
        // sheet back from the fragment manager, and without the watcher the rotated window would have no
        // way to notice the sheet closing.
        //
        // There is deliberately NO back-stack listener beside this. The sheet is added without
        // addToBackStack, so a back-stack listener could never fire - it would be a second registration
        // that does nothing, which is how an unpaired listener gets written and then defended.
        supportFragmentManager.registerFragmentLifecycleCallbacks(sheetWatcher, false)
    }

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(sheetWatcher)
        super.onDestroy()
    }

    private fun showCompanionSheet() {
        supportFragmentManager.commit {
            add(WearSyncSettingsFragment(), SHEET_TAG)
        }
    }

    private fun finishWhenSheetGone() {
        if (!isFinishing && supportFragmentManager.findFragmentByTag(SHEET_TAG) == null) {
            finish()
        }
    }

    companion object {

        private const val SHEET_TAG = "wear_companion_sheet"

        /** Every other program is opened by an Intent, so the panel, the menu and settings all use this. */
        fun createIntent(context: Context): Intent = Intent(context, WearCompanionActivity::class.java)
    }
}
