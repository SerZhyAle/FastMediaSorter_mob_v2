# Tactical Plan: S0356 - bugfix-player-media-load-npe

**Strategic spec:** [`../S0356_bugfix-player-media-load-npe.md`](../S0356_bugfix-player-media-load-npe.md)
**Feature:** NPE при загрузке файлов в плеере (MediaFile.copy on corrupted model)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 70
**Status:** Implemented (awaiting on-device test)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | model-integrity-diagnostics | - | ✅ Done | 3/3 | [PHASE_01__model-integrity-diagnostics.md](PHASE_01__model-integrity-diagnostics.md) |
| 02 | model-creation-integrity-guard | 01 | ✅ Done | 4/4 | [PHASE_02__model-creation-integrity-guard.md](PHASE_02__model-creation-integrity-guard.md) |
| 03 | player-reconcile-isolation | 02 | ✅ Done | 2/2 | [PHASE_03__player-reconcile-isolation.md](PHASE_03__player-reconcile-isolation.md) |
| 04 | favorites-log-level | 03 | ✅ Done | 2/2 | [PHASE_04__favorites-log-level.md](PHASE_04__favorites-log-level.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 is itself the diagnostic phase that resolves both research items. It produces the evidence (deobfuscated frame or instrumented integrity log) that tells Phase 02 *which* creation site to harden and tells Phase 04 *whether* the favorites-reconcile failure is always a data defect. Phase 02 must not start until both items below are marked resolved by Phase 01's output.

- [x] **Research:** Source of the null in a declared non-null `MediaFile` field - resolved: the network media scanners (SMB/SFTP/FTP) building `MediaFile` from SMBJ/SSHJ/Commons-Net platform-typed values; the 2026-06-04 logs pin the crashing session to an SFTP source. See strategic §6.1 and PHASE_01 Handoff Notes.
- [x] **Research:** Whether the favorites-reconcile failure is always a data defect - resolved: always a true data defect (real null in a non-null field surfaced by `copy()`), but now handled gracefully by per-element isolation + upstream guard, so the reconcile log is degraded to `Timber.w`. See strategic §6.2.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped; strategic §8 states "Без изменений в docs/FEATURES".
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new `MediaFileIntegrity`).
- [ ] `/spec-check S0356` returns `Verified` - pending on-device test (journal at `BlockNeedUserTest`).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` - pending.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0356`.

---

## Blockers Log

- 2026-06-04 - Phase 02 and Phase 04 gated on Phase 01 diagnostics output (strategic §6.1, §6.2). Next: run Phase 01, record creation site + reconcile-failure nature, then unblock.

---

## Change Log

- 2026-06-04 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-04 - Code for Phases 02/03/04 implemented in a prior session (MediaFileIntegrity + 3 scanners, reconcileFavoriteFlags isolation, Timber.w level) but left untracked.
- 2026-06-04 - `/spec-dev` continuation: reconciled tracking against actual code; resolved research §6.1/§6.2 from the 2026-06-04 logs (creation site = network scanners, SFTP confirmed); Phase 01 diverged - temporary integrity probe loop superseded by the MediaFileIntegrity substitution log, only the BlockNeedUserTest tag added. Reconcile unit test PASS, standardDebug compiles. Status -> Implemented / BlockNeedUserTest (awaiting on-device test).
