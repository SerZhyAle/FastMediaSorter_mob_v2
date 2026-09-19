package com.sza.fastmediasorter.ui.common.widget

import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.sza.fastmediasorter.domain.model.MediaFile
import java.io.File

/**
 * Single Glide entry point for a role-scoped thumbnail (`docs/ui/PHONE_UI_COMPONENT_PATTERNS.md`
 * section 2.2). [ThumbnailRole] pins the override size, disk-cache strategy and crop so a call site
 * never assembles its own [RequestOptions].
 *
 * [com.sza.fastmediasorter.ui.browse.AdapterThumbnailLoader] keeps its own per-media-type Glide
 * pipeline (EPUB/PDF/video routing, local/network/cloud sourcing, decode-budget probing, the favicon
 * atlas) unchanged - that pipeline reads per-adapter closures (scroll state, credentials, settings
 * flags) that do not fit a stateless, Hilt-singleton binder. It delegates the shared constant
 * ([CACHED_THUMBNAIL_SIZE]) here instead, so the cached thumbnail size has one source of truth
 * (S3246). This class is the direct entry point for the simpler call sites that have no per-media-type
 * routing to do - e.g. [com.sza.fastmediasorter.ui.launcher.menu.LauncherAppGridAdapter]'s app icons.
 *
 * Plain constructor, not `@Inject`-annotated: [com.sza.fastmediasorter.di.ThumbnailModule] provides
 * this class instead, so DI wiring for it lives in one place rather than split between an annotation
 * here and a module elsewhere.
 */
class MediaItemThumbnailBinder {

    /**
     * Loads [file]'s thumbnail into [view], scoped to [role]. Mirrors
     * [com.sza.fastmediasorter.ui.browse.AdapterThumbnailLoader.thumbnailModel]: a `content://` path
     * or a resolved content Uri is preferred over the raw file path, since a MediaStore-backed row
     * carries one for exactly the files scoped storage can no longer open by path.
     */
    fun bind(view: ImageView, file: MediaFile, role: ThumbnailRole) {
        val model: Any = when {
            file.path.startsWith("content://") -> Uri.parse(file.path)
            !file.contentUri.isNullOrEmpty() -> Uri.parse(file.contentUri)
            else -> File(file.path)
        }
        Glide.with(view)
            .load(model)
            .apply(requestOptionsFor(role))
            .into(view)
    }

    /** Cancels any in-flight request targeting [view] and frees its Glide target. */
    fun clear(view: ImageView) {
        Glide.with(view).clear(view)
    }

    /**
     * The [RequestOptions] pinned by [role]. Exposed (not private) so
     * [com.sza.fastmediasorter.ui.browse.AdapterThumbnailLoader]'s existing per-branch Glide calls can
     * read the same values instead of repeating the literal override/disk-cache pair.
     */
    fun requestOptionsFor(role: ThumbnailRole): RequestOptions = when (role) {
        ThumbnailRole.LIST_ROW, ThumbnailRole.GRID_CELL -> RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(CACHED_THUMBNAIL_SIZE, CACHED_THUMBNAIL_SIZE)
            .centerCrop()
        ThumbnailRole.PREVIEW -> RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
    }

    companion object {
        /** Side length, in px, of every cached list/grid thumbnail. Single source of truth (S3246). */
        const val CACHED_THUMBNAIL_SIZE = 300
    }
}
