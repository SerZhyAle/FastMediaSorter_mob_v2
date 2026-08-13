# Tactical Plan: S0053 — bugfix-m2ts-bdmv-network-playback

**Strategic spec:** [`../S0053_bugfix-m2ts-bdmv-network-playback.md`](../S0053_bugfix-m2ts-bdmv-network-playback.md)
**Feature:** .m2ts (BDMV) playback via network sources (SFTP, SMB, FTP)
**Tier:** 3 — Moderate
**Priority:** 65
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-05-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | informative-error | — | ✅ Done | 4/4 | [PHASE_01__informative-error.md](PHASE_01__informative-error.md) |
| 02 | bd-ts-strip-datasource | 01 | ✅ Done | 4/4 | [PHASE_02__bd-ts-strip-datasource.md](PHASE_02__bd-ts-strip-datasource.md) |
| 03 | wire-bd-ts-playback | 02 | ✅ Done | 4/4 | [PHASE_03__wire-bd-ts-playback.md](PHASE_03__wire-bd-ts-playback.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Research items from strategic §6 — resolved inline during `/spec-all` investigation:

- [x] **Research:** Media3 1.2.1 BD-packet support — `DefaultExtractorsFactory.setTsExtractorFlags()` has no 192-byte BD-TS flag; requires custom `DataSource` wrapper. Resolved: Phase 02 implements `BdTsStripDataSource`.
- [x] **Research:** Local proxy as bridge for MediaPlayer — `LocalCastProxyServer` serves local files only and has no Range-header support; cannot bridge SFTP/SMB/FTP to MediaPlayer. Resolved: custom DataSource approach (Phase 02-03) is the correct path.
- [x] **Research:** SMB/FTP behavior vs SFTP — all three protocols use identical ExoPlayer flow; fallback is skipped identically for all. Resolved: Phase 03 applies the same wrapper to all three.

No blockers. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8 — only if .m2ts actually plays; informative error alone does not qualify).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes added in Phase 02-03).
- [ ] `/spec-check S0053` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0053`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-02 — Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
