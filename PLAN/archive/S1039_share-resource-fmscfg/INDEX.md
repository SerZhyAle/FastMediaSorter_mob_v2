# Tactical Plan: S1039 - share-resource-fmscfg

**Strategic spec:** [`../S1039_share-resource-fmscfg.md`](../S1039_share-resource-fmscfg.md)
**Research inputs:** [`research/01__fmscfg-share-architecture.md`](research/01__fmscfg-share-architecture.md)
**Feature:** Share a resource's `.fmscfg` as a QR code
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device verification)
**Phases:** 6 / 6 done
**Last updated:** 2026-07-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | compact-serialization | - | ✅ Done | 2/2 | [PHASE_01__compact-serialization.md](PHASE_01__compact-serialization.md) |
| 02 | qr-encoder | - | ✅ Done | 2/2 | [PHASE_02__qr-encoder.md](PHASE_02__qr-encoder.md) |
| 03 | qr-display-screen | 02 | ✅ Done | 4/4 | [PHASE_03__qr-display-screen.md](PHASE_03__qr-display-screen.md) |
| 04 | qr-share-domain | 01, 03 | ✅ Done | 4/4 | [PHASE_04__qr-share-domain.md](PHASE_04__qr-share-domain.md) |
| 05 | qr-share-dialog-wiring | 03, 04 | ✅ Done | 3/3 | [PHASE_05__qr-share-dialog-wiring.md](PHASE_05__qr-share-dialog-wiring.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 has no open research items (all design forks resolved from the frozen `.fmscfg` contract and codebase; see `research/01`).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/ALL_FEATURES.jsonl` has the S1039 capability record (showcase `docs/FEATURES*.md` is `/skill-release`-owned, not edited per-spec - CLAUDE.md §11).
- [x] `dev/CHANGELOG.md` has an entry for the change.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes: `QrCodeEncoder`, `CompanionQrShareActivity`, roles set).
- [ ] `/spec-check S1039` returns `Verified` - pending on-device QR scan round-trip (status now `BlockNeedUserTest`).
- [ ] Strategic spec `Status:` advanced by `/spec-check` after device verification.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log; set the journal status if the whole spec is blocked.
5. All done: run `/spec-check S1039`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-14 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-14 - All 6 phases implemented via `/spec-dev` (build green, 2 unit tests pass, fast gates green). Status -> `BlockNeedUserTest`; 2 `S1039:` debug tags inserted. Device verification (QR scan round-trip) deferred - no usable device (single emulator without the app/SFTP cannot cover the two-device scan).
