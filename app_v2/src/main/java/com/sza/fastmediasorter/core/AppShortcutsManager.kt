package com.sza.fastmediasorter.core

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sza.fastmediasorter.core.panel.ResourceTypeIconMap
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppShortcutsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceDao: ResourceDao
) {
    /** Refresh top-3 recent resources as dynamic shortcuts. */
    suspend fun updateRecentResourceShortcuts() {
        try {
            val recent = resourceDao.getRecentResourcesSync(3)
            val shortcuts = recent.map { entity ->
                ShortcutInfoCompat.Builder(context, "resource_${entity.id}")
                    .setShortLabel(entity.name)
                    .setLongLabel(entity.name)
                    .setIcon(IconCompat.createWithResource(context, iconForType(entity.type)))
                    .setIntent(
                        Intent(context, MainActivity::class.java).apply {
                            action = MainActivity.ACTION_BROWSE_RESOURCE
                            putExtra(MainActivity.EXTRA_SHORTCUT_RESOURCE_ID, entity.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update dynamic shortcuts")
        }
    }

    // S0890: single source of truth for the type -> icon table.
    private fun iconForType(type: ResourceType): Int = ResourceTypeIconMap.iconFor(type)
}
