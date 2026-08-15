# Tactical Plan: S0355 - bugfix-cloud-apk-classify-crash

**Strategic spec:** [`../S0355_bugfix-cloud-apk-classify-crash.md`](../S0355_bugfix-cloud-apk-classify-crash.md)
**Feature:** Crash on cloud APK classification (noLegal/VR)
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 90
**Status:** Verified (device-test passed via log 2026-06-08; debug tag removed)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-04

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fault-tolerant-temp-transfer | - | ✅ Done | 5/5 | [PHASE_01__fault-tolerant-temp-transfer.md](PHASE_01__fault-tolerant-temp-transfer.md) |
| 02 | classification-isolation | 01 | ✅ Done | 3/3 | [PHASE_02__classification-isolation.md](PHASE_02__classification-isolation.md) |
| 03 | root-cause-and-temp-protection | 01, 02 | ✅ Done | 2/3 (1 skipped) | [PHASE_03__root-cause-and-temp-protection.md](PHASE_03__root-cause-and-temp-protection.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

These map the two `Status: Open` research items in strategic §6. They gate Phase 03 only.

- [x] **Research:** Root cause of temp-copy disappearance - resolved: OS storage-pressure eviction of `getCacheDir()`. Ruled out app-side cleaners (`CleanupOrphanedTempFilesUseCase` handles only `*.temp_copy`; `TempFileManager.cleanupOldTempFiles` excludes the `cloud_download_` prefix and only acts on files >24h). See strategic §6.1.
- [x] **Research:** Sufficiency of defensive interception - resolved: interception sufficient, no temp protection. Root cause is unfixable in-app without relocating temps out of `cacheDir` (out of scope); resolver already re-validates the cache file post-download. See strategic §6.2.

**Scope of the gate:** Phase 01 (fault-tolerant transfer) and Phase 02 (classification isolation) are research-independent - they remove the crash and the cascade regardless of the deletion cause, and may start immediately. Phase 03 is the only research-gated phase; it must not start while either blocker above is unchecked. If §6.2 concludes interception alone is sufficient, Phase 03 reduces to a documentation step and the optional temp-protection sub-step is `⏭️ Skipped`.

---

## Completion Gate

- [ ] All non-skipped phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - no change (strategic §8 = "Без изменений"; VR recognition is noLegal-only and not in public feature files).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if any public API changed.
- [ ] `/spec-check S0355` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0355`.

---

## Blockers Log

- 2026-06-04 - Phase 03 gated on strategic §6.1 and §6.2 (both `Status: Open`). Phases 01-02 unblocked. Next: resolve research before Phase 03 design.

---

## Change Log

- 2026-06-04 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-04 - Phase 01, 02 implemented (`/spec-dev`). Crash + cascade closed. Builds deferred per "no build".
- 2026-06-04 - Phase 03 research resolved: root cause = OS `getCacheDir()` eviction under storage pressure; verdict = interception sufficient. Step 03.3 (temp-copy protection) `⏭️ Skipped` - the genuine fix (relocate cloud temp out of `cacheDir`) is out of scope and recorded as a follow-up in strategic §6.2.
- 2026-06-09 - Device-test passed: log 2026-06-08 shows ENOSPC caught as `W/App` + degrade-to-NOT_VR, no crash. Status `BlockNeedUserTest -> Verified`; S0355 debug tag removed from `CloudFileOperationHandler`. Deferred root cause (§6.2) carried into follow-up S0388.
