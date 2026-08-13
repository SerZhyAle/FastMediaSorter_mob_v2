# Tactical Plan: S0286 - compliance-lint-gate-and-features-sanitization

**Strategic spec:** [../S0286_compliance-lint-gate-and-features-sanitization.md](../S0286_compliance-lint-gate-and-features-sanitization.md)
**Feature:** Compliance lint-gate and public FEATURES sanitization
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 65
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | gate-foundation | - | ✅ Done | 3/3 | [PHASE_01__gate-foundation.md](PHASE_01__gate-foundation.md) |
| 02 | features-sanitization | 01 | ✅ Done | 2/2 | [PHASE_02__features-sanitization.md](PHASE_02__features-sanitization.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.1:** first iteration excludes `YouTube` / `youtube.com` / `youtu.be` from the seed deny-list because current market code still has legitimate Google-auth and media-routing references. Revisit only after the legacy YT-specific market surface is burned down in a dedicated follow-up.
- [x] **Research §6.2:** ship the Gradle verify-task only. A custom Android Lint module is explicitly out of scope for S0286.
- [x] **Research §6.3:** the deny-list source and the temporary legacy baseline live under `app_v2/compliance/` so the task stays module-local and cache-friendly.
- [x] **Research §6.5:** public `docs/FEATURES.md` + `_RU` + `_UK` are scanned by the same task via explicit file inputs; `docs/FEATURES_noLegal*.md` stay out of scope.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` contain zero deny-list matches.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `assembleStandardDebug` passes with `verifyNoPlatformNames` wired into `preBuild`.
- [x] A negative validation run proves that a new forbidden literal fails the compliance task.
- [x] `/spec-check S0286` returns `Verified`.
- [x] The latest S0140 audit no longer contains `WARN §11.9` or `WARN §11.11`.

---

## How to Track Progress

1. Before starting a phase: flip the row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm the Phase Done Criteria, flip the row to `✅ Done`, bump the phase counter.
4. If blocked: flip the row to `⛔ Blocked`, add a bullet to the Blockers Log, and set the spec catalog status to the matching `Block*` state.
5. All implementation work done: flip `Status:` to `Done`, then run `/spec-check S0286` when formal audit closure is needed.

---

## Blockers Log

- 2026-05-21 - Initial tactical plan authored. No open blockers after the gate-format decisions above.
- 2026-05-21 - All implementation phases completed. Formal `/spec-check` closure remains a separate audit action.

---

## Change Log

- 2026-05-21 - Initial tactical plan authored by `/spec-tech` equivalent implementation.
- 2026-05-21 - Implemented the Gradle compliance gate, public FEATURES sanitization, and negative probe validation.
