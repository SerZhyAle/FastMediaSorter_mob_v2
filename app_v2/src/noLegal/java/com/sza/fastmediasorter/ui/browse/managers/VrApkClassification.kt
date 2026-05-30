package com.sza.fastmediasorter.ui.browse.managers

enum class VrApkSignal {
    META_OCULUS_SUPPORTED_DEVICES,
    FEATURE_VR_MODE,
    FEATURE_VR_HEADTRACKING,
}

data class VrApkClassification(
    val isVrCapable: Boolean,
    val signals: Set<VrApkSignal> = emptySet(),
) {
    companion object {
        val NOT_VR = VrApkClassification(isVrCapable = false)
    }
}