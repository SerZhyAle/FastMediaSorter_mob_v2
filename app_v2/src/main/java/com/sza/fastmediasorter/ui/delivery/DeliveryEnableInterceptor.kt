package com.sza.fastmediasorter.ui.delivery

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gate for every action that turns OCR/translation/DTS on (S0386 Phase 06, Pillar D). If the
 * matching [DeliverableSet] is already installed, the action proceeds immediately; otherwise the
 * set is effectively `NOT_INSTALLED`, so the download prompt is shown and the action proceeds only
 * after a successful install. Refusal or failure leaves the capability off and runs [onUnavailable]
 * so the call-site can degrade softly (e.g. keep a toggle OFF) without crashing.
 *
 * The UI-placement contract (strategic §3.3) maps each enable point to its set:
 * - OCR settings toggle, player/PDF/EPUB/text OCR triggers, camera-OCR -> [DeliverableSet.OCR_ENGINES].
 * - Translation settings toggle and translate triggers -> [DeliverableSet.TRANSLATION].
 * - DTS playback trigger -> [DeliverableSet.FFMPEG_DTS].
 * - Audio-player background visualizations -> [DeliverableSet.AUDIO_VISUALIZATIONS].
 */
@Singleton
class DeliveryEnableInterceptor @Inject constructor(
    private val capabilityRepository: DeliverableCapabilityRepository
) {

    /** Fragment-hosted enable point (e.g. a settings toggle). */
    fun requireInstalled(
        host: Fragment,
        set: DeliverableSet,
        onReady: () -> Unit,
        onUnavailable: () -> Unit = {}
    ) = gate(host.parentFragmentManager, host, set, onReady, onUnavailable)

    /** Activity-hosted enable point (e.g. a player/camera trigger driven by a Manager). */
    fun requireInstalled(
        activity: FragmentActivity,
        set: DeliverableSet,
        onReady: () -> Unit,
        onUnavailable: () -> Unit = {}
    ) = gate(activity.supportFragmentManager, activity, set, onReady, onUnavailable)

    private fun gate(
        fragmentManager: FragmentManager,
        owner: LifecycleOwner,
        set: DeliverableSet,
        onReady: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        if (capabilityRepository.isInstalledBlocking(set)) {
            onReady()
            return
        }
        fragmentManager.setFragmentResultListener(DeliveryPromptDialogFragment.REQUEST_KEY, owner) { _, bundle ->
            val outcome = runCatching {
                DeliveryPromptOutcome.valueOf(
                    bundle.getString(DeliveryPromptDialogFragment.RESULT_OUTCOME)
                        ?: DeliveryPromptOutcome.REFUSED.name
                )
            }.getOrDefault(DeliveryPromptOutcome.REFUSED)
            when (outcome) {
                DeliveryPromptOutcome.INSTALLED -> onReady()
                DeliveryPromptOutcome.REFUSED,
                DeliveryPromptOutcome.FAILED -> onUnavailable()
            }
        }
        DeliveryPromptDialogFragment.show(fragmentManager, set)
    }
}

/** Resolves the [DeliveryEnableInterceptor] for non-Hilt Manager call-sites (player/camera). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DeliveryEnableInterceptorEntryPoint {
    fun deliveryEnableInterceptor(): DeliveryEnableInterceptor
}
