# Research: Standalone media player defects (S0920)

Read-only architecture research. Evidence is file:line at time of writing (working tree, 2026-07-03).

## Shape

Four specialized standalone hosts, each `BaseActivity<...Binding>` directly (NOT subclasses of `PlayerActivity` / `StandalonePlayerActivity`):

- `PhotoVideoStandaloneActivity` - image + video (one shared surface).
- `TextStandaloneActivity` - plain text.
- `DocumentStandaloneActivity` - PDF / EPUB / Office.
- `AudioStandaloneActivity` - audio.

Dispatch: `StandalonePlayerDispatcherActivity` (NoDisplay trampoline) resolves `MediaFamily` and forwards `ACTION_VIEW` to one host.

`StandalonePlayerActivity` is `@Deprecated (S0393)`, no manifest alias targets it - poor reference, already diverged.

Shared resources:
- Menu: `res/menu/overflow_menu_standalone_player.xml` (all 4 hosts inflate it).
- Bottom "Copy to.."/"Move to.." panel: `res/layout*/player_bottom_panels_container_content.xml` include (`copyToPanel`/`copyToPanelHeader`/`copyToButtonsGrid` + Move twins).
- Each host lazily builds its own `DestinationButtonsManager` (owns `safeViews.copyToPanelHeader`/`moveToPanelHeader`, exposes `setCopyPanelExpanded()`/`setMovePanelExpanded()`).
- In-app `PlayerActivity` instead routes the header toggles through `CommandPanelController` -> `PlayerCommandPanelCallbackImpl` -> `PlayerDialogAndUiStateManager` -> `DestinationButtonsManager`.

## Defect 1 - Copy/Move groups render collapsed, never expand on tap

Root cause: standalone hosts never register `copyToPanelHeader.setOnExpandedChangeListener` / `moveToPanelHeader.setOnExpandedChangeListener`. Header tap flips the chevron and the header's own `expanded` field (`CollapsibleSectionHeader.bindClicks` -> `setExpanded(!expanded)` with `notify=true`), but with no external listener registered nothing toggles `copyToButtonsGrid`/`moveToButtonsGrid`. The only wiring in the tree is `CommandPanelController` (in-app path only).

`StandaloneKeyboardManager.onToggleCopyPanel()` / `onToggleMovePanel()` are stubbed no-ops with a stale "no copy targets in standalone" comment.

Affected: text, image, video, audio (all 4).

Fix location: register the listeners in each host; cleanest is one binder method on `DestinationButtonsManager` (it already owns the headers + the persist/visibility methods) called once per host during setup.

## Defect 2 - Fullscreen covers OS bars outside fullscreen playback

Root cause: `PhotoVideoStandaloneActivity.setupVideoControls()` unconditionally calls `StandaloneFullscreenManager.enterFullscreen()` on video-ready (hides system bars) while `setupCloseButton()` keeps `binding.topCommandPanel.isVisible = true`. Result: bars hidden but command panel visible - a half-immersive state, neither "commands + bars" nor "true fullscreen". No gating on any setting.

Image / text / document only call `WindowCompat.setDecorFitsSystemWindows(window, false)` (edge-to-edge + inset padding) - bars stay visible. Video is the only surface that force-hides bars on open.

`StandaloneFullscreenManager.enterFullscreen()`/`exitFullscreen()` never consult `hideSystemUiInFullscreen` (AppSettings.kt:141) - separate broader gap (parked candidate).

Affected: video only.

Fix location: `PhotoVideoStandaloneActivity` video-open path - gate the fullscreen entry, and when entering use `enterFullscreenWithPanel(topCommandPanel){...}` so bars and panel move together.

## Defect 3 - Text (and audio) surface shows "Draw overlay" menu item

Root cause: shared `overflow_menu_standalone_player.xml` declares `menu_draw_overlay` visible-by-default (S0410, image-host feature). Each host must explicitly hide items it does not own. `TextStandaloneActivity`'s hide list omits `R.id.menu_draw_overlay` -> item stays visible, with no click handler (dead tap). `PhotoVideoStandaloneActivity` gates it correctly (`isVisible = isStaticImage`); `DocumentStandaloneActivity` hides it explicitly with a comment warning about exactly this leak.

