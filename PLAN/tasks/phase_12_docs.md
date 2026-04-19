# Phase 12 — Docs Sync

**Status:** Not started · **Depends on:** Phases 1–11 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Bring user-facing and developer docs in line with the shipped VR edition. Follows the project's mandatory `/doc-update` skill rule for `docs/FEATURES*.md` mirrors.

## Current State

- `docs/FEATURES.md` EN/RU/UK have no VR section (previous 3D-video references were removed in commit `e0f25f7`).
- No `docs/VR_EDITION.md`.
- No `docs/VR_SIDELOAD.md` (sideload instructions for testers).
- `docs/TECH_STACK.md` does not list OpenXR, isoparser, or vr-flavor native components.

## Work

1. **`docs/FEATURES.md` + `_RU` + `_UK`** — add a "VR Edition" section listing:
   - Stereoscopic 3D viewing (SBS/OU) — videos and images.
   - 360° spherical video (mono + stereo SBS/OU).
   - VR180 panoramic video.
   - Cylinder 180° panoramic.
   - Auto-detection + manual override.
   - SBS PNG snapshot capture.
   - Cinema mode for flat content.
2. **`docs/VR_EDITION.md`** (new, EN only for now) — overview for end users:
   - Supported devices (Quest 2/3/Pro).
   - Supported formats (detection + override).
   - Settings (forced plat/spherical, rendering mode, remember-per-file).
   - Known limitations (cubemap not supported, HDR not supported).
3. **`docs/VR_SIDELOAD.md`** (new) — sideload instructions:
   - Enable developer mode on Quest.
   - `adb install` command.
   - How to launch from Unknown Sources.
   - Troubleshooting (fallback activity, missing runtime).
4. **`docs/TECH_STACK.md`** — add OpenXR loader, isoparser (vr-flavor-only), native layer bindings.
5. **`dev/CHANGELOG.md`** — via `scripts/add_to_dev_log.ps1` per post-change rule.
6. Style conformance: use `..` not `...`; use `ё` in Russian where grammatically correct (per CLAUDE.md author-style rules).

## Acceptance Criteria

- All three FEATURES mirrors include a "VR Edition" section of equivalent scope.
- `VR_EDITION.md` + `VR_SIDELOAD.md` exist and cross-link from README.
- `TECH_STACK.md` documents new dependencies.
- Docs read through the `/doc-update` skill pass without sync warnings.

## Files Touched

- `docs/FEATURES.md`
- `docs/FEATURES_RU.md`
- `docs/FEATURES_UK.md`
- `docs/VR_EDITION.md` (new)
- `docs/VR_SIDELOAD.md` (new)
- `docs/TECH_STACK.md`
- `dev/CHANGELOG.md` (via script)
- README cross-links

## Out of Scope

- Meta Horizon Store listing copy.
- Marketing / promotional content.
- Migration guide for users of the standard flavor (covered inline in VR_EDITION.md).
