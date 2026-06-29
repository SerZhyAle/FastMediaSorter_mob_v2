---
name: player-family-glue-mirroring
description: Players are a family of activities; per-host glue/layout changes must be mirrored, only shared-layer changes propagate
type: feedback
---

The media players are a **family of activities**, not one screen: in-app `PlayerActivity` (unified) + standalone hosts `PhotoVideoStandaloneActivity` / `AudioStandaloneActivity` / `DocumentStandaloneActivity` / `TextStandaloneActivity` (routed by `StandalonePlayerDispatcherActivity`) + legacy `StandalonePlayerActivity`. S0380 split the monolith into per-type hosts for cold-start speed, deliberately NOT sharing the in-app panel engine.

**Why:** the in-app panel engine (`CommandPanelController` / `CommandPanelAvailabilityUpdater`) is hard-wired to `ActivityPlayerUnifiedBinding` + `PlayerViewModel`, and action delegates (`PlayerCropDelegate`, `PlayerDrawingSaveHelper`) are typed to `PlayerActivity`. So the *engines* (`ImageCropManager`, `ImageDrawOverlayManager`, use-cases) are reusable, but the *glue* between engine and Activity is duplicated per host. The compiler will NOT catch a forgotten sibling host - each host wires its own panel independently, so a change in one can silently diverge from the others.

**How to apply:** It is NOT "change one -> change all". It is layer-dependent:
- Change in the **shared layer** (engine / use-case / manager / `PlayerHostCapabilities` contract) -> propagates to all hosts automatically. Put logic here by default.
- Change in **per-host glue or a standalone layout** -> mirror it into every host that has that feature (and "has it" differs: standalone lacks draw/OCR; `photos` lacks audio/docs; `lite` has no external entry). Layout edits = always `layout/` + `layout-land/`.
- New action -> add a flag to `PlayerHostCapabilities` and branch on it, never `if standalone`.
- Adding panel buttons -> check command-bar overflow on phone width (the standalone row overflowed and clipped btnPagePrev/btnOverflowMenu until S0389 wrapped it in HorizontalScrollView + pinned overflow).

The real fix that collapses "change all" back to "change one" is extracting a binding-agnostic **host-seam** (interface: root view + current file + reload hook) so delegates are written once. Tracked by [[project_s0392_player_family_parity]] (research + catch-up) and S0390 phase 06 / waves-C follow-ups.
