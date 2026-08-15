# Tactical Plan: S0190 — nolegal-youtube-shorts-ytmusic-extraction

**Strategic spec:** [`../S0190_nolegal-youtube-shorts-ytmusic-extraction.md`](../S0190_nolegal-youtube-shorts-ytmusic-extraction.md)
**Feature:** noLegal — YouTube Shorts & YouTube Music share download via yt-dlp internal downloader
**Tier:** 2
**Priority:** 75
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-05-14 (Phase 04 done → BlockNeedUserTest)

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-media-hint | — | ✅ Done | 6/6 | [PHASE_01__foundations-media-hint.md](PHASE_01__foundations-media-hint.md) |
| 02 | ytdlp-internal-downloader | 01 | ✅ Done | 5/5 | [PHASE_02__ytdlp-internal-downloader.md](PHASE_02__ytdlp-internal-downloader.md) |
| 03 | progress-hook-wiring | 02 | ✅ Done | 4/4 | [PHASE_03__progress-hook-wiring.md](PHASE_03__progress-hook-wiring.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items in the strategic spec are Resolved. No external blockers.

- [x] §6.1 yt-dlp pin — resolved (already bumped to 2026.3.17 in Phase A).
- [x] §6.2 NewPipe + music.youtube.com — resolved via `LinkUrlCanonicalizer` (Phase B).
- [x] §6.3 NewPipe + PoToken — deferred to S0198, not blocking Phase D.
- [x] §6.4 Cookie passthrough — resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` — sentence already added by Phase C of S0190; Phase 04 verifies it is still accurate and tightens the wording if the yt-dlp internal-downloader switch shifts user-visible behaviour (e.g. audio-only YTMusic).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (`add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `LinkUrlCanonicalizer` and `LinkDownloadSessionContext` changes).
- [ ] `/spec-check S0190` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0190`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech` for Phase D (yt-dlp internal downloader chosen over OkHttp write).
- 2026-05-14 — All 4 phases done. Ticket advanced to `BlockNeedUserTest`. 3 Timber.d S0190 tags active in code. Build PASS (standardDebug 1m 4s).
