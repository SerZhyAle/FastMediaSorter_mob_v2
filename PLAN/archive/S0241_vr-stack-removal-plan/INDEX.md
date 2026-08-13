# S0241 Tactical Index

Strategic spec: ../S0241_vr-stack-removal-plan.md
Ticket: S0241
Status: Tactical
Working branch: DEBUG-v004

## Execution Rules

- Remove the VR stack in small, reversible phases until Phase 04.
- Keep flat-screen single-eye stereo working on every surviving flavor at all times.
- Do not change the public surface of the white-list stereo classes except to unlink deleted VR code.
- After every Kotlin change, run catalog scan and render for `app_v2`.
- Close each phase with the exact validation command and result.

## Phase Board

- [x] Phase 00 - Historical: archive branch + snapshot doc captured at start of work. See `PHASE_00_ARCHIVE.md`. No longer a forward requirement (git history is the canonical source).
- [x] Phase 01 - Remove VR UI entry points from player layouts, command panel, and settings. See `PHASE_01_UI_ENTRY_POINTS.md`.
- [x] Phase 02 - Remove main-side VR routing and transition hooks. See `PHASE_02_ROUTING_AND_SETTINGS.md`.
- [ ] Phase 03 - Remove main-side stereoscopic VR render abstractions, XR helpers, and shared VR-only player settings while keeping the flavor/source-set layout intact for the rewrite. See `PHASE_03_XR_AND_RENDER_HELPERS.md`.
- [ ] Phase 04 - Delete VR flavor source sets and test source sets.
- [ ] Phase 05 - Remove VR build configuration, OpenXR dependency wiring, and native build hooks.
- [ ] Phase 06 - Remove VR resources, manifest entries, and leftover localized strings.
- [ ] Phase 07 - Finalize catalog and spec states (was Phase 08; old Phase 07 "Documentation" dropped per 2026-05-18 owner feedback).

## Notes On Removed Phase 07 (Documentation)

The original plan included a documentation refresh phase. After 2026-05-18 feedback the owner asked to keep `docs/*.md` untouched in this task; updates to documentation will happen by demand when new functionality lands. The strategic spec's §2 non-goals reflects this explicitly.
