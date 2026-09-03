package com.sza.fastmediasorter.ui.settings.fragments

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.common.compose.FastMediaSorterComposeTheme
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import com.sza.fastmediasorter.ui.settings.WearWatchResourceEvent
import com.sza.fastmediasorter.ui.settings.helpers.BeamAnimationDialog
import com.sza.fastmediasorter.ui.wear.companion.WearCompanionScreen
import com.sza.fastmediasorter.ui.wear.companion.WearDocLink
import com.sza.fastmediasorter.ui.wearresources.WearResourceSelectionActivity
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Hosts the companion's Compose island and nothing else - the window's content lives in
 * [WearCompanionScreen], and each group of settings in its own file.
 *
 * S2000: this used to be a bottom sheet. It is now an ordinary fragment filling
 * `WearCompanionActivity`'s container, because the owner ruled the companion is a full window
 * (that spec's §3.3.1).
 */
@AndroidEntryPoint
class WearSyncSettingsFragment : Fragment() {

    private val viewModel: WearSyncViewModel by viewModels()

    // Defense in depth: the companion itself is already unreachable when the flavor lacks it
    // (S1883 - OperationsWearGroupManager hides the whole Wear OS settings group, which is where every
    // entry point to this window now lives), so this gate is the second one rather than the only one.
    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    // The island reads its colours off its own context, so it is built on the inflater's context
    // rather than the plain activity one - otherwise the window and the content inside it can
    // resolve different surfaces.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FastMediaSorterComposeTheme {
                    WearCompanionScreen(
                        viewModel = viewModel,
                        onPushClick = { launchBeamDialog() },
                        showResourceSelection = mediaCapabilities.supportsWearCompanion,
                        onSelectResourcesClick = { WearResourceSelectionActivity.start(requireContext()) },
                        onWatchResourceClick = {
                            viewModel.addOrOpenWatchResource(getString(R.string.resource_type_wear_watch))
                        },
                        onOpenDocLink = ::openDocLink
                    )
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The browser is started from here rather than from the island: an Activity launch is the
        // host's business, and the island must stay a pure function of the view model's state.
        collectOnLifecycle(viewModel.watchResourceEvents) { event ->
            when (event) {
                is WearWatchResourceEvent.Created -> toast(
                    getString(R.string.paired_watch_resource_added, event.name)
                )
                is WearWatchResourceEvent.Open ->
                    startActivity(BrowseActivity.createIntent(requireContext(), event.resourceId))
                WearWatchResourceEvent.Failed -> toast(getString(R.string.friendly_copy_error_generic))
            }
        }
    }

    // The island names a destination, never an address: resolving the locale-specific URL and
    // starting a browser are the host's business, and the browser-missing fallback already lives here
    // in the same shape the Wear settings group uses.
    private fun openDocLink(link: WearDocLink) {
        val url = when (link) {
            WearDocLink.PORTAL -> SupportIntentFactory.wearWebPortalUrl(requireContext())
            WearDocLink.INSTALL_GUIDE -> SupportIntentFactory.wearInstallGuideUrl(requireContext())
        }
        Timber.d("S2460: companion docs link tapped")
        try {
            startActivity(SupportIntentFactory.openUrl(url))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No browser to open the Wear documentation")
            toast(getString(R.string.settings_no_browser_for_docs))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun launchBeamDialog() {
        viewModel.startPush()
        if (childFragmentManager.findFragmentByTag("beam_dialog") == null) {
            BeamAnimationDialog().show(childFragmentManager, "beam_dialog")
        }
    }
}
