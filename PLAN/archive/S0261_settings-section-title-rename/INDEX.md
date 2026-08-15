# Tactical Plan: S0261 - settings-section-title-rename

**Strategic spec:** [`../S0261_settings-section-title-rename.md`](../S0261_settings-section-title-rename.md)
**Feature:** Rename expandable settings section titles
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | scope-rename-map | - | ✅ Done | 2/2 | [PHASE_01__scope-rename-map.md](PHASE_01__scope-rename-map.md) |
| 02 | general-operations-strings | 01 | ✅ Done | 2/2 | [PHASE_02__general-operations-strings.md](PHASE_02__general-operations-strings.md) |
| 03 | media-playback-strings | 02 | ✅ Done | 2/2 | [PHASE_03__media-playback-strings.md](PHASE_03__media-playback-strings.md) |
| 04 | docs-catalog-cleanup | 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open blockers. Strategic §6 items are resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check <S0261>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <S0261>`.

---

## Blockers Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
