# Tactical Plan: S0265 - bugfix-audio-cover-lyrics-track-race

**Strategic spec:** [`../S0265_bugfix-audio-cover-lyrics-track-race.md`](../S0265_bugfix-audio-cover-lyrics-track-race.md)
**Feature:** Audio cover / metadata / lyrics desync fix on fast track switch (SFTP-heavy car scenario)
**Tier:** 3 - Moderate (ad-hoc, bugfix)
**Priority:** 90
**Status:** BlockNeedUserTest (all phases ✅; pending on-device acceptance)
**Phases:** 5 / 5 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | identity-contract | - | ✅ Done | 6/6 | [PHASE_01__identity-contract.md](PHASE_01__identity-contract.md) |
| 02 | stale-guard | 01 | ✅ Done | 5/5 | [PHASE_02__stale-guard.md](PHASE_02__stale-guard.md) |
| 03 | glide-cancellation | 02 | ✅ Done | 2/2 | [PHASE_03__glide-cancellation.md](PHASE_03__glide-cancellation.md) |
| 04 | media-session-identity | 02 | ✅ Done | 2/2 | [PHASE_04__media-session-identity.md](PHASE_04__media-session-identity.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved. No human-research blockers remain.

- [x] **Research §6.1:** Identity carrier - resolved to `MediaFile.path`.
- [x] **Research §6.2:** Generation counter - deferred (path identity sufficient for v1).
- [x] **Research §6.3:** Persist stale results - rejected (silent drop per ADR-2).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **skip** (strategic §8 explicitly states "Без изменений в docs/FEATURES").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Callback signature on public class `AudioCoverArtLoader` changed).
- [ ] `/spec-check S0265` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

- 2026-05-20 - No blockers at plan creation.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
