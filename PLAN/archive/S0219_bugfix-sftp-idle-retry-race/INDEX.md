# Tactical Plan: S0219 — bugfix-sftp-idle-retry-race

**Strategic spec:** [`../S0219_bugfix-sftp-idle-retry-race.md`](../S0219_bugfix-sftp-idle-retry-race.md)
**Feature:** Устранение гонки idle/retry в SFTP-цепочке
**Tier:** 2 — Easy (ad-hoc, bugfix)
**Priority:** 90
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | client-wrapper-unwrap | — | ✅ Done | 5/5 | [PHASE_01__client-wrapper-unwrap.md](PHASE_01__client-wrapper-unwrap.md) |
| 02 | pool-active-borrow | 01 | ✅ Done | 4/4 | [PHASE_02__pool-active-borrow.md](PHASE_02__pool-active-borrow.md) |
| 03 | rearm-on-finally | 01 | ✅ Done | 3/3 | [PHASE_03__rearm-on-finally.md](PHASE_03__rearm-on-finally.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 contains three Open research items. All three are resolved here as design decisions captured in the tactical plan — no human research is required before Phase 01 starts.

- [x] **Research §6.1 — Pillar B implementation choice.** Resolved: active-borrow counter (Option ii augmented with deferred disconnect). Captured in Phase 02 objective and steps. Reason: avoids holding `poolMutex` across network IO, satisfies strategic §3.2 performance constraint.
- [x] **Research §6.2 — Rearm placement.** Resolved: rearm in `finally`, but skip when the thrown exception is `CancellationException`. Captured in Phase 03 step 03.1. Reason: preserves S0205 invariant.
- [x] **Research §6.3 — Point-catch coverage in `exists`.** Resolved: keep only `SSH_FX_NO_SUCH_FILE` translated to `Result.success(false)`. All other `SftpException` ids and any `IOException` propagate. Captured in Phase 01 step 01.5.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/FUNCTIONALITY.log` carries a FIX entry for S0219.
- [ ] `/spec-check S0219` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0219`.

---

## Blockers Log

- _none yet_

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-tech`.
