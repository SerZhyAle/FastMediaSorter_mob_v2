# Tactical Plan: S0624 - bugfix-sftp-scan-hang-network

**Strategic spec:** [`../S0624_bugfix-sftp-scan-hang-network.md`](../S0624_bugfix-sftp-scan-hang-network.md)
**Research inputs:** [`research/01__root-cause-analysis.md`](research/01__root-cause-analysis.md) · [`research/05__forced-reset-lease-safety.md`](research/05__forced-reset-lease-safety.md)
**Feature:** SFTP scan hang on network handover - bounded termination + error channel
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | sftp-network-invalidation | - | ✅ Done | 2/2 | [PHASE_01__sftp-network-invalidation.md](PHASE_01__sftp-network-invalidation.md) |
| 02 | scan-timeout-error-channel | 01 | ✅ Done | 5/5 | [PHASE_02__scan-timeout-error-channel.md](PHASE_02__scan-timeout-error-channel.md) |
| 03 | sftp-session-hardening | 01 | ✅ Done | 2/2 | [PHASE_03__sftp-session-hardening.md](PHASE_03__sftp-session-hardening.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The only code-blocking research item (strategic §6.5 - forced-reset lease safety) is **Resolved** via [`research/05__forced-reset-lease-safety.md`](research/05__forced-reset-lease-safety.md): `disconnectAll()` takes only `poolMutex` (free under a parked scan), never the channel mutex, so the force-reset is deadlock-free and unblocks the parked listing.

Remaining strategic §6 items are not code blockers:

- §6.2 (repro on stable Wi-Fi) and §6.4 (catalog size vs watchdog budget) - **device verification**, handled at the `BlockNeedUserTest` gate, not before Phase 01.
- §6.3 (`/J:/..` path validity) - **out of scope** per strategic §2 non-goal (separate ticket if confirmed on device).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public symbol `ScanTimeoutException`).
- [ ] `/spec-check S0624` returns `Verified` (after device test confirms bounded termination).
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All code done + device verification required: set journal status `BlockNeedUserTest` with the §6.2/§6.4 test script as the note; on device confirmation run `/spec-check`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech` (4 phases; §6.5 resolved inline via research/05).
