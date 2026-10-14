package com.sza.fastmediasorter.data.remote.sftp.server

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.di.MainDispatcher
import com.sza.fastmediasorter.data.repository.settings.SftpServerSettingsStore
import com.sza.fastmediasorter.domain.model.SftpServerFailure
import com.sza.fastmediasorter.domain.model.SftpServerState
import com.sza.fastmediasorter.service.SftpServerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings the phone's sharing back each time the app comes to the foreground (contract DEVICE-EXCHANGE 26 item DB,
 * owner decision 16 of S4203): a cold start, a return from the background and every return to the launcher home
 * screen. The stored `enabled` is the start intent - only the person's own "off" clears it - so a share that the
 * data-sync daily limit paused, a swipe from recents stopped or a dead process lost resumes here.
 *
 * The foreground is also what resets the platform's data-sync timer, and a foreground service may be started
 * only while the app is visible, so this is the one trigger: no boot receiver, no periodic job. Each foreground
 * retries once; a failure only the person can fix (no folder, no credential) is left for them.
 */
@Singleton
class SftpServerResumeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: SftpServerController,
    private val settingsStore: SftpServerSettingsStore,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {

    private val started = AtomicBoolean(false)

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            scope.launch(ioDispatcher) { onForeground() }
        }
    }

    /** Idempotent; the observer is added on the main thread, which `LifecycleRegistry` requires. */
    suspend fun start() {
        if (!controller.isAvailable() || !started.compareAndSet(false, true)) return
        withContext(mainDispatcher) { ProcessLifecycleOwner.get().lifecycle.addObserver(observer) }
    }

    private suspend fun onForeground() {
        val state = controller.state.value
        Timber.d("S4225: foreground, share state on resume check")
        if (state is SftpServerState.Running) {
            controller.refreshAddresses()
            return
        }
        if (!isRetryable(state) || !settingsStore.snapshot().enabled) return
        try {
            SftpServerService.start(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the app left the foreground before the read above
            // finished; the next foreground tries again.
            Timber.w(e, "SftpServerResumeCoordinator: sharing not resumed, the app is no longer visible")
        }
    }

    private fun isRetryable(state: SftpServerState): Boolean = when (state) {
        SftpServerState.Stopped, is SftpServerState.Paused -> true
        is SftpServerState.Failed ->
            state.reason == SftpServerFailure.START_FAILED || state.reason == SftpServerFailure.PORT_IN_USE
        SftpServerState.Starting, is SftpServerState.Running -> false
    }
}
