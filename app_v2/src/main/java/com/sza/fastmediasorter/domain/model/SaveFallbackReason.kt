package com.sza.fastmediasorter.domain.model

/**
 * Single fallback-reason vocabulary shared by every save flow (screenshot, internet download,
 * video frame, camera photo/video, microphone recording). [ResourceUnavailable] and
 * [ResourceWriteFailed] are the user-visible cases that trigger a notification;
 * [NoResourceConfigured] is the silent "no destination chosen, write to default folder" case.
 */
enum class SaveFallbackReason {
    NoResourceConfigured,
    ResourceUnavailable,
    ResourceWriteFailed,
}
