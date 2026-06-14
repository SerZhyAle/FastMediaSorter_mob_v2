package com.sza.fastmediasorter.ui.player.contracts

import android.graphics.Bitmap
import android.graphics.RectF
import android.view.View
import android.widget.ImageView
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource

/**
 * S0393: binding-agnostic seam that type-specific action delegates (crop, draw, ..) consume instead
 * of `PlayerActivity`/`ActivityPlayerUnifiedBinding`. Both the in-app player and the standalone hosts
 * implement it, so an action is wired once rather than re-glued per host (S0392 ROADMAP R0, blocker B1).
 *
 * Keep this surface minimal: add a member only when a real action needs it, and prefer expressing
 * host-specific behaviour (e.g. navigate-after-save vs toast-after-save) as a hook the host overrides,
 * not as an `if standalone` inside the delegate.
 */
interface PlayerActionHost {

    /** Dialog/inflater/lifecycle host. */
    val hostActivity: AppCompatActivity

    /** Coroutine scope tied to the host lifecycle. */
    val hostScope: LifecycleCoroutineScope

    /** File the action operates on, or null when nothing is open. */
    val actionCurrentFile: MediaFile?

    /** Owning resource when a resource context exists (in-app); null for a single external file. */
    val actionCurrentResource: MediaResource?

    /** Container an action mounts its overlay into (the media content area). */
    val overlayMountTarget: ViewGroup

    /** The image surface used as a crop pinch-passthrough target. */
    val imagePinchTarget: View

    /**
     * Normalize the image surface(s) to FIT_CENTER before mounting the crop overlay, so the overlay's
     * normalized rect maps onto the displayed image. Default handles the pinch target; the in-app host
     * overrides to also normalize its secondary `imageView` surface (S0393 - parity with the pre-seam
     * `PlayerCropDelegate`, which set FIT_CENTER on both photoView and imageView).
     */
    fun prepareImageSurfacesForCrop() {
        (imagePinchTarget as? ImageView)?.scaleType = ImageView.ScaleType.FIT_CENTER
    }

    /** Current on-screen image rectangle (for overlay-to-image mapping). */
    fun imageDisplayRect(): RectF

    /** Base bitmap currently displayed, for merge operations (draw); null if unavailable. */
    val displayedBitmap: Bitmap?

    /** Re-decode the current file in place after it was overwritten (crop/draw in-place save). */
    fun reloadCurrentImageInPlace()

    /**
     * A new file was saved into the current folder (crop-to-file / compress / draw save-as).
     * In-app navigates to it; a single-file host typically just confirms with a toast.
     */
    fun onFileSavedInFolder(savedPath: String)
}
