# S0241 Phase 04 — Delete VR Source-Sets

Ticket: S0241
Phase status: Done
Goal: physically delete every Kotlin/C++/resource/manifest file that backed the OpenXR / immersive VR stack, so the codebase no longer carries the runtime.

## Scope

- `app_v2/src/vr/` — Kotlin + C++ + res + AndroidManifest.xml — deleted in full.
- `app_v2/src/testVr/` — 8 VR-only unit-test files — deleted.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt` — exit-helper (98 LOC) — deleted; no remaining caller after Phase 03.
- `app_v2/src/noLegal/` + `app_v2/src/noLegalDebug/` — verified clean of VR overlays (no VR-specific files were ever placed there; `manifest.srcFile("src/vr/AndroidManifest.xml")` redirection was wired in `build.gradle.kts`, removed in Phase 05).

## Checklist

- [x] `git rm -r app_v2/src/vr/`
- [x] `git rm -r app_v2/src/testVr/`
- [x] `git rm app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt`
- [x] Verify `find app_v2/src -type d -name "vr*"` returns empty.
- [x] Comment sweep in 9 main-side files (KDoc references to deleted classes) — parallel agent.
- [x] Drop `open` modifier on `PlayerActivity` class and `exitPlayerWithAudioCheck()` method (no subclasses left).

## Validation

- PASS: `git status --short` shows ~120 `D` entries (vr/, testVr/, VrTaskTransition.kt).
- PASS: parallel-agent comment sweep, no stale `[VrPlayerActivity]` / `OpenXrSessionManager` KDoc links remain in `src/main/`.

## Notes

- Builds for the surviving 5 flavors are validated together with Phase 05 (the gradle config has to follow the source deletion or `manifest.srcFile("src/vr/AndroidManifest.xml")` errors out).
- Git history retains every deleted file at the parent commit of this phase. No archive branch needed (the historical `archive/vr-stack-2026-05` ref still exists in `origin` from the earlier Phase 00 step).
