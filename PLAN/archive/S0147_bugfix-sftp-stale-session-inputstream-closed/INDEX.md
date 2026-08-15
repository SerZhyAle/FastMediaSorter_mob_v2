# Tactical Plan: S0147 — bugfix-sftp-stale-session-inputstream-closed

**Strategic spec:** [`../S0147_bugfix-sftp-stale-session-inputstream-closed.md`](../S0147_bugfix-sftp-stale-session-inputstream-closed.md)
**Feature:** SFTP stale session `IOException: inputstream is closed` — silent reconnect on dead JSch transport
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | stale-session-retry | — | ✅ Done | 4/4 | [PHASE_01__stale-session-retry.md](PHASE_01__stale-session-retry.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 3/3 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.1:** Full set of JSch dead-transport error strings. `DEAD_TRANSPORT_MESSAGES` = ["inputstream is closed" (field-confirmed), "channel is not opened" (Channel.checkConnected()), "broken pipe" (SocketException)].
- [x] **Research §6.2:** `session.isConnected()` is a simple boolean field read (`return _isConnected`); does not re-check the socket — confirmed stays `true` after silent TCP drop. This explains why the existing retry path did not fire in the field log.

> Both blockers are resolvable by reading JSch source (`Channel.java`, `Session.java`) — no device test required. Phase 01 Step 01.1 is the resolution step.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update needed (non-user-facing, see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0147` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [ ] All `Timber.d("S0147:` tags removed (done at the `Verified` transition).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0147`.

---

## Blockers Log

_(none yet)_

---

## Change Log

- 2026-05-10 — Initial tactical plan authored by `/spec-tech`.
