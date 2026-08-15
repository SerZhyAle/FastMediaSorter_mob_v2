# Tactical Plan: S0144 — fix-link-download-auth-ux

**Strategic spec:** [`../S0144_fix-link-download-auth-ux.md`](../S0144_fix-link-download-auth-ux.md)
**Feature:** Доводка авторизаций для скачивания по ссылке
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 85
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | known-resources-registry | — | ✅ Done | 1/1 | [PHASE_01__known-resources-registry.md](PHASE_01__known-resources-registry.md) |
| 02 | auth-list-top-toolbar | — | ✅ Done | 4/4 | [PHASE_02__auth-list-top-toolbar.md](PHASE_02__auth-list-top-toolbar.md) |
| 03 | auth-add-resource-picker | 01, 02 | ✅ Done | 2/2 | [PHASE_03__auth-add-resource-picker.md](PHASE_03__auth-add-resource-picker.md) |
| 04 | webview-intent-redirect-fix | — | ✅ Done | 1/1 | [PHASE_04__webview-intent-redirect-fix.md](PHASE_04__webview-intent-redirect-fix.md) |
| 05 | share-auth-offer | 01, 04 | ✅ Done | 4/4 | [PHASE_05__share-auth-offer.md](PHASE_05__share-auth-offer.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** canonical web login URLs per known resource — confirmed 2026-05-10: seed best-known URLs (e.g. Instagram → `https://www.instagram.com/accounts/login/`); verify on device during Phase 05 testing. See strategic §6.1.
- [x] **Research:** where to show the share-auth offer dialog relative to the download pipeline — confirmed 2026-05-10: show dialog, wait for response, then continue (option a). See strategic §6.2.
- [x] **Research:** storage backend for per-host offer dismissals — confirmed 2026-05-10: dedicated SharedPreferences-backed singleton (no Room). See strategic §6.3.

> All three blockers confirmed by the owner on 2026-05-10. Phases may proceed.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-facing — see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new classes added).
- [ ] `/spec-check S0144` returns `Verified` — pending; spec is at `BlockNeedUserTest` (on-device verification of Instagram sign-in + share-auth offer).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0144`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-05-10 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-10 — Phase 02 trimmed 5→4 steps (reuse existing `setting_saved_authorizations_title` / `auth_sessions_add_button`, no new title key).
- 2026-05-10 — All 6 phases implemented by `/spec-dev`; `build-debug.PS1` BUILD SUCCESSFUL; strategic spec → `Implemented`; journal → `BlockNeedUserTest` (on-device verification pending). Next: `/spec-check S0144` after device test.
