# Tactical Plan: S1006 - sftp-multipath-endpoint-fallback

**Strategic spec:** [`../S1006_sftp-multipath-endpoint-fallback.md`](../S1006_sftp-multipath-endpoint-fallback.md)
**Research inputs:** [`research/01__sftp-address-consumers.md`](research/01__sftp-address-consumers.md), [`research/02__happy-eyeballs-probe-tuning.md`](research/02__happy-eyeballs-probe-tuning.md), [`research/03__network-change-signal.md`](research/03__network-change-signal.md)
**Feature:** Multi-path SFTP endpoint fallback for companion resources
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 70
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-07-12

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | resource-alt-paths | - | ✅ Done | 0/5 | [PHASE_01__resource-alt-paths.md](PHASE_01__resource-alt-paths.md) |
| 02 | companion-import-multipath | 01 | ✅ Done | 0/3 | [PHASE_02__companion-import-multipath.md](PHASE_02__companion-import-multipath.md) |
| 03 | endpoint-resolver | 01 | ✅ Done | 0/4 | [PHASE_03__endpoint-resolver.md](PHASE_03__endpoint-resolver.md) |
| 04 | resolver-wire-in | 02, 03 | ✅ Done | 0/6 | [PHASE_04__resolver-wire-in.md](PHASE_04__resolver-wire-in.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 0/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `✅ Done` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see Research inputs). No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 says "не новая фича сама по себе"; owned by `/skill-release`, skip per-spec.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class `SftpEndpointResolver`, new migration).
- [ ] `/spec-check S1006` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log; if the whole spec is blocked, set journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1006`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-12 - Initial tactical plan authored by `/spec-tech`.
