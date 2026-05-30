package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.sza.fastmediasorter.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VrApkClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun classify(file: File): VrApkClassification = withContext(ioDispatcher) {
        if (!file.exists() || !file.isFile) {
            return@withContext VrApkClassification.NOT_VR
        }

        val packageInfo = try {
            readPackageArchive(file.absolutePath)
        } catch (error: Exception) {
            Timber.i(error, "VR APK badge: archive parse failed for ${file.name}")
            return@withContext VrApkClassification.NOT_VR
        } ?: return@withContext VrApkClassification.NOT_VR

        // PackageManager needs the real archive path on ApplicationInfo so manifest-backed
        // metadata stays addressable when the package is not installed on the device.
        packageInfo.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }

        val signals = linkedSetOf<VrApkSignal>()
        if (packageInfo.applicationInfo?.metaData?.containsKey(OCULUS_SUPPORTED_DEVICES_KEY) == true) {
            signals += VrApkSignal.META_OCULUS_SUPPORTED_DEVICES
        }
        packageInfo.reqFeatures
            ?.mapNotNull { it?.name }
            ?.forEach { featureName ->
                when (featureName) {
                    VR_MODE_FEATURE -> signals += VrApkSignal.FEATURE_VR_MODE
                    VR_HEADTRACKING_FEATURE -> signals += VrApkSignal.FEATURE_VR_HEADTRACKING
                }
            }

        return@withContext VrApkClassification(
            isVrCapable = signals.isNotEmpty(),
            signals = signals,
        )
    }

    @Suppress("DEPRECATION")
    private fun readPackageArchive(apkPath: String) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageArchiveInfo(
            apkPath,
            PackageManager.PackageInfoFlags.of(ARCHIVE_INFO_FLAGS.toLong()),
        )
    } else {
        context.packageManager.getPackageArchiveInfo(apkPath, ARCHIVE_INFO_FLAGS)
    }

    private companion object {
        private const val OCULUS_SUPPORTED_DEVICES_KEY = "com.oculus.supportedDevices"
        private const val VR_MODE_FEATURE = "android.software.vr.mode"
        private const val VR_HEADTRACKING_FEATURE = "android.hardware.vr.headtracking"
        private const val ARCHIVE_INFO_FLAGS =
            PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS
    }
}