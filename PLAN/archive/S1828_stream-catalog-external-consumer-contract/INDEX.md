# Tactical Plan: S1828 - stream-catalog-external-consumer-contract

**Strategic spec:** [`../S1828_stream-catalog-external-consumer-contract.md`](../S1828_stream-catalog-external-consumer-contract.md)
**Research inputs:** none (strategic §6 items 1 and 2 resolved in place; item 3 carried by S1835)
**Feature:** External-consumer contract for the stream catalog
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 65
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-08-20

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | consumer-registry | - | ✅ Done | 3/3 | [PHASE_01__consumer-registry.md](PHASE_01__consumer-registry.md) |
| 02 | pinned-revision-gate | 01 | ✅ Done | 3/3 | [PHASE_02__pinned-revision-gate.md](PHASE_02__pinned-revision-gate.md) |
| 03 | document-registry-records | 01, 02 | ✅ Done | 2/2 | [PHASE_03__document-registry-records.md](PHASE_03__document-registry-records.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 item 3 (`artwork-manifest.json` as a declared invalidation handle) is `Open` but carries `Carrier: S1835`, and strategic §2 Non-goals exclude it from this ticket. Phase 01 records it as an extension point rather than a declared contract, so no phase here waits on it.

- [x] **Research:** which of the eleven invariants are checked, held by construction, or unprotected - Resolved in strategic §6.1.
- [x] **Research:** whether a producer of non-empty `access` still exists - Resolved in strategic §6.2.

---

## Correction carried into this plan

Strategic §4 states that one revision parameter is substituted into four asset names. The tree carries **two** parameters, both defaulting to `v3`:

- `$SheetRev` (`scripts/streams/collect-stream-candidates.ps1:183`) -> `channel-preview-atlas-{rev}.webp`, `stream-logo-atlas-{rev}.webp`
- `$CoordsRev` (`scripts/streams/collect-stream-candidates.ps1:184`) -> `channel-preview-coords-{rev}.json`, `stream-logo-coords-{rev}.json`

Phase 02 therefore checks both parameters, not one. Substitution happens in `Invoke-PublishChannelPreviewAtlas` (line 2228) and `Invoke-PublishStreamLogoAtlas` (line 2467).

Second correction, from strategic §0's second consumer letter: the consumer pins **both** preview revisions, `-v1` and `-v3`, and switches to neither on its own. The publisher has one revision default, so a check demanding that every pinned name be produced by the next run would refuse every publication permanently rather than occasionally, which is not the trade strategic §7 accepted. The pinned-asset block therefore carries a `Coverage` column with the tokens `default` and `frozen`, and Phase 02 fails only on a `default` row that stopped being produced.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped; strategic §8 reads "Без изменений в docs/FEATURES".
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - not expected; no Kotlin is touched.
- [ ] `/spec-check S1828` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1828`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-20 - Initial tactical plan authored by `/spec-tech`.
