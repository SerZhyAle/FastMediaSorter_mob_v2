# Tactical Plan: S0187 — noLegal YouTube Extraction Recovery

**Strategic spec:** [`../S0187_nolegal-youtube-extraction-recovery.md`](../S0187_nolegal-youtube-extraction-recovery.md)
**Feature:** noLegal — reliable YouTube / YouTube Music share downloads
**Tier:** TBD
**Priority:** 80
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ytdlp-format-fallback | — | ✅ Done | 2/2 | [PHASE_01__ytdlp-format-fallback.md](PHASE_01__ytdlp-format-fallback.md) |
| 02 | known-auth-youtube | 01 | ✅ Done | 2/2 | [PHASE_02__known-auth-youtube.md](PHASE_02__known-auth-youtube.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

_None. §6 open questions resolved during /spec-tech analysis:_

- §6.1 — §A re-ordering is NOT implemented. S0186 cascade-resilience catch already in place;
  the yt-dlp `NotFound` mapping in Phase 01 is the complementary fix that lets cascade reach NewPipe.
- §6.2 — yt-dlp version pin not bumped in this spec (format cascade in `download_to_file` is already
  comprehensive; the root cause is `extract_info` raising DownloadError, not the format string).
- §6.3 — YTMusic audio-only: `NewPipeSiteExtractionStrategy.selectProgressiveStream()` already falls
  through to audio streams when no video stream is available. No extra work needed.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates it).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0187` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status.
5. All done: flip `Status:` to `Done`, run `/spec-check S0187`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech`.
