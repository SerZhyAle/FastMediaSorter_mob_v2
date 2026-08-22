package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.ui.player.VideoPlayerManager

/**
 * S1549: re-binds the holders of `activity_player_unified` after the host re-inflates the layout
 * for a new orientation.
 *
 * `PlayerActivity` and `StandalonePlayerActivity` absorb a rotation on purpose - a recreate would
 * tear down the live render surface and stop playback - so their landscape variant never inflates
 * by itself. Re-inflating is the only remaining means, and every object built from the previous
 * `ActivityPlayerUnifiedBinding` is then pointing at a detached hierarchy, silently and with no
 * exception raised. This class owns one operation per holder group of that inventory.
 *
 * ## Holder inventory (measured 2026-08-16, verified against the code)
 *
 * Grouped by what a re-inflate must do to each holder. Line references are to the current tree.
 *
 * ### Re-point - live state sits behind a captured view, so it must NOT be re-created
 * - `PdfViewerManager` (renderer, page index, file descriptor), `EpubViewerManager` (parsed book,
 *   chapter index, the live `WebView`), `TextViewerManager` (pager position, editor buffer),
 *   `OfficeDocumentViewerHost` (rendered document `WebView`). All four take a bare `root: View`
 *   (`PlayerViewerFactory.kt:60,109,144,197`; standalone `StandaloneViewManager.kt`), so one
 *   `root: View -> Unit` serves both hosts: [rebindDocumentViewers].
 * - `ImageDrawOverlayManager` + `DrawCropOverlayController` (unsaved strokes in a `DrawCanvasView`
 *   parented to `photoDualSurfaceContainer`, `PlayerManagerInitializer.kt:298,306`):
 *   [rebindDrawOverlay] re-parents the canvas and re-registers the toolbar.
 * - `VideoPlayerManager` owns the live `ExoPlayer` and only needs the new surface:
 *   [repointVideoSurface]. The player instance is never re-created here - every playback helper
 *   already re-attaches with `currentPlayerView?.player = exoPlayer`.
 * - `StandaloneViewManager` holds its own `ExoPlayer` and does not drive `VideoPlayerManager` at
 *   all, so it is a separate seam: [rebindStandaloneViews].
 *
 * ### Self-healing - reads through the host at call time
 * - `PlayerUiStateCoordinatorCallbackImpl` (`PlayerManagerInitializer.kt:254`) and
 *   `BlackScreenOverlayManager`, which holds a `WeakReference` to the activity (`:273`). Both need
 *   nothing beyond the activity's binding field being reassigned.
 * - Every helper that resolves views through a shared [PlayerBindingSafeViews]: each accessor
 *   resolves on read, so `PlayerBindingSafeViews.rebindRoot` re-points a manager and all of its
 *   delegates at once. That is why the document viewers above need only their own root re-pointed.
 *
 * ### Re-construct - holds no state worth keeping, but does hold the binding
 * `PlayerControlsSetupManager` (`PlayerManagerInitializer.kt:935`), `PlayerGestureSetupManager`
 * (`:952`) and the `VideoTouchDelegate` it builds, `ImageLoadingManager` (`:429`),
 * `AudioEmptyStateController` (`:446-449`), `ExoPlayerControlsManager` (`:618` - its
 * `setupExoPlayerNavigationButtons()` re-does `setPlayerView` and every transport listener),
 * `PictureInPictureManager` (`:753`, captured `playerView` + chrome list; standalone
 * `StandalonePlayerActivity.kt:270`), `StandaloneVideoTouchDelegate`
 * (`StandalonePlayerActivity.kt:856`).
 *
 * ### Measured correction to the phase inventory
 * The re-construct group is materially larger than the eight holders the tactical plan listed.
 * `PlayerManagerInitializer` alone passes `activity.activityBinding` (or a view resolved from it)
 * into **25** constructors - beyond those named above: `PlayerUiStateCoordinator` (`:251`),
 * `UndoOperationManager` (`:261`), `PlayerImmersiveModeManager` (`:317`), `CommandPanelController`
 * (`:415`), `NowPlayingManager` (`:739`), `SleepTimerManager` (`:747`), `PlayerMediaLoaderManager`
 * (`:779`), `FilenameOverlayAutoHideManager` (`:911`) and the managers created at `:518,525,538,
 * 550,655,673,688,860,873`. Re-constructing them cannot simply re-run the `init*()` functions:
 * those interleave construction with `setup()` calls, `repeatOnLifecycle` collectors and listener
 * registration, so a second pass would double-register. Wiring the re-inflate before that group has
 * a re-bind path would leave the command panel, touch zones, image loading and the transport
 * controls driving a detached tree while playback still runs.
 */
internal class PlayerLayoutRebindManager {

    /**
     * Aim the document viewers at [newRoot]. Each keeps its reading state; the caller re-renders
     * the current page afterwards, because the new views start empty. Only the viewers that were
     * actually instantiated are passed - a null argument means the host never opened that type.
     */
    fun rebindDocumentViewers(
        newRoot: View,
        pdfViewerManager: PdfViewerManager?,
        epubViewerManager: EpubViewerManager?,
        textViewerManager: TextViewerManager?,
        officeViewerHost: OfficeDocumentViewerHost?,
    ) {
        pdfViewerManager?.rebindLayoutRoot(newRoot)
        epubViewerManager?.rebindLayoutRoot(newRoot)
        textViewerManager?.rebindLayoutRoot(newRoot)
        officeViewerHost?.rebindLayoutRoot(newRoot)
    }

    /**
     * Move an active draw session into the re-inflated tree. The manager is not re-created because
     * the strokes drawn since the last save live only in its canvas view.
     */
    fun rebindDrawOverlay(
        drawOverlayManager: ImageDrawOverlayManager?,
        newImageContainer: ViewGroup,
        newToolbarRoot: View,
    ) {
        drawOverlayManager?.rebindLayoutRoot(newImageContainer, newToolbarRoot)
    }

    /**
     * Hand the surviving `ExoPlayer` to the freshly inflated [newPlayerView]. Never constructs a
     * player: the window's surface is what changed, not the playback.
     */
    fun repointVideoSurface(videoPlayerManager: VideoPlayerManager?, newPlayerView: PlayerView) {
        val manager = videoPlayerManager ?: return
        manager.setPlayerView(newPlayerView)
        newPlayerView.player = manager.getPlayer()
    }

    /** Standalone counterpart of [repointVideoSurface] - it owns its player and its own lookups. */
    fun rebindStandaloneViews(viewManager: StandaloneViewManager, newRoot: View) {
        viewManager.rebindLayoutRoot(newRoot)
    }
}
