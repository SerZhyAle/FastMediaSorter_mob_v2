# S0175 — noLegal: NewPipeExtractor version bump

**Ticket:** S0175
**Status:** Tactical
**Strategic spec:** `PLAN/S0175_nolegal-newpipe-version-bump.md`
**Epic:** S0156

## Research findings

- Current dependency: `com.github.TeamNewPipe:NewPipeExtractor:v0.24.0`
- Latest stable: `v0.26.1` (released 2026-04-10)
- Breaking changes affecting our wrapper between v0.24.0 → v0.26.1:
  - v0.25.0: `DateWrapper(Calendar)` constructors removed — **not used in wrapper**
  - v0.26.0: `Service.getMediaCapabilities()` returns `Set` not `List` — **not called in wrapper**
  - Net impact: **zero wrapper changes required**
- Odysee/LBRY: **not in upstream `ServiceList`** (YouTube, SoundCloud, MediaCCC, PeerTube, Bandcamp only). Strategic spec §2 goal is incorrect — Odysee cannot be added via allowlist until upstream supports it. Spec patched accordingly.

## Phases

- [Phase 1](phase1_version_bump.md) — Bump dependency to v0.26.1; verify clean compile

## Deferred

- Odysee allowlist: blocked on upstream support. Separate spec required if/when NewPipeExtractor adds Odysee extractor.
