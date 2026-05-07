package com.sza.fastmediasorter.data.permissions

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.domain.repository.ContextualRationaleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextualRationaleRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : ContextualRationaleRepository {

    private val prefs: SharedPreferences =
        StrictModeHelper.allowDiskReads {
            context.getSharedPreferences("perm_rationale_prefs", Context.MODE_PRIVATE)
        }

    override fun isShown(permissionId: String): Boolean =
        StrictModeHelper.allowDiskReads { prefs.getBoolean(permissionId, false) }

    override fun markShown(permissionId: String) =
        StrictModeHelper.allowDiskWrites { prefs.edit().putBoolean(permissionId, true).apply() }
}
