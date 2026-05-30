package com.sza.fastmediasorter.domain.model

object MidiPlaybackPolicy {
    val SUPPORTED_EXTENSIONS: Set<String> = setOf("mid", "midi")

    fun isMidiExtension(extension: String): Boolean {
        return extension.lowercase() in SUPPORTED_EXTENSIONS
    }

    fun isMidiPath(path: String): Boolean {
        val cleanPath = path
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val extension = cleanPath.substringAfterLast('.', missingDelimiterValue = "")
        return isMidiExtension(extension)
    }
}