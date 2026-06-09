package com.sza.fastmediasorter.delivery

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.NoOpTextTranslationFacade
import com.sza.fastmediasorter.ui.player.helpers.TextTranslationFacade
import com.sza.fastmediasorter.ui.player.helpers.TextTranslationFacadeFactory
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicTextTranslationFacadeFactory @Inject constructor(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val capabilityRepository: DeliverableCapabilityRepository
) : TextTranslationFacadeFactory {

    override fun create(callback: TranslationManager.TranslationCallback): TextTranslationFacade {
        if (!capabilityRepository.isInstalledBlocking(DeliverableSet.TRANSLATION)) {
            Timber.i("Translation dynamic feature is not installed. Returning No-Op facade.")
            return NoOpTextTranslationFacade()
        }

        return try {
            val splitInstallManager = SplitInstallManagerFactory.create(context)
            if (!splitInstallManager.installedModules.contains("translate_feature")) {
                Timber.w("translate_feature split is marked installed in repository but not in SplitInstallManager. Returning No-Op.")
                return NoOpTextTranslationFacade()
            }

            // Load class via reflection from dynamic feature module
            val clazz = Class.forName("com.sza.fastmediasorter.ui.player.helpers.MlKitTextTranslationFacadeFactory")
            val factory = clazz.getDeclaredConstructor(
                Context::class.java,
                SettingsRepository::class.java,
                DeliverableCapabilityRepository::class.java
            ).newInstance(context, settingsRepository, capabilityRepository) as TextTranslationFacadeFactory

            factory.create(callback)
        } catch (e: Exception) {
            Timber.e(e, "Error instantiating translation backend from dynamic feature")
            NoOpTextTranslationFacade()
        }
    }
}