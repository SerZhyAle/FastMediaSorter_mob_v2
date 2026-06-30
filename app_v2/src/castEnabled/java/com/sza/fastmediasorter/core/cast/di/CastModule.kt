package com.sza.fastmediasorter.core.cast.di

import android.content.Context
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.cast.CastController
import com.sza.fastmediasorter.core.cast.CastControllerFactory
import com.sza.fastmediasorter.core.cast.CastMediaManagerImpl
import com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Hilt bindings for the `castEnabled` source set (strategic S0403).
 *
 * Provides a [CastControllerFactory] that constructs the GMS-backed [CastMediaManagerImpl]. The
 * application context and flavor-resolved [MediaCapabilities] come from the singleton graph; the
 * Activity-scoped arguments (lifecycle scope, [NetworkFileManager], casting callback) are supplied
 * at [CastControllerFactory.create] time.
 *
 * Paired with the same-named module in `src/castDisabled/java/`. AGP mounts exactly one of the two
 * per flavor, mirroring `XrModule`/`NoOpXrModule`, so no duplicate-binding conflict is possible.
 */
@Module
@InstallIn(SingletonComponent::class)
object CastModule {

    @Provides
    @Singleton
    fun provideCastControllerFactory(
        @ApplicationContext context: Context,
        mediaCapabilities: MediaCapabilities
    ): CastControllerFactory = object : CastControllerFactory {
        override fun create(
            lifecycleScope: CoroutineScope,
            networkFileManager: NetworkFileManager,
            onCastStateChanged: (isCasting: Boolean, deviceName: String?) -> Unit
        ): CastController = CastMediaManagerImpl(
            context = context,
            lifecycleScope = lifecycleScope,
            networkFileManager = networkFileManager,
            mediaCapabilities = mediaCapabilities,
            onCastStateChanged = onCastStateChanged
        )
    }
}
