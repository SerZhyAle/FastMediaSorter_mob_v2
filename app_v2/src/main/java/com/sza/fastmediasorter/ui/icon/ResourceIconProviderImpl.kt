package com.sza.fastmediasorter.ui.icon

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.sza.fastmediasorter.domain.icon.ResourceIcon
import com.sza.fastmediasorter.domain.icon.ResourceIconProvider
import com.sza.fastmediasorter.domain.model.MediaResource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1289: the single place where the domain-owned icon seam meets [ResourceIconComposer].
 *
 * Picking which drawable a resource gets stays entirely inside the composer - this class only
 * carries the result across the layer boundary and stamps it with a stable identity.
 */
@Singleton
class ResourceIconProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ResourceIconProvider {

    override fun iconFor(resource: MediaResource): ResourceIcon =
        ResourceIcon(ResourceIconComposer.compose(context, resource), resourceIconKey(resource))
}

/**
 * Identity of the picture [ResourceIconComposer.compose] would produce for [resource].
 *
 * Every field the composer branches on is listed here and nothing else: a resource that differs
 * only in, say, its file count must keep the same key, or the desktop rebuilds every cell on an
 * emission that changed nothing visible. Conversely a field the composer starts reading must be
 * added here in the same change, or a real icon change would go unnoticed.
 */
@VisibleForTesting
fun resourceIconKey(resource: MediaResource): String = listOf(
    resource.iconId.orEmpty(),
    resource.storageVolumeId.orEmpty(),
    resource.profile.name,
    resource.type.name,
    resource.cloudProvider?.name.orEmpty(),
    resource.path,
    resource.id.toString(),
).joinToString(separator = KEY_SEPARATOR)

/** A unit separator cannot occur in a path, a provider name or an icon id. */
private const val KEY_SEPARATOR = "\u001F"
