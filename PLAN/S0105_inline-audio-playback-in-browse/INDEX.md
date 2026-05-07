# Tactical Plan: S0105 — inline-audio-playback-in-browse

**Strategic spec:** [`../S0105_inline-audio-playback-in-browse.md`](../S0105_inline-audio-playback-in-browse.md)
**Feature:** Inline audio playback button on file cards in any Browse resource
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | adapter-play-condition | — | ✅ Done | 3/3 | [PHASE_01__adapter-play-condition.md](PHASE_01__adapter-play-condition.md) |
| 02 | stop-on-folder-navigate | 01 | ✅ Done | 3/3 | [PHASE_02__stop-on-folder-navigate.md](PHASE_02__stop-on-folder-navigate.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — all §6 research items resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (§8 of strategic spec).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0105` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0105`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
