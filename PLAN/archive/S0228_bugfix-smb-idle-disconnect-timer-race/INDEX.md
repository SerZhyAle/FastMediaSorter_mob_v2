# Tactical Plan: S0228 — bugfix-smb-idle-disconnect-timer-race

**Strategic spec:** [`../S0228_bugfix-smb-idle-disconnect-timer-race.md`](../S0228_bugfix-smb-idle-disconnect-timer-race.md)
**Feature:** SMB idle-disconnect exact-once timeout semantics
**Tier:** 2 — Easy (ad-hoc, bugfix)
**Priority:** 90
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | idle-generation-ownership | — | ✅ Done | 3/3 | [PHASE_01__idle-generation-ownership.md](PHASE_01__idle-generation-ownership.md) |
| 02 | smb-timeout-entrypoint | 01 | ✅ Done | 3/3 | [PHASE_02__smb-timeout-entrypoint.md](PHASE_02__smb-timeout-entrypoint.md) |
| 03 | exact-once-coverage | 01, 02 | ✅ Done | 3/3 | [PHASE_03__exact-once-coverage.md](PHASE_03__exact-once-coverage.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.1:** generation-token is the ownership primitive for one transport-key. Per-key mutex was rejected because it expands the critical section without adding better testability for this bugfix.
- [x] **Research §6.2:** stale-callback suppression lives only in `IdleDisconnectPolicyImpl`; the SMB callback entrypoint remains cleanup-only.
- [x] **Research §6.3:** manual SMB acceptance predicate is frozen: at most one `IdleDisconnect: timeout fired` per transport-key per 30 s idle window, `IdleDisconnect: stale timeout dropped` is allowed for superseded generations, and no second `timeout fired` appears before the next `touch`/`arm` cycle.

> All strategic §6 blockers were resolved on 2026-05-16 by delegated engineering defaults. `/spec-dev S0228` may proceed.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` + `app_v2.md` regenerated.
- [ ] `dev/FUNCTIONALITY.log` carries a FIX entry for S0228.
- [ ] `/spec-check S0228` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0228`.

---

## Blockers Log

- 2026-05-16 — Initial tactical plan authored with temporary blockers on strategic §6.1 / §6.2 / §6.3.
- 2026-05-16 — Blockers resolved by delegated engineering defaults: generation-token ownership, shared-layer-only stale guard, and exact manual log predicate frozen. Execution may continue.

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-16 — Pre-Implementation Blockers resolved in-place; tactical execution unblocked.