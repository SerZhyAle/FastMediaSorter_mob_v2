package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.data.delivery.DeliveredNativeLibraryLoader
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitTextTranslationFacadeFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val capabilityRepository: DeliverableCapabilityRepository,
    private val libraryLoader: DeliveredNativeLibraryLoader
) : TextTranslationFacadeFactory {
    override fun create(callback: TranslationManager.TranslationCallback): TextTranslationFacade {
        return TranslationBackend(context, callback, settingsRepository, capabilityRepository, libraryLoader)
    }
}
