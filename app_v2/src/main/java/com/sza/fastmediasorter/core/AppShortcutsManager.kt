package com.sza.fastmediasorter.core

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sza.fastmediasorter.R
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

    private fun iconForType(type: ResourceType): Int = when (type) {
        ResourceType.LOCAL -> R.drawable.ic_resource_local
        ResourceType.SMB -> R.drawable.ic_resource_smb
        ResourceType.SFTP -> R.drawable.ic_resource_sftp
        ResourceType.FTP -> R.drawable.ic_resource_ftp
        ResourceType.CLOUD -> R.drawable.ic_resource_cloud
        ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> R.drawable.ic_cast
    }
}
