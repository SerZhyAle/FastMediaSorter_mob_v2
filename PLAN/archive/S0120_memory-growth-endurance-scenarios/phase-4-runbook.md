# Phase 4 — Manual Test Runbook (On-Device Verification)

**Status:** [x] Done

This phase captures the manual runbook itself.

Running the actual endurance scenarios on a physical device remains a human follow-up outside the authored artifact, so the phase is considered done once the document below is complete.

## Prerequisites

- Phase 2 and Phase 3 completed and merged.
- Debug APK installed on the target device(s).
- Logcat filter: `MEM_ENDURANCE` tag (or run `/log-reader` to extract structured lines).
- At least one device with MemoryTier.STANDARD/HIGH and one with MemoryTier.LOW or emulator with heap ≤ 256 MB.
- Media corpus: ≥ 200 images, ≥ 100 audio tracks, ≥ 50 videos, one folder with ≥ 500 mixed files.

## Execution steps

### Before each scenario

1. Launch app fresh (kill process, re-launch).
2. Navigate to the target surface.
3. Wait 10 seconds for warm-up GC to settle.
4. Start the scenario — the tracker fires BASELINE automatically.

### IMG-1, IMG-2, IMG-3 (Image)

- Open a folder with ≥ 200 images.
- IMG-1: tap next/previous manually, minimum 50 transitions over 10 minutes.
- IMG-2: start slideshow in sequential mode, let it run ≥ 50 transitions.
- IMG-3: start slideshow in random mode, let it run ≥ 50 transitions.
- After last transition, navigate away (back to browse). Wait 30 seconds. Capture cooldown log.

### AUD-1, AUD-2, AUD-3 (Audio)

- Open audio playlist with ≥ 100 tracks.
- AUD-1: manually change tracks 50+ times.
- AUD-2: start continuous playback, sequential. Fast-forward between tracks or let it auto-advance; 50+ transitions.
- AUD-3: same as AUD-2 but shuffle mode.
- Stop playback, wait 30 seconds. Capture cooldown log.

### VID-1, VID-2, VID-3 (Video)

- Open video folder with ≥ 50 items.
- VID-1/2: advance sequentially / back-and-forth 50+ times (use short clips or skip forward in player).
- VID-3: shuffle mode 50+ transitions if supported.
- Stop playback, wait 30 seconds. Capture cooldown log.

### BRW-1, BRW-2, BRW-3 (Browse/Sort)

- Open a folder with ≥ 500 mixed files.
- BRW-1: change sort mode 20+ times (name/date/size/type round-trip).
- BRW-2: navigate into a subfolder and back 30+ times.
- BRW-3: change filter type 30+ times.
- Exit folder, wait 30 seconds. Capture cooldown log.

### MIX-1, MIX-2 (Audio + Image simultaneous) — standard/legacy only

- MIX-1 (Audio slideshow): start audio playback, then start slideshow in photo-background mode (`AudioSlideshowPhotoModeManager`). Let it run 50+ image transitions over 10 minutes while audio continues.
- MIX-2 (Audio + manual image browse): start audio playlist via background service, then navigate to image viewer or browse. Manually advance 50+ images over 10 minutes while audio plays.
- After scenario: stop both surfaces, wait 30 seconds. Capture cooldown log.

**These are highest-priority scenarios** — simultaneous decoder pressure is the most likely path to monotonic growth.

## Recording results

For each scenario, extract from Logcat:

```
grep "MEM_ENDURANCE" logcat.txt > results/<scenario-id>_<device>.txt
```

Extract the `SUMMARY` lines and record verdict in the table below.

## Results table (fill after device run)

| Scenario | Device | verdict | peak MB | final MB | cooldown MB | notes |
|----------|--------|---------|---------|----------|-------------|-------|
| IMG-1 | primary | ? | ? | ? | ? | |
| IMG-2 | primary | ? | ? | ? | ? | |
| IMG-3 | primary | ? | ? | ? | ? | |
| AUD-1 | primary | ? | ? | ? | ? | |
| AUD-2 | primary | ? | ? | ? | ? | |
| AUD-3 | primary | ? | ? | ? | ? | |
| VID-1 | primary | ? | ? | ? | ? | |
| VID-2 | primary | ? | ? | ? | ? | |
| VID-3 | primary | ? | ? | ? | ? | |
| BRW-1 | primary | ? | ? | ? | ? | |
| BRW-2 | primary | ? | ? | ? | ? | |
| BRW-3 | primary | ? | ? | ? | ? | |
| MIX-1 | primary | ? | ? | ? | ? | audio slideshow |
| MIX-2 | primary | ? | ? | ? | ? | audio bg + image browse |
| IMG-1 | low-RAM | ? | ? | ? | ? | |
| BRW-1 | low-RAM | ? | ? | ? | ? | |
| MIX-1 | low-RAM | ? | ? | ? | ? | highest risk |

## Follow-up ticket creation

For each scenario with verdict = FAIL or SUSPICIOUS:

1. Allocate a new spec id via `scripts/spec_catalog/next-id.ps1`.
2. Write `PLAN/Sxxxx_<surface>-memory-fix.md` with the failing scenario and measured delta.
3. Set priority: FAIL → 70, SUSPICIOUS → 40.
4. Record follow-up ticket ids in the INDEX.md Follow-up tickets section.

## Verification predicate

- [x] Runbook covers IMG, AUD, VID, BRW, and MIX scenarios.
- [x] Results table template exists for primary and low-RAM verification.
- [x] Follow-up ticket creation procedure is documented.
- [x] Log storage path under `temp/` is documented.

## Revision History

- **2026-05-09** - by `/spec-update` (GPT-5.4, focus: consistency)
	- Applied: 1. Proposed (DISCUSS): 0.
