package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds the name of a resource copy - "<name> (Copy)", then "<name> (Copy N)" - in the UI language,
 * because the result is persisted as a user-visible resource name.
 */
class GenerateUniqueCopyNameUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    operator fun invoke(sourceName: String, existingNames: Set<String>): String {
        val normalized = sourceName.trim().ifBlank { "Resource" }
        // The editor refuses a name that collides case-insensitively, so the generator must skip those too.
        val taken = existingNames.mapTo(HashSet()) { it.trim().lowercase() }
        val baseCandidate = context.getString(R.string.resource_copy_name, normalized)
        if (baseCandidate.lowercase() !in taken) {
            return baseCandidate
        }

        var suffix = 1
        while (true) {
            val candidate = context.getString(R.string.resource_copy_name_numbered, normalized, suffix)
            if (candidate.lowercase() !in taken) {
                return candidate
            }
            suffix++
        }
    }
}
