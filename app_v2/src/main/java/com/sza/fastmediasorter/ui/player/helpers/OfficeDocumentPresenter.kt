package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.view.View
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import com.sza.fastmediasorter.util.showBoundToHost
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S1960: the Office-document scenario, lifted out of [StandaloneViewManager]. It is the one thing
 * that class hosted which is not a facet of showing media itself - "open a file the app does not
 * draw": its own fallback dialog, its own hand-off to another app, its own selection translation
 * and its own dependency ([SendToMenuManager]), which travels with it.
 *
 * The heavy collaborators arrive as suppliers, not instances: [StandaloneViewManager] builds them
 * lazily and keeps owning them, because the document viewer managers share the very same objects.
 * A copy here would double the debt instead of moving it.
 */
class OfficeDocumentPresenter(
    private val activity: Activity,
    root: View,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val networkFileManager: () -> NetworkFileManager,
    private val translationManager: () -> TranslationManager,
    private val callback: Callback,
) {

    /** What the presenter does not own: the host chrome, the toast surface, the translation dialog. */
    interface Callback {
        fun showError(message: String)
        fun showTranslatedText(text: String)
        fun onEnterFullscreenMode()
        fun onExitFullscreenMode()
    }

    // S0380/S1549: the host layout root is replaced on re-inflate, so it cannot be captured once.
    private var currentRoot: View = root

    // S0301 Phase 02: flavor-safe Office viewer decision provider for standalone mode.
    // Market flavors delegate externally (S0299); noLegal plugs an embedded viewer in Phase 03.
    private val viewerProvider by lazy {
        OfficeDocumentViewerProviderFactory().create()
    }

    // S0301 Phase 03: embedded Office viewer host. Market = no-op host, noLegal = engine-backed
    // read-only in-app viewer. Fullscreen toggling is delegated back to the host screen.
    private val viewerHostDelegate = lazy {
        OfficeDocumentViewerProviderFactory().createViewerHost(
            root = currentRoot,
            coroutineScope = lifecycleScope,
            callback = object : OfficeDocumentViewerHost.Callback {
                override fun showError(message: String) = callback.showError(message)
                override fun onEnterFullscreenMode() = callback.onEnterFullscreenMode()
                override fun onExitFullscreenMode() = callback.onExitFullscreenMode()
                override fun onTranslateSelection(text: String) = translateSelection(text)
                override fun onRequireExternalFallback(mediaFile: MediaFile) {
                    // S0301 Phase 05: engine could not render internally - show the explicit
                    // external / share / cancel dialog. Cancel keeps the current screen open per
                    // the approved fallback contract.
                    showFallbackDialog(mediaFile)
                }
            },
        )
    }
    private val viewerHost: OfficeDocumentViewerHost by viewerHostDelegate

    fun display(mediaFile: MediaFile) {
        val session = viewerProvider.resolve(mediaFile)
        Timber.d("S1960: office display ${mediaFile.name} outcome=${session.outcome}")
        when (session.outcome) {
            OfficeDocumentViewerOutcome.DISPLAY_INTERNALLY ->
                displayInternally(mediaFile)
            // S0301 Phase 05: provider asked for the explicit external / share / cancel dialog.
            // Cancel keeps the standalone screen open, matching the shared Office UX contract.
            OfficeDocumentViewerOutcome.SHOW_FALLBACK_DIALOG ->
                showFallbackDialog(mediaFile)
            OfficeDocumentViewerOutcome.DELEGATE_EXTERNAL ->
                displayExternally(mediaFile, finishAfterHandoff = true)
        }
    }

    /** S1549: re-point the embedded viewer at a re-inflated hierarchy; no-op before first use. */
    fun rebindLayoutRoot(newRoot: View) {
        currentRoot = newRoot
        if (viewerHostDelegate.isInitialized()) {
            viewerHost.rebindLayoutRoot(newRoot)
        }
    }

    /** The active Office WebView selection callback, or null when no Office document is shown. */
    fun selectionActionModeCallback(): DocumentSelectionActionModeCallback? {
        if (!viewerHostDelegate.isInitialized() || !viewerHost.isActive) return null
        return viewerHost.getSelectionActionModeCallback()
    }

    /**
     * Explicit Office fallback dialog for standalone mode (S0301 Phase 05). Offers opening in
     * another app or sharing the file. Cancel keeps the screen open per the approved fallback
     * contract. Strings stay factual (COMMUNICATION_POLICY section 6).
     */
    private fun showFallbackDialog(mediaFile: MediaFile) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.office_viewer_fallback_title)
            .setMessage(R.string.office_viewer_fallback_message)
            .setPositiveButton(R.string.office_viewer_fallback_open_external) { _, _ ->
                displayExternally(mediaFile, finishAfterHandoff = true)
            }
            .setNeutralButton(R.string.office_viewer_fallback_share) { _, _ ->
                share(mediaFile)
            }
            .setNegativeButton(R.string.office_viewer_fallback_cancel, null)
            .showBoundToHost(activity)
    }

    /**
     * Share the prepared Office [mediaFile] through the unified send-to menu (S0459 Phase 06, was a
     * standalone ACTION_SEND chooser in S0301 Phase 05). prepareFileForRead keeps network fetches
     * off the main thread; the resulting FileProvider Uri is handed to the menu.
     */
    // Any failure of an external file hand-off must degrade to a message or the fallback dialog,
    // never crash the screen the user opened from another app - so the catch is deliberately wide.
    // Cancellation is rethrown first: it is the coroutine being torn down, not a failure (S1910).
    @Suppress("TooGenericExceptionCaught")
    private fun share(mediaFile: MediaFile) {
        val host = activity as? FragmentActivity ?: run {
            Timber.w("Office share host is not a FragmentActivity - cannot open send-to menu")
            return
        }
        lifecycleScope.launch {
            try {
                val preparedFile = networkFileManager().prepareFileForRead(mediaFile)
                val uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    preparedFile
                )
                val settings = settingsRepository.getSettings().first()
                val content = ShareableContent(
                    uris = listOf(uri),
                    mime = "application/octet-stream",
                    mediaType = MediaType.OFFICE_DOCUMENT,
                    displayName = mediaFile.name,
                    mediaFile = mediaFile,
                )
                sendToMenuManager().show(host, content, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "OfficeDocumentPresenter: failed to share Office document")
                callback.showError(activity.getString(R.string.error_opening_file_simple))
            }
        }
    }

    /** S0459: app-scoped accessor for [SendToMenuManager] (Singleton) from a manually-built helper. */
    private fun sendToMenuManager(): SendToMenuManager =
        EntryPointAccessors
            .fromApplication(activity.applicationContext, SendToMenuEntryPoint::class.java)
            .sendToMenuManager()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface SendToMenuEntryPoint {
        fun sendToMenuManager(): SendToMenuManager
    }

    /**
     * Render an Office document inside the in-app viewer (S0301 Phase 03, noLegal standalone).
     * Falls back to the explicit Office dialog when the host cannot accept the prepared file.
     */
    // Any failure of an external file hand-off must degrade to a message or the fallback dialog,
    // never crash the screen the user opened from another app - so the catch is deliberately wide.
    // Cancellation is rethrown first: it is the coroutine being torn down, not a failure (S1910).
    @Suppress("TooGenericExceptionCaught")
    private fun displayInternally(mediaFile: MediaFile) {
        lifecycleScope.launch {
            try {
                val preparedFile = networkFileManager().prepareFileForRead(mediaFile)
                val started = viewerHost.open(mediaFile, preparedFile)
                if (!started) showFallbackDialog(mediaFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "OfficeDocumentPresenter: failed to render Office document internally")
                showFallbackDialog(mediaFile)
            }
        }
    }

    // Any failure of an external file hand-off must degrade to a message or the fallback dialog,
    // never crash the screen the user opened from another app - so the catch is deliberately wide.
    // Cancellation is rethrown first: it is the coroutine being torn down, not a failure (S1910).
    @Suppress("TooGenericExceptionCaught")
    private fun displayExternally(mediaFile: MediaFile, finishAfterHandoff: Boolean) {
        lifecycleScope.launch {
            try {
                val preparedFile = networkFileManager().prepareFileForRead(mediaFile)
                val opened = OfficeDocumentOpenManager.openPreparedFile(
                    activity = activity,
                    file = preparedFile,
                    displayName = mediaFile.name
                )
                if (!opened) {
                    callback.showError(activity.getString(R.string.no_app_to_open))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "OfficeDocumentPresenter: failed to open Office document externally")
                callback.showError(activity.getString(R.string.error_opening_file_simple))
            } finally {
                if (finishAfterHandoff) activity.finish()
            }
        }
    }

    private fun translateSelection(text: String) {
        if (text.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
            val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
            val translated = translationManager().translate(text, sourceLang, targetLang)
            withContext(Dispatchers.Main) {
                if (translated != null) {
                    callback.showTranslatedText(translated)
                } else {
                    callback.showError(activity.getString(R.string.translation_error))
                }
            }
        }
    }
}
