# Tactical Plan: S0042 — agp10-kapt-to-ksp-migration

**Strategic spec:** [`../S0042_agp10-kapt-to-ksp-migration.md`](../S0042_agp10-kapt-to-ksp-migration.md)
**Feature:** Migrate all annotation processors from kapt to KSP; remove legacy DSL compat flags
**Tier:** 4 — Strategic
**Priority:** 55
**Status:** Not started
**Phases:** 0 / 7 done
**Last updated:** 2026-05-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | glide-to-ksp | — | ⛔ Blocked | 0/3 | [PHASE_01__glide-to-ksp.md](PHASE_01__glide-to-ksp.md) |
| 02 | room-to-ksp | 01 | ⬜ Not started | 0/3 | [PHASE_02__room-to-ksp.md](PHASE_02__room-to-ksp.md) |
| 03 | hilt-to-ksp | 02 | ⬜ Not started | 0/4 | [PHASE_03__hilt-to-ksp.md](PHASE_03__hilt-to-ksp.md) |
| 04 | kapt-plugin-removal | 03 | ⬜ Not started | 0/4 | [PHASE_04__kapt-plugin-removal.md](PHASE_04__kapt-plugin-removal.md) |
| 05 | compat-flags-removal | 04 | ⬜ Not started | 0/2 | [PHASE_05__compat-flags-removal.md](PHASE_05__compat-flags-removal.md) |
| 06 | wear-and-sourcesets-unify | 05 | ⬜ Not started | 0/3 | [PHASE_06__wear-and-sourcesets-unify.md](PHASE_06__wear-and-sourcesets-unify.md) |
| 07 | docs-catalog-cleanup | all | ⬜ Not started | 0/2 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are resolved. No blockers — Phase 01 may start immediately.

- [x] **§6.1 Device test depth** — Resolved inline in strategic spec: CI build + unit tests only; no manual device run required per phase.
- [x] **§6.2 Glide 4.16.0 KSP behavior** — Resolved: Glide 4.14.2+ ships stable KSP support via the same `com.github.bumptech.glide:compiler` artifact. No runtime semantic differences vs kapt for this codebase (`exportSchema`-equivalent does not exist for Glide; no custom `AppGlideModule` options affected by processor switch).
- [x] **§6.3 Hilt 2.57.2 test DI + KSP** — Resolved: Hilt 2.51+ fully supports KSP for all annotation targets including androidTest (`kspAndroidTest`). No test infrastructure changes required for version 2.57.2.
- [x] **§6.4 Build time baseline** — Resolved: baseline is measured as part of Phase 01 execution (record cold-build time before and after); the §3.2 threshold (+20% max for cold build) is already defined. Measurement is procedural, not a prerequisite to starting.
- [x] **§6.5 Compat flags sequence** — Resolved: `android.builtInKotlin=false` and `android.newDsl=false` are co-dependent (removing one without the other produces a build error). Both are removed together in a single Phase 05 step.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` — no change required (infra-only, see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` — no regen needed (no `.kt` files changed).
- [ ] `/spec-check S0042` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0042`.

---

## Blockers Log

- 2026-05-07 — Phase 01 blocked: `glide:ksp:4.16.0` is incompatible with `okhttp3-integration:4.16.0`. The `okhttp3-integration` API jar contains pre-indexed `GlideIndexer_GlideModule_*.class` files that KSP cannot process (`different roots` error). Project uses `@GlideModule` on `GlideAppModule` — processor cannot be removed. See BlockQuestions below for options.

---

## Change Log

- 2026-05-07 — Initial tactical plan authored by `/spec-tech`.
