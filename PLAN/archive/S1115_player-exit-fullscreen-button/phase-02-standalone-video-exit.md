# Phase 02 - Standalone host (PhotoVideoStandaloneActivity): add fullscreen-exit overlay button

**Status:** ✅ Done
**Completed:** 2026-07-19

## Step 02.1 - Add the overlay exit button to the standalone photo/video layout

**Status:** `[x] done`

**Files Touched:**

| File | Change |
| --- | --- |
| `app_v2/src/main/res/layout/activity_standalone_photo_video.xml` | Add `<include layout="@layout/player_document_fullscreen_exit_button_content" />` as a top-end overlay child of the root frame (same shared content layout the main host uses), so `btnDocumentFullscreenExit` exists in `ActivityStandalonePhotoVideoBinding`. |
| `app_v2/src/main/res/layout-land/activity_standalone_photo_video.xml` | Same include in the landscape variant (landscape parity). |

**Prompt for developer:**

Add the shared include `player_document_fullscreen_exit_button_content` to the root overlay container of both the portrait and landscape `activity_standalone_photo_video.xml`, positioned so it renders on top (declare it late in the root frame, after the media/content views, mirroring how the main `activity_player_unified.xml` places the include). The included `ImageButton` is `top|end`, `visibility="gone"` by default. Reuse the existing shared content file unchanged - no new drawable/dimen/string.

**Verification:**

- Grep: both layout files contain `player_document_fullscreen_exit_button_content`.
- Build generates `ActivityStandalonePhotoVideoBinding.btnDocumentFullscreenExit`.

**Step Log:**

## Step 02.2 - Wire the exit button click + visibility in PhotoVideoStandaloneActivity

**Status:** `[x] done`

**Depends on:** 02.1

**Files Touched:**

| File | Change |
| --- | --- |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Add a private `updateFullscreenExitButtonVisibility()` that shows `binding.btnDocumentFullscreenExit` only when `!binding.topCommandPanel.isVisible && viewModel.state.value.mediaType == MediaType.VIDEO`; set its click listener (in `setupVideoControls`) to exit fullscreen via `fullscreenManager?.exitFullscreenWithPanel(binding.topCommandPanel) { updateFullscreenExitButtonVisibility() }`; call `updateFullscreenExitButtonVisibility()` after every command-panel visibility change (keyboard toggle, context-menu toggle, initial open-in-fullscreen enter callback, transient-bars edge-swipe restore). |

**Prompt for developer:**

1. Add `private fun updateFullscreenExitButtonVisibility()` reading `binding.topCommandPanel.isVisible` and `viewModel.state.value.mediaType`; set `binding.btnDocumentFullscreenExit.isVisible = !panelVisible && type == MediaType.VIDEO`.
2. In `setupVideoControls`, set `binding.btnDocumentFullscreenExit.setOnClickListener { fullscreenManager?.exitFullscreenWithPanel(binding.topCommandPanel) { updateFullscreenExitButtonVisibility() } }` and log the click via `UserActionLogger.logButtonClick("FullscreenExit", "PhotoVideoStandaloneActivity")`.
3. Fill the currently-empty `enterFullscreenWithPanel(...) {}` callback (open-in-fullscreen path) and the transient-bars callback (line restoring `topCommandPanel.isVisible = true`) to also call `updateFullscreenExitButtonVisibility()`.
4. In the keyboard `onToggleCommandPanel`/`onShowContextMenu` lambdas and any other site that flips `binding.topCommandPanel.isVisible`, call `updateFullscreenExitButtonVisibility()` right after.

Reuse existing `UserActionLogger` import pattern. No new strings; the included button already has `contentDescription="@string/exit_fullscreen"`.

**Verification:**

- Grep: `PhotoVideoStandaloneActivity.kt` contains `updateFullscreenExitButtonVisibility` and `btnDocumentFullscreenExit`.
- Grep: click listener calls `exitFullscreenWithPanel`.
- Project compiles.

**Step Log:**
