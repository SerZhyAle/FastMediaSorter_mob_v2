# Tactical Plan: S1651 - sftp-unreachable-host-stacked-timeouts

**Strategic spec:** [`../S1651_sftp-unreachable-host-stacked-timeouts.md`](../S1651_sftp-unreachable-host-stacked-timeouts.md)
**Research inputs:** [`research/01__connection-failure-eligibility.md`](research/01__connection-failure-eligibility.md), [`research/02__cooldown-and-invalidation.md`](research/02__cooldown-and-invalidation.md)
**Feature:** Reuse recent unreachable SFTP connection failures
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done - pending the owning session's closure run
**Phases:** 3 / 3 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | failure-cache | none | ✅ Done | 2/2 | [PHASE_01__failure-cache.md](PHASE_01__failure-cache.md) |
| 02 | pool-integration | 01 | ✅ Done | 3/3 | [PHASE_02__pool-integration.md](PHASE_02__pool-integration.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Connection failure eligibility - resolved in `research/01__connection-failure-eligibility.md`.
- [x] **Research:** Cooldown and invalidation - resolved in `research/02__cooldown-and-invalidation.md`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] No public `docs/FEATURES` update is required by strategic §8; record the capability change through the standard close workflow - **open, closure-owned**. Recommended: user-visible record in `docs/ALL_FEATURES.jsonl`; see Step 03.2.
- [ ] `dev/CHANGELOG.md` has entry for every modified file - **open, closure-owned** (`post-change.ps1 -ChangeType Kotlin -ScopeToFile`).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [ ] `/spec-check S1651` returns `Verified` - **open, closure-owned**.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` - **open, closure-owned**.

### Implementation file set (for the closure run)

- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionFailureCache.kt` (new)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` (modified)
- `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionFailureCacheTest.kt` (new)
- `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPoolTest.kt` (modified)

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1651`.

---

## Blockers Log

- None.

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-14 - All three phases executed. Two plan-vs-codebase corrections recorded in Step 02.1: the guard moved behind the live-session reuse check, and the pool's second handshake site (blocking PLAYBACK) was wired to the same hooks. Closure actions left to the owning session.
