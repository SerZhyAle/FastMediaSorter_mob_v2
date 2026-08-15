# Tactical Plan: S0472 - screenshot-gesture-send-to-recipients

**Strategic spec:** [`../S0472_screenshot-gesture-send-to-recipients.md`](../S0472_screenshot-gesture-send-to-recipients.md)
**Research inputs:** [`research/01__capture-to-curated-send-seam.md`](research/01__capture-to-curated-send-seam.md)
**Feature:** Жест-скриншот: отправка в выбранных получателей
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-06-17

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | curated-send-action | - | ✅ Done | 3/3 | [PHASE_01__curated-send-action.md](PHASE_01__curated-send-action.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Architecture decisions (resolved during planning)

- **Host = existing standalone viewer.** The curated «Send to..» dialog needs a `FragmentActivity`; the post-capture dispatcher runs from a Service. Resolution: reuse the existing `PhotoVideoStandaloneActivity` + `EXTRA_AUTO_ACTION` launch path - the same verified mechanism the dispatcher already uses for `OPEN_IN_DRAW` and `OCR_TRANSLATE`. A new `AUTO_ACTION_SEND_TO` triggers the activity's existing curated-send call. No new Activity, no new manifest entry, no new background-launch path.
- **All changes are `src/main` (shared).** The screenshot-gesture settings group is rendered only in noLegal (empty injected-controller set elsewhere), so the new action is reachable only there - the flavor gate is inherited, not re-implemented. No `src/<flavor>` files, no `BuildConfig.IS_*` guard.
- **Reuse, do not rebuild, the curated send.** The new action calls the standalone viewer's existing send-to entry, which builds the receiver list from the same shared «Send file to» settings (image-applicable + enabled), with single-receiver direct-send inherited.

---

## Pre-Implementation Blockers

Both strategic §6 items were resolved during tactical planning from `research/01`; no open blocker remains.

- [x] **Research §6.1 - host context for the curated dialog:** RESOLVED - reuse `PhotoVideoStandaloneActivity` + new `EXTRA_AUTO_ACTION`, mirroring the verified `OPEN_IN_DRAW` / `OCR_TRANSLATE` route.
- [x] **Decision §6.2 - empty applicable-recipient list:** RESOLVED - mirror the existing `SendToMenuManager` convention (no sheet shown; the screenshot stays open in the viewer as the predictable fallback). A dedicated empty-list hint is deferred as optional polish (strategic §3.1 wish, not required this iteration).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal.md` + `_RU` + `_UK` updated (strategic §8 - noLegal feature; public `docs/FEATURES*.md` untouched).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new enum constant on the public surface).
- [ ] noLegal debug build passes (`.\a.ps1 nd` or `assembleNoLegalDebug`).
- [ ] `/spec-check S0472` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0472`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-17 - Initial tactical plan authored by `/spec-tech`.
