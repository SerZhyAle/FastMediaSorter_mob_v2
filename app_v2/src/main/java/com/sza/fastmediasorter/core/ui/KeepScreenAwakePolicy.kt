package com.sza.fastmediasorter.core.ui

import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.core.util.PowerPolicyLevel

/**
 * S3285: the single answer to "must this window keep the screen on", shared by [BaseActivity] and
 * [AppKeepScreenAwakeManager].
 *
 * The two paths cover disjoint halves of the app - the base class its own subclasses, the lifecycle
 * callback every other host - and each carried its own copy of the expression. A copy is what let a
 * screen drift out of the global setting without anything failing, so the decision is stated once
 * here and both callers read it. Pure and free of Android types, so it is unit-testable.
 */
object KeepScreenAwakePolicy {

    /**
     * S2536: the global hold stands down in the power-saving level, whatever the user setting says.
     * The level is passed in rather than read here so a caller that already has it (or a test) does
     * not depend on process-wide state.
     */
    fun shouldKeepScreenAwake(
        preventSleep: Boolean,
        level: PowerPolicyLevel = AnimationPolicy.level,
    ): Boolean = preventSleep && level != PowerPolicyLevel.SAVING
}
