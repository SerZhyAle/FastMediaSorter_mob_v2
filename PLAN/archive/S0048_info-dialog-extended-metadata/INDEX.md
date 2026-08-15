# Tactical Plan: S0048 — info-dialog-extended-metadata

**Strategic spec:** [`../S0048_info-dialog-extended-metadata.md`](../S0048_info-dialog-extended-metadata.md)
**Feature:** Extended metadata in file-info dialog (audio + file information block)
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | split-fileinfo-launch | — | ✅ Done | 4/4 | [PHASE_01__split-fileinfo-launch.md](PHASE_01__split-fileinfo-launch.md) |
| 02 | extend-audio-extractor | 01 | ✅ Done | 6/6 | [PHASE_02__extend-audio-extractor.md](PHASE_02__extend-audio-extractor.md) |
| 03 | info-dialog-audio-route | 02 | ✅ Done | 7/7 | [PHASE_03__info-dialog-audio-route.md](PHASE_03__info-dialog-audio-route.md) |
| 04 | file-info-block | 01 | ✅ Done | 7/7 | [PHASE_04__file-info-block.md](PHASE_04__file-info-block.md) |
| 05 | docs-catalog-cleanup | 03, 04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research items. All §6 questions are Resolved as of 2026-05-02.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-visible — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `AudioMetadataLoader` and new helpers/managers changed).
- [ ] `/spec-check S0048` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0048`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-02 — Initial tactical plan authored by `/spec-tech`.
