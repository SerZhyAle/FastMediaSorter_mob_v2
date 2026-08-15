# Tactical Plan: S0308 - password-protected-files

**Strategic spec:** [`../S0308_password-protected-files.md`](../S0308_password-protected-files.md)
**Feature:** Password-protected files
**Tier:** 4 - Strategic
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | zip-domain | - | ✅ Done | 3/3 | [PHASE_01__zip-domain.md](PHASE_01__zip-domain.md) |
| 02 | browse-password-ui | 01 | ✅ Done | 4/4 | [PHASE_02__browse-password-ui.md](PHASE_02__browse-password-ui.md) |
| 03 | document-fallbacks | 02 | ✅ Done | 3/3 | [PHASE_03__document-fallbacks.md](PHASE_03__document-fallbacks.md) |
| 04 | docs-catalog-cleanup | 01-03 | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** PDF protection - first slice is detection-only + external fallback; no PDF password engine.
- [x] **Research:** ZIP protection - first slice uses ZipCrypto/AES support through a permissive ZIP library.
- [x] **Research:** Office protection - first slice is unsupported-protection classification + external fallback.
- [x] **Research:** EPUB protection - DRM remains unsupported; password ZIP container follows ZIP handling when practical.
- [x] **Research:** Remote/cloud sources - first slice uses existing materialization where present; Browse ZIP extraction remains local/SAF until a follow-up remote extraction phase.
- [x] **Research:** Password lifecycle - password is not stored.
- [x] **Research:** UI prompt - modal text field with OK and Cancel.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0308` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0308`.

---

## Blockers Log

- None.

---

## Change Log

- 2026-05-30 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-30 - `/spec-dev` started Phase 01.
- 2026-05-30 - Phase 01 completed. Targeted archive extraction tests passed.
- 2026-05-30 - `/spec-dev` started Phase 02.
- 2026-05-30 - Phase 02 completed. Password prompt wiring compiled and strings audit passed.
- 2026-05-30 - `/spec-dev` started Phase 03.
- 2026-05-30 - Phase 03 completed. Protected PDF/EPUB fallback and Office ZIP-container classification compiled.
- 2026-05-30 - Phase 04 completed. S0308 implementation validated and ready for `/spec-check`.