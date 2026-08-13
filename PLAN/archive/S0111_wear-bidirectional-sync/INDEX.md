# Tactical Plan: S0111 — wear-bidirectional-sync

**Strategic spec:** [`../S0111_wear-bidirectional-sync.md`](../S0111_wear-bidirectional-sync.md)
**Feature:** Wear OS Bidirectional Sync — Sources, Settings, Favorites, Playback
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | data-layer-envelope | — | ✅ Done | 8/8 | [PHASE_01__data-layer-envelope.md](PHASE_01__data-layer-envelope.md) |
| 02 | settings-sync | 01 | ✅ Done | 9/9 | [PHASE_02__settings-sync.md](PHASE_02__settings-sync.md) |
| 03 | sources-watch-to-phone | 01 | ✅ Done | 9/9 | [PHASE_03__sources-watch-to-phone.md](PHASE_03__sources-watch-to-phone.md) |
| 04 | ftp-sftp-browse | — | ✅ Done | 5/5 | [PHASE_04__ftp-sftp-browse.md](PHASE_04__ftp-sftp-browse.md) |
| 05 | playback-state-remote-control | 01 | ✅ Done | 13/13 | [PHASE_05__playback-state-remote-control.md](PHASE_05__playback-state-remote-control.md) |
| 06 | favorites-sync | 01 | ✅ Done | 11/11 | [PHASE_06__favorites-sync.md](PHASE_06__favorites-sync.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 7/7 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research: Data Layer size budget** (§6.1) — **Resolved 2026-05-07.** Calculated max payload: 50 sources × 350 B + 200 favorites × 100 B ≈ 37 KB — well within 100 KB limit. PutDataItem is sufficient; no ChannelAPI chunking needed.
- [x] **Research: Battery impact of playback state updates** (§6.2) — **Resolved 2026-05-07.** Decision: publish only on `isPlaying` toggle and track change. No timer-based updates. Phase 05 steps already encode this.
- [x] **Owner decision: FTP/SFTP library for Wear APK** (§6.3) — **Resolved 2026-05-07.** Add `commons-net:3.10.0` (FTP, ~200 KB ProGuard) + `com.github.mwiede:jsch:0.2.17` (SFTP, ~130 KB ProGuard). SSHJ excluded due to BouncyCastle conflict with SMBJ already in `:wear`. Phase 04 Step 4.1 uses these versions.
- [x] **Owner decision: Watch→Phone import notification UX** (§6.4) — **Resolved 2026-05-07.** Badge/card on WearSync screen (no POST_NOTIFICATIONS permission required). This is what Phase 03 implements.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/wear.jsonl` regenerated.
- [x] All `Timber.d("S0111:` tags removed from `.kt` files (Phase 07 step 7.7).
- [x] `/spec-check S0111` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Id S0111 -Status BlockQuestions`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0111`.

---

## Blockers Log

_(none yet)_

---

## Change Log

- 2026-05-07 — Initial tactical plan authored by `/spec-tech`.
