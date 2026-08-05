package com.sza.fastmediasorter.domain.icon

import android.graphics.drawable.Drawable
import com.sza.fastmediasorter.domain.model.MediaResource

/**
 * A resource's final icon together with the identity of the picture it shows.
 *
 * [key] must change if and only if [drawable] would render differently. Drawable inherits identity
 * equality, so anything that compares two icons - StateFlow conflation, DiffUtil, a manual
 * last-bound guard - has to compare this key instead of the drawable itself.
 */
data class ResourceIcon(val drawable: Drawable, val key: String)

/**
 * S1289: how a caller outside the UI layer obtains the composed logo of a resource.
 *
 * The composing itself lives in the UI layer, which the domain layer must not import. This role is
 * the seam: the domain declares what it needs, the UI implements it over its own composer.
 */
interface ResourceIconProvider {

    fun iconFor(resource: MediaResource): ResourceIcon
}
