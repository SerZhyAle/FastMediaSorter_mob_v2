package com.sza.fastmediasorter.ui.launcher.share

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Transparent, launcher-only host for receiving a place shared from another map application. */
@AndroidEntryPoint
class LauncherPlaceShareActivity : AppCompatActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject
    lateinit var placeShareManager: LauncherPlaceShareManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        lifecycleScope.launch {
            val outcome = placeShareManager.handle(intent, isLandscape, System.currentTimeMillis())
            messageFor(outcome)?.let { message ->
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    @StringRes
    private fun messageFor(outcome: LauncherPlaceShareOutcome): Int? = when (outcome) {
        LauncherPlaceShareOutcome.NOT_OURS -> null
        LauncherPlaceShareOutcome.PLACED_ROUTE -> R.string.launcher_share_place_added_route
        LauncherPlaceShareOutcome.PLACED_PLACE -> R.string.launcher_share_place_added_place
        LauncherPlaceShareOutcome.NOT_PLACED -> R.string.launcher_share_place_not_added
    }
}
