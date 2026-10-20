package com.sza.fastmediasorter.core.systeminfo

/**
 * Locale-independent identity of a System info group.
 *
 * Section titles are localized and flavor sections may reuse a base title ("Network"), so neither the
 * remembered expanded state nor the group icon can key on the title; [key] is what both use.
 * Pure data, no Android dependencies.
 */
enum class SystemInfoGroup(val key: String) {
    DEVICE("device"),
    USER("user"),
    OS("os"),
    APP("app"),
    HARDWARE("hardware"),
    CAMERAS("cameras"),
    BATTERY("battery"),
    MEMORY("memory"),
    STORAGE("storage"),
    NETWORK("network"),
    DISPLAY("display"),
    LOCALE("locale"),
    TIME("time"),
    SYSTEM("system"),
    BENCHMARKS("benchmarks"),
    OS_SECURITY("os_security"),
    PERMISSIONS("permissions"),
    INSTALLER_SIGNATURE("installer_signature"),
    RUNTIMES("runtimes"),
    MOUNTS("mounts"),
    NETWORK_DIAGNOSTICS("network_diagnostics"),
    PROCESS_RESOURCES("process_resources"),
}
