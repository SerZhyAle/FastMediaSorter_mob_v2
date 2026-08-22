package com.sza.fastmediasorter.ui.launcher.gadget.di

import com.sza.fastmediasorter.ui.launcher.gadget.AudioWindowGadget
import com.sza.fastmediasorter.ui.launcher.gadget.DocumentWindowGadget
import com.sza.fastmediasorter.ui.launcher.gadget.ImageWindowGadget
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadget
import com.sza.fastmediasorter.ui.launcher.gadget.VideoWindowGadget
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S1754: the four media windows, supplied as one binding the way every other gadget family is.
 *
 * Its own qualifier rather than a second provider of [AggregatedGadgets]: that key already has exactly
 * one `@Provides`, and a second one for the same key is a duplicate binding Dagger refuses.
 * [AggregatedGadgetModule] joins this list in, which is the one place families are ordered.
 *
 * Order inside the family follows the owner's inbox: audio, video, document, image.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaWindowGadgets

@Module
@InstallIn(SingletonComponent::class)
object MediaGadgetModule {

    @Provides
    @Singleton
    @MediaWindowGadgets
    fun provideMediaWindowGadgets(
        audio: AudioWindowGadget,
        video: VideoWindowGadget,
        document: DocumentWindowGadget,
        image: ImageWindowGadget,
    ): List<LauncherGadget> = listOf(audio, video, document, image)
}
