package com.sza.fastmediasorter.core.share

import android.app.Activity

/**
 * Per-receiver send behaviour for the unified «Send to..» menu (S0459). One implementation per
 * registered [ShareTarget], bound by Hilt `@IntoMap` keyed by [targetId]; the menu resolves the
 * handler for the chosen receiver and calls [send].
 *
 * Implementations wrap an existing invoker ([SystemShareInvoker], the print path, ..) or new send
 * code. They must read only the passed [ShareableContent] - never global UI/player state - so the
 * same handler serves every surface (player, browse, standalone).
 */
interface ShareTargetHandler {
    /** Registry key; must equal the [ShareTarget.id] this handler serves. */
    val targetId: String

    /** @return true when an Activity was launched (the receiver app or the system chooser). */
    fun send(activity: Activity, content: ShareableContent): Boolean

    /**
     * Host-capability gate (S0459 ADR-10). The three content gates ([ShareTarget.appliesTo],
     * availability, settings) live in [com.sza.fastmediasorter.domain.usecase.BuildSendToReceiverListUseCase],
     * which is pure domain and cannot see the Activity. A receiver that needs a host capability the
     * domain layer can't express (Print needs the host's [SharePrintHost]) overrides this so the menu
     * hides it on incapable hosts instead of showing a row that silently no-ops. Default: supported.
     */
    fun isSupportedBy(activity: Activity): Boolean = true
}
