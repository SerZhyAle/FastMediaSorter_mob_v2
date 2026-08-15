# Tactical Plan: S0229 — bugfix-smb-audio-metadata-browse-instability

**Strategic spec:** [`../S0229_bugfix-smb-audio-metadata-browse-instability.md`](../S0229_bugfix-smb-audio-metadata-browse-instability.md)
**Feature:** SMB audio metadata browse stability — Handler fix, EOFException downgrade, concurrency reduction
**Tier:** 3 — Moderate (ad-hoc, bugfix)
**Priority:** 75
**Status:** BlockNeedUserTest
**Phases:** 1 / 2 done (Phase 01 — 5/5; Phase 02 — 3/4, Step 02.4 deferred to on-device BlockNeedUserTest run)
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Research Summary (pre-tactical)

All four §6 open items resolved from codebase before writing this plan:

1. **`Handler on a dead thread`** — `extractMetadataFromBytes()` calls `trackGroupsFuture.get(5, TimeUnit.SECONDS)` — a blocking Java future `.get()` — from a coroutine on `Dispatchers.IO`. When the coroutine scope is cancelled (user navigates away), the thread does not receive an interrupt, so `MetadataRetriever`'s internal handler may be on a dead/cancelled thread when the future resolves. Fix: `runInterruptible { trackGroupsFuture.get(5, TimeUnit.SECONDS) }`.

2. **`EOFException` classification** — `shouldLogMetadataRetrieverFailureAsDebug()` currently downgrades only `UnrecognizedInputFormatException`. `EOFException` and `IOException` from parsing 64KB partial headers are equally expected misses on partial-read network paths. Fix: extend the check to include `java.io.EOFException` and `java.io.IOException` in the cause chain.

3. **Parser choice** — no parser change needed; the MetadataRetriever call site (temp-file bridge) is correct. The two fixes above (runInterruptible + EOFException downgrade) are sufficient.

4. **Concurrency limit** — semaphore is currently `Semaphore(3)`. Reducing to `Semaphore(2)` limits simultaneous partial-read fetches during scroll-idle burst, reducing jank pressure without perceptibly slowing enrichment.

Primary target file: `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt` (773 LOC — backup required before edit).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | exception-policy-handler-fix | — | ✅ Done | 5/5 | [PHASE_01__exception-policy-handler-fix.md](PHASE_01__exception-policy-handler-fix.md) |
| 02 | docs-catalog-cleanup | 01 | 🚧 In Progress | 3/4 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` — skip; strategic §8 states "Без изменений".
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0229` returns `Verified` (or `BlockNeedUserTest` if on-device check is required).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0229`.

---

## Blockers Log

- 2026-05-16 — Phase 02 Step 02.4 device acceptance DEFERRED: requires device connected to SMB share with audio files. Verify: no `Handler on a dead thread` in browse metadata path, EOFException noise downgraded to debug, artist/title still enriched in visible rows. Two `Timber.d("S0229: …")` tags inserted at the changed flow entries (shouldLogMetadataRetrieverFailureAsDebug + extractMetadataFromBytes future await).

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-all`.
