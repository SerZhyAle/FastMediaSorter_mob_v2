package com.sza.fastmediasorter.ui.launcher.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.sza.fastmediasorter.databinding.WidgetGoogleMapsLiveFrameBinding
import com.sza.fastmediasorter.util.WebMercatorTile

/**
 * S2241: Interactive live Google Maps frame view for the Launcher desktop.
 *
 * Everything shared with the other live-Web gadgets lives in [WebGadgetFrameView]; this class owns
 * its binding, the geolocation denial, the coarsened recentring and the wider banner-dismiss set the
 * maps page needs.
 */
class GoogleMapsLiveFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebGadgetFrameView(context, attrs, defStyleAttr) {

    private val binding = WidgetGoogleMapsLiveFrameBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    override val webView: WebView
        get() = binding.webViewLiveMap

    override val progressView: View
        get() = binding.progressLiveMapLoading

    override val styleElementId: String = "fms-map-style"

    override val extraHiddenSelectors: List<String> = listOf(
        ".section-hero-header-app-promo, div[data-section-id=\"mb\"]",
        ".promoted-app-banner"
    )

    override val extraDismissPhrases: List<String> = listOf("использовать веб-версию")

    override val extraDismissDelaysMs: List<Int> = listOf(LATE_RECLICK_DELAY_MS)

    init {
        attachWebView()
        webView.settings.setGeolocationEnabled(false)
        webView.webChromeClient = object : WebChromeClient() {
            /**
             * S2292: the embedded page never polls GPS on its own schedule. `setGeolocationEnabled`
             * above already disables the API, so this should never fire - it stays as the explicit
             * denial that keeps a later edit of that settings block from silently restoring the
             * grant. Centring does not depend on it: [updateLocation] loads an already-centred URL.
             */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, false, false)
            }
        }
        binding.btnRecenterMap.setOnClickListener {
            val lat = lastLatitude
            val lng = lastLongitude
            if (lat != null && lng != null) {
                loadMapUrl(mapUrlFor(lat, lng))
            } else {
                loadMapUrl(DEFAULT_MAP_URL)
            }
        }
        loadMapUrl(DEFAULT_MAP_URL)
    }

    /**
     * S2292: the position is coarsened to the centre of its [DEFAULT_ZOOM] Web Mercator tile before
     * anything stores or publishes it - the same precision class the static map gadget already ships
     * through `OsmMapTileProvider`. The precise value is deliberately never written to a field: the
     * recenter button reads [lastLatitude] and [lastLongitude], so keeping it would give the exact
     * coordinate a second route into a Google URL.
     */
    fun updateLocation(latitude: Double, longitude: Double) {
        val tileLatitude = WebMercatorTile.coarseLatitude(latitude, DEFAULT_ZOOM)
        val tileLongitude = WebMercatorTile.coarseLongitude(longitude, DEFAULT_ZOOM)
        lastLatitude = tileLatitude
        lastLongitude = tileLongitude
        loadMapUrl(mapUrlFor(tileLatitude, tileLongitude))
    }

    fun loadMapUrl(url: String) {
        loadUrl(url)
    }

    private fun mapUrlFor(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/@$latitude,$longitude,${DEFAULT_ZOOM}z"

    companion object {
        const val DEFAULT_MAP_URL = "https://www.google.com/maps"
        private const val DEFAULT_ZOOM = 15
        private const val LATE_RECLICK_DELAY_MS = 1800
    }
}
