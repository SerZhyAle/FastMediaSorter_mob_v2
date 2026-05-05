# Tactical Plan: S0056 — smb-scan-slowness-investigation

**Strategic spec:** [`../S0056_smb-scan-slowness-investigation.md`](../S0056_smb-scan-slowness-investigation.md)
**Feature:** SMB scan slowness investigation — adaptive SLOW SCAN threshold + progress-UI follow-up
**Tier:** 3 — Moderate
**Priority:** 70
**Status:** In Progress
**Phases:** 2 / 4 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | adaptive-threshold | — | ✅ Done | 5/5 | [PHASE_01__adaptive-threshold.md](PHASE_01__adaptive-threshold.md) |
| 02 | progress-ui-followup-spec | 01 | ✅ Done | 3/3 | [PHASE_02__progress-ui-followup-spec.md](PHASE_02__progress-ui-followup-spec.md) |
| 03 | on-device-verification | 01 | ⛔ Blocked | 1/4 | [PHASE_03__on-device-verification.md](PHASE_03__on-device-verification.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ⬜ Not started | 0/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items resolved (Q1–Q4) or deferred to Phase 03 (Q5 — on-device repeatability). No outstanding blockers gating Phase 01.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` — **no update required** (internal optimization, not user-facing per strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `ScanMetricsRecorder` changed).
- [ ] `/spec-check S0056` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0056`.

---

## Blockers Log

- 2026-05-03 — Plan authored. No blockers.
- 2026-05-03 — Phase 03 blocked at Step 03.2: requires owner to execute 5 force-refresh scans on Quest 3 against SMB resourceId=18 (`192.168.1.110:445`). Spec status set to `BlockNeedUserTest`. Resume `/spec-dev S0056` after the table in `temp/S0056_on_device_verification_20260503.md` is filled.

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
