# Tactical Plan: S1496 - dependency-pinning-gaps

**Strategic spec:** [`../S1496_dependency-pinning-gaps.md`](../S1496_dependency-pinning-gaps.md)
**Research inputs:** [`research/01__bouncycastle-actual-resolution.md`](research/01__bouncycastle-actual-resolution.md) · [`research/02__jsch-module-split.md`](research/02__jsch-module-split.md) · [`research/03__gate-second-module-coverage.md`](research/03__gate-second-module-coverage.md)
**Feature:** Working pins for BouncyCastle and jsch, with cross-module divergence visible to the doc-drift gate
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | bouncycastle-version-assertion | - | ✅ Done | 3/3 | [PHASE_01__bouncycastle-version-assertion.md](PHASE_01__bouncycastle-version-assertion.md) |
| 02 | wear-jsch-alignment | - | ✅ Done | 1/1 | [PHASE_02__wear-jsch-alignment.md](PHASE_02__wear-jsch-alignment.md) |
| 03 | cross-module-pin-gate | 01, 02 | ✅ Done | 5/5 | [PHASE_03__cross-module-pin-gate.md](PHASE_03__cross-module-pin-gate.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

**Ordering constraints.**

- Phase 03 makes a version divergence between `app_v2` and `wear` a hard failure of `check-doc-vs-gradle.ps1`, and phase 02 removes the only divergence that exists today (`jsch`, measured in research 03). Landing 03 before 02 would leave the gate red on the clean tree.
- Step 03.4 parses the `expectedBouncyCastleVersion` line that step 01.2 writes, so phase 01 also precedes phase 03.
- Phases 01 and 02 are independent of each other and may land in either order.

---

## Pre-Implementation Blockers

None - strategic §6 items 1, 2 and 3 are all `Status: Resolved`, each backed by an artifact under `research/`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic §8 states the ticket adds no capability.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected, no Kotlin is touched.
- [ ] `/spec-check S1496` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1496`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-09 - Initial tactical plan authored by `/spec-tech`.
