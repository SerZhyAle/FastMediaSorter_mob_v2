package com.sza.fastmediasorter.wear.domain.repository.preferences

import com.sza.fastmediasorter.wear.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearColorScheme
import kotlinx.coroutines.flow.Flow

/** How the app looks and how the screen behaves outside the players. */
interface WearAppearancePreferences {

    /**
     * S2000: what is drawn behind the app's screens. Read from here rather than from the last
     * message received, so the watch draws the right background after a restart out of BT range.
     */
    val backgroundMode: Flow<WearBackgroundMode>
    suspend fun setBackgroundMode(mode: WearBackgroundMode)

    /**
     * S2522: the colour scheme the interface is drawn in. Read from here rather than from the last
     * message received, so the watch renders the chosen scheme after a restart out of BT range.
     */
    val colorScheme: Flow<WearColorScheme>
    suspend fun setColorScheme(scheme: WearColorScheme)

    /** S2209: disable visual transition and decorative animations across the Wear OS app. */
    val isAnimationsDisabled: Flow<Boolean>
    suspend fun setAnimationsDisabled(disabled: Boolean)

    /**
     * S2536: the charge at which the watch enters the power-saving level on its own. Stricter than
     * the animations switch above - it also freezes the branded backdrop drawn under every screen.
     *
     * The VALUE travels from the phone over the settings channel; the verdict does not. The watch
     * judges its own charge, because a paired phone at eighty percent says nothing about a watch
     * at twelve.
     */
    val powerSavingTrigger: Flow<PowerSavingTrigger>
    suspend fun setPowerSavingTrigger(trigger: PowerSavingTrigger)

    /** S1781: the players hold the screen unconditionally, so this covers only the rest of the app. */
    val keepScreenAwakeOutsidePlayers: Flow<Boolean>
    suspend fun setKeepScreenAwakeOutsidePlayers(enabled: Boolean)

    /** S1718: watch screen auto-rotation setting. Default: false (forbidden). */
    val isAutoRotationEnabled: Flow<Boolean>
    suspend fun setAutoRotationEnabled(enabled: Boolean)

    /** S1814: active app language inherited from the phone companion; null means system locale default. */
    val appLanguage: Flow<String?>
    suspend fun setAppLanguage(languageCode: String?)
}
