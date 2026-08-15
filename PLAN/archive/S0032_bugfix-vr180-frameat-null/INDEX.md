# Tactical Plan: S0032 — bugfix-vr180-frameat-null

**Strategic spec:** [`../S0032_bugfix-vr180-frameat-null.md`](../S0032_bugfix-vr180-frameat-null.md)
**Feature:** Resilient poster-frame extraction for VR180 / 7K videos
**Tier:** 2 — Easy
**Priority:** 45
**Status:** Not started
**Phases:** 3 / 3 done
**Implemented:** 2026-04-29
**Last updated:** 2026-04-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | preventive-guards-logging | — | ✅ Done | 4/4 | [PHASE_01__preventive-guards-logging.md](PHASE_01__preventive-guards-logging.md) |
| 02 | fallback-hierarchy | 01 | ✅ Done | 5/5 | [PHASE_02__fallback-hierarchy.md](PHASE_02__fallback-hierarchy.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 research items are resolved by the directive that drove this tactical plan — see `PLAN/dev-queue-2026-04-29.md`:

- Native-heap threshold: **50 MB** via `android.os.Debug.getNativeHeapFreeSize()` (existing project pattern, see [VideoPlayerManager.kt:648](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt#L648)).
- Decoder-busy detection: `ExoPlayer.isPlaying || ExoPlayer.isLoading` for the same `currentFilePath`.
- Browse-cache thumbnail access: Glide `asBitmap().onlyRetrieveFromCache(true).submit().get(50ms)` — bounded short window keeps it memory-cache-only in practice.
- No further blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated under the Player section (resilience bullet — see Phase 03).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0032` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Status Block...`.
5. All done: `/spec-check S0032`.

---

## Blockers Log

- _none yet_

---

## Change Log

- 2026-04-29 — Initial tactical plan authored by `/spec-tech`.
