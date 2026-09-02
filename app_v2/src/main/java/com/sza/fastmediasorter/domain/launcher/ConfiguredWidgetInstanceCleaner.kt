package com.sza.fastmediasorter.domain.launcher

/**
 * S2217: seam for throwing away the stored instance behind a configured widget cell's `target`.
 *
 * The launcher reset cannot call the gadget codec directly: the codec lives in the launcher flavor
 * source set while the reset use case compiles in every flavor. So this mirrors the
 * [LauncherModeContract] pattern - the flavors that ship the launcher surface bind the real
 * implementation, the rest bind a no-op, and `src/main` never guards on a flag.
 *
 * The caller passes a raw cell `target` column and knows nothing about which gadgets configure
 * anything; every guard (decoding, token range) lives behind the seam, so a target that names no
 * configured widget is a no-op.
 */
fun interface ConfiguredWidgetInstanceCleaner {
    fun clearInstanceOf(target: String?)
}
