# Phase 01 - Main host: extend fullscreen-exit button to video

**Status:** ✅ Done
**Completed:** 2026-07-19

## Step 01.1 - Include VIDEO in the fullscreen-exit overlay button visibility

**Status:** `[x] done`

**Files Touched:**

| File | Change |
| --- | --- |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt` | `updateDocumentFullscreenExitButtonVisibility()` - add `MediaType.VIDEO` to the eligible-type check so the always-visible overlay exit button shows in video fullscreen too. Update the KDoc to say video is included. |

**Prompt for developer:**

In `updateDocumentFullscreenExitButtonVisibility()` extend the type predicate that currently matches only `PDF || EPUB || TEXT` to also match `MediaType.VIDEO`. The existing click handler already calls `viewModel.toggleCommandPanel()` when the panel is hidden, which for video exits fullscreen and restores the command panel - no click-handler change needed. Update the method/`setupDocumentFullscreenExitButton` KDoc lines that say "PDF/EPUB/TXT" to include video. Do not rename the view id or the methods (stable resource id; internal method rename out of scope).

**Verification:**

- Grep: `updateDocumentFullscreenExitButtonVisibility` body references `MediaType.VIDEO`.
- Grep: no other logic branch removed.

**Step Log:**
