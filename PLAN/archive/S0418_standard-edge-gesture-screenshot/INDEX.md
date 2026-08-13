# Tactical Plan: S0418 - standard-edge-gesture-screenshot (Play phase)

**Strategic spec:** [`../S0418_standard-edge-gesture-screenshot.md`](../S0418_standard-edge-gesture-screenshot.md)
**Research inputs:** [`research/01__play-capture-port-analysis.md`](research/01__play-capture-port-analysis.md)
**Feature:** Port the edge gesture-strip screenshot capability from `noLegal` to Play flavors `standard` + `photos`, capturing via MediaProjection only (consent-based). The silent accessibility path stays `noLegal`-exclusive.
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 20
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-06-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec. The `src/main` core is already flavor-agnostic and is not modified; this plan adds a Play flavor implementation and refactors shared machinery into a shared source set.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | extract-shared-capture-machinery | - | ✅ Done | 4/4 | [PHASE_01__extract-shared-capture-machinery.md](PHASE_01__extract-shared-capture-machinery.md) |
| 02 | play-flavor-capture-impl | 01 | ✅ Done | 3/3 | [PHASE_02__play-flavor-capture-impl.md](PHASE_02__play-flavor-capture-impl.md) |
| 03 | play-flavor-manifests | 02 | ✅ Done | 3/3 | [PHASE_03__play-flavor-manifests.md](PHASE_03__play-flavor-manifests.md) |
| 04 | docs-catalog-features | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_04__docs-catalog-features.md](PHASE_04__docs-catalog-features.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- **Deferred (Play release gate, does NOT block impl/build):** Play Console declaration forms for `SYSTEM_ALERT_WINDOW` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`. Strategic §6.1. Filed at submission time, not here.

---

## Completion Gate

- [x] All phases ✅ Done.
- [x] `assembleStandardDebug` green (Play flavor compiles; merged manifest carries the screencapture services + perms, NO accessibility service - verified in packaged manifest).
- [x] `assembleNoLegalDebug` green (no regression from the machinery move).
- [x] `assemblePhotosDebug` green (second Play target compiles).
- [x] `docs/FEATURES.md` (+ `_RU`/`_UK`) updated - capability now in Play builds.
- [x] `dev/CHANGELOG.md` has entries for the modified files.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0418` returns `Verified` - pending on-device test (status `BlockNeedUserTest`).

---

## How to Track Progress

1. Before a phase: flip row to `🚧 In Progress`, update `Phases: X/N done`.
2. During: flip step `[~]` when started, `[x]` when its Verification passes. Never `[x]` on intent.
3. On phase completion: confirm every step `[x]` + Phase Done Criteria, flip row `✅ Done`, bump counter.
4. If blocked: flip `⛔ Blocked`, add a Blockers Log bullet; set journal status if the whole spec is blocked.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-15 - Initial tactical plan authored by `/spec-all` (F2).
