package com.sza.fastmediasorter.ui.common.help

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityHelpPageBinding
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.common.support.DocsPageOpenManager
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.utils.HelpPageBodyLoader
import com.sza.fastmediasorter.utils.getStatusBarHeightSafe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Offline help page viewer (S4288 ADR-1): a bundled `assets/help/` page in a WebView with JavaScript
 * off and no network, plus the "open on the site" link to the published twin of the same page.
 */
@AndroidEntryPoint
class HelpPageActivity : BaseActivity<ActivityHelpPageBinding>() {

    private val assetUrl: String by lazy { intent.getStringExtra(EXTRA_ASSET_URL).orEmpty() }
    private val siteUrl: String by lazy { intent.getStringExtra(EXTRA_SITE_URL).orEmpty() }
    private val surface: UiSurface? by lazy {
        intent.getStringExtra(EXTRA_SURFACE)?.let { name -> UiSurface.entries.firstOrNull { it.name == name } }
    }

    // The header mirrors through the layout direction; the WebView body needs the same forced on the page.
    private val mirrorPageBody: Boolean
        get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val webView = binding.helpWebView
            if (webView.canGoBack()) webView.goBack() else finish()
        }
    }

    override fun getViewBinding(): ActivityHelpPageBinding = ActivityHelpPageBinding.inflate(layoutInflater)

    override fun setupViews() {
        applyEdgeToEdgeInsets()
        setupHeader()
        setupWebView()
        onBackPressedDispatcher.addCallback(this, backCallback)
        if (assetUrl.startsWith(HelpTargetResolver.ASSET_ROOT)) {
            loadHelpPage()
        } else {
            Timber.w("Help page: refusing a non-bundled start URL")
            finish()
        }
    }

    override fun observeData() = Unit

    override fun getInputHelpSurface(): UiSurface? = surface

    override fun getInitialFocusView() = binding.helpWebView

    private fun setupHeader() {
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnOpenOnSite.isVisible = siteUrl.isNotBlank()
        binding.btnOpenOnSite.setOnClickListener { DocsPageOpenManager.open(this, siteUrl) }
        val shortcutsSurface = surface
        binding.btnKeyboardShortcuts.isVisible = shortcutsSurface != null
        if (shortcutsSurface != null) {
            binding.btnKeyboardShortcuts.setOnClickListener {
                InputHelpDialogFragment.show(supportFragmentManager, shortcutsSurface)
            }
        } else {
            // The XML chain runs through the shortcuts button; skip it while it is gone.
            binding.backButton.nextFocusRightId = binding.btnOpenOnSite.id
            binding.btnOpenOnSite.nextFocusLeftId = binding.backButton.id
        }
    }

    private fun setupWebView() {
        val webView = binding.helpWebView
        webView.setBackgroundColor(MaterialColors.getColor(webView, com.google.android.material.R.attr.colorSurface))
        webView.settings.apply {
            javaScriptEnabled = false
            blockNetworkLoads = true
            allowContentAccess = false
            // Asset URLs stay readable: allowFileAccess does not gate file:///android_asset/.
            allowFileAccess = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val external = !url.startsWith(HelpTargetResolver.ASSET_ROOT)
                if (external) confirmExternalLink(url)
                return external
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                // WebView reports the URL itself as the title of a page without <title>.
                val pageTitle = title?.takeUnless { it.isBlank() || it.startsWith(FILE_SCHEME) }
                binding.titleText.text = pageTitle ?: getString(R.string.help_page_title)
            }
        }
    }

    // A WebView never calls shouldInterceptRequest for android_asset, so the mirrored body is read,
    // patched and served through loadDataWithBaseURL; a plain locale keeps the direct asset load.
    private fun loadHelpPage() {
        val webView = binding.helpWebView
        if (!mirrorPageBody) {
            webView.loadUrl(assetUrl)
            return
        }
        lifecycleScope.launch {
            val html = HelpPageBodyLoader.load(this@HelpPageActivity, assetUrl, mirrorBody = true)
            if (html == null) {
                webView.loadUrl(assetUrl)
                return@launch
            }
            webView.loadDataWithBaseURL(assetUrl, html, "text/html", "utf-8", assetUrl)
        }
    }

    private fun confirmExternalLink(url: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_external_link_title)
            .setMessage(R.string.help_external_link_message)
            .setPositiveButton(R.string.action_open) { _, _ -> DocsPageOpenManager.open(this, url) }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(this)
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyWindowInsets(insets)
            insets
        }
        // setupViews() runs inside post{}, after the first insets dispatch: apply the cached ones now.
        val current = ViewCompat.getRootWindowInsets(binding.root)
        if (current != null) applyWindowInsets(current) else ViewCompat.requestApplyInsets(binding.root)
    }

    private fun applyWindowInsets(insets: WindowInsetsCompat) {
        val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val top = maxOf(insets.getStatusBarHeightSafe(resources), safe.top)
        binding.titleRow.setPadding(safe.left, top, safe.right, binding.titleRow.paddingBottom)
        binding.webContainer.setPadding(safe.left, 0, safe.right, safe.bottom)
    }

    override fun onDestroy() {
        // BaseActivity.onDestroy drops the binding, so the WebView is released first.
        val webView = binding.helpWebView
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ASSET_URL = "help_asset_url"
        private const val EXTRA_SITE_URL = "help_site_url"
        private const val EXTRA_SURFACE = "help_surface"
        private const val FILE_SCHEME = "file:"

        fun intent(context: Context, assetUrl: String, siteUrl: String, surfaceName: String?): Intent =
            Intent(context, HelpPageActivity::class.java)
                .putExtra(EXTRA_ASSET_URL, assetUrl)
                .putExtra(EXTRA_SITE_URL, siteUrl)
                .putExtra(EXTRA_SURFACE, surfaceName)
    }
}
