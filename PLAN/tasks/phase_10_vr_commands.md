# Phase 10 — VR Command Overrides (Fullscreen, Save Frame)

**Status:** Not started · **Depends on:** Phase 9 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Override a small set of standard player commands in the vr flavor so they behave correctly inside an XR session instead of calling phone-only UI.

## Current State

- Standard player commands (`FullscreenCommand`, `SaveFrameCommand`, `ToggleSystemUiCommand`, etc.) are defined in `ui/player/commands/`.
- They manipulate Android window flags or ExoPlayer frame grab.
- In vr flavor they either no-op or crash (the VR render loop owns the surface).

## Work

1. Audit all commands in `ui/player/commands/` that touch window state, system UI, or frame capture.
2. For each such command, add a `VrXxxCommand` override in `app_v2/src/vr/java/com/sza/fastmediasorter/vr/commands/` that:
   - Fullscreen: no-op (always fullscreen in VR) + show toast "Already fullscreen in VR".
   - Save Frame: delegate to `VrStereoSnapshotManager` (phase 9).
   - System UI toggle: show/hide the `VrControlOverlayManager` QuadLayer instead of Android system bars.
3. Wire via Hilt: vr module provides `@Binds` replacements for the affected commands.
4. Verify command bar still lists the same commands in vr flavor — user sees identical UX, only the effect changes.

## Acceptance Criteria

- Pressing Fullscreen in VR flavor does not crash; shows brief toast.
- Pressing Save Frame in VR produces an SBS PNG (phase 9 hook works).
- Pressing System UI toggle shows/hides the VR QuadLayer control overlay.
- Other flavors (standard/lite/photos/legacy) retain their existing command behaviour (no regression).

## Files Touched

- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/commands/` (new package, 3-4 files)
- `vr/di/VrModule.kt` — `@Binds` replacements
- Audit: `ui/player/commands/*Command.kt` (identify which ones need VR overrides)

## Out of Scope

- New commands unique to VR (e.g. "Reset view") — track as follow-up if requested.
- Changing the command bar layout or visibility rules.
