# Research 03 - Image zoom/pan preservation across mode switch

**Strategic item:** §6.3
**Status:** Resolved
**Date:** 2026-06-24

## Question

When the player switches between fullscreen and command-panel mode on rotation, must zoom and pan of the image be explicitly preserved?

## Finding

The existing mode transitions do not touch the media view transform - they only flip system-bar visibility and command-panel visibility:

- Standalone host: `StandaloneFullscreenManager.enterFullscreenWithPanel` / `exitFullscreenWithPanel` toggle `WindowInsetsController` system bars and `commandPanel.isVisible`. No image-view scale/translation is reset.
- Stream/collection host: `PlayerViewModel.enterFullscreenMode` / `enterCommandPanelMode` flip the `showCommandPanel` state flag only; `PlayerActivity.updatePanelVisibility` -> `updateSystemBarsForPlayer` updates bars and panel, not the image transform.

Therefore the mode switch introduced by S0667 requires no extra zoom/pan handling: reusing these existing transitions preserves the image transform by construction.

Independent of S0667, `PlayerActivity.onConfigurationChanged` already re-evaluates image scale type and reloads the drawable on rotation for `IMAGE`/`GIF`. That is pre-existing rotation behaviour and is out of scope for S0667.

## Decision

No dedicated zoom/pan preservation step is needed. Implementation must apply the mode switch only through the existing transitions named above, never by recreating or re-laying-out the media view.
