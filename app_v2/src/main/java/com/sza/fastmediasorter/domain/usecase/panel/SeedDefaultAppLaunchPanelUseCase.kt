package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import com.sza.fastmediasorter.domain.model.APP_LAUNCH_PANEL_SLOT_COUNT
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * First-run seed: places the FastMediaSorter tile at slot 0, then fills following slots with the
 * default candidate apps (strategic S0623 §3.1.4) that are actually installed. No-op once any tile
 * exists. Remaining slots stay empty (invitation to configure, strategic §6.5).
 */
class SeedDefaultAppLaunchPanelUseCase @Inject constructor(
    private val repository: AppLaunchPanelRepository,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        if (repository.count() > 0) return@withContext
        val now = System.currentTimeMillis()
        repository.setTile(AppLaunchPanelTile(0, AppLaunchPanelTileType.OWN_APP, null, null, now))

        var slot = 1
        for (intent in candidateIntents()) {
            if (slot >= APP_LAUNCH_PANEL_SLOT_COUNT) break
            val packageName = context.packageManager.resolveActivityCompat(intent, 0)
                ?.activityInfo?.packageName
                ?.takeIf {
                    it != context.packageName &&
                        context.packageManager.getLaunchIntentForPackage(it) != null
                }
                ?: continue
            repository.setTile(
                AppLaunchPanelTile(slot, AppLaunchPanelTileType.EXTERNAL_APP, packageName, null, now)
            )
            slot++
        }
    }

    private fun candidateIntents(): List<Intent> = listOf(
        appCategory(Intent.CATEGORY_APP_CALCULATOR),
        appCategory(Intent.CATEGORY_APP_FILES),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        appCategory(Intent.CATEGORY_APP_GALLERY),
        appCategory(Intent.CATEGORY_APP_BROWSER),
        appCategory(Intent.CATEGORY_APP_MAPS),
        Intent(Settings.ACTION_SETTINGS)
    )

    private fun appCategory(category: String): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(category)
}