Same leak on `AudioStandaloneActivity` (hide list also omits it).

Affected: text (reported), audio (same bug class).

Fix location: add `R.id.menu_draw_overlay` to the hide lists in `TextStandaloneActivity` and `AudioStandaloneActivity`.

## Defect 4 - Video ignores `openVideoInFullscreen` (S0820) + "does not autoplay"

`openVideoInFullscreen` (AppSettings.kt:146) is read only on the in-app Browse path (`BrowseEventHandler` -> `EXTRA_ENTER_FULLSCREEN` -> `PlayerActivity.initEnterFullscreenOnLaunch()`). No standalone class references it. Combined with defect 2, standalone video always opens in commands mode regardless of the setting. Confirmed.

Autoplay: `StandaloneViewManager.playVideo()` sets `player.playWhenReady = true` unconditionally before `prepare()`. Standalone only handles local/content URIs (no network `playWhenReady`-param path). Static reading shows autoplay SHOULD occur - the "does not start playing" half is NOT reproduced statically. Likely perception of commands-mode + never-hiding ExoPlayer controller (`show_timeout=MAX`, `hide_on_touch=false`), or a device-specific audio-focus denial. -> on-device verification required.

Fix location: same as defect 2 - read `openVideoInFullscreen` on the video-open path; when true, enter real fullscreen (panel + bars hidden). Verify autoplay on device.

## Defect 5 - Different top-toolbar button order

Order = view declaration order per layout xml (land counterparts byte-identical). Image + video share ONE layout, so only 2 distinct orders exist.

Divergence is the position of Rename:
- Photo/Video (`activity_standalone_photo_video.xml`): ... Info -> Rename -> [Crop/Rotate type-specific] -> Overflow.
- Document (`activity_standalone_document.xml`): ... Info -> Rename -> [PDF/EPUB/Text cluster] -> Overflow.
- Audio (`activity_standalone_audio.xml`): ... Info -> Rename -> Overflow (no type-specific toolbar buttons).
- Text (`activity_standalone_text.xml`): ... Info -> [Search/Translate/Copy/Edit] -> Rename -> Overflow  <-- OUTLIER.

3 of 4 hosts agree: Rename BEFORE the type-specific cluster. Text is the sole outlier.

Canonical order (majority / richest surface = Document):
`Back -> [Prev, Next, Random, Slideshow] -> Delete -> Favorite -> Share -> Info -> Rename -> [type-specific] -> Overflow`

Fix location: move `btnRenameCmd` in `activity_standalone_text.xml` (+ `layout-land` twin) to immediately after `btnInfoCmd`. XML-only; `TextStandaloneActivity` toggles `btnRenameCmd.isVisible` reactively, no Kotlin change.

## Risks

- Defect 1 must be fixed in all 4 hosts; a partial fix leaves audio broken.
- Do not re-couple standalone to in-app `PlayerViewModel`/`CommandPanelController` state (standalone uses a distinct simpler VM).
- Landscape parity (Rule 11): defect 5 xml edit must touch both `layout/` and `layout-land/` text layouts.
- `lite` flavor removes the default-player manifest aliases but keeps the Kotlin hosts - fixes apply uniformly.
- Deprecated `StandalonePlayerActivity` has the same defect-2 pattern (line ~869); leave it (pending removal) or note divergence.

## Owner-decision items (resolved defaults for this ticket)

1. Defect 4 "not playing" - on-device confirm (device-test gate). Default: autoplay already wired; verify.
2. Image/text "fullscreen" semantics - no play/pause. Default: image/text never auto-hide bars; only video honors `openVideoInFullscreen`.
3. Unified order - document convention in `docs/ARCHITECTURE.md`. Default: adopt majority order, add a short convention note.
4. Copy/Move collapsed state - keep GLOBAL (shared with in-app); the bug is the missing listener, not the state model.
5. Menu leak fix style - per-host hide (matches existing Document pattern) + fix audio; not the fail-closed shared-default rewrite.

## Parked candidate (out of this ticket's 5 defects)

- `StandaloneFullscreenManager` never honors `hideSystemUiInFullscreen` (unlike in-app). Broader gap; consider a follow-up ticket.
