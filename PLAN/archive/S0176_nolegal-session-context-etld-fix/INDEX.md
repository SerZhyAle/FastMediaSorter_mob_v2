# Tactical Plan: S0176 — nolegal-session-context-etld-fix

**Strategic spec:** [../S0176_nolegal-session-context-etld-fix.md](../S0176_nolegal-session-context-etld-fix.md)
**Feature:** noLegal: eTLD+1 session-context lookup fix
**Tier:** 2 — Easy
**Priority:** 60
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | domain-resolution | — | ✅ Done | 2/2 | [PHASE_01__domain-resolution.md](PHASE_01__domain-resolution.md) |
| 02 | session-context-binding | 01 | ✅ Done | 2/2 | [PHASE_02__session-context-binding.md](PHASE_02__session-context-binding.md) |
| 03 | regression-tests | 02 | ✅ Done | 2/2 | [PHASE_03__regression-tests.md](PHASE_03__regression-tests.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** direct consumers are frozen. The per-run session context is consumed by the shared HTTP cookie bridge and by the dynamic WebView extraction path; regression coverage must include both. Strategic §6 Q1 resolved 2026-05-12.
- [x] **ADR:** use the PSL-aware registrable-domain helper already available in the current OkHttp pin, with null-guard for IP, localhost, and public-suffix-only inputs. Strategic §6 Q2 resolved 2026-05-12.
- [x] **Scope:** keep `LinkDownloadSessionContext` matching unchanged; Phase 02 binds the matched persisted host into the holder instead of expanding holder logic. Strategic §6 Q3 resolved 2026-05-12.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated only if the implementation becomes user-facing; current strategic §8 says this is not required.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated after Kotlin changes.
- [ ] `/spec-check S0176` returns `Verified` after user verification clears `BlockNeedUserTest`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip the row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, and bump the counter.
4. If blocked: flip the row to `⛔ Blocked`, add a bullet to Blockers Log, and mirror the matching `Block*` journal status if the whole spec stops.
5. All done: flip `Status:` to `Done`, then run `/spec-check S0176`.

---

## Blockers Log

- 2026-05-12 — Planning stopped before Phase 01: strategic §6 Q1/Q2/Q3 remain open and gate implementation.
- 2026-05-12 — Research blockers Q1/Q2/Q3 resolved via `/spec-update`. Implementation is ready for `/spec-dev`.

---

## Change Log

- 2026-05-12 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-12 — Research blockers Q1/Q2/Q3 resolved by `/spec-update`.
- 2026-05-16 — `/spec-all` F5 audit: all unit tests pass (7+6+6). Added `Timber.d("S0176:...")` probe tags at flow entry points. Status confirmed BlockNeedUserTest — on-device verification pending.