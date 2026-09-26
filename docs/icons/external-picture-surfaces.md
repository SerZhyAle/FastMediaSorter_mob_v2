# Surfaces that show an external picture

Every surface of this product that shows a picture loaded at run time - album art, a favicon, a
thumbnail, a photo, another app's icon - and what it shows when that picture is missing, not yet
loaded, refused or broken. Contract: `ICON-EXTERNAL` rule 4 (pointer: `docs/contracts/ICON-EXTERNAL.md`).

A new surface that shows a loaded picture enters this list in the ticket that creates it.

Checked surface by surface on 2026-09-24 (S3444). The offline walk on a device - network off, each
surface opened - is the remaining evidence.

## Phone and launcher

- **Browse grid and list thumbnail** - `ui/browse/AdapterThumbnailLoader.kt`. Stands for the file. Loading and failure: the generated file-type tile. Holds.
- **Launcher app grid icon** - `ui/common/widget/MediaItemThumbnailBinder.kt` via `LauncherAppGridAdapter`. Stands for an app. Loading and failure: `ic_launcher_mode`; the label is set independently of the load. Holds.
- **Player album art** - `ui/player/AudioCoverArtLoader.kt`. Stands for `content.audio`. Failure: `ic_audio` on the dark surface (`AudioArtworkPlaceholder.onDarkSurface`). Holds.
- **Mini-player bar artwork** - `ui/player/helpers/NowPlayingManager.kt`. Loading and failure: `ic_audio`. Holds.
- **Now-playing sheet artwork** - `ui/player/NowPlayingBottomSheetFragment.kt`. Loading and failure: `ic_audio`. Holds.
- **Audio slideshow background photo** - `ui/player/helpers/AudioSlideshowPhotoModeManager.kt`. Stands for `content.image`. Failure: the previous photo stays; with none, `ic_image` on the dark surface (`AudioArtworkPlaceholder.imageOnDarkSurface`). Fixed by S3444 - was blank.
- **PDF page strip** - `ui/player/helpers/PdfThumbnailAdapter.kt`. Stands for a page. Loading: spinner. Failure: `ic_document`. Fixed by S3444 - the spinner never stopped.
- **Streams grid tile** - `ui/streams/StreamGridAdapter.kt`. Stands for `content.stream`. The media-kind glyph (`ic_audio`/`ic_video`) is painted first; captured frame, atlas preview, logo, favicon or flag overwrite it. Holds.
- **Streams list row favicon** - `ui/streams/StreamSourceAdapter.kt`. Stands for `content.stream`. Failure: the country flag; with no country, the media-kind glyph as on the grid. Fixed by S3444 - the slot was left empty.
- **Home-panel stream chip** - `ui/main/helpers/StreamPanelChannelAdapter.kt`. No tile: the channel's own name, a picture made from the item's own data. Holds.
- **Launcher contact shortcut** - `domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`. Stands for `content.person`. No photo: the initials monogram. Holds.
- **Send-to app rows** - `core/share/ShareTargetIconResolver.kt`. Another app's system icon; unreadable: the row's own glyph, `content.apps` for a package receiver (S3430). Holds.
- **Launcher YouTube gadgets** - `ui/launcher/gadget/YouTubeGadget.kt`, `YouTubeMusicGadget.kt`. The installed app's launcher icon; unreadable/absent: the official bundled brand vector mark (`ic_youtube` / `ic_youtube_music`, S3477). Holds.


## Home widgets

- **Now-playing widget artwork** - `widget/AudioNowPlayingWidgetProvider.kt`. Stands for `content.audio`. A remote or unreadable artwork URI now gets `ic_audio` on the plate (`WidgetPlateGlyph.drawableUriOrNull`). Fixed by S3444 - the launcher drew an empty slot.
- **Photo frame widget** - `widget/RandomPhotoFrameWidgetProvider.kt`. Stands for `content.image`. Not configured, empty, or a cached photo that is gone: `ic_image` with a text overlay. Fixed by S3444 - an evicted cache file drew an empty slot.
- **Stream launch widget** - `widget/StreamLaunchWidgetProvider.kt`. No stored icon: `ic_stream` on the plate. Holds.

## Watch

- **File list and grid thumbnail** - `ui/common/ThumbnailCell.kt`. Loading and unavailable: the caller's content-type glyph. Holds.
- **Image viewer** - `ui/player/image/ImageViewerScreen.kt`. Stands for `content.image`. Failure: `ic_image`, keeping the file name as its description. Fixed by S3444 - was a warning triangle in the error colour.

## Not a loaded picture

- Favourites widget and watch-listen widget - resource icons only.
- Watch brand frame - the app's own adaptive icon.

## Not yet traced line by line

- VR immersive browse thumbnails - `ui/xr/browse/ImmersiveThumbnailDecoder.kt`.
- Media notification large icon - fed by `AudioCoverArtLoader`; the system's own empty-artwork state is not ours to draw.
- Launcher stream picker dialog - reuses the favicon atlas slicer of the streams screens.
