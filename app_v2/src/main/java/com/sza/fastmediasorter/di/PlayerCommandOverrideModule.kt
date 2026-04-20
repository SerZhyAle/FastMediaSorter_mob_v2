package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.ui.player.commands.FullscreenCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SaveFrameCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SystemUiCommandOverride
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Optional player command overrides. VR binds concrete replacements while other flavors keep
 * the shared callback behavior without needing flavor checks in main source.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerCommandOverrideModule {

    @BindsOptionalOf
    abstract fun bindFullscreenCommandOverride(): FullscreenCommandOverride

    @BindsOptionalOf
    abstract fun bindSaveFrameCommandOverride(): SaveFrameCommandOverride

    @BindsOptionalOf
    abstract fun bindSystemUiCommandOverride(): SystemUiCommandOverride
}