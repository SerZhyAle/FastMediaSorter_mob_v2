# Tactical Plan: S0117 - url-media-downloader-nolegal-flavor

**Strategic spec:** [../S0117_url-media-downloader-nolegal-flavor.md](../S0117_url-media-downloader-nolegal-flavor.md)
**Feature:** `noLegal` flavor with site-specific URL extraction and album batch handling
**Tier:** 3 - Moderate
**Priority:** 30
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-09

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | flavor-foundation | - | ✅ Done | 1/1 | [PHASE_01__flavor-foundation.md](PHASE_01__flavor-foundation.md) |
| 02 | site-resolver | 01 | ✅ Done | 3/3 | [PHASE_02__site-resolver.md](PHASE_02__site-resolver.md) |
| 03 | batch-coordinator | 02 | ✅ Done | 3/3 | [PHASE_03__batch-coordinator.md](PHASE_03__batch-coordinator.md) |
| 04 | license-ui | 03 | ✅ Done | 2/2 | [PHASE_04__license-ui.md](PHASE_04__license-ui.md) |
| 05 | docs-catalog-cleanup | 04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** pin NewPipe dependency/version. Closed in strategic §6.1.
- [x] **Research:** choose GPL notice placement. Closed in strategic §6.2.
- [x] **Research:** confirm album partial-failure policy. Closed in strategic §6.5.
- [x] **Note:** sideload distribution channel is deferred per strategic §6.4 and does not block code implementation.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] Public FEATURES inventory intentionally remains unchanged per strategic §8.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin API changes.
- [ ] `/spec-check S0117` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0117`.

---

## Blockers Log

- 2026-05-09 - No active blockers after the user confirmed execution defaults for S0117.

---

## Change Log

- 2026-05-09 - Initial tactical plan authored during implementation start. Phase 01 captured as already completed.
- 2026-05-09 - Phases 02-05 completed; implementation awaits only `/spec-check` for Verified status.