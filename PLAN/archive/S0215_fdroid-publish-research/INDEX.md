# Tactical Plan: S0215 — fdroid-publish-research (Phase 1 — IzzyOnDroid)

**Strategic spec:** [`../S0215_fdroid-publish-research.md`](../S0215_fdroid-publish-research.md)
**Feature:** Publish FastMediaSorter STANDARD on IzzyOnDroid catalog (Phase 1 of two-phase F-Droid strategy)
**Tier:** 4 — Strategic (ad-hoc)
**Priority:** 50
**Status:** ⛔ Blocked (BlockExternal — Phase 05 submission pending owner action)
**Phases:** 6 / 7 done (Phase 05 blocked; Phase 07 in progress)
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
>
> **Phase 2 scope:** main F-Droid (`f-droid.org`) tactical decomposition is intentionally deferred — see strategic §6 item 13 and ADR-4. After Phase 7 completes, the owner decides whether to open a separate spec for the FOSS-flavor refactor. This INDEX covers Phase 1 only.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-license-repo | — | ✅ Done | 4/4 | [PHASE_01__foundations-license-repo.md](PHASE_01__foundations-license-repo.md) |
| 02 | fastlane-metadata-en | 01 | ✅ Done | 6/6 | [PHASE_02__fastlane-metadata-en.md](PHASE_02__fastlane-metadata-en.md) |
| 03 | fastlane-metadata-ru-uk | 02 | ✅ Done | 4/4 | [PHASE_03__fastlane-metadata-ru-uk.md](PHASE_03__fastlane-metadata-ru-uk.md) |
| 04 | changelog-pipeline-hook | 02 | ✅ Done | 4/4 | [PHASE_04__changelog-pipeline-hook.md](PHASE_04__changelog-pipeline-hook.md) |
| 05 | izzyondroid-submission | 02, 03, 04 | ⛔ Blocked | 2/5 | [PHASE_05__izzyondroid-submission.md](PHASE_05__izzyondroid-submission.md) |
| 06 | readme-badge-trilingual | 05 | ✅ Done | 4/4 | [PHASE_06__readme-badge-trilingual.md](PHASE_06__readme-badge-trilingual.md) |
| 07 | docs-catalog-cleanup | all | 🚧 In Progress | 3/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 1 research items from strategic §6 with concrete resolution. Phase 2 items (§6 items 2, 3, 5, 9, 10, 11, 13) are deferred to a separate tactical plan and intentionally NOT listed here — they do not block Phase 1.

- [x] **Research §6.4 — Project LICENSE choice:** RESOLVED → **Apache 2.0** (matches the majority of project dependencies — AndroidX, Compose, Material3, Hilt, Retrofit, OkHttp, Glide, Markwon, JSoup, Tesseract). Compatible with both IzzyOnDroid and main F-Droid Inclusion Policy. Implemented in Phase 01 step 01.1.
- [x] **Research §6.6 — Locale notation in fastlane:** RESOLVED → **BCP47 (`en-US`, `ru-RU`, `uk-UA`)**. F-Droid and IzzyOnDroid both accept BCP47; project `androidResources.localeFilters` already uses ISO 639-1 (`en`, `ru`, `uk`), and BCP47 strict form aligns with industry convention. Implemented in Phases 02 / 03.
- [x] **Research §6.7 — Anti-Features for IzzyOnDroid recipe:** RESOLVED → **`NonFreeDep` + `NonFreeNet`** (per strategic ADR-3). Concrete justification text drafted in Phase 05 step 05.2.
- [x] **Research §6.8 — Fastlane metadata location:** RESOLVED → **`<root>/fastlane/metadata/android/<locale>/`** (single shared tree). Per-flavor `src/standard/fastlane/` is overkill for Phase 1 (only STANDARD is published to IzzyOnDroid). Implemented in Phase 02.

**Phase 1 blockers status:** None outstanding. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All seven phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — **skip** (strategic §8 says "Без изменений в `docs/FEATURES.md`"; publication channel is not a user-facing feature inside the app).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`).
- [ ] `dev/FUNCTIONALITY.log` has the `ADD S0215` entry recording the new distribution channel (via `add_to_functionality_log.ps1`).
- [ ] `dev/CATALOG/<module>.jsonl` is unchanged (no `.kt` file touched in Phase 1) — verified by running scan/render with no diff.
- [ ] IzzyOnDroid submission ticket / merge request has a publicly visible URL recorded in `dev/CHANGELOG.md`.
- [ ] `/spec-check S0215` returns `Verified` for Phase 1 scope.
- [ ] Strategic spec `Status:` advanced from `Tactical` → `Implemented` (after Phase 7) → `BlockNeedUserTest` (when submission is publicly live but acceptance pending review) → `Verified` (after IzzyOnDroid recipe accepted).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

- 2026-05-15 — none.

---

## Change Log

- 2026-05-15 — Initial tactical plan authored by `/spec-tech` (Phase 1 only; Phase 2 deferred per strategic ADR-4).
