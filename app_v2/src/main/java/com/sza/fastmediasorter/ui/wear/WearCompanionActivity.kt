package com.sza.fastmediasorter.ui.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivityWearCompanionBinding
import com.sza.fastmediasorter.ui.settings.fragments.WearSyncSettingsFragment
import com.sza.fastmediasorter.ui.wear.helpers.WearCompanionHeaderHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1735: the window the Wear companion opens in, so it can be a sub-program like the calculator.
 *
 * The companion used to exist only as a sheet inside the settings screen, which made it the one program
 * with no window of its own and therefore no "open in a new window" - strategic §5.1. This activity gives
 * it that window and nothing else.
 *
 * **It hosts the companion's fragment; it does not re-implement it.** CLAUDE.md Rule 32 forbids a new
 * Compose island in `app_v2`, and the fragment already carries the only one the companion needs - putting
 * a second copy of this UI here would break that outright.
 *
 * S2000: the window is opaque and ordinary, and the fragment sits in its container rather than floating
 * over it as a sheet. The owner's ruling is quoted verbatim in that spec's §3.3.1 - the companion is a
 * full window - and with no sheet left there is nothing to watch for dismissal: closing the window is the
 * system back gesture, exactly as in every other program.
 *
 * S2460: the window now carries the app's standard toolbar with a back arrow, set up here rather than
 * drawn in the island - Rule 32 forbids growing a Compose island in `app_v2`, and the shell is where
 * every other screen keeps its header anyway.
 */
@AndroidEntryPoint
class WearCompanionActivity : AppCompatActivity(), WearCompanionHeaderHost {

    private lateinit var binding: ActivityWearCompanionBinding

    // S3185: the toolbar's sync views are handed to the fragment, which owns the view model they drive.
    override val headerSyncButton: Button get() = binding.wearPushSettings
    override val headerSyncCaption: TextView get() = binding.wearSyncSettingsStatus

    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWearCompanionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setUpNavigation(this)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.wearCompanionContainer, WearSyncSettingsFragment(), COMPANION_TAG)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {

        private const val COMPANION_TAG = "wear_companion"

        /** Every other program is opened by an Intent, so the panel, the menu and settings all use this. */
        fun createIntent(context: Context): Intent = Intent(context, WearCompanionActivity::class.java)
    }
}
