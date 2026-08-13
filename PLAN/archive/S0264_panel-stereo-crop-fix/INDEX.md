# Tactical Plan: S0264 - panel-stereo-crop-fix

**Strategic spec:** [`../S0264_panel-stereo-crop-fix.md`](../S0264_panel-stereo-crop-fix.md)
**Feature:** Panel stereo single-eye crop fix
**Tier:** 2 - Easy (ad-hoc player bugfix)
**Priority:** 75
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | panel-playback-fix | - | ✅ Done | 3/3 | [PHASE_01__panel-playback-fix.md](PHASE_01__panel-playback-fix.md) |
| 02 | settings-strings-parity | 01 | ✅ Done | 3/3 | [PHASE_02__settings-strings-parity.md](PHASE_02__settings-strings-parity.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Concrete panel crop mechanism resolved - use local `TextureView` transform path. See strategic §6.1.
- [x] **Research:** Validation flavor set resolved - `standard + noLegal` mandatory. See strategic §6.2.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check <S0264>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <S0264>`.

---

## Blockers Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`. No open blockers after resolving strategic §6 items.
- 2026-05-20 - Implementation evidence closed: `assembleStandardDebug` PASS after standardDebug kapt-state cleanup, `build-nolegal-debug.ps1` PASS, strings parity PASS, catalog sync PASS.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-20 - All three phases marked done from repository state and validation evidence.
