# Tactical Plan: S1158 - video-stream-program-name-overlay

**Strategic spec:** [`../S1158_video-stream-program-name-overlay.md`](../S1158_video-stream-program-name-overlay.md)
**Research inputs:** none (path verified in strategic §5)
**Feature:** Current programme name overlay for video streams
**Tier:** 2 - Small (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-07-24

> **Scope:** tactical, English, developer handoff. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | programme-label | - | ✅ Done | 4/4 | [PHASE_01__programme-label.md](PHASE_01__programme-label.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Two phases, not the usual four: the change is one new callback mirrored through an existing
four-point chain (`onStreamWaitPhase`), with no new class, no data layer and no new string. Splitting
a single mirrored signal across more phases would be ceremony, not sequencing.

---

## Design summary (binding for both phases)

Strategic §5 verified the whole chain in code. This ticket adds a second, parallel signal:

- `PlayerCallback.onStreamProgramName(name: String?)` declared with an empty default in
  `VideoPlayerManager.kt`, exactly like `onStreamWaitPhase`.
- Raised from the `IcyInfo` branch of the stream listener's `onMetadata` in `StreamPlaybackHelper.kt`,
  where the non-blank now-playing title is already extracted and fed to `updateNowPlayingTitle`.
- Cleared from `VideoPlayerManager.playVideo`, which every new file and every new channel goes
  through - that is the one point where "the previous programme name is now wrong" is always true.
  It is deliberately **not** cleared where `streamWaitLabel` is cleared: that happens when playback
  becomes ready, and a programme name must survive its own stream starting to play.
- Rendered in `PlayerPlaybackCallbackImpl.kt` into a new `streamProgramLabel`, declared in both
  orientations of `activity_player_unified.xml`.

**Content decision (implementation-level, recorded here because the spec left it implicit).** The
overlay shows the programme string alone, not `station - programme`. The station name is already on
screen in `tvFileNameOverlay` directly above it, so prefixing it again would render the channel name
twice in two adjacent lines. The radio inline control combines them because it has no other place to
show the station.

---

## Pre-Implementation Blockers

None - strategic §6 has no `Open` items.

---

## Completion Gate

- [x] Both phases show ✅ Done.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] Capability recorded in the feature inventory.
- [ ] `/spec-check S1158` returns `Verified` - after the device test, not before.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-24 - Initial tactical plan authored by `/spec-tech`.
