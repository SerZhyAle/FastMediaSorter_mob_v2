package com.sza.fastmediasorter.ui.exchange

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeCastOfferManager
import com.sza.fastmediasorter.widget.StreamPlayLaunchActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S4116: transparent, no-UI target of the cast notification's Accept and Decline (DEVICE-EXCHANGE 7.8). An
 * Activity because only a notification tap lets a background app start one, which Accept needs to open the
 * player; Decline goes through the same door so both answers take one path. Every branch ends in finish().
 */
@AndroidEntryPoint
class ExchangeCastOfferActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var offers: ExchangeCastOfferManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val broadcastId = intent?.getStringExtra(EXTRA_BROADCAST_ID)
        val playUrl = intent?.getStringExtra(EXTRA_PLAY_URL)
        if (broadcastId.isNullOrBlank() || playUrl.isNullOrBlank()) {
            finish()
            return
        }
        val accepted = intent.getBooleanExtra(EXTRA_ACCEPT, false)
        lifecycleScope.launch {
            offers.answer(broadcastId, accepted)
            if (accepted) startActivity(StreamPlayLaunchActivity.createIntent(this@ExchangeCastOfferActivity, playUrl))
            finish()
        }
    }

    companion object {
        private const val EXTRA_BROADCAST_ID = "extra_cast_broadcast_id"
        private const val EXTRA_PLAY_URL = "extra_cast_play_url"
        private const val EXTRA_ACCEPT = "extra_cast_accept"

        fun intent(context: Context, broadcastId: String, playUrl: String, accept: Boolean): Intent =
            Intent(context, ExchangeCastOfferActivity::class.java).apply {
                putExtra(EXTRA_BROADCAST_ID, broadcastId)
                putExtra(EXTRA_PLAY_URL, playUrl)
                putExtra(EXTRA_ACCEPT, accept)
            }
    }
}
