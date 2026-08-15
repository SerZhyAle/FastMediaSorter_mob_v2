# Tactical Plan: S0245 — vr-settings-scaffold-stage0

**Strategic spec:** [`../S0245_vr-settings-scaffold-stage0.md`](../S0245_vr-settings-scaffold-stage0.md)
**Feature:** VR Settings scaffold + master toggle (Stage 0 of S0240 VR rewrite epic)
**Tier:** 2 — implementation step (thin scaffolding, no real VR functionality)
**Priority:** 85
**Status:** Not started
**Phases:** 0 / 7 done
**Last updated:** 2026-05-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec and `PLAN/S0240_vr-stack-rewrite-epic/RESEARCH.md`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | vr-flavor-restore | — | ⬜ Not started | 0/6 | [PHASE_01__vr-flavor-restore.md](PHASE_01__vr-flavor-restore.md) |
| 02 | xr-core-contracts | 01 | ⬜ Not started | 0/5 | [PHASE_02__xr-core-contracts.md](PHASE_02__xr-core-contracts.md) |
| 03 | xr-stub-impls | 02 | ⬜ Not started | 0/4 | [PHASE_03__xr-stub-impls.md](PHASE_03__xr-stub-impls.md) |
| 04 | xr-real-impls | 02 | ⬜ Not started | 0/5 | [PHASE_04__xr-real-impls.md](PHASE_04__xr-real-impls.md) |
| 05 | settings-tab-extension | 02 | ⬜ Not started | 0/4 | [PHASE_05__settings-tab-extension.md](PHASE_05__settings-tab-extension.md) |
| 06 | vr-settings-fragment | 04, 05 | ⬜ Not started | 0/7 | [PHASE_06__vr-settings-fragment.md](PHASE_06__vr-settings-fragment.md) |
| 07 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 lists no open research items — R-02 / R-05 / R-06 / R-07 / R-09 are captured in `PLAN/S0240_vr-stack-rewrite-epic/RESEARCH.md` (S0244 Verified).

UI ambiguity items from strategic §4 are resolved with defaults below (see "UI Decisions Applied"). `/ui-clarify` may later refine the user-visible copy and visual treatment without changing the architecture.

- [x] Research items closed (S0244 Verified).
- [x] Blocker spec S0241 Verified (old VR stack removed).
- [x] UI defaults captured below; no architecture-level ambiguity remains.

---

## UI Decisions Applied (resolved from codebase / strategic spec / docs)

| Strategic §4 item | Decision | Source |
|-------------------|----------|--------|
| Master toggle text | EN: "Enable 3D VR" / RU: "Включить 3D VR" / UK: "Увімкнути 3D VR" | `docs/COMMUNICATION_POLICY.md` §2 (preference-toggle formula) |
| Master toggle summary | EN: "Show VR features in this app" / RU: "Показывать VR-функции в этом приложении" / UK: "Показувати VR-функції у цьому застосунку" | `docs/COMMUNICATION_POLICY.md` §6 (tone checklist) |
| VR block icon | Reuse `R.drawable.ic_vr_3d` (already in `src/main/res/drawable/`, survived S0241) | R-01 |
| Visibility on non-VR device | Hide entirely (`XrEnvironmentDetector.detect() == NONE` → tab not added) | Strategic §3.5 ("блок может вообще не появляться") |
| Placeholder controls on Stage 0 | Empty section with single info `TextView` "Дополнительные настройки появятся по мере включения VR-функций" | Strategic §3.5 ("задел под Этап 1+") |
| Tab position | 5th position (after Operations), via dynamic `SettingsTabExtension` set | R-02 best practice (option A) |
| Device-change behaviour | Persist user choice; do NOT reset toggle if device switches | Conservative default (user choice is sticky data) |

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `assembleStandardDebug`, `assembleVrDebug`, `assembleNoLegalDebug` all pass.
- [ ] APK inspection: `vr` APK contains `VrSettingsFragment`; `standard` APK does **not** contain any `core/xr/*Impl` class from `src/vr/java/`.
- [ ] `docs/FEATURES*.md` — **not** updated on Stage 0 (no user-visible capability yet per strategic §7).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with new XR classes.
- [ ] String parity for `vr_settings_*` keys verified by `check_strings_localized.ps1`.
- [ ] `/spec-check S0245` returns `Verified` after `BlockNeedUserTest` device gate.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: set journal status to `BlockNeedUserTest` (device gate), insert `Timber.d("S0245: ...")` tags, run `/spec-check S0245` after operator confirms logcat sighting.

---

## Blockers Log

- 2026-05-18 — Tactical plan authored; no active blockers.

---

## Change Log

- 2026-05-18 — Initial tactical plan authored by `/spec-tech`.
