package com.sza.fastmediasorter.delivery

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.model.TranslationModelPrewarmStatus
import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmer
import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicTranslationModelPrewarmer @Inject constructor(
    private val context: Context,
    private val capabilityRepository: DeliverableCapabilityRepository,
    @TranslationModelPrewarmEnabled private val enabledMarkers: Set<@JvmSuppressWildcards String>
) : TranslationModelPrewarmer {

    private val _status = MutableStateFlow<TranslationModelPrewarmStatus>(TranslationModelPrewarmStatus.Idle)
    override val status: StateFlow<TranslationModelPrewarmStatus> = _status.asStateFlow()

    private var delegate: TranslationModelPrewarmer? = null

    override suspend fun prewarm(targetSettingsCode: String) {
        if (!capabilityRepository.isInstalledBlocking(DeliverableSet.TRANSLATION)) {
            _status.value = TranslationModelPrewarmStatus.Idle
            return
        }

        try {
            val delegate = getOrCreateDelegate()
            if (delegate != null) {
                delegate.prewarm(targetSettingsCode)
                _status.value = delegate.status.value
            } else {
                _status.value = TranslationModelPrewarmStatus.Idle
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during dynamic translation prewarm")
            _status.value = TranslationModelPrewarmStatus.Failed(targetSettingsCode)
        }
    }

    private fun getOrCreateDelegate(): TranslationModelPrewarmer? {
        if (delegate != null) return delegate

        val splitInstallManager = SplitInstallManagerFactory.create(context)
        if (!splitInstallManager.installedModules.contains("translate_feature")) {
            return null
        }

        return try {
            val clazz = Class.forName("com.sza.fastmediasorter.domain.usecase.PrewarmTranslationModelUseCase")
            val instance = clazz.getDeclaredConstructor(Set::class.java).newInstance(enabledMarkers) as TranslationModelPrewarmer
            delegate = instance
            instance
        } catch (e: Exception) {
            Timber.e(e, "Error instantiating PrewarmTranslationModelUseCase from dynamic feature")
            null
        }
    }
}