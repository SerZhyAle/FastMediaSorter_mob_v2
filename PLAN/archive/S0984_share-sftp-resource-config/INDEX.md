# Tactical Plan: S0984 - share-sftp-resource-config

**Strategic spec:** [`../S0984_share-sftp-resource-config.md`](../S0984_share-sftp-resource-config.md)
**Research inputs:** [`research/01__companion-artifacts-map.md`](research/01__companion-artifacts-map.md)
**Feature:** Share an SFTP resource by `.fmscfg` file (Telegram/email) + one-tap import of a received config
**Tier:** Ad-hoc
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-07-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | contract-relaxation | - | ✅ Done | 4/4 | [PHASE_01__contract-relaxation.md](PHASE_01__contract-relaxation.md) |
| 02 | export-domain | 01 | ✅ Done | 2/2 | [PHASE_02__export-domain.md](PHASE_02__export-domain.md) |
| 03 | export-ui | 02 | ✅ Done | 5/5 | [PHASE_03__export-ui.md](PHASE_03__export-ui.md) |
| 04 | import-trampoline | 01 | ✅ Done | 4/4 | [PHASE_04__import-trampoline.md](PHASE_04__import-trampoline.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 item is Resolved (quiz + architecture decisions 2026-07-10); research 01 folded the remaining open questions into concrete steps.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (owned by `/skill-release`; strategic §8 not a FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes introduced).
- [ ] `docs/ALL_FEATURES.jsonl` has an S0984 record.
- [ ] `/spec-check S0984` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log, set the journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0984`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-11 - Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
