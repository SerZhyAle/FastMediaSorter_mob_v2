# Tactical Plan: S0025 — smb-fast-fail

**Strategic spec:** [`../S0025_smb-fast-fail.md`](../S0025_smb-fast-fail.md)
**Feature:** Fast-fail for remote resources when network is absent (no-network gate for SMB/FTP/SFTP/Cloud, Wi-Fi gate for SMB, smart retry for SMB)
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-04-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | — | ✅ Done | 6/6 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | smb-integration | 01 | ✅ Done | 6/6 | [PHASE_02__smb-integration.md](PHASE_02__smb-integration.md) |
| 03 | ftp-sftp-integration | 01 | ✅ Done | 4/4 | [PHASE_03__ftp-sftp-integration.md](PHASE_03__ftp-sftp-integration.md) |
| 04 | cloud-integration | 01 | ✅ Done | 5/5 | [PHASE_04__cloud-integration.md](PHASE_04__cloud-integration.md) |
| 05 | docs-catalog-cleanup | 02, 03, 04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items in the strategic spec are `Resolved`. No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` unchanged (per strategic §8 — no user-visible feature added).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated (public APIs of network sources changed).
- [ ] `/spec-check S0025` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0025`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-04-29 — Initial tactical plan authored by `/spec-tech`.
