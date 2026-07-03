package com.sza.fastmediasorter.core.cast.di

import com.sza.fastmediasorter.core.cast.CastController
import com.sza.fastmediasorter.core.cast.CastControllerFactory
import com.sza.fastmediasorter.core.cast.NoOpCastController
import com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Hilt binding for the `castDisabled` source set (strategic S0403, FOSS flavor).
 *
 * Provides a [CastControllerFactory] that ignores its runtime arguments and returns the inert
 * [NoOpCastController]. The paired `castEnabled` module supplies the GMS-backed factory - AGP
 * mounts exactly one of the two per flavor (see `app_v2/build.gradle.kts` `sourceSets`), so there
 * is no duplicate-binding conflict.
 */
@Module
@InstallIn(SingletonComponent::class)
object CastModule {

    @Provides
    @Singleton
    fun provideCastControllerFactory(): CastControllerFactory = object : CastControllerFactory {
        override fun create(
            lifecycleScope: CoroutineScope,
            networkFileManager: NetworkFileManager,
            onCastStateChanged: (isCasting: Boolean, deviceName: String?) -> Unit
        ): CastController = NoOpCastController()
    }
}
