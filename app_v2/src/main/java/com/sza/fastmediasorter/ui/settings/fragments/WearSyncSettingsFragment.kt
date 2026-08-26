package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.ui.common.compose.FastMediaSorterComposeTheme
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import com.sza.fastmediasorter.ui.settings.helpers.BeamAnimationDialog
import com.sza.fastmediasorter.ui.wear.companion.WearCompanionScreen
import com.sza.fastmediasorter.ui.wearresources.WearResourceSelectionActivity
import dagger.hilt.android.AndroidEntryPoint
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
                        onSelectResourcesClick = { WearResourceSelectionActivity.start(requireContext()) }
                    )
                }
            }
        }

    private fun launchBeamDialog() {
        viewModel.startPush()
        if (childFragmentManager.findFragmentByTag("beam_dialog") == null) {
            BeamAnimationDialog().show(childFragmentManager, "beam_dialog")
        }
    }
}
