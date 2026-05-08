# Tactical Plan: S0113 — sftp-pool-stability

**Strategic spec:** [`../S0113_sftp-pool-stability.md`](../S0113_sftp-pool-stability.md)
**Feature:** SFTP connection pool stability — idle eviction guard, download deduplication, recoverable error triage
**Tier:** 3 — Moderate
**Priority:** 80
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|------------|--------|------:|------|
| 01 | pool-active-guard | — | ✅ Done | 5/5 | [PHASE_01__pool-active-guard.md](PHASE_01__pool-active-guard.md) |
| 02 | download-dedup | — | ✅ Done | 5/5 | [PHASE_02__download-dedup.md](PHASE_02__download-dedup.md) |
| 03 | error-triage | 01 | ✅ Done | 4/4 | [PHASE_03__error-triage.md](PHASE_03__error-triage.md) |
| 04 | unified-session | 01 | ✅ Done | 6/6 | [PHASE_04__unified-session.md](PHASE_04__unified-session.md) |
| 05 | docs-catalog-cleanup | 01, 02, 03, 04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phases 01, 02, 03 have no blockers and may start immediately.
Phase 04 is blocked by open research items — do not start until both are Resolved.

- [x] **Research #1:** Max safe JSch channels per SSH session — Resolved.  
  MAX_CHANNELS_PER_SESSION = 5 kept (OpenSSH default MaxSessions = 10). Split: MAX_PLAYBACK_CHANNELS = 1, MAX_FILE_OPS_CHANNELS = 4. Safe for all target servers.
- [x] **Research #2:** JSch `Session.openChannel()` thread-safety — Resolved.  
  JSch's openChannel() is NOT thread-safe (no internal synchronization on Channel[] array). Mitigation: `PooledConnection.openChannelLock: ReentrantLock` serializes all openChannel() calls across blocking and suspend paths.

---

## Completion Gate

- [ ] All phases show ✅ Done (or ⏭️ Skipped for Phase 04 if research is deferred).
- [ ] `docs/FEATURES.md` unchanged — no user-facing surface changed (confirmed in strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after all `.kt` additions/changes.
- [ ] `/spec-check S0113` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0113`.

---

## Blockers Log

- 2026-05-08 — Phase 04 blocked: JSch channel limit (Research #1) and thread-safety (Research #2) unresolved.

---

## Change Log

- 2026-05-08 — Initial tactical plan authored by `/spec-tech`.
