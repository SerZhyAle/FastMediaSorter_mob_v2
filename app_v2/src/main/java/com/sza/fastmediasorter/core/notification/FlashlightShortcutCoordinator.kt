package com.sza.fastmediasorter.core.notification

import android.content.Context
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.screencapture.gesture.DeviceActionHandler
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2776: the only caller of [FlashlightShortcutNotifier].
 *
 * The shade entry exists exactly while the owner's setting says so, so a single collector decides it
 * rather than every writer of that setting having to remember the notification.
 */
@Singleton
class FlashlightShortcutCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val notifier: FlashlightShortcutNotifier,
    private val deviceActionHandler: DeviceActionHandler,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    /** Idempotent: a second call must not add a second collector. */
    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            settingsRepository.getSettings()
                .map { it.flashlightShortcutNotificationEnabled }
                .distinctUntilChanged()
                .collect { apply(it) }
        }
    }

    /** One-shot form for a caller that cannot wait for the collector, such as the boot receiver. */
    suspend fun syncOnce() {
        apply(settingsRepository.getSettings().first().flashlightShortcutNotificationEnabled)
    }

    private fun apply(enabled: Boolean) {
        if (enabled && notifier.hasFlashUnit()) {
            notifier.show()
            return
        }
        // Taking the entry away also puts the flash out: it was the only control left for it. Guarded
        // on the entry having been posted, because a torch lit by the water flashlight or an edge
        // gesture belongs to that surface, and this path must not reach across and extinguish it.
        if (notifier.isShown && deviceActionHandler.isTorchOn) {
            deviceActionHandler.setTorch(context, false)
        }
        notifier.hide()
    }
}
