package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.launcher.picker.LauncherStreamPickerDialogFragment
import com.sza.fastmediasorter.ui.streams.helpers.StreamWidgetResolveManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1916 - the `APPWIDGET_CONFIGURE` screen the system opens when a stream tile is placed.
 *
 * View/XML and deliberately not Compose: Rule 32 holds the Compose-island baseline as a ceiling that
 * only falls, and four of the six existing islands are already widget configuration screens. It costs
 * nothing here, because the channel search this screen reuses was never Compose (strategic ADR-2).
 *
 * The screen itself is an empty container - all of its interface is [LauncherStreamPickerDialogFragment],
 * which Phase 01 moved into the common source set so this activity also exists in legacy and vr, where
 * streams ship without the launcher. Every catalog lookup is delegated to [StreamWidgetResolveManager];
 * what stays here is Android plumbing only (Rule 3).
 */
@AndroidEntryPoint
class StreamLaunchWidgetConfigActivity : AppCompatActivity() {

    @Inject
    lateinit var resolveManager: StreamWidgetResolveManager

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** Set the moment a channel is picked, so [onResume] tells a dismissal apart from a selection. */
    private var applyingPick = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream_launch_widget_config)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Cancelled until proven otherwise: the host must not keep a tile whose configuration never
        // completed, and this is the result it reads if the user backs out.
        setResult(RESULT_CANCELED, resultIntent())

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.w("StreamLaunchWidgetConfigActivity: started without a widget id")
            finish()
            return
        }

        Timber.d("S1916: config open id=%d", appWidgetId)

        supportFragmentManager.setFragmentResultListener(
            LauncherStreamPickerDialogFragment.RESULT_KEY,
            this,
        ) { _, bundle ->
            val identityKey = bundle.getString(LauncherStreamPickerDialogFragment.RESULT_STREAM_IDENTITY)
            if (!identityKey.isNullOrBlank()) {
                applyPick(identityKey)
            }
        }

        if (savedInstanceState == null) {
            LauncherStreamPickerDialogFragment.newInstance()
                .show(supportFragmentManager, LauncherStreamPickerDialogFragment.TAG)
        }
    }

    /**
     * The picker is this screen's whole interface, so its dismissal is the user's cancellation. There is
     * no listener for that on a DialogFragment, and resuming with the dialog gone is the same event.
     */
    override fun onResume() {
        super.onResume()
        val pickerGone = supportFragmentManager
            .findFragmentByTag(LauncherStreamPickerDialogFragment.TAG) == null
        if (pickerGone && !applyingPick) {
            finish()
        }
    }

    private fun applyPick(identityKey: String) {
        applyingPick = true
        lifecycleScope.launch {
            val resolved = resolveManager.resolve(identityKey)
            if (resolved == null) {
                Timber.w("StreamLaunchWidgetConfigActivity: picked channel %s is gone", identityKey)
                finish()
            } else {
                commit(resolved)
            }
        }
    }

    private fun commit(resolved: StreamWidgetResolveManager.ResolvedChannel) {
        StreamLaunchWidgetStore.save(
            context = this,
            appWidgetId = appWidgetId,
            url = resolved.source.url,
            title = resolved.source.title,
            mediaKind = resolved.source.mediaKind,
            iconTile = resolved.icon,
        )
        StreamLaunchWidgetProvider.updateAppWidget(
            this,
            AppWidgetManager.getInstance(this),
            appWidgetId,
        )
        Timber.d("S1916: tile saved id=%d kind=%s", appWidgetId, resolved.source.mediaKind)
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        fun createIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, StreamLaunchWidgetConfigActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
    }
}
