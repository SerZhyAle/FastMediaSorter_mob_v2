# Tactical Plan: S0099 — sftp-concurrent-access-fix

**Strategic spec:** [`../S0099_sftp-concurrent-access-fix.md`](../S0099_sftp-concurrent-access-fix.md)
**Feature:** SFTP concurrent playback and file operations
**Tier:** 3 — Moderate
**Priority:** 70
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | session-pool-isolation | — | ✅ Done | 6/6 | [PHASE_01__session-pool-isolation.md](PHASE_01__session-pool-isolation.md) |
| 02 | retry-with-backoff | 01 | ✅ Done | 4/4 | [PHASE_02__retry-with-backoff.md](PHASE_02__retry-with-backoff.md) |
| 03 | sftp-error-strings | 02 | ✅ Done | 3/3 | [PHASE_03__sftp-error-strings.md](PHASE_03__sftp-error-strings.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.2:** `invalidateConnection()` blast radius — Resolved. Shared `connectionPool` map; ExoPlayer and FILE_OPS sessions are the same CHM entry. Phase 01 addresses this by introducing `playbackConnectionPool`.
- [x] **Research §6.1:** JSch exception when server rejects second session (MaxSessions) — Resolved. JSch throws `JSchException` for all session-level failures; MaxSessions rejection surfaces as `JSchException` with SSH disconnect reason code 11. Phase 03 maps this via `error_sftp_connection_limit`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated with SFTP concurrent playback bullet.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0099` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0099`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
