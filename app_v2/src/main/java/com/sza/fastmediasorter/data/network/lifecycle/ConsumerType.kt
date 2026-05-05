package com.sza.fastmediasorter.data.network.lifecycle

/**
 * Identifies the consumer that owns a network connection lease. S0067.
 *
 * UI flavors are closed by [NetworkLifecycleObserver] on `ProcessLifecycleOwner.ON_STOP`.
 * [BACKGROUND_WORKER]-tagged connections survive backgrounding until the worker completes.
 */
enum class ConsumerType {
    UI_SCANNER,
    UI_PLAYER,
    UI_OPERATION,
    BACKGROUND_WORKER;

    val isUi: Boolean get() = this != BACKGROUND_WORKER
}
