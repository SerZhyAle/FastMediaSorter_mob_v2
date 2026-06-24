package com.sza.fastmediasorter.core.panel

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.game.GameLaunchIntents
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.streams.StreamsActivity

/**
 * Builds the launch [Intent] for each panel internal route, reusing the exact entry points the
 * home-screen widgets already use (strategic S0663 ADR-1) - no new navigation is introduced here.
 * Every intent gets [Intent.FLAG_ACTIVITY_NEW_TASK], matching the existing panel launch path.
 */
object AppLaunchPanelRouteIntents {

    fun calculator(context: Context): Intent =
        Intent(context, CalculatorActivity::class.java).withPanelFlags()

    fun game(context: Context): Intent =
        GameLaunchIntents.game(context).withPanelFlags()

    fun ocr(context: Context): Intent =
        CameraOcrTranslateActivity.createIntent(context).withPanelFlags()

    fun streams(context: Context): Intent =
        Intent(context, StreamsActivity::class.java).withPanelFlags()

    fun favorites(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_FAVORITES, true)
            .withPanelFlags()

    fun resource(context: Context, resourceId: Long): Intent =
        BrowseActivity.createIntent(context, resourceId).withPanelFlags()

    private fun Intent.withPanelFlags(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Matches the key `FavoritesWidgetProvider` / `MainActivity` already agree on (S0134). */
    private const val EXTRA_OPEN_FAVORITES = "open_favorites"
}
