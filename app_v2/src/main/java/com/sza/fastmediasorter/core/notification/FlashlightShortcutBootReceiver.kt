package com.sza.fastmediasorter.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * S2776: a notification does not survive a reboot, so the shade shortcut is re-posted here.
 *
 * Same shape as `ScheduledOperationsBootReceiver`, which re-drives its own state at boot for the same
 * reason.
 */
@AndroidEntryPoint
class FlashlightShortcutBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: FlashlightShortcutCoordinator

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // No super.onReceive: BroadcastReceiver declares it abstract, and the Hilt Gradle plugin's
        // bytecode transform is what injects the fields below - the same shape every other
        // @AndroidEntryPoint receiver in this module uses.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                coordinator.syncOnce()
            } catch (e: CancellationException) {
                // First arm on purpose: CancellationException extends IllegalStateException, so the
                // arm below would otherwise swallow a cancelled scope (S1363/S1889).
                throw e
            } catch (e: IOException) {
                Timber.w(e, "FlashlightShortcutBootReceiver: settings unreadable at boot")
            } catch (e: IllegalStateException) {
                // Safe default is the shortcut simply not appearing: the setting is untouched and the
                // next process start syncs it again, so a corrupt store must not crash the broadcast.
                Timber.w(e, "FlashlightShortcutBootReceiver: settings store unusable at boot")
            } finally {
                pending.finish()
            }
        }
    }
}
