package com.sza.fastmediasorter.ui.broadcast

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.core.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BroadcastShareActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BroadcastControlActivity.launch(this)
        finish()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MODE = "extra_mode"

        private var lastLaunchedUrl: String? = null

        @Suppress("UnusedParameter")
        fun launch(context: Context, url: String, title: String? = null, mode: String = "AUDIO_ONLY") {
            lastLaunchedUrl = url
            BroadcastControlActivity.launch(context)
        }

        @Suppress("UnusedParameter")
        fun launchIfNew(context: Context, url: String, title: String? = null, mode: String = "AUDIO_ONLY") {
            if (lastLaunchedUrl == url) return
            launch(context, url, title, mode)
        }

        fun resetLaunchTracking() {
            lastLaunchedUrl = null
        }
    }
}
