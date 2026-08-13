# Tactical Plan: S0145 — bugfix-print-rejected-context-not-activity

**Strategic spec:** [`../S0145_bugfix-print-rejected-context-not-activity.md`](../S0145_bugfix-print-rejected-context-not-activity.md)
**Feature:** Document/image print rejected on Samsung One UI / Android 13+ (context not recognised as Activity)
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 90
**Status:** Verified
**Phases:** 5 / 5 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | print-environment-probe | — | ✅ Done | 3/3 | [PHASE_01__print-environment-probe.md](PHASE_01__print-environment-probe.md) |
| 02 | direct-dispatch-fix | 01 + research §6.1/§6.2 | ⏭️ Skipped | 0/3 | [PHASE_02__direct-dispatch-fix.md](PHASE_02__direct-dispatch-fix.md) |
| 03 | system-print-fallback | 01 | ✅ Done | 4/4 | [PHASE_03__system-print-fallback.md](PHASE_03__system-print-fallback.md) |
| 04 | print-messages | 03 | ✅ Done | 2/2 | [PHASE_04__print-messages.md](PHASE_04__print-messages.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.1:** **Resolved 2026-05-10** — field log `fastmediasorter_20260510_201249.log`, Samsung SM-S731B (Galaxy S25), Android 16 / API 36. Root cause: `Activity.getSystemService(PRINT_SERVICE)` on Samsung API 36 delegates through `ContextWrapper` chain → `SystemServiceRegistry` binds `ContextThemeWrapper` as `mContext` in `PrintManager`. `PrintManager.print()` checks `mContext instanceof Activity` (line 519) → false → `IllegalStateException`.
- [x] **Research §6.2:** **Resolved 2026-05-10** — `ContextThemeWrapper` wrap is **insufficient** (code already uses Activity; Samsung delegates `getSystemService` through `ContextThemeWrapper` to `ContextImpl` regardless). Phase 02 must NOT add more wrapping. Viable paths: (a) bypass `getSystemService` delegation to obtain `PrintManager` with raw Activity context, or (b) dedicated transparent print Activity (strategic §6.3). Decision delegated to Phase 02.

> All blockers cleared. Phase 02 is unblocked and ready for implementation.

---

## Completion Gate

- [ ] All phases show ✅ Done (or ⏭️ Skipped with reason).
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Print entry — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0145` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [ ] All `Timber.d("S0145:` tags and any temporary print-environment diagnostics removed (done at the `Verified` transition).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0145`.

---

## Blockers Log

- 2026-05-10 — Phase 02 blocked: needs a field log from a Samsung One UI device built with Phase 01 diagnostics to resolve strategic §6.1/§6.2. Next: owner runs the Phase 01 build on the Galaxy S25, exports the log; then concretise Phase 02 steps and unblock.
- 2026-05-10 — Phases 01/03/04 done. Spec set to `BlockNeedUserTest`. Debug tags present: `Timber.d("S0145: printCurrentFile…")` in `DocumentPrintManager`, `Timber.w("S0145: print fallback…")` in `PlayerPrintFallbackManager`. Required action: install current build on Galaxy S25, attempt to print a file, export log → confirms Phase 03 fallback works and captures §6.1 diagnostics.
- 2026-05-10 — **Blockers §6.1/§6.2 resolved** from field log `fastmediasorter_20260510_201249.log` (Samsung SM-S731B, Android 16 / API 36). Print attempted on PDF, failed with `IllegalStateException: Can print only from an activity`. Context chain captured: `PlayerActivity(Activity) → ContextThemeWrapper → ContextImpl`. Root cause: Samsung API 36 `getSystemService(PRINT_SERVICE)` returns `PrintManager` with `mContext = ContextThemeWrapper` — fails `instanceof Activity` check. Phase 03 fallback to share confirmed working. `Timber.d("S0145: printCurrentFile")` tag removed, spec re-opened `Tactical`. Phase 02 steps require re-authoring (old `ContextThemeWrapper` approach is ruled out).

---

## Change Log

- 2026-05-10 — Initial tactical plan authored by `/spec-tech`.
