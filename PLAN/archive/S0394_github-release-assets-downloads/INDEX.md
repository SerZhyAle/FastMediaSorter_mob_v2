# Tactical Plan: S0394 - github-release-assets-downloads

**Strategic spec:** [`../S0394_github-release-assets-downloads.md`](../S0394_github-release-assets-downloads.md)
**Research inputs:** [`research/01__wear-release-signing.md`](research/01__wear-release-signing.md) · [`research/02__spectrum-version-reconciliation.md`](research/02__spectrum-version-reconciliation.md) · [`research/03__website-release-rendering.md`](research/03__website-release-rendering.md) · [`research/04__spectrum-build-orchestrator.md`](research/04__spectrum-build-orchestrator.md)
**Feature:** Auto-publish full release spectrum to GitHub Release assets + website downloads
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 6 / 6 done
**Last updated:** 2026-06-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | wear-release-signing | - | ✅ Done | 1/1 | [PHASE_01__wear-release-signing.md](PHASE_01__wear-release-signing.md) |
| 02 | release-spectrum-build | 01 | ✅ Done | 2/2 | [PHASE_02__release-spectrum-build.md](PHASE_02__release-spectrum-build.md) |
| 03 | extended-publisher | 02 | ✅ Done | 3/3 | [PHASE_03__extended-publisher.md](PHASE_03__extended-publisher.md) |
| 04 | website-download-showcase | 03 | ✅ Done | 3/3 | [PHASE_04__website-download-showcase.md](PHASE_04__website-download-showcase.md) |
| 05 | downloads-docs-and-matrix | 03 | ✅ Done | 2/2 | [PHASE_05__downloads-docs-and-matrix.md](PHASE_05__downloads-docs-and-matrix.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (see Research inputs above).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений": distribution channel, not an app capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` - skip (no Kotlin public API changed; only gradle config, scripts, html, docs).
- [ ] `/spec-check S0394` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0394`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-10 - Initial tactical plan authored by `/spec-tech`.
