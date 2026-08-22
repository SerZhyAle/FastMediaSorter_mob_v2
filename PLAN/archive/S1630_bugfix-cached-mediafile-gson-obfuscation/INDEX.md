# Tactical Plan: S1630 - bugfix-cached-mediafile-gson-obfuscation

**Strategic spec:** [`../S1630_bugfix-cached-mediafile-gson-obfuscation.md`](../S1630_bugfix-cached-mediafile-gson-obfuscation.md)
**Research inputs:** [`research/01__cached-blob-r8-compatibility.md`](research/01__cached-blob-r8-compatibility.md)
**Feature:** Protect cached media-file blobs from incompatible R8 serialization
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | serialization-contract | - | ✅ Done | 1/1 | [PHASE_01__serialization-contract.md](PHASE_01__serialization-contract.md) |
| 02 | cache-boundary-recovery | 01 | ✅ Done | 2/2 | [PHASE_02__cache-boundary-recovery.md](PHASE_02__cache-boundary-recovery.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 1/1 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic research item 1 is Resolved.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic §8 says no changes.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1630` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1630`.

---

## Blockers Log

- None.

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-14 - Phases 02 and 03 executed by `/spec-all`: cache-boundary tests, targeted R8 evidence, scoped closure.
