package com.sza.fastmediasorter.core.di

import android.content.Context
import com.sza.fastmediasorter.FastMediaSorterApp
import com.sza.fastmediasorter.core.notification.FlashlightShortcutCoordinator
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

/**
 * Dependencies of the manifest `BOOT_COMPLETED` receivers, resolved by hand instead of through
 * `@AndroidEntryPoint`: the generated receiver injects before its own code runs, so a boot broadcast
 * delivered into an instrumentation process under `HiltTestApplication` - where no graph exists
 * until a test's `HiltAndroidRule` builds one - crashed the whole Hilt self-test pass (S4014).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootReceiverEntryPoint {

    fun workManagerScheduler(): WorkManagerScheduler

    fun settingsRepository(): SettingsRepository

    fun flashlightShortcutCoordinator(): FlashlightShortcutCoordinator

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}

/**
 * Boot re-drive is meaningful only in the production application; any other application (a test
 * one) gets `null` and the broadcast is dropped.
 */
fun bootReceiverEntryPointOrNull(context: Context, caller: String): BootReceiverEntryPoint? {
    val app = context.applicationContext
    if (app !is FastMediaSorterApp) {
        Timber.w("%s: boot broadcast skipped - not the production application (%s)", caller, app.javaClass.name)
        return null
    }
    return EntryPointAccessors.fromApplication(app, BootReceiverEntryPoint::class.java)
}
