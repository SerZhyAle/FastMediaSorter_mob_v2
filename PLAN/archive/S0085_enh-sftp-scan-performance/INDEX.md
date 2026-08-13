# Tactical Plan: S0085 — enh-sftp-scan-performance

**Strategic spec:** [`../S0085_enh-sftp-scan-performance.md`](../S0085_enh-sftp-scan-performance.md)
**Feature:** Eliminate per-file stat() calls in SFTP scanner + wire scan progress reporting
**Tier:** 3 — Moderate
**Priority:** 70
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | listfiles-attr-contract | — | ✅ Done | 4/4 | [PHASE_01__listfiles-attr-contract.md](PHASE_01__listfiles-attr-contract.md) |
| 02 | scanner-stat-elimination | 01 | ✅ Done | 5/5 | [PHASE_02__scanner-stat-elimination.md](PHASE_02__scanner-stat-elimination.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 items resolved by code inspection before writing this plan:

- **§6.1 Null attrs** — JSch `channel.ls()` uses SFTP `readdir` (protocol v3+); `SftpATTRS` object is always present per the JSch source. Guard: add null/zero-size fallback to `stat()` in Phase 02 scanner steps for maximum compatibility. **Resolved.**
- **§6.2 stat() usage outside scanner** — `sftpClient.stat()` is also used in `SftpOperationStrategy` (7 sites, file transfer) and `SftpDataSource` (1 site, playback). Both are unrelated to scanning and must NOT be changed. `SftpMediaScanner` has 3 stat() call sites — all targeted by Phase 02. **Resolved.**

No blockers — Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `SftpClient` changed).
- [ ] `/spec-check S0085` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, set journal to `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0085`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-05 — Initial tactical plan authored by `/spec-tech`.
